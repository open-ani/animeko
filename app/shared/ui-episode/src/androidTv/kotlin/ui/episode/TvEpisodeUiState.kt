/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import me.him188.ani.app.domain.episode.SubjectRecommendation
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.AspectRatioMode

data class TvEpisodeTitle(val subjectName: String = "", val episodeLine: String = "")

data class TvStripEpisode(
    val episodeId: Int,
    val sortLabel: String,
    val title: String,
    val watched: Boolean,
    val stillUrl: String? = null,
    val isKnownBroadcast: Boolean = false,
)

data class TvPlayerPanelState(
    val recommendations: List<SubjectRecommendation> = emptyList(),
    val danmaku: List<DanmakuPresentation> = emptyList(),
    val recommendationsLoading: Boolean = false,
)

data class TvEpisodeUiState(
    val title: TvEpisodeTitle = TvEpisodeTitle(),
    val playbackState: PlaybackState = PlaybackState.READY,
    val loadingState: VideoLoadingState = VideoLoadingState.Initial,
    val isBuffering: Boolean = false,
    val playerError: Boolean = false,
    val mediaLabel: String? = null,
    val durationMillis: Long = 0,
    val positionMillis: Long = 0,
    val bufferedFraction: Float = 0f,
    val playbackSpeed: Float = 1f,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val episodes: List<TvStripEpisode> = emptyList(),
    val currentEpisodeId: Int = 0,
    val selectedMedia: Media? = null,
    val interaction: TvPlaybackInteractionState = TvPlaybackInteractionState(),
    val panel: TvPlayerPanelState = TvPlayerPanelState(),
    val sources: TvSourceSelectionState = TvSourceSelectionState(),
    val options: TvPlayerOptionsState = TvPlayerOptionsState(),
    val danmakuMatch: TvDanmakuMatchState = TvDanmakuMatchState(),
) {
    val hasNextEpisode: Boolean
        get() {
            val index = episodes.indexOfFirst { it.episodeId == currentEpisodeId }
            return index >= 0 && episodes.getOrNull(index + 1)?.isKnownBroadcast == true
        }
}

sealed interface TvEpisodeIntent {
    data object UiReady : TvEpisodeIntent
    data object TogglePause : TvEpisodeIntent
    data class PreviewBy(val deltaMillis: Long) : TvEpisodeIntent
    data class PreviewSeek(val positionMillis: Long?) : TvEpisodeIntent
    data class SeekTo(val positionMillis: Long) : TvEpisodeIntent
    data class SwitchNeighbor(val offset: Int) : TvEpisodeIntent
    data class HoldSpeed(val engaged: Boolean) : TvEpisodeIntent
    data class ObserveStats(val enabled: Boolean) : TvEpisodeIntent
    data class CancelDanmakuMatch(val requestId: Long) : TvEpisodeIntent
    data object BackDanmakuMatch : TvEpisodeIntent
    data object NextEpisode : TvEpisodeIntent
    data class SelectEpisode(val episodeId: Int) : TvEpisodeIntent
    data class SelectMedia(val media: Media, val requestId: Long = 0) : TvEpisodeIntent
    data class OpenRecommendation(val recommendation: SubjectRecommendation) : TvEpisodeIntent
    data object CycleAspectRatio : TvEpisodeIntent
    data object ReleaseHeldSpeed : TvEpisodeIntent
    data object ToggleDanmaku : TvEpisodeIntent
    data object OpenLogin : TvEpisodeIntent
    data object CancelAutoSkip : TvEpisodeIntent
    data class SetSpeed(val speed: Float) : TvEpisodeIntent
    data class AdjustSpeed(val direction: Int) : TvEpisodeIntent
    data class SetDefaultSpeed(val speed: Float) : TvEpisodeIntent
    data class SetHoldSpeed(val speed: Float) : TvEpisodeIntent
    data object ToggleRememberSpeed : TvEpisodeIntent
    data class SelectSubtitle(val id: String?, val requestId: Long = 0) : TvEpisodeIntent
    data class SetEnhancement(val mode: VideoEnhancementMode) : TvEpisodeIntent
    data class ViewportChanged(val width: Int, val height: Int) : TvEpisodeIntent
    data class ForegroundChanged(val foreground: Boolean) : TvEpisodeIntent
    data class AdjustDanmaku(val property: TvDanmakuProperty, val direction: Int) : TvEpisodeIntent
    data class ToggleDanmakuSource(val serviceId: DanmakuServiceId) : TvEpisodeIntent
    data class ShiftDanmakuSource(val serviceId: DanmakuServiceId, val deltaMillis: Long?) : TvEpisodeIntent
    data class MatchDanmaku(val providerId: DanmakuProviderId, val requestId: Long = 0) : TvEpisodeIntent
    data class DanmakuQuery(val value: String) : TvEpisodeIntent
    data object SearchDanmaku : TvEpisodeIntent
    data class SelectDanmakuSubject(val id: String) : TvEpisodeIntent
    data class SelectDanmakuEpisode(val id: String) : TvEpisodeIntent
    data class SetCollection(val type: UnifiedCollectionType, val requestId: Long = 0) : TvEpisodeIntent
    data class MarkAllWatched(val requestId: Long = 0) : TvEpisodeIntent
    data class SetEpisodeWatched(val episodeId: Int, val watched: Boolean, val requestId: Long = 0) : TvEpisodeIntent
    data class RetrySources(val instanceId: String? = null) : TvEpisodeIntent
    data class ResolveSourceCaptcha(val instanceId: String) : TvEpisodeIntent
    data class RetryPlayback(val requestId: Long = 0) : TvEpisodeIntent
}

/** Completed business actions; the UI decides which follow-up prompt to show. */
sealed interface TvEpisodeEvent {
    data class CollectionChanged(val type: UnifiedCollectionType, val requestId: Long) : TvEpisodeEvent
    data class AllEpisodesWatched(val requestId: Long) : TvEpisodeEvent
    data class EpisodeWatched(val episodeId: Int, val requestId: Long) : TvEpisodeEvent
    data class DanmakuMatched(val requestId: Long) : TvEpisodeEvent
    data class EpisodeSelected(val episodeId: Int) : TvEpisodeEvent
    data class MediaSelected(val requestId: Long) : TvEpisodeEvent
    data class SubtitleSelected(val requestId: Long) : TvEpisodeEvent
    data class PlaybackRetried(val requestId: Long) : TvEpisodeEvent
}
