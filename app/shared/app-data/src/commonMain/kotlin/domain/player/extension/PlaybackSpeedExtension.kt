/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.episode.EpisodeSession
import org.koin.core.Koin
import org.openani.mediamp.features.PlaybackSpeed

/**
 * 将全局持久化的倍速同步到播放器, 并持续跟随设置变更.
 */
class PlaybackSpeedExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("PlaybackSpeed") {
    private val settingsRepository: SettingsRepository by koin.inject()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("PlaybackSpeed") {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.playbackSpeed }
                .distinctUntilChanged()
                .collect { context.player.features[PlaybackSpeed]?.set(it) }
        }
    }

    companion object : EpisodePlayerExtensionFactory<PlaybackSpeedExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): PlaybackSpeedExtension {
            return PlaybackSpeedExtension(context, koin)
        }
    }
}
