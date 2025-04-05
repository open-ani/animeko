/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.getCurrentEpisodeId
import me.him188.ani.app.domain.episode.player
import me.him188.ani.app.domain.player.extension.*
import me.him188.ani.app.domain.player.VideoLoadingState
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class PlayerExtensionManager(
    val extensions: List<PlayerExtension>,
) {
    inline fun call(block: (PlayerExtension) -> Unit) {
        extensions.forEach {
            try {
                block(it)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }

                throw ExtensionException("Error calling extension ${it.name}, see cause", e)
            }
        }
    }
}

fun PlayerExtensionManager(
    extensions: List<EpisodePlayerExtensionFactory<*>>,
    state: EpisodeFetchSelectPlayState,
    koin: Koin,
): PlayerExtensionManager {
    // 创建一个协程作用域供扩展使用
    val extensionScope = CoroutineScope(state.episodeSessionFlow.value.sessionScope.coroutineContext)
    
    val context = object : PlayerExtensionContext {
        override val subjectId: Int
            get() = state.subjectId

        override val player: MediampPlayer
            get() = state.player

        override val videoLoadingStateFlow: Flow<VideoLoadingState>
            get() = state.playerSession.videoLoadingState

        override val sessionFlow: Flow<EpisodeSession>
            get() = state.episodeSessionFlow
            
        override val coroutineScope: CoroutineScope = extensionScope

        @UnsafeEpisodeSessionApi
        override suspend fun getCurrentEpisodeId(): Int {
            return state.getCurrentEpisodeId()
        }

        @OptIn(UnsafeEpisodeSessionApi::class)
        override suspend fun switchEpisode(newEpisodeId: Int) {
            if (getCurrentEpisodeId() == newEpisodeId) {
                error("Cannot switch to the same episode: $newEpisodeId")
            }

            state.switchEpisode(newEpisodeId)
        }
    }

    // 为内置扩展创建工厂
    val loadMediaOnSelectFactory = object : EpisodePlayerExtensionFactory<PlayerExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): PlayerExtension {
            return object : PlayerExtension("LoadMediaOnSelect") {
                override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
                    backgroundTaskScope.launch("LoadMediaOnSelect") {
                        // 在这里实现加载媒体的逻辑
                    }
                }
            }
        }
    }
    
    val rememberPlayProgressFactory = object : EpisodePlayerExtensionFactory<PlayerExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): PlayerExtension {
            return object : PlayerExtension("RememberPlayProgress") {
                // 实现记住播放进度的扩展
            }
        }
    }
    
    val markAsWatchedFactory = object : EpisodePlayerExtensionFactory<PlayerExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): PlayerExtension {
            return object : PlayerExtension("MarkAsWatched") {
                // 实现标记为已观看的扩展
            }
        }
    }
    
    val intrinsicExtensions = listOf<EpisodePlayerExtensionFactory<out PlayerExtension>>(
        loadMediaOnSelectFactory,
        rememberPlayProgressFactory,
        markAsWatchedFactory,
        BurnAfterReadExtension.Companion
    )

    return PlayerExtensionManager(
        intrinsicExtensions.map { factory -> factory.create(context, koin) } + 
        extensions.map { factory -> factory.create(context, koin) }
    )
}

class ExtensionException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
