/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.tv.ui.episode.TvEpisodeEvent
import me.him188.ani.tv.ui.episode.TvEpisodeIntent
import me.him188.ani.tv.ui.episode.TvEpisodeUiState
import me.him188.ani.tv.ui.episode.danmaku.TvDanmakuAdjustment
import me.him188.ani.tv.ui.episode.playback.TvPlaybackInteractionState
import me.him188.ani.tv.ui.subject.collection.TvCollectionPrompt
import org.openani.mediamp.PlayerState

internal data class TvPlayerOverlayState(
    val controlsVisible: Boolean = true,
    /**
     * 精简控制层: 只显示时间轴与画面预览, 不出现标题栏/胶囊行/图标行.
     *
     * 控制层隐藏时按 ←/→ 调整进度会唤出精简控制层; 展开完整控制层时清除.
     * 收起控制层时保留原值, 让退出动画保持当前形态, 不会闪回完整控制层.
     */
    val timelineOnly: Boolean = false,
    val interactionGeneration: Int = 0,
    val stripExpanded: Boolean = false,
    val recommendationsVisible: Boolean = false,
    val activePanel: TvPlayerPanel? = null,
    val playbackInteraction: TvPlaybackInteractionState = TvPlaybackInteractionState(),
    val surfaceId: Long = 0,
    val sourceDialogVisible: Boolean = false,
    val dialog: TvPlayerDialog? = null,
) {
    val scrubMillis: Long? get() = playbackInteraction.scrubMillis
    val speedHolding: Boolean get() = playbackInteraction.speedHolding
    val sidebarVisible: Boolean get() = activePanel?.presentation == TvPlayerPanelPresentation.Sidebar
    val handlesBack: Boolean get() = dialog != null || sourceDialogVisible || activePanel != null || scrubMillis != null || stripExpanded || controlsVisible
    val canAutoHide: Boolean get() = controlsVisible && !recommendationsVisible && dialog == null && scrubMillis == null && !stripExpanded && !sourceDialogVisible && activePanel == null
}

enum class TvRemoteKey { Left, Right, Up, Down, Confirm, Menu, PlayPause, Play, Pause, Next, Previous, Other }

internal sealed interface TvPlayerAction {
    data class RemoteKey(
        val key: TvRemoteKey,
        val isDown: Boolean,
        val repeatCount: Int,
        val eventTimeMillis: Long,
        val seekBarFocused: Boolean,
        val iconRowFocused: Boolean,
        val sourceDialogFocused: Boolean,
        val sidebarFocused: Boolean = false,
        val recommendationsFocused: Boolean = false,
        val dialogFocused: Boolean = true,
    ) : TvPlayerAction

    data object Back : TvPlayerAction
    data object ToggleEpisodeStrip : TvPlayerAction
    data object OpenRecommendations : TvPlayerAction
    data object CloseRecommendations : TvPlayerAction
    data class TogglePanel(val panel: TvPlayerPanel) : TvPlayerAction
    data object OpenSourceDialog : TvPlayerAction
    data class OpenDialog(val dialog: TvPlayerDialog) : TvPlayerAction
    data object StripFocusLost : TvPlayerAction
}

internal sealed interface TvPlayerFocusRequest {
    data object Root : TvPlayerFocusRequest
    data object SeekBar : TvPlayerFocusRequest
    data object SourceButton : TvPlayerFocusRequest
    data object EpisodesButton : TvPlayerFocusRequest
    data object Recommendations : TvPlayerFocusRequest
    data class DialogButton(val dialog: TvPlayerDialog) : TvPlayerFocusRequest
    data class PanelChip(val panel: TvPlayerPanel) : TvPlayerFocusRequest
}

/** Playback facts supplied by the VM; the presentation reducer never accesses the player. */
internal data class TvPlaybackSnapshot(val playerState: PlayerState, val positionMillis: Long, val durationMillis: Long)

internal sealed interface TvPlaybackCommand {
    data class PreviewBy(val deltaMillis: Long) : TvPlaybackCommand
    data class PreviewSeek(val positionMillis: Long?) : TvPlaybackCommand
    data object TogglePause : TvPlaybackCommand
    data class SeekTo(val positionMillis: Long) : TvPlaybackCommand
    data class SwitchNeighbor(val offset: Int) : TvPlaybackCommand
    data class SpeedHold(val engaged: Boolean) : TvPlaybackCommand
    data object CycleAspectRatio : TvPlaybackCommand
}

