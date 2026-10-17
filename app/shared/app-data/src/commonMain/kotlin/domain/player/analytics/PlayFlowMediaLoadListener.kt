/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import me.him188.ani.app.domain.episode.MediaLoadListener
import me.him188.ani.app.domain.episode.MediaLoadPhase
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.datasources.api.Media
import org.openani.mediamp.PlaybackException
import kotlin.time.Duration

/**
 * 把一次 `PlayerSession.loadMedia` 的结果记为一条 `video_load_done`.
 *
 * @param cancelReason 加载被取消时的原因: `user_switched` (用户换源) / `episode_switched` / `left_page`, 在取消发生时求值.
 */
internal class PlayFlowMediaLoadListener(
    private val recorder: PlayFlowRecorder,
    private val episode: PlayFlowEpisode,
    private val media: Media,
    private val cancelReason: () -> String,
) : MediaLoadListener {
    private var resolverName: String? = null
    private var engineKey: MediaCacheEngineKey? = null

    override fun onResolveStart(resolverName: String?) {
        this.resolverName = resolverName
    }

    override fun onResolved(engineKey: MediaCacheEngineKey?, elapsed: Duration) {
        this.engineKey = engineKey
    }

    override fun onOpened(engineKey: MediaCacheEngineKey?, elapsed: Duration) {
        this.engineKey = engineKey
    }

    override fun onReady(elapsed: Duration) {
        record("ok", stage = null, reason = null, elapsed)
    }

    override fun onFailed(phase: MediaLoadPhase, state: VideoLoadingState.Failed, elapsed: Duration) {
        record("failed", phase.toStage(), state.toReason(), elapsed)
    }

    override fun onCancelled(phase: MediaLoadPhase, elapsed: Duration) {
        record("cancelled", phase.toStage(), cancelReason(), elapsed)
    }

    private fun record(outcome: String, stage: String?, reason: String?, elapsed: Duration) {
        recorder.record(PlayFlowStep.VIDEO_LOAD_DONE, episode) {
            put("sel_no", episode.selectionNoOf(media.mediaId))
            put("outcome", outcome)
            put("stage", stage)
            put("reason", reason)
            put("media_source_id", media.mediaSourceId)
            put("kind", media.toPlayFlowKind())
            put("engine", playFlowEngine(resolverName, engineKey))
            put("duration_ms", elapsed)
        }
    }

    private fun MediaLoadPhase.toStage(): String = when (this) {
        MediaLoadPhase.RESOLVE -> "resolve"
        MediaLoadPhase.OPEN -> "open"
        MediaLoadPhase.SET_MEDIA_DATA -> "player"
    }

    /**
     * 失败原因按用户能理解的分类归并; 未知错误取播放器错误码、解析失败原因或异常的简单类名, 不含异常消息.
     */
    private fun VideoLoadingState.Failed.toReason(): String = when (this) {
        VideoLoadingState.NoMatchingFile -> "no_playable_link"
        VideoLoadingState.ResolutionTimedOut -> "resolve_timeout"
        VideoLoadingState.NetworkError -> "network"
        VideoLoadingState.WebEngineUnavailable -> "web_engine_unavailable"
        VideoLoadingState.UnsupportedMedia -> "unsupported_media"
        VideoLoadingState.Cancelled -> "cancelled"
        is VideoLoadingState.UnknownError -> when (val cause = cause) {
            is PlaybackException -> cause.toPlayFlowReason()
            is MediaResolutionException -> cause.reason.name.lowercase()
            else -> cause::class.simpleName?.lowercase() ?: "unknown"
        }
    }
}
