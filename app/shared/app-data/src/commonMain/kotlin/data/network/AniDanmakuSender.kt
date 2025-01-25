/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.network.protocol.AniUser
import me.him188.ani.app.data.network.protocol.BangumiLoginRequest
import me.him188.ani.app.data.network.protocol.BangumiLoginResponse
import me.him188.ani.app.data.network.protocol.DanmakuInfo
import me.him188.ani.app.data.network.protocol.DanmakuPostRequest
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuProviderConfig
import me.him188.ani.danmaku.api.applyDanmakuProviderConfig
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.registerLogging
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.currentPlatform
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

interface AniDanmakuSender : AutoCloseable {
    val selfId: Flow<String?>

    @Throws(SendDanmakuException::class, CancellationException::class)
    suspend fun send(
        episodeId: Int,
        info: DanmakuInfo
    ): Danmaku
}

sealed class SendDanmakuException : Exception()
class AuthorizationFailureException(override val cause: Throwable?) : SendDanmakuException()
class RequestFailedException(
    override val message: String?,
    override val cause: Throwable? = null
) : SendDanmakuException()

class NetworkErrorException(override val cause: Throwable?) : SendDanmakuException()

class AniDanmakuSenderImpl(
    private val config: DanmakuProviderConfig,
    private val bangumiToken: Flow<String?>,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AniDanmakuSender, HasBackgroundScope by BackgroundScope(parentCoroutineContext) {
    private fun getBaseUrl() = AniBangumiSeverBaseUrls.getBaseUrl(config.useGlobal)

    companion object {
        private val logger = logger<AniDanmakuSenderImpl>()
    }

    private val client = createDefaultHttpClient {
        applyDanmakuProviderConfig(config)
        followRedirects = true
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 30_000
        }
    }.apply {
        registerLogging(logger)
    }

    private suspend fun getUserInfo(token: String): AniUser {
        return invokeRequest {
            client.get("${getBaseUrl()}/v1/me") {
                bearerAuth(token)
            }
        }.body<AniUser>()
    }

    private inline fun invokeRequest(
        block: () -> HttpResponse
    ): HttpResponse {
        val resp = try {
            block()
        } catch (e: Throwable) {
            throw NetworkErrorException(e)
        }
        if (resp.status.value == 401) {
            throw AuthorizationFailureException(null)
        }
        if (!resp.status.isSuccess()) {
            throw RequestFailedException(resp.toString())
        }
        return resp
    }

    private suspend fun authByBangumiToken(
        bangumiToken: String
    ): String {
        return invokeRequest {
            client.post("${getBaseUrl()}/v1/login/bangumi") {
                contentType(ContentType.Application.Json)
                setBody(
                    BangumiLoginRequest(
                        bangumiToken,
                        clientVersion = currentAniBuildConfig.versionName,
                        clientOS = currentPlatform().name,
                        clientArch = currentPlatform().arch.displayName,
                    ),
                )
            }
        }.body<BangumiLoginResponse>().token
    }

    private suspend fun sendDanmaku(
        episodeId: Int,
        info: DanmakuInfo,
    ) {
        val token = requireSession().token
        invokeRequest {
            client.post("${getBaseUrl()}/v1/danmaku/$episodeId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(DanmakuPostRequest(info))
            }
        }
    }

    private class Session(
        val token: String,
        val selfInfo: AniUser,
    )

    private val session = MutableStateFlow<Session?>(null)

    private suspend inline fun requireSession() =
        session.value ?: kotlin.run {
            login()
        }

    private val loginLock = Mutex()
    private suspend fun login(): Session = loginLock.withLock {
        session.value?.let { return it }
        return suspend {
            val bangumiToken = bangumiToken.first()
                ?: throw AuthorizationFailureException(null)

            val token = authByBangumiToken(bangumiToken)
            val selfInfo = getUserInfo(token)
            val session = Session(token, selfInfo)
            this.session.value = session
            session
        }.asFlow().retryWithBackoffDelay { e, _ ->
            e !is AuthorizationFailureException
        }.first()
    }

    init {
        launchInBackground {
            kotlin.runCatching { login() }.onFailure {
                logger.error(it) { "Failed to login to danmaku sever (on startup). Will try later when sending danmaku." }
            }
        }
    }

    override val selfId = session.map { it?.selfInfo?.id }

    private val sendLock = Mutex()
    override suspend fun send(episodeId: Int, info: DanmakuInfo): Danmaku = sendLock.withLock {
        val selfId = requireSession().selfInfo.id

        sendDanmaku(episodeId, info)

        Danmaku(
            id = "self" + Random.nextInt(),
            providerId = AniDanmakuProvider.ID,
            playTimeMillis = info.playTime,
            senderId = selfId,
            location = info.location.toApi(),
            text = info.text,
            color = info.color,
        )
    }

    override fun close() {
        client.close()
        backgroundScope.cancel()
    }
}