/** View-owned presentation state. Playback commands are dispatched to the VM. */
@Stable
internal class TvPlayerPresentationState(
    private val playback: () -> TvPlaybackSnapshot,
    private val execute: (TvPlaybackCommand) -> Unit,
    playbackInteraction: TvPlaybackInteractionState = TvPlaybackInteractionState(),
) {
    private val state = MutableStateFlow(TvPlayerOverlayState(playbackInteraction = playbackInteraction))
    val states = state.asStateFlow()
    private val focus = Channel<TvPlayerFocusRequest>(Channel.BUFFERED)
    val focusRequests = focus.receiveAsFlow()
    var episodeActionId by mutableStateOf<Int?>(null)
    var collectionPrompt by mutableStateOf<TvCollectionPrompt?>(null)
    var confirmLeave by mutableStateOf(false)
    var commentDetail by mutableStateOf<EpisodeComment?>(null)
    var commentReturn by mutableStateOf<Pair<String, Int>?>(null)
    var danmakuAdjustment by mutableStateOf<TvDanmakuAdjustment?>(null)
    var matchReturnSource by mutableStateOf<DanmakuServiceId?>(null)
    var statsVisible by mutableStateOf(false)

    /** 松键后的待提交目标; 空闲期 (见 [SCRUB_INPUT_IDLE_MILLIS]) 内再次按下方向键会取消它, 继续本次调整. */
    var pendingScrubCommitMillis by mutableStateOf<Long?>(null)
        private set
    private var episodeId: Int? = null
    private var roomIdentity: Pair<Boolean, String>? = null
    private var trackingConfirm = false
    private var firedConfirm = false
    private var lastScrubAdvanceMillis = Long.MIN_VALUE
    private var scrubHoldStartMillis = Long.MIN_VALUE
    private var scrubHoldForward: Boolean? = null
    private var lastScrubKeyEventMillis = Long.MIN_VALUE

    /** Correlation is opaque to the VM: only this originating surface may consume its result. */
    fun tagRequest(intent: TvEpisodeIntent): TvEpisodeIntent = when (intent) {
        is TvEpisodeIntent.SelectMedia -> intent.copy(requestId = state.value.surfaceId)
        is TvEpisodeIntent.SelectSubtitle -> intent.copy(requestId = state.value.surfaceId)
        is TvEpisodeIntent.SetCollection -> intent.copy(requestId = state.value.surfaceId)
        is TvEpisodeIntent.MarkAllWatched -> intent.copy(requestId = state.value.surfaceId)
        is TvEpisodeIntent.RetryPlayback -> intent.copy(requestId = state.value.surfaceId)
        else -> intent
    }

    fun onEvent(event: TvEpisodeEvent) {
        when (event) {
            is TvEpisodeEvent.CollectionChanged -> if (state.value.activePanel == TvPlayerPanel.Collection && state.value.surfaceId == event.requestId) {
                collectionPrompt = TvCollectionPrompt.MarkAllWatched.takeIf { event.type == UnifiedCollectionType.DONE }
            }
            is TvEpisodeEvent.AllEpisodesWatched -> if (state.value.surfaceId == event.requestId) collectionPrompt = null
            is TvEpisodeEvent.EpisodeWatched -> completeEpisodeAction(event.episodeId, event.requestId)
            is TvEpisodeEvent.DanmakuMatched -> completeMatching(event.requestId)
            is TvEpisodeEvent.EpisodeSelected -> episodeSelected()
            is TvEpisodeEvent.MediaSelected -> if (state.value.sourceDialogVisible && state.value.surfaceId == event.requestId) mediaSelected()
            is TvEpisodeEvent.SubtitleSelected -> if (state.value.dialog == TvPlayerDialog.Subtitles && state.value.surfaceId == event.requestId) closeDialog()
            is TvEpisodeEvent.PlaybackRetried -> if (state.value.surfaceId == event.requestId) sourceControls()
        }
    }

    fun onAction(intent: TvPlayerAction): Boolean {
        val previousPanel = state.value.activePanel
        val previousDialog = state.value.dialog
        val handled = reduce(intent)
        if (state.value.activePanel != previousPanel) resetPanelPages()
        if (state.value.dialog != previousDialog) danmakuAdjustment = null
        return handled
    }

    private fun reduce(intent: TvPlayerAction): Boolean {
        when (intent) {
            is TvPlayerAction.RemoteKey -> return onKey(intent)
            TvPlayerAction.Back -> return back()
            TvPlayerAction.OpenRecommendations -> openRecommendations()
            TvPlayerAction.CloseRecommendations -> closeRecommendations()
            TvPlayerAction.ToggleEpisodeStrip -> {
                releaseHeldSpeed()
                clearPreview()
                val expanded = !state.value.stripExpanded
                state.value = state.value.copy(
                    controlsVisible = true,
                    timelineOnly = false,
                    recommendationsVisible = false,
                    stripExpanded = expanded,
                    activePanel = null,
                    dialog = null,
                    sourceDialogVisible = false,
                )
                if (!expanded) focus.trySend(TvPlayerFocusRequest.EpisodesButton)
                bump()
            }

            is TvPlayerAction.TogglePanel -> {
                releaseHeldSpeed()
                clearPreview()
                state.value = state.value.copy(
                    controlsVisible = true,
                    timelineOnly = false,
                    recommendationsVisible = false,
                    sourceDialogVisible = false,
                    surfaceId = state.value.surfaceId + 1,
                    activePanel = intent.panel.takeUnless { it == state.value.activePanel },
                    stripExpanded = false,
                    dialog = null,
                )
                bump()
            }

            TvPlayerAction.OpenSourceDialog -> {
                releaseHeldSpeed()
                clearPreview()
                state.value = state.value.copy(
                    sourceDialogVisible = true,
                    surfaceId = state.value.surfaceId + 1,
                    controlsVisible = true,
                    timelineOnly = false,
                    recommendationsVisible = false,
                    stripExpanded = false,
                    activePanel = null,
                    dialog = null,
                )
                bump()
            }

            is TvPlayerAction.OpenDialog -> {
                releaseHeldSpeed()
                clearPreview()
                state.value = state.value.copy(
                    dialog = intent.dialog,
                    surfaceId = state.value.surfaceId + 1,
                    controlsVisible = true,
                    timelineOnly = false,
                    recommendationsVisible = false,
                    activePanel = state.value.activePanel.takeIf {
                        intent.dialog == TvPlayerDialog.DanmakuList || intent.dialog == TvPlayerDialog.DanmakuMatch
                    },
                    stripExpanded = state.value.stripExpanded && intent.dialog == TvPlayerDialog.EpisodeActions,
                    sourceDialogVisible = false,
                )
                bump()
            }

            TvPlayerAction.StripFocusLost -> if (state.value.dialog == null) {
                state.value = state.value.copy(stripExpanded = false)
            }

        }
        return true
    }

    private fun resetPanelPages() {
        commentDetail = null
        commentReturn = null
        danmakuAdjustment = null
        confirmLeave = false
        collectionPrompt = null
    }

    fun episodeChanged(currentEpisodeId: Int) {
        val previous = episodeId
        episodeId = currentEpisodeId
        if (previous == null || previous == currentEpisodeId) return
        commentDetail = null
        commentReturn = null
        if (state.value.dialog == TvPlayerDialog.DanmakuMatch || state.value.dialog == TvPlayerDialog.EpisodeActions) closeDialog()
    }

    fun roomChanged(joined: Boolean, roomName: String) {
        val identity = joined to roomName
        if (roomIdentity != null && roomIdentity != identity) confirmLeave = false
        roomIdentity = identity
    }

    fun episodeSelected() = hideControls()

    fun mediaSelected() {
        state.value = state.value.copy(sourceDialogVisible = false)
        bump()
        focus.trySend(TvPlayerFocusRequest.SourceButton)
    }

    fun completeEpisodeAction(episodeId: Int, requestId: Long) {
        if (state.value.dialog == TvPlayerDialog.EpisodeActions && state.value.surfaceId == requestId && episodeActionId == episodeId) closeDialog()
    }

    fun completeMatching(requestId: Long) {
        if (state.value.dialog == TvPlayerDialog.DanmakuMatch && state.value.surfaceId == requestId) closeDialog()
    }

    private fun clearPreview() {
        cancelPendingScrubCommit()
        if (state.value.scrubMillis != null) execute(TvPlaybackCommand.PreviewSeek(null))
    }

    fun closeDialog() {
        val dialog = state.value.dialog ?: return
        state.value = state.value.copy(dialog = null)
        bump()
        focus.trySend(TvPlayerFocusRequest.DialogButton(dialog))
    }

    fun sourceControls() {
        releaseHeldSpeed()
        clearPreview()
        state.value = state.value.copy(
            controlsVisible = true,
            timelineOnly = false,
            recommendationsVisible = false,
            sourceDialogVisible = false,
            dialog = null,
            stripExpanded = false,
            activePanel = null,
        )
        bump()
        focus.trySend(TvPlayerFocusRequest.SourceButton)
    }

    fun autoHide() {
        if (playback().playerState.isPlaying && state.value.canAutoHide) hideControls()
    }

    /** 精简时间轴是调整进度的即时反馈, 手势结束后短暂停留即收起. */
    fun autoHideTimeline() {
        if (state.value.timelineOnly && state.value.scrubMillis == null) hideControls()
    }

    private fun bump() {
        state.value = state.value.copy(interactionGeneration = state.value.interactionGeneration + 1)
    }

    private fun showControls() {
        releaseHeldSpeed()
        clearPreview()
        state.value = state.value.copy(controlsVisible = true, timelineOnly = false, recommendationsVisible = false)
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    private fun hideControls() {
        releaseHeldSpeed()
        clearPreview()
        state.value = state.value.copy(
            controlsVisible = false,
            recommendationsVisible = false,
            stripExpanded = false,
            activePanel = null,
            dialog = null,
        )
        focus.trySend(TvPlayerFocusRequest.Root)
    }

    fun releaseHeldSpeed() {
        if (firedConfirm || state.value.speedHolding) execute(TvPlaybackCommand.SpeedHold(false))
        trackingConfirm = false
        firedConfirm = false
    }

    private fun moveScrub(deltaMillis: Long) {
        val playback = playback()
        if (playback.durationMillis <= 0) return
        // The VM applies the delta to the live player position, not the UI's sampled timestamp.
        execute(TvPlaybackCommand.PreviewBy(deltaMillis))
        state.value = state.value.copy(
            controlsVisible = true,
            // 控制层隐藏时进入调整只显示时间轴; 完整控制层内的调整保持完整.
            timelineOnly = state.value.timelineOnly || !state.value.controlsVisible,
            recommendationsVisible = false,
            activePanel = null,
            stripExpanded = false,
        )
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    /**
     * 按住 ←/→ 期间推进预览点: 方向键事件节流到 [SCRUB_REPEAT_INTERVAL_MILLIS] 一次;
     * 步长按按住时长加大, 单击 (首次按下) 永远是一档基础步长.
     *
     * 遥控器/输入链路可能把长按拆成连续的按下事件 (repeatCount 恒为 0), 因此用事件间隔
     * 判断是否同一次按住: 间隔不超过 [SCRUB_INPUT_IDLE_MILLIS] 视为继续, 否则从基础步长重新开始.
     * 换方向同样重置, 让过度快进后能立刻精细回退.
     */
    private fun moveScrubByKey(forward: Boolean, event: TvPlayerAction.RemoteKey) {
        val now = event.eventTimeMillis
        cancelPendingScrubCommit()
        val continuing = forward == scrubHoldForward && now - lastScrubKeyEventMillis <= SCRUB_INPUT_IDLE_MILLIS
        lastScrubKeyEventMillis = now
        if (!continuing) {
            scrubHoldForward = forward
            scrubHoldStartMillis = now
            advanceScrub(forward, stepForHold(heldMillis = 0), now)
            return
        }
        if (now - lastScrubAdvanceMillis < SCRUB_REPEAT_INTERVAL_MILLIS) return
        advanceScrub(forward, stepForHold(now - scrubHoldStartMillis), now)
    }

    private fun advanceScrub(forward: Boolean, stepMillis: Long, eventTimeMillis: Long) {
        lastScrubAdvanceMillis = eventTimeMillis
        moveScrub(if (forward) stepMillis else -stepMillis)
    }

    /** 按住时长对应的每步步长: 先精密后快速, 长片也不会失控. */
    private fun stepForHold(heldMillis: Long): Long = when {
        heldMillis < 1_000 -> 5_000L // 首秒保持单击步长
        heldMillis < 3_000 -> 10_000L
        heldMillis < 6_000 -> 30_000L
        else -> 60_000L
    }

    /**
     * 松开 ←/→: 记录提交目标, 等短暂空闲再真正 seek.
     *
     * 输入链路可能把长按拆成"按下-松开"的连续事件, 立即提交会导致连发期间不停 seek;
     * 空闲期 (见 [SCRUB_INPUT_IDLE_MILLIS]) 内再次按下方向键会取消提交, 继续本次调整.
     * 提交后预览点保留到界面位置追上目标 (由 VM 清除), 避免指示器与时间回跳.
     */
    private fun scheduleScrubCommit(event: TvPlayerAction.RemoteKey) {
        lastScrubKeyEventMillis = event.eventTimeMillis
        pendingScrubCommitMillis = state.value.scrubMillis ?: return
    }

    /** 空闲窗口结束: 提交松键时记录的目标位置. */
    fun commitPendingScrub() {
        val preview = pendingScrubCommitMillis ?: return
        pendingScrubCommitMillis = null
        if (state.value.scrubMillis != preview) return
        execute(TvPlaybackCommand.SeekTo(preview))
        bump()
    }

    private fun cancelPendingScrubCommit() {
        pendingScrubCommitMillis = null
    }

    private fun togglePause() {
        val wasPlayWhenReady = playback().playerState.playWhenReady
        execute(TvPlaybackCommand.TogglePause)
        if (wasPlayWhenReady && !state.value.controlsVisible) showControls() else bump()
    }

    private fun back(): Boolean {
        when {
            danmakuAdjustment != null -> danmakuAdjustment = null
            commentDetail != null -> commentDetail = null
            state.value.activePanel == TvPlayerPanel.Together && confirmLeave -> confirmLeave = false
            state.value.activePanel == TvPlayerPanel.Collection && collectionPrompt != null -> collectionPrompt = null
            state.value.dialog != null -> closeDialog()
            state.value.sourceDialogVisible -> mediaSelected()
            state.value.recommendationsVisible -> closeRecommendations()
            state.value.activePanel != null -> {
                val panel = state.value.activePanel!!
                state.value = state.value.copy(activePanel = null)
                bump()
                focus.trySend(TvPlayerFocusRequest.PanelChip(panel))
            }

            state.value.scrubMillis != null -> {
                clearPreview()
                // 精简控制层随预览取消一并收起; 完整控制层保持显示.
                if (state.value.timelineOnly) hideControls() else bump()
            }

            state.value.stripExpanded -> {
                state.value = state.value.copy(stripExpanded = false)
                bump()
                focus.trySend(TvPlayerFocusRequest.EpisodesButton)
            }

            state.value.controlsVisible -> hideControls()
            else -> return false
        }
        return true
    }

    private fun openRecommendations() {
        releaseHeldSpeed()
        clearPreview()
        state.value = state.value.copy(
            controlsVisible = true,
            timelineOnly = false,
            recommendationsVisible = true,
            stripExpanded = false,
            activePanel = null,
            dialog = null,
            sourceDialogVisible = false,
        )
        bump()
        focus.trySend(TvPlayerFocusRequest.Recommendations)
    }

    private fun closeRecommendations() {
        if (!state.value.recommendationsVisible) return
        state.value = state.value.copy(recommendationsVisible = false)
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    private fun onKey(event: TvPlayerAction.RemoteKey): Boolean {
        val key = event.key
        val isDown = event.isDown
        val isNewPress = isDown && event.repeatCount == 0
        if (key == TvRemoteKey.Menu) {
            if (isNewPress) sourceControls()
            return true
        }
        // Explicit Play/Pause keys are idempotent, while PlayPause toggles.
        when (key) {
            TvRemoteKey.PlayPause, TvRemoteKey.Play, TvRemoteKey.Pause -> {
                if (isNewPress && (key == TvRemoteKey.PlayPause ||
                            (key == TvRemoteKey.Play && !playback().playerState.playWhenReady) ||
                            (key == TvRemoteKey.Pause && playback().playerState.playWhenReady))
                ) togglePause()
                return true
            }

            TvRemoteKey.Next, TvRemoteKey.Previous -> {
                if (isNewPress) {
                    execute(TvPlaybackCommand.SwitchNeighbor(if (key == TvRemoteKey.Next) 1 else -1))
                    showControls()
                }
                return true
            }

            else -> Unit
        }
        if (state.value.sourceDialogVisible) {
            return !event.sourceDialogFocused && key in navigationKeys
        }
        if (state.value.dialog != null) return !event.dialogFocused && key in navigationKeys
        if (state.value.recommendationsVisible) {
            if (isDown) bump()
            if (key == TvRemoteKey.Up) {
                if (isNewPress) closeRecommendations()
                return true
            }
            // Do not send navigation to the outgoing controller while the row is entering.
            return !event.recommendationsFocused && key in navigationKeys
        }
        // Until the sidebar has acquired focus, D-pad input must not operate the player behind it.
        if (state.value.sidebarVisible) {
            if (isDown) bump()
            return !event.sidebarFocused && key in navigationKeys
        }
        // 画面预览: ←/→ 按住期间移动预览点, 松键后短暂空闲即提交; 返回/↑↓ 取消, 确认=播停.
        if (state.value.scrubMillis != null) {
            when (key) {
                TvRemoteKey.Left, TvRemoteKey.Right -> if (isDown) {
                    moveScrubByKey(forward = key == TvRemoteKey.Right, event)
                } else {
                    scheduleScrubCommit(event)
                }

                TvRemoteKey.Confirm -> if (isNewPress) togglePause()
                TvRemoteKey.Up, TvRemoteKey.Down -> if (isNewPress) {
                    clearPreview()
                    if (state.value.timelineOnly) showControls() else bump()
                }

                else -> return false
            }
            return true
        }
        // 控制层隐藏: 确认短按=播↔停/长按=倍速; ←/→ 调整进度 (只显示时间轴); ↑↓ 唤出控制层.
        if (!state.value.controlsVisible) {
            when (key) {
                TvRemoteKey.Confirm -> {
                    if (isDown) {
                        if (isNewPress) {
                            releaseHeldSpeed()
                            trackingConfirm = true
                        } else if (trackingConfirm && !firedConfirm) {
                            firedConfirm = true
                            execute(TvPlaybackCommand.SpeedHold(true))
                        }
                    } else if (trackingConfirm) {
                        val wasHeld = firedConfirm
                        releaseHeldSpeed()
                        if (!wasHeld) togglePause()
                    }
                }

                TvRemoteKey.Left, TvRemoteKey.Right -> if (isDown) moveScrubByKey(forward = key == TvRemoteKey.Right, event)
                TvRemoteKey.Up, TvRemoteKey.Down -> if (isNewPress) showControls()
                else -> return false
            }
            return true
        }
        // 精简时间轴: 继续调整进度, ↑↓ 展开完整控制层, 确认=播停.
        if (state.value.timelineOnly) {
            if (isDown) bump()
            when (key) {
                TvRemoteKey.Left, TvRemoteKey.Right -> if (isDown) moveScrubByKey(forward = key == TvRemoteKey.Right, event)
                TvRemoteKey.Up, TvRemoteKey.Down -> if (isNewPress) showControls()
                TvRemoteKey.Confirm -> if (isNewPress) togglePause()
                else -> return false
            }
            return true
        }
        if (isDown) bump()
        if (event.seekBarFocused) {
            when (key) {
                TvRemoteKey.Left, TvRemoteKey.Right -> {
                    if (isDown) moveScrubByKey(forward = key == TvRemoteKey.Right, event)
                    return true
                }

                TvRemoteKey.Confirm -> {
                    if (isNewPress) togglePause(); return true
                }

                else -> Unit
            }
        }
        if (event.iconRowFocused && key == TvRemoteKey.Up) {
            if (isNewPress) focus.trySend(TvPlayerFocusRequest.SeekBar)
            return true
        }
        if (event.iconRowFocused && key == TvRemoteKey.Down) {
            if (isNewPress) openRecommendations()
            return true
        }
        return false
    }

    companion object {
        fun saver(create: () -> TvPlayerPresentationState) = mapSaver(
            save = { value ->
                val overlay = value.state.value
                mapOf(
                    "controls" to overlay.controlsVisible,
                    "strip" to overlay.stripExpanded,
                    "recommendations" to overlay.recommendationsVisible,
                    "panel" to overlay.activePanel?.name,
                    "source" to overlay.sourceDialogVisible,
                    "dialog" to overlay.dialog?.name,
                    "surfaceId" to overlay.surfaceId,
                    "episode" to value.episodeId,
                    "episodeAction" to value.episodeActionId,
                    "collectionPrompt" to value.collectionPrompt?.name,
                    "confirmLeave" to value.confirmLeave,
                    "roomJoined" to value.roomIdentity?.first,
                    "roomName" to value.roomIdentity?.second,
                    "matchSource" to value.matchReturnSource?.value,
                    "stats" to value.statsVisible,
                    // The reader's content comes from Paging; restore its list position by identity.
                    "commentId" to value.commentReturn?.first,
                    "commentIndex" to value.commentReturn?.second,
                )
            },
            restore = { saved ->
                create().apply {
                    state.value = state.value.copy(
                        controlsVisible = saved["controls"] as Boolean,
                        stripExpanded = saved["strip"] as Boolean,
                        recommendationsVisible = saved["recommendations"] as Boolean,
                        activePanel = (saved["panel"] as String?)?.let(TvPlayerPanel::valueOf),
                        sourceDialogVisible = saved["source"] as Boolean,
                        dialog = (saved["dialog"] as String?)?.let(TvPlayerDialog::valueOf),
                        surfaceId = saved["surfaceId"] as Long,
                    )
                    episodeId = saved["episode"] as Int?
                    episodeActionId = saved["episodeAction"] as Int?
                    collectionPrompt = (saved["collectionPrompt"] as String?)?.let(TvCollectionPrompt::valueOf)
                    confirmLeave = saved["confirmLeave"] as Boolean
                    roomIdentity = (saved["roomJoined"] as Boolean?)?.let { it to (saved["roomName"] as String) }
                    matchReturnSource = (saved["matchSource"] as String?)?.let(::DanmakuServiceId)
                    statsVisible = saved["stats"] as Boolean
                    commentReturn = (saved["commentId"] as String?)?.let { it to (saved["commentIndex"] as Int) }
                }
            },
        )

        private val navigationKeys =
            setOf(TvRemoteKey.Left, TvRemoteKey.Right, TvRemoteKey.Up, TvRemoteKey.Down, TvRemoteKey.Confirm)

        /** 按住方向键调整进度时预览点的最小推进间隔 (系统按键连发约 50ms 一次, 全量处理会让数字闪烁). */
        private const val SCRUB_REPEAT_INTERVAL_MILLIS = 200L

        /**
         * 方向键事件间隔不超过该值视为同一次按住; 松键后空闲同样时长再提交 seek.
         *
         * 部分遥控器/输入链路把长按拆成连续的按下事件 (repeatCount 恒为 0), 不能依赖连发计数.
         */
        internal const val SCRUB_INPUT_IDLE_MILLIS = 300L
    }
}

@Composable
internal fun rememberTvPlayerPresentationState(
    uiState: TvEpisodeUiState,
    onIntent: (TvEpisodeIntent) -> Boolean,
): TvPlayerPresentationState {
    val latest by rememberUpdatedState(uiState)
    val dispatch by rememberUpdatedState(onIntent)
    val create = {
        TvPlayerPresentationState(
            playback = { TvPlaybackSnapshot(latest.playerState, latest.positionMillis, latest.durationMillis) },
            execute = { command ->
                dispatch(when (command) {
                    is TvPlaybackCommand.PreviewBy -> TvEpisodeIntent.PreviewBy(command.deltaMillis)
                    is TvPlaybackCommand.PreviewSeek -> TvEpisodeIntent.PreviewSeek(command.positionMillis)
                    TvPlaybackCommand.TogglePause -> TvEpisodeIntent.TogglePause
                    is TvPlaybackCommand.SeekTo -> TvEpisodeIntent.SeekTo(command.positionMillis)
                    is TvPlaybackCommand.SpeedHold -> TvEpisodeIntent.HoldSpeed(command.engaged)
                    is TvPlaybackCommand.SwitchNeighbor -> TvEpisodeIntent.SwitchNeighbor(command.offset)
                    TvPlaybackCommand.CycleAspectRatio -> TvEpisodeIntent.CycleAspectRatio
                })
            },
            playbackInteraction = uiState.interaction,
        )
    }
    val saver = remember(uiState.interaction) { TvPlayerPresentationState.saver(create) }
    return rememberSaveable(uiState.interaction, saver = saver, init = create)
}
