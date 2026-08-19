/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.episode.EpisodeSession
import org.koin.core.Koin
import org.openani.mediamp.features.PlaybackSpeed

/**
 * 将当前生效的倍速同步到播放器, 并持续跟随其变更.
 *
 * [playbackSpeedFlow] 由调用方 (通常是播放页 ViewModel) 提供, 其作用域即为一次播放:
 * 播放页内切集时本扩展会随新的 [EpisodeSession] 重新应用当前值, 因此倍速在切集后保持;
 * 播放页退出后该 flow 随之消失.
 */
class PlaybackSpeedExtension(
    private val context: PlayerExtensionContext,
    private val playbackSpeedFlow: Flow<Float>,
) : PlayerExtension("PlaybackSpeed") {
    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("PlaybackSpeed") {
            playbackSpeedFlow
                .distinctUntilChanged()
                .collect { speed ->
                    withContext(context.player.mainDispatcher) {
                        context.player.features[PlaybackSpeed]?.set(speed)
                    }
                }
        }
    }

    class Factory(
        private val playbackSpeedFlow: Flow<Float>,
    ) : EpisodePlayerExtensionFactory<PlaybackSpeedExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): PlaybackSpeedExtension {
            return PlaybackSpeedExtension(context, playbackSpeedFlow)
        }
    }
}
