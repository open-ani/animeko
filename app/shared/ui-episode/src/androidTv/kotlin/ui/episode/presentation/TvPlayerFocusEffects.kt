/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.presentation

import androidx.compose.animation.core.Transition
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.episode.TvEpisodeUiState
import me.him188.ani.leanback.ui.episode.danmaku.TvDanmakuSettingsPanelState
import me.him188.ani.leanback.ui.subject.collection.TvCollectionPrompt
import me.him188.ani.leanback.ui.subject.collection.tvCollectionEntryType
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.watchtogether.TvTogetherIntent
import me.him188.ani.leanback.ui.watchtogether.TvTogetherState

/** 播放页焦点锚点. Root 仅 HIDDEN 态可聚焦 (无焦点持有者按键派发会整体失效). */
internal enum class TvPlayerFocus : TvFocusKey {
    Root, SeekBar, IconRow, PlayPauseButton, NextEpisodeButton, SourceDialog,
    PanelHost, PanelEntry,
    Sidebar,
    SourceButton, SpeedButton, SubtitleButton, EpisodesButton, DialogHost, DialogEntry,
    RecommendationsRow, RecommendationsEntry,
}

/** 胶囊按钮锚点 (面板关闭/向下退出时焦点回对应胶囊). */
internal data class PanelChipKey(val panel: TvPlayerPanel) : TvFocusKey

/** Confirmation content has its own anchor so a request cannot reach the previous lazy item. */
internal data class CollectionPanelEntryKey(val prompt: TvCollectionPrompt?) : TvFocusKey

internal data class TogetherPanelEntryKey(val requiresLogin: Boolean, val joined: Boolean, val confirmLeave: Boolean) : TvFocusKey

internal data class EpisodeCardKey(val episodeId: Int) : TvFocusKey

internal data class CommentKey(val id: String) : TvFocusKey

internal data class PanelEntryKey(val panel: TvPlayerPanel?) : TvFocusKey

