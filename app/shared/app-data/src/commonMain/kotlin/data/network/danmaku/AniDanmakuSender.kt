/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network.danmaku

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.client.apis.DanmakuAniApi
import me.him188.ani.client.models.AniDanmakuInfo
import me.him188.ani.client.models.AniDanmakuLocation
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

sealed class SendDanmakuException : Exception()
class AuthorizationFailureException(override val cause: Throwable?) : SendDanmakuException()
class RequestFailedException(
    override val message: String?,
    override val cause: Throwable? = null
) : SendDanmakuException()

class NetworkErrorException(override val cause: Throwable?) : SendDanmakuException()

class AniDanmakuSender(
    private val aniDanmakuApi: ApiInvoker<DanmakuAniApi>,
): KoinComponent {
    companion object {
        private val logger = logger<AniDanmakuSender>()
    }
    
    private val userRepository: UserRepository by inject()

    private suspend fun sendDanmaku(
        episodeId: Int,
        content: DanmakuContent,
    ) {
        aniDanmakuApi {
            postDanmaku(
                episodeId.toString(),
                me.him188.ani.client.models.AniDanmakuPostRequest(
                    AniDanmakuInfo(
                        color = content.color,
                        location = content.location.toAniLocation(),
                        playTime = content.playTimeMillis,
                        text = content.text,
                    ),
                ),
            ).body()
        }
    }

    val selfId = userRepository.selfInfoFlow.map { it?.id?.toString() }

    private val sendLock = Mutex()

    @Throws(SendDanmakuException::class, CancellationException::class)
    suspend fun send(episodeId: Int, info: DanmakuContent): DanmakuInfo = sendLock.withLock {
        val selfId = selfId.first()

        sendDanmaku(episodeId, info)

        DanmakuInfo(
            "self" + Random.nextInt(),
            AniDanmakuProvider.ID,
            selfId?: "", // 如果用户没有登录，则是空串
            DanmakuContent(
                info.playTimeMillis,
                info.color,
                info.text,
                info.location,
            ),
        )
    }
}

private fun DanmakuLocation.toAniLocation(): AniDanmakuLocation {
    return when (this) {
        DanmakuLocation.TOP -> AniDanmakuLocation.TOP
        DanmakuLocation.BOTTOM -> AniDanmakuLocation.BOTTOM
        DanmakuLocation.NORMAL -> AniDanmakuLocation.NORMAL
    }
}
