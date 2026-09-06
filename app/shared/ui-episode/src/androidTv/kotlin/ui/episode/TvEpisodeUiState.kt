/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
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
    val stillUrl: String? = null
)

data class TvPlayerOverlayState(
    val controlsVisible: Boolean = true,
    val interactionGeneration: Int = 0,
    val stripExpanded: Boolean = false,
    val activePanel: TvPlayerPanel? = null,
    val scrubMillis: Long? = null,
    val speedHolding: Boolean = false,
    val sourceDialogVisible: Boolean = false,
    val dialog: TvPlayerDialog? = null,
) {
    val handlesBack: Boolean get() = dialog != null || sourceDialogVisible || activePanel != null || scrubMillis != null || stripExpanded || controlsVisible
    val canAutoHide: Boolean get() = controlsVisible && dialog == null && scrubMillis == null && !stripExpanded && !sourceDialogVisible && activePanel == null
}

data class TvPlayerPanelState(
    val relatedSubjects: List<RelatedSubjectInfo> = emptyList(),
    val danmaku: List<DanmakuPresentation> = emptyList(),
)

data class TvEpisodeUiState(
    val title: TvEpisodeTitle = TvEpisodeTitle(),
    val playbackState: PlaybackState = PlaybackState.READY,
    val loadingState: VideoLoadingState = VideoLoadingState.Initial,
    val mediaLabel: String? = null,
    val durationMillis: Long = 0,
    val positionMillis: Long = 0,
    val bufferedFraction: Float = 0f,
    val playbackSpeed: Float = 1f,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val episodes: List<TvStripEpisode> = emptyList(),
    val currentEpisodeId: Int = 0,
    val selectedMedia: Media? = null,
    val overlay: TvPlayerOverlayState = TvPlayerOverlayState(),
    val panel: TvPlayerPanelState = TvPlayerPanelState(),
    val sources: TvSourceSelectionState = TvSourceSelectionState(),
    val options: TvPlayerOptionsState = TvPlayerOptionsState(),
    val danmakuMatch: TvDanmakuMatchState = TvDanmakuMatchState(),
)

enum class TvRemoteKey { Left, Right, Up, Down, Confirm, Menu, PlayPause, Play, Pause, Next, Previous, Other }

sealed interface TvEpisodeIntent {
    data object UiReady : TvEpisodeIntent
    data class RemoteKey(
        val key: TvRemoteKey,
        val isDown: Boolean,
        val repeatCount: Int,
        val eventTimeMillis: Long,
        val seekBarFocused: Boolean,
        val iconRowFocused: Boolean,
        val sourceDialogFocused: Boolean,
    ) : TvEpisodeIntent

    data object Back : TvEpisodeIntent
    data object ToggleEpisodeStrip : TvEpisodeIntent
    data object NextEpisode : TvEpisodeIntent
    data class SelectEpisode(val episodeId: Int) : TvEpisodeIntent
    data class SelectMedia(val media: Media) : TvEpisodeIntent
    data class TogglePanel(val panel: TvPlayerPanel) : TvEpisodeIntent
    data class OpenSubject(val subjectId: Int) : TvEpisodeIntent
    data object OpenSourceDialog : TvEpisodeIntent
    data object CycleAspectRatio : TvEpisodeIntent
    data object StripFocusLost : TvEpisodeIntent
    data object ReleaseHeldSpeed : TvEpisodeIntent
    data object ToggleDanmaku : TvEpisodeIntent
    data object CancelAutoSkip : TvEpisodeIntent
    data class OpenDialog(val dialog: TvPlayerDialog) : TvEpisodeIntent
    data class SetSpeed(val speed: Float) : TvEpisodeIntent
    data class AdjustSpeed(val direction: Int) : TvEpisodeIntent
    data class SetDefaultSpeed(val speed: Float) : TvEpisodeIntent
    data class SetHoldSpeed(val speed: Float) : TvEpisodeIntent
    data object ToggleRememberSpeed : TvEpisodeIntent
    data class SelectSubtitle(val id: String?) : TvEpisodeIntent
    data class SetEnhancement(val mode: VideoEnhancementMode) : TvEpisodeIntent
    data class ViewportChanged(val width: Int, val height: Int) : TvEpisodeIntent
    data class ForegroundChanged(val foreground: Boolean) : TvEpisodeIntent
    data class AdjustDanmaku(val property: TvDanmakuProperty, val direction: Int) : TvEpisodeIntent
    data class ToggleDanmakuSource(val serviceId: DanmakuServiceId) : TvEpisodeIntent
    data class ShiftDanmakuSource(val serviceId: DanmakuServiceId, val deltaMillis: Long?) : TvEpisodeIntent
    data class MatchDanmaku(val providerId: DanmakuProviderId) : TvEpisodeIntent
    data class DanmakuQuery(val value: String) : TvEpisodeIntent
    data object SearchDanmaku : TvEpisodeIntent
    data class SelectDanmakuSubject(val id: String) : TvEpisodeIntent
    data class SelectDanmakuEpisode(val id: String) : TvEpisodeIntent
    data class SetCollection(val type: UnifiedCollectionType) : TvEpisodeIntent
    data object MarkAllWatched : TvEpisodeIntent
    data class SetEpisodeWatched(val episodeId: Int, val watched: Boolean) : TvEpisodeIntent
    data class RetrySources(val instanceId: String? = null) : TvEpisodeIntent
    data object RetryPlayback : TvEpisodeIntent
}

/** Completed business actions; the UI decides which follow-up prompt to show. */
sealed interface TvEpisodeEvent {
    data class CollectionChanged(val type: UnifiedCollectionType) : TvEpisodeEvent
    data object AllEpisodesWatched : TvEpisodeEvent
}

sealed interface TvPlayerFocusRequest {
    data object Root : TvPlayerFocusRequest
    data object SeekBar : TvPlayerFocusRequest
    data object SourceButton : TvPlayerFocusRequest
    data object EpisodesButton : TvPlayerFocusRequest
    data class DialogButton(val dialog: TvPlayerDialog) : TvPlayerFocusRequest
    data class PanelChip(val panel: TvPlayerPanel) : TvPlayerFocusRequest
}
