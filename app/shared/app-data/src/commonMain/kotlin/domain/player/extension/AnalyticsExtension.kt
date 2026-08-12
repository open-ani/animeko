/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.EpisodePlaying
import org.koin.core.Koin

/**
 * 进入播放页后是否成功播放了视频.
 * 只记录一次, 用来检验 MediaSelector 的质量.
 */
class AnalyticsExtension(
    private val context: PlayerExtensionContext
) : PlayerExtension("AnalyticsStartPlay") {
    private val hasPlayedOnce = MutableStateFlow(false)

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("AnalyticsStartPlay") {
            hasPlayedOnce.collectLatest { played ->
                if (played) return@collectLatest

                context.player.state.collectLatest { state ->
                    if (state.isPlaying) {
                        Analytics.recordEvent(
                            EpisodePlaying,
                            mapOf(
                                "subject_id" to context.subjectId,
                                "episode_id" to episodeSession.episodeId,
                            ),
                        )
                        hasPlayedOnce.value = true // side effect
                    }
                }
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<AnalyticsExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): AnalyticsExtension {
            return AnalyticsExtension(context)
        }
    }
}