/** Coordinates focus after a surface change; component-local row identities stay in their panels. */
@Composable
internal fun TvPlayerFocusEffects(
    focus: TvFocusScope,
    presentationState: TvPlayerPresentationState,
    state: TvPlayerOverlayState,
    uiState: TvEpisodeUiState,
    togetherState: TvTogetherState,
    onTogetherIntent: (TvTogetherIntent) -> Unit,
    panelEntryKey: TvFocusKey,
    panelListState: LazyListState,
    danmakuSettingsState: TvDanmakuSettingsPanelState,
    recommendationListState: LazyListState,
    recommendationTransition: Transition<Boolean>,
    stripListState: LazyListState,
) {
    val latestOverlay by rememberUpdatedState(state)
    val latestState by rememberUpdatedState(uiState)
    val latestEntryKey by rememberUpdatedState(panelEntryKey)
    val latestDanmakuSettingsState by rememberUpdatedState(danmakuSettingsState)
    val latestEpisodeActionId by rememberUpdatedState(presentationState.episodeActionId)
    val focusRequests = presentationState.focusRequests
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(focus, lifecycle) {
        focus.requestPrepared {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            when {
                latestOverlay.sourceDialogVisible -> null // The source dialog owns its selected result.
                latestOverlay.dialog != null -> TvPlayerFocus.DialogEntry
                latestOverlay.activePanel != null -> latestEntryKey
                latestOverlay.stripExpanded -> {
                    val episodes = snapshotFlow { latestState.episodes }.first { it.isNotEmpty() }
                    val index = episodes.indexOfFirst { it.episodeId == latestState.currentEpisodeId }.coerceAtLeast(0)
                    stripListState.scrollToItem(index)
                    EpisodeCardKey(episodes[index].episodeId)
                }
                latestOverlay.recommendationsVisible -> TvPlayerFocus.RecommendationsEntry
                latestOverlay.controlsVisible -> TvPlayerFocus.SeekBar
                else -> TvPlayerFocus.Root
            }
        }
    }
    LaunchedEffect(focusRequests, focus) {
        focusRequests.collectLatest { request ->
            val surfaceId = presentationState.states.value.surfaceId
            focus.requestPrepared(isRelevant = { latestOverlay.surfaceId == surfaceId }) {
                // Wait for the overlay removal to reach composition before leaving its focus trap.
                snapshotFlow { latestOverlay }.first { overlay ->
                    when (request) {
                        TvPlayerFocusRequest.Root -> !overlay.controlsVisible
                        TvPlayerFocusRequest.Recommendations -> overlay.controlsVisible && overlay.recommendationsVisible
                        is TvPlayerFocusRequest.PanelChip, TvPlayerFocusRequest.SourceButton ->
                            overlay.controlsVisible && !overlay.recommendationsVisible && overlay.activePanel == null &&
                                    !overlay.sourceDialogVisible && overlay.dialog == null

                        else -> overlay.controlsVisible && !overlay.recommendationsVisible &&
                                !overlay.sourceDialogVisible && overlay.dialog == null
                    }
                }
                if (!latestOverlay.sidebarVisible) {
                    snapshotFlow { focus.isAnchorAttached(TvPlayerFocus.Sidebar) }.first { !it }
                }
                if (request != TvPlayerFocusRequest.Root) {
                    snapshotFlow {
                        !recommendationTransition.isRunning &&
                                recommendationTransition.currentState == recommendationTransition.targetState
                    }.first { it }
                }
                when (request) {
                    TvPlayerFocusRequest.Root -> TvPlayerFocus.Root
                    TvPlayerFocusRequest.SeekBar -> TvPlayerFocus.SeekBar
                    TvPlayerFocusRequest.SourceButton -> TvPlayerFocus.SourceButton
                    TvPlayerFocusRequest.EpisodesButton -> TvPlayerFocus.EpisodesButton
                    TvPlayerFocusRequest.Recommendations -> {
                        recommendationListState.scrollToItem(0)
                        TvPlayerFocus.RecommendationsEntry
                    }

                    is TvPlayerFocusRequest.DialogButton -> {
                        when (request.dialog) {
                            TvPlayerDialog.Speed -> TvPlayerFocus.SpeedButton
                            TvPlayerDialog.Subtitles -> TvPlayerFocus.SubtitleButton
                            TvPlayerDialog.DanmakuMatch -> latestDanmakuSettingsState.prepareReturn(latestState.options.danmakuOrigins, presentationState.matchReturnSource)
                            TvPlayerDialog.DanmakuList -> latestDanmakuSettingsState.prepareReturn(latestState.options.danmakuOrigins, null)
                            TvPlayerDialog.EpisodeActions -> EpisodeCardKey(
                                latestEpisodeActionId ?: latestState.currentEpisodeId,
                            )
                        }
                    }

                    is TvPlayerFocusRequest.PanelChip -> PanelChipKey(request.panel)
                }
            }
        }
    }

    var recommendationsWereEmpty by remember { mutableStateOf(uiState.panel.recommendations.isEmpty()) }
    var recommendationsWereLoading by remember { mutableStateOf(uiState.panel.recommendationsLoading) }
    LaunchedEffect(uiState.panel.recommendations.isEmpty(), uiState.panel.recommendationsLoading) {
        val loadingChanged = recommendationsWereLoading != uiState.panel.recommendationsLoading
        val contentChanged = recommendationsWereEmpty && (uiState.panel.recommendations.isNotEmpty() || loadingChanged)
        recommendationsWereEmpty = uiState.panel.recommendations.isEmpty()
        recommendationsWereLoading = uiState.panel.recommendationsLoading
        if (contentChanged && latestOverlay.recommendationsVisible) {
            focus.requestPrepared {
                snapshotFlow { !recommendationTransition.isRunning }.first { it }
                TvPlayerFocus.RecommendationsEntry.takeIf { latestOverlay.recommendationsVisible }
            }
        }
    }

    // Popup entry follows the current selection; sidebar entry is owned by its animated host.
    LaunchedEffect(state.activePanel) {
        if (state.activePanel != null && !state.sidebarVisible) {
            focus.requestPrepared {
                if (state.activePanel == TvPlayerPanel.Collection) {
                    panelListState.scrollToItem(UnifiedCollectionType.entries.indexOf(uiState.options.collectionType.tvCollectionEntryType))
                }
                panelEntryKey
            }
        }
        if (state.activePanel == TvPlayerPanel.Together) onTogetherIntent(TvTogetherIntent.Open)
    }
    LaunchedEffect(state.dialog, state.surfaceId) {
        if (state.dialog != null) focus.request(TvPlayerFocus.DialogEntry)
    }
    LaunchedEffect(
        presentationState.collectionPrompt,
        uiState.options.collectionBusy,
    ) {
        if (state.activePanel == TvPlayerPanel.Collection && !uiState.options.collectionBusy) focus.request(
            panelEntryKey,
        )
    }
    LaunchedEffect(togetherState.requiresLogin, togetherState.joined, presentationState.confirmLeave) {
        if (state.activePanel == TvPlayerPanel.Together) {
            focus.requestPrepared {
                panelListState.scrollToItem(0)
                panelEntryKey
            }
        }
    }
    LaunchedEffect(presentationState.commentDetail) {
        if (presentationState.commentDetail != null) focus.request(TvPlayerFocus.PanelEntry)
    }

    // 选集条展开: 等列表数据就绪 → 滚到当前集 → 送焦当前集卡 (全事件驱动)
    LaunchedEffect(state.stripExpanded) {
        if (!state.stripExpanded) return@LaunchedEffect
        focus.requestPrepared {
            val episodes = snapshotFlow { latestState.episodes }.first { it.isNotEmpty() }
            val index = episodes.indexOfFirst { it.episodeId == latestState.currentEpisodeId }.coerceAtLeast(0)
            stripListState.scrollToItem(index)
            EpisodeCardKey(episodes[index].episodeId)
        }
    }

}
