/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.user.QrLoginPollResult
import me.him188.ani.app.data.repository.user.QrLoginRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * 待登录的设备一侧的扫码登录流程: 创建二维码, 轮询结果, 二维码过期时自动换新.
 */
class QrLoginPoller(
    private val repository: QrLoginRepository,
    private val deviceName: String?,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val pollInterval: Duration = 2.seconds,
) {
    private val logger = logger<QrLoginPoller>()

    /**
     * 收集时开始流程, 取消收集即停止. 在 [QrLoginProgress.isFinal] 的状态后完结.
     */
    fun run(): Flow<QrLoginProgress> = flow {
        while (true) {
            emit(QrLoginProgress.Loading)
            val session = try {
                repository.createSession(deviceName)
            } catch (e: RepositoryException) {
                emit(QrLoginProgress.Failed(LoadError.fromException(e)))
                return@flow
            }
            logger.info { "QR login session created: ${session.requestId}" }
            val expiry = timeSource.markNow() + session.lifetime

            var scannedBy: QrLoginPollResult.Scanned? = null
            var sinceLastPoll = Duration.ZERO
            while (expiry.hasNotPassedNow()) {
                val remaining = -expiry.elapsedNow()
                emit(
                    if (scannedBy != null) QrLoginProgress.Scanned(session.qrContent, scannedBy.nickname)
                    else QrLoginProgress.Waiting(session.qrContent, remaining),
                )

                delay(TICK)
                sinceLastPoll += TICK
                if (sinceLastPoll < pollInterval) continue
                sinceLastPoll = Duration.ZERO

                val result = try {
                    repository.poll(session)
                } catch (_: RepositoryException) {
                    continue // 网络波动, 下次再试. 一直失败的话二维码过期后 createSession 会报告错误
                }
                when (result) {
                    QrLoginPollResult.Pending -> {}
                    is QrLoginPollResult.Scanned -> scannedBy = result
                    QrLoginPollResult.Expired -> break
                    QrLoginPollResult.Rejected -> {
                        emit(QrLoginProgress.Rejected)
                        return@flow
                    }

                    QrLoginPollResult.Approved -> {
                        logger.info { "QR login approved: ${session.requestId}" }
                        emit(QrLoginProgress.Approved)
                        return@flow
                    }
                }
            }
        }
    }

    private companion object {
        // 倒计时的刷新间隔
        val TICK = 1.seconds
    }
}

sealed interface QrLoginProgress {
    val isFinal: Boolean get() = false

    data object Loading : QrLoginProgress

    data class Waiting(val qrContent: String, val remaining: Duration) : QrLoginProgress

    /**
     * 已扫码, 等待用户在扫码设备上确认
     */
    data class Scanned(val qrContent: String, val nickname: String?) : QrLoginProgress

    /**
     * 已登录. 会话已保存.
     */
    data object Approved : QrLoginProgress {
        override val isFinal: Boolean get() = true
    }

    data object Rejected : QrLoginProgress {
        override val isFinal: Boolean get() = true
    }

    data class Failed(val error: LoadError) : QrLoginProgress {
        override val isFinal: Boolean get() = true
    }
}
