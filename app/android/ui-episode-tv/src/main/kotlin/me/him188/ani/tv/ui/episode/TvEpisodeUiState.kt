/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.datasources.api.Media
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.AspectRatioMode

data class TvEpisodeTitle(val subjectName: String = "", val episodeLine: String = "")

data class TvStripEpisode(val episodeId: Int, val sortLabel: String, val title: String, val watched: Boolean)

data class TvPlayerOverlayState(
    val controlsVisible: Boolean = true,
    val interactionGeneration: Int = 0,
    val stripExpanded: Boolean = false,
    val activePanel: TvPlayerPanel? = null,
    val scrubMillis: Long? = null,
    val speedHolding: Boolean = false,
    val sourceDialogVisible: Boolean = false,
    val seekFlash: Pair<String, Int>? = null,
) {
    val handlesBack: Boolean get() = sourceDialogVisible || activePanel != null || scrubMillis != null || stripExpanded || controlsVisible
    val canAutoHide: Boolean get() = controlsVisible && scrubMillis == null && !sourceDialogVisible && activePanel == null
}

data class TvPlayerPanelState(
    val relatedSubjects: List<RelatedSubjectInfo> = emptyList(),
    val staff: List<RelatedPersonInfo> = emptyList(),
    val characters: List<RelatedCharacterInfo> = emptyList(),
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
    val mediaCandidates: List<Media> = emptyList(),
    val selectedMedia: Media? = null,
    val overlay: TvPlayerOverlayState = TvPlayerOverlayState(),
    val panel: TvPlayerPanelState = TvPlayerPanelState(),
    val clockText: String = "",
)

enum class TvRemoteKey { Left, Right, Up, Down, Confirm, PlayPause, Play, Pause, Next, Previous, Other }

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
    data object SeekBack : TvEpisodeIntent
    data object SeekForward : TvEpisodeIntent
    data object NextEpisode : TvEpisodeIntent
    data class SelectEpisode(val episodeId: Int) : TvEpisodeIntent
    data class SelectMedia(val media: Media) : TvEpisodeIntent
    data class TogglePanel(val panel: TvPlayerPanel) : TvEpisodeIntent
    data class OpenSubject(val subjectId: Int) : TvEpisodeIntent
    data object OpenSourceDialog : TvEpisodeIntent
    data object CycleSpeed : TvEpisodeIntent
    data object CycleAspectRatio : TvEpisodeIntent
    data object StripFocusLost : TvEpisodeIntent
    data object ReleaseHeldSpeed : TvEpisodeIntent
}

sealed interface TvPlayerFocusRequest {
    data object Root : TvPlayerFocusRequest
    data object SeekBar : TvPlayerFocusRequest
    data class PanelChip(val panel: TvPlayerPanel) : TvPlayerFocusRequest
}

internal const val TV_SPEED_HOLD_FACTOR = 2.5f
