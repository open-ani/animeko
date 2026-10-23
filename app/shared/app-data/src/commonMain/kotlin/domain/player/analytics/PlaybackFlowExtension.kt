/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.player.extension.EpisodePlayerExtensionFactory
import me.him188.ani.app.domain.player.extension.ExtensionBackgroundTaskScope
import me.him188.ani.app.domain.player.extension.PlayerExtension
import me.him188.ani.app.domain.player.extension.PlayerExtensionContext
import me.him188.ani.datasources.api.Media
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus

/**
 * 记录每次选中资源后的起播 (`playback_started`) 与起播后的播放错误 (`play_error`).
 *
 * 一次选择对应一次加载: 等本次加载经过 [VideoLoadingState.Succeed] 后播放器首次进入播放中, 记一条 `playback_started`;
 * 之后播放器报错记一条 `play_error`. 新的选择取消对前一次的观察.
 */
class PlaybackFlowExtension(
    private val context: PlayerExtensionContext,
) : PlayerExtension("PlayFlowPlayback") {
    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val recorder = context.playFlow
        val episode = episodeSession.playFlowEpisode
        var playedOnce = false

        backgroundTaskScope.launch("PlaybackStarted") {
            episodeSession.fetchSelectFlow.filterNotNull().collectLatest { bundle ->
                bundle.mediaSelector.selected.filterNotNull().collectLatest { media ->
                    val selectedAt = recorder.markNow()
                    // 加载从 Initial 开始, 先等上一次的 Succeed 被替换掉, 再等本次 Succeed
                    context.videoLoadingStateFlow.first { it !is VideoLoadingState.Succeed }
                    context.videoLoadingStateFlow.first { it is VideoLoadingState.Succeed }
                    context.player.state.first { it.isPlaying }
                    recorder.record(PlayFlowStep.PLAYBACK_STARTED, episode) {
                        put("sel_no", episode.selectionNoOf(media.mediaId))
                        put("media_source_id", media.mediaSourceId)
                        put("kind", media.toPlayFlowKind())
                        put("is_first", !playedOnce)
                        put("duration_ms", selectedAt.elapsedNow())
                        put("since_enter_ms", episode.startedAt.elapsedNow())
                    }
                    playedOnce = true
                    recordPlayError(media, episode)
                }
            }
        }
    }

    private suspend fun recordPlayError(media: Media, episode: PlayFlowEpisode) {
        val error = context.player.state.first { it.mediaStatus is MediaStatus.Error }.mediaStatus as MediaStatus.Error
        context.playFlow.record(PlayFlowStep.PLAY_ERROR, episode) {
            put("sel_no", episode.selectionNoOf(media.mediaId))
            put("reason", error.error.toPlayFlowReason())
            put("position_ms", context.player.currentPositionMillis.value)
        }
    }

    companion object : EpisodePlayerExtensionFactory<PlaybackFlowExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): PlaybackFlowExtension {
            return PlaybackFlowExtension(context)
        }
    }
}
