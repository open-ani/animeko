/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.user

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.session.AccessTokenPair
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.client.apis.QRLoginAniApi
import me.him188.ani.client.models.AniConfirmQrLoginRequest
import me.him188.ani.client.models.AniCreateQrLoginRequest
import me.him188.ani.client.models.AniPollQrLoginRequest
import me.him188.ani.client.models.AniQrLoginStatus
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 扫码登录. 待登录的设备 (例如电视) 用 [createSession] 得到二维码并用 [poll] 等待结果;
 * 已登录的设备 (手机) 扫码后用 [scan] 获取待登录设备的信息, 再用 [confirm] 批准或拒绝.
 */
interface QrLoginRepository {
    /**
     * @param deviceName 本设备的名称, 会展示在扫码设备的确认页上
     * @throws RepositoryException
     */
    suspend fun createSession(deviceName: String?): QrLoginSession

    /**
     * 查询 [session] 的进度. 返回 [QrLoginPollResult.Approved] 时已经完成登录 (已保存会话).
     *
     * @throws RepositoryException
     */
    suspend fun poll(session: QrLoginSession): QrLoginPollResult

    /**
     * 以当前登录的用户标记二维码已扫描.
     *
     * @throws RepositoryException
     */
    suspend fun scan(requestId: String): QrLoginScanResult

    /**
     * 批准或拒绝 [scan] 过的登录请求.
     *
     * @return 会话已过期或已被处理时为 `false`
     * @throws RepositoryException
     */
    suspend fun confirm(requestId: String, approve: Boolean): Boolean

    companion object {
        /**
         * 从二维码内容或 `ani://qr-login` 链接中取得 `requestId`. 不是扫码登录的链接时返回 `null`.
         */
        fun parseRequestId(content: String): String? {
            val url = runCatching { Url(content.trim()) }.getOrNull() ?: return null
            val isQrLoginLink = url.encodedPath.substringAfterLast('/').startsWith("qrLogin.")
                    || (url.protocol.name == "ani" && url.host == "qr-login")
            if (!isQrLoginLink) return null
            return url.parameters["requestId"]?.takeIf { it.isNotBlank() }
        }
    }
}

class QrLoginSession(
    val requestId: String,
    /**
     * 只有创建会话的设备持有, 用于 [QrLoginRepository.poll]. 不能展示或放进二维码.
     */
    val pollSecret: String,
    /**
     * 二维码的内容
     */
    val qrContent: String,
    /**
     * 从创建时起的有效时间. 不使用绝对时间, 因为待登录的设备的系统时间不一定准确.
     */
    val lifetime: Duration,
)

sealed interface QrLoginPollResult {
    data object Pending : QrLoginPollResult

    /**
     * 已扫码, 等待用户在扫码设备上确认
     */
    data class Scanned(val nickname: String?) : QrLoginPollResult
    data object Approved : QrLoginPollResult
    data object Rejected : QrLoginPollResult
    data object Expired : QrLoginPollResult
}

sealed interface QrLoginScanResult {
    data class Success(val deviceName: String?) : QrLoginScanResult

    /**
     * 二维码已过期或不存在
     */
    data object Expired : QrLoginScanResult

    /**
     * 二维码已被其他用户扫描, 或已处理
     */
    data object AlreadyHandled : QrLoginScanResult
}

class DefaultQrLoginRepository(
    private val api: ApiInvoker<QRLoginAniApi>,
    private val sessionManager: SessionManager,
) : QrLoginRepository {
    override suspend fun createSession(deviceName: String?): QrLoginSession = request {
        val resp = api.invoke { createQrLogin(AniCreateQrLoginRequest(deviceName = deviceName)).body() }
        QrLoginSession(resp.requestId, resp.pollSecret, resp.qrContent, resp.expiresInSeconds.seconds)
    }

    override suspend fun poll(session: QrLoginSession): QrLoginPollResult = request {
        val resp = try {
            api.invoke { pollQrLogin(AniPollQrLoginRequest(session.requestId, session.pollSecret)).body() }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) return@request QrLoginPollResult.Expired
            throw e
        }
        when (resp.status) {
            AniQrLoginStatus.PENDING -> QrLoginPollResult.Pending
            AniQrLoginStatus.SCANNED -> QrLoginPollResult.Scanned(resp.scannedByNickname?.takeIf { it.isNotEmpty() })
            AniQrLoginStatus.REJECTED -> QrLoginPollResult.Rejected
            AniQrLoginStatus.APPROVED -> {
                val tokens = resp.login?.tokens ?: error("QR login is approved but the server sent no tokens")
                sessionManager.setSession(
                    AccessTokenSession(
                        AccessTokenPair(
                            aniAccessToken = tokens.accessToken,
                            expiresAtMillis = tokens.expiresAtMillis,
                            bangumiAccessToken = tokens.bangumiAccessToken,
                        ),
                    ),
                    refreshToken = tokens.refreshToken,
                )
                QrLoginPollResult.Approved
            }
        }
    }

    override suspend fun scan(requestId: String): QrLoginScanResult = request {
        try {
            val info = api.invoke { scanQrLogin(requestId).body() }
            QrLoginScanResult.Success(info.deviceName?.takeIf { it.isNotBlank() })
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.NotFound -> QrLoginScanResult.Expired
                HttpStatusCode.Conflict -> QrLoginScanResult.AlreadyHandled
                else -> throw e
            }
        }
    }

    override suspend fun confirm(requestId: String, approve: Boolean): Boolean = request {
        try {
            api.invoke { confirmQrLogin(requestId, AniConfirmQrLoginRequest(approve)) }
            true
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.NotFound, HttpStatusCode.Conflict -> false
                else -> throw e
            }
        }
    }

    private suspend inline fun <R> request(crossinline block: suspend () -> R): R = withContext(Dispatchers.Default) {
        try {
            block()
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}
