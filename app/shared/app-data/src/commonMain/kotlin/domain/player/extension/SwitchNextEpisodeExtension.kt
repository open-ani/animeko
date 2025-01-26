/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState

/**
 * 自动连播.
 *
 * 监控 [MediampPlayer.playbackState], 当其变为 [PlaybackState.FINISHED] 且距离视频结束不足 5 秒时, 切换到下一集.
 * 下一集由 [getNextEpisode] 提供.
 */
class SwitchNextEpisodeExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
    private val getNextEpisode: suspend (currentEpisodeId: Int) -> Int?,
) : PlayerExtension("SwitchNextEpisode") {
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("SwitchNextEpisode") {
            context.sessionFlow.collectLatest { session ->
                getVideoScaffoldConfigUseCase()
                    .map { it.autoPlayNext }
                    .distinctUntilChanged()
                    .collectLatest inner@{ enabled ->
                        if (!enabled) return@inner

                        impl(session)
                    }
            }
        }
    }

    private suspend fun impl(session: EpisodeSession): Nothing {
        val player = context.player
        player.playbackState.collect { playback ->
            val closeToEnd = player.mediaProperties.value.let { prop ->
                prop != null && prop.durationMillis > 0L && prop.durationMillis - player.currentPositionMillis.value < 5000
            }

            if (playback == PlaybackState.FINISHED && closeToEnd) {
                val nextEpisode = getNextEpisode(session.episodeId)
                logger.info("播放完毕，切换下一集 $nextEpisode")
                context.switchEpisode(nextEpisode ?: return@collect)
            }
        }
    }

    class Factory(
        private val getNextEpisode: suspend (currentEpisodeId: Int) -> Int?,
    ) : EpisodePlayerExtensionFactory<SwitchNextEpisodeExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): SwitchNextEpisodeExtension {
            return SwitchNextEpisodeExtension(context, koin, getNextEpisode)
        }
    }

    companion object {
        private val logger = logger<SwitchNextEpisodeExtension>()
    }
}