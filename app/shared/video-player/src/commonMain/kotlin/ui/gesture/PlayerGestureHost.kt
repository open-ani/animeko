/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.annotation.UiThread
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.systemGesturesPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.effects.onPointerEventMultiplatform
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.isSystemInFullscreen
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.utils.fixToString
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.BRIGHTNESS
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.FAST_BACKWARD
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.FAST_FORWARD
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.PAUSED_ONCE
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.RESUMED_ONCE
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.SEEKING
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.VOLUME
import me.him188.ani.app.videoplayer.ui.gesture.SwipeSeekerState.Companion.swipeToSeek
import me.him188.ani.app.videoplayer.ui.playerFocusHost
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.utils.platform.Platform
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AudioLevelController
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

@Stable
private fun renderTime(seconds: Int): String {
    return "${(seconds / 60).fixToString(2)}:${(seconds % 60).fixToString(2)}"
}

@Composable
fun rememberGestureIndicatorState(): GestureIndicatorState = remember { GestureIndicatorState() }

@Stable
class GestureIndicatorState {
    internal enum class State {
        PAUSED_ONCE,
        RESUMED_ONCE,
        VOLUME,
        BRIGHTNESS,
        SEEKING,
        FAST_FORWARD,
        FAST_BACKWARD,
    }

    internal var visible: Boolean by mutableStateOf(false)
    internal var state: State? by mutableStateOf(null)
    internal var progressValue: Float by mutableFloatStateOf(0f)
    internal var deltaSeconds: Int by mutableIntStateOf(0)
    internal var seekCancelled: Boolean by mutableStateOf(false)
    private var counter: Int = 0

    private inline fun startShow(
        state: State,
        setup: () -> Unit = {},
    ): Int {
        val ticket = ++counter
        setup()
        this.state = state
        visible = true
        return ticket
    }

    private inline fun show(
        state: State,
        setup: () -> Unit = {},
        action: () -> Unit
    ) {
        val ticket = ++counter
        try {
            setup()
            this.state = state
            visible = true
            action()
        } finally {
            if (this.counter == ticket && // no one changed the state after us
                this.state == state
            ) {
                visible = false
            }
        }
    }

    private companion object {
        private const val LONG: Long = 700
        private const val SHORT: Long = 500
    }

    @UiThread
    suspend fun showPausedLong() {
        show(PAUSED_ONCE) {
            delay(LONG)
        }
    }

    @UiThread
    suspend fun showResumedLong() {
        show(RESUMED_ONCE) {
            delay(LONG)
        }
    }

    @UiThread
    suspend fun showVolumeRange(currentRatio: Float) {
        show(VOLUME, setup = { progressValue = currentRatio }) {
            delay(SHORT)
        }
    }

    @UiThread
    suspend fun showBrightnessRange(currentRatio: Float) {
        show(BRIGHTNESS, setup = { progressValue = currentRatio }) {
            delay(SHORT)
        }
    }

    @UiThread
    suspend fun showSeeking(
        deltaSeconds: Int,
    ) {
        show(SEEKING, setup = {
            this.deltaSeconds = deltaSeconds
            seekCancelled = false
        }) {
            delay(SHORT)
        }
    }

    @UiThread
    fun startSeekCancellation(): Int {
        return startShow(SEEKING) {
            seekCancelled = true
        }
    }

    @UiThread
    fun stopSeekCancellation(ticket: Int) {
        stopShow(ticket)
    }

    @UiThread
    fun startFastForward(): Int {
        startShow(FAST_FORWARD, setup = { })
        return counter
    }

    @UiThread
    fun stopFastForward(ticket: Int) {
        stopShow(ticket)
    }

    @UiThread
    fun startFastBackward(): Int {
        startShow(FAST_BACKWARD, setup = { })
        return counter
    }

    @UiThread
    fun stopFastBackward(ticket: Int) {
        stopShow(ticket)
    }

    private fun stopShow(ticket: Int) {
        if (ticket == this.counter) {
            visible = false
        }
    }
}

/**
 * 展示当前快进/快退秒数的指示器.
 *
 * `<< 00:00` / `>> 00:00`
 */
@Composable
fun GestureIndicator(
    state: GestureIndicatorState,
    swipeSeekerState: SwipeSeekerState? = null,
) {
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    val activeSwipeSeekerState = swipeSeekerState?.takeIf { it.isSeeking }
    var lastDelta by remember(state) {
        mutableIntStateOf(state.deltaSeconds)
    }

    AniAnimatedVisibility(
        visible = state.visible || activeSwipeSeekerState != null,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(tween(durationMillis = 500)),
        label = "SeekPositionIndicator",
    ) {
        Surface(
            Modifier.alpha(0.8f),
            color = colors.surface,
            shape = shape,
            shadowElevation = 1.dp,
            contentColor = colors.onSurface,
        ) {
            val iconSize = 36.dp
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) {
                Row(
                    Modifier.background(Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(iconSize),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Used by volume and brightness
                    val progressIndicator: @Composable () -> Unit = remember(state, colors) {
                        // This remember is needed because Compose does not remember lambdas 
                        // and can cause performance problem in this fast-changing composable.
                        {
                            LinearProgressIndicator(
                                progress = { state.progressValue },
                                modifier = Modifier.width(80.dp),
                                color = colors.primary,
                                trackColor = colors.onSurface.copy(alpha = 0.5f),
                                drawStopIndicator = {},
                            )
                        }
                    }

                    when (if (activeSwipeSeekerState != null) SEEKING else state.state) {
                        RESUMED_ONCE -> {
                            Icon(
                                Icons.Rounded.PlayArrow, null,
                                Modifier.size(iconSize).background(Color.Transparent),
                            )
                        }

                        PAUSED_ONCE -> {
                            Icon(Icons.Rounded.Pause, null, Modifier.size(iconSize))
                        }

                        SEEKING -> {
                            val deltaDuration = activeSwipeSeekerState?.deltaSeconds ?: state.deltaSeconds
                            val seekCancelled = activeSwipeSeekerState?.isCancelled ?: state.seekCancelled
                            // 记忆变为 0 之前的 delta, 这样在快进/快退结束后, 会显示上一次的 delta, 而不是显示 0
                            val duration = if (deltaDuration == 0) {
                                lastDelta
                            } else {
                                deltaDuration.also {
                                    lastDelta = deltaDuration
                                }
                            }

                            Icon(
                                when {
                                    seekCancelled -> Icons.Rounded.Close
                                    duration > 0 -> Icons.Rounded.FastForward
                                    else -> Icons.Rounded.FastRewind
                                },
                                contentDescription = null,
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                text = if (seekCancelled) {
                                    stringResource(Lang.video_player_release_to_cancel)
                                } else {
                                    renderTime(duration.absoluteValue)
                                },
                                maxLines = 1,
                            )
                        }

                        VOLUME -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp, null,
                                Modifier.size(iconSize),
                            )
                            progressIndicator()
                        }

                        BRIGHTNESS -> {
                            Icon(
                                when (state.progressValue) {
                                    in 0.67..1.0 -> Icons.Rounded.BrightnessHigh
                                    in 0.33..0.67 -> Icons.Rounded.BrightnessMedium
                                    else -> Icons.Rounded.BrightnessLow
                                },
                                null,
                                Modifier.size(iconSize),
                            )
                            progressIndicator()
                        }

                        FAST_FORWARD -> {
                            Icon(Icons.Rounded.FastForward, null, Modifier.size(iconSize))
                        }

                        FAST_BACKWARD -> {
                            Icon(Icons.Rounded.FastRewind, null, Modifier.size(iconSize))
                        }

                        null -> {}
                    }
                }
            }
        }
    }
}

@Stable
val Platform.mouseFamily: GestureFamily
    get() = when (this) {
        is Platform.Desktop -> GestureFamily.MOUSE
        is Platform.Android, is Platform.Ios -> GestureFamily.TOUCH
    }

@Immutable
enum class GestureFamily(
    val clickToPauseResume: Boolean,
    val clickToToggleController: Boolean,
    val doubleClickToFullscreen: Boolean,
    val doubleClickToPauseResume: Boolean,
    val swipeToSeek: Boolean,
    val swipeRhsForVolume: Boolean,
    val swipeLhsForBrightness: Boolean,
    val swipeMidForFullscreen: Boolean,
    val longPressForFastSkip: Boolean,
    val scrollForVolume: Boolean,
    val autoHideController: Boolean,
    val volumeControllerOnBottomBar: Boolean,
    val mouseHoverForController: Boolean = true, // not supported on mobile
) {
    TOUCH(
        clickToPauseResume = false,
        clickToToggleController = true,
        doubleClickToFullscreen = false,
        doubleClickToPauseResume = true,
        swipeToSeek = true,
        swipeRhsForVolume = true,
        swipeLhsForBrightness = true,
        swipeMidForFullscreen = true,
        longPressForFastSkip = true,
        volumeControllerOnBottomBar = false,
        scrollForVolume = false,
        autoHideController = true,
        mouseHoverForController = false,
    ),
    MOUSE(
        clickToPauseResume = true,
        clickToToggleController = false,
        doubleClickToFullscreen = true,
        doubleClickToPauseResume = false,
        swipeToSeek = false,
        swipeRhsForVolume = false,
        swipeLhsForBrightness = false,
        swipeMidForFullscreen = false,
        longPressForFastSkip = false,
        scrollForVolume = true,
        autoHideController = false,
        volumeControllerOnBottomBar = true,
    )
}

val VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION = 3.seconds
val VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION = 3.seconds

/**
 * 将屏幕横滑 seek 的状态迁移映射到控制器显隐和进度预览。
 * [SwipeSeekerState] 负责识别手势，本类只响应开始、取消区域变化和结束事件。
 */
private class SwipeSeekInteraction(
    private val controllerState: PlayerControllerState,
    private val seekerState: SwipeSeekerState,
    private val progressSliderState: PlayerProgressSliderState,
) {
    fun onStarted() {
        if (controllerState.visibility.bottomBar) {
            controllerState.setRequestInlineProgressSlider(this)
        } else {
            controllerState.setRequestProgressBar(this)
        }
    }

    fun onCancellationChanged(cancelled: Boolean) {
        if (cancelled) {
            progressSliderState.cancelPreview()
        } else {
            updatePreview()
        }
    }

    fun updatePreview() {
        if (seekerState.isCancelled) {
            progressSliderState.cancelPreview()
            return
        }
        if (progressSliderState.totalDurationMillis == 0L) return

        val previewPositionMillis =
            progressSliderState.currentPositionMillis + seekerState.deltaSeconds.times(1000)
        val offsetRatio = previewPositionMillis.toFloat() / progressSliderState.totalDurationMillis
        progressSliderState.previewPositionRatio(offsetRatio.coerceIn(0f, 1f))
    }

    fun onStopped(cancelled: Boolean) {
        cancelControllerRequest()
        if (cancelled) {
            progressSliderState.cancelPreview()
        } else {
            progressSliderState.finishPreview()
        }
    }

    fun dispose() {
        cancelControllerRequest()
    }

    private fun cancelControllerRequest() {
        controllerState.cancelRequestInlineProgressSlider(this)
        controllerState.cancelRequestProgressBarVisible(this)
    }
}

@Composable
private fun rememberSwipeSeekInteraction(
    controllerState: PlayerControllerState,
    seekerState: SwipeSeekerState,
    progressSliderState: PlayerProgressSliderState,
): SwipeSeekInteraction {
    val interaction = remember(controllerState, seekerState, progressSliderState) {
        SwipeSeekInteraction(controllerState, seekerState, progressSliderState)
    }
    DisposableEffect(interaction) {
        onDispose(interaction::dispose)
    }
    return interaction
}

@Composable
fun PlayerGestureHost(
    controllerState: PlayerControllerState,
    seekerState: SwipeSeekerState,
    progressSliderState: PlayerProgressSliderState,
    indicatorState: GestureIndicatorState,
    fastSkipState: FastSkipState?,
    playerState: MediampPlayer, // TODO: remove playerState from VideoGestureHost
    enableSwipeToSeek: Boolean,
    audioController: LevelController,
    brightnessController: LevelController,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    modifier: Modifier = Modifier,
    family: GestureFamily = LocalPlatform.current.mouseFamily,
    onTogglePauseResume: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onExitFullscreen: () -> Unit = {},
    onToggleDanmaku: () -> Unit = {},
    onTogglePlayerStats: () -> Unit = {},
) {
    val onTogglePauseResumeState by rememberUpdatedState(onTogglePauseResume)

    BoxWithConstraints {
        Row(
            Modifier.align(Alignment.TopCenter)
                .systemGesturesPadding()
                .padding(top = 16.dp),
        ) {
            GestureIndicator(indicatorState, swipeSeekerState = seekerState)
        }
        val maxHeight = maxHeight
        val adjustingVolumeOrBrightness =
            indicatorState.visible && (indicatorState.state == VOLUME || indicatorState.state == BRIGHTNESS)
        val adjustingForwardOrBackward =
            indicatorState.visible && (indicatorState.state == FAST_FORWARD || indicatorState.state == FAST_BACKWARD)

        val indicatorTasker = rememberUiMonoTasker()
        val audioLevelController = playerState.features[AudioLevelController]
        val useMediaAudioController = family == GestureFamily.MOUSE
        val systemFullscreen = isSystemInFullscreen()
        val playerFocusState = controllerState.focusState

        val keyboardModifier = modifier
            .testTag("VideoGestureHost")
            .playerKeyboardShortcuts(
                seekerState = seekerState,
                fastSkipState = fastSkipState,
                playbackSpeedControllerState = playbackSpeedControllerState,
                volumeEnabled = !useMediaAudioController || audioLevelController != null,
                onVolumeUp = { fineAdjustment ->
                    if (useMediaAudioController) {
                        checkNotNull(audioLevelController)
                        if (fineAdjustment) audioLevelController.volumeUp(0.01f) else audioLevelController.volumeUp()
                        audioLevelController.setMute(false)
                        indicatorTasker.launch {
                            indicatorState.showVolumeRange(audioLevelController.volume.value / audioLevelController.maxVolume)
                        }
                    } else {
                        audioController.increaseLevel(if (fineAdjustment) audioController.levelStep else 0.10f)
                        indicatorTasker.launch {
                            indicatorState.showVolumeRange(audioController.level)
                        }
                    }
                },
                onVolumeDown = { fineAdjustment ->
                    if (useMediaAudioController) {
                        checkNotNull(audioLevelController)
                        if (fineAdjustment) audioLevelController.volumeDown(0.01f) else audioLevelController.volumeDown()
                        audioLevelController.setMute(false)
                        indicatorTasker.launch {
                            indicatorState.showVolumeRange(audioLevelController.volume.value / audioLevelController.maxVolume)
                        }
                    } else {
                        audioController.decreaseLevel(if (fineAdjustment) audioController.levelStep else 0.10f)
                        indicatorTasker.launch {
                            indicatorState.showVolumeRange(audioController.level)
                        }
                    }
                },
                onTogglePauseResume = onTogglePauseResumeState,
                onToggleFullscreen = onToggleFullscreen,
                onExitFullscreen = onExitFullscreen,
                onToggleDanmaku = onToggleDanmaku,
                onTogglePlayerStats = onTogglePlayerStats,
            )
            .playerFocusHost(playerFocusState, systemFullscreen)

        if (family.autoHideController) {
            LaunchedEffect(controllerState.visibility, controllerState.alwaysOn) {
                if (controllerState.alwaysOn) return@LaunchedEffect
                if (controllerState.visibility.bottomBar) {
                    delay(VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION)
                    controllerState.toggleFullVisible(false)
                }
            }
        }

        if (family.mouseHoverForController) {
            // 没有人请求 alwaysOn 时自动隐藏控制器
            LaunchedEffect(controllerState) {
                snapshotFlow { controllerState.alwaysOn }.collectLatest { alwaysOn ->
                    if (alwaysOn) return@collectLatest
                    snapshotFlow { controllerState.visibility != ControllerVisibility.Invisible }.collectLatest {
                        if (!it) {
                            delay(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION)
                            controllerState.toggleFullVisible(false)
                        }
                    }
                }
            }
        }

        @Composable
        fun Modifier.combineClickableWithFamilyGesture() = this then
                combinedClickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = remember(family, playerFocusState) {
                        {
                            if (family.clickToPauseResume) {
                                onTogglePauseResumeState()
                            }
                            if (family.clickToToggleController) {
                                controllerState.toggleFullVisible()
                            }
                            playerFocusState.requestPlayerFocus()
                        }
                    },
                    onDoubleClick = remember(family, onToggleFullscreen, playerFocusState) {
                        {
                            if (family.doubleClickToFullscreen) {
                                onToggleFullscreen()
                            }
                            if (family.doubleClickToPauseResume) {
                                onTogglePauseResumeState()
                            }
                            playerFocusState.requestPlayerFocus()
                        }
                    },
                )

        val mouseMoveTasker = rememberUiMonoTasker()
        Box(
            keyboardModifier
                .combineClickableWithFamilyGesture()
                .ifThen(family.swipeToSeek && enableSwipeToSeek) {
                    val swipeSeekInteraction = rememberSwipeSeekInteraction(
                        controllerState,
                        seekerState,
                        progressSliderState,
                    )
                    swipeToSeek(
                        seekerState,
                        Orientation.Horizontal,
                        //调节音量/亮度时禁用水平seek
                        enabled = !adjustingVolumeOrBrightness,
                        onDragStarted = {
                            swipeSeekInteraction.onStarted()
                        },
                        onDragStopped = { _, cancelled ->
                            swipeSeekInteraction.onStopped(cancelled)
                        },
                        onCancellationChanged = { cancelled ->
                            swipeSeekInteraction.onCancellationChanged(cancelled)
                        },
                    ) {
                        swipeSeekInteraction.updatePreview()
                    }
                }
                .onPointerEventMultiplatform(PointerEventType.Move) { event ->
                    if (event.changes.firstOrNull()?.type == PointerType.Mouse) {
                        playerFocusState.requestPlayerFocus()
                    }
                }
                .ifThen(family.mouseHoverForController) {
                    // 这里不能用 hover, 因为在当控制器隐藏后, hover 状态仍然有, 于是下次移动鼠标时不会重复触发 hover 事件, 也就无法显示
                    // See test case: `mouse - mouseHoverForController - center screen twice`
                    onPointerEventMultiplatform(PointerEventType.Move) { _ ->
                        controllerState.toggleFullVisible(true)
                        mouseMoveTasker.launch {
                            delay(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION)
                            controllerState.toggleFullVisible(false)
                        }
                    }
                }
                .ifThen(family.scrollForVolume && audioLevelController != null) {
                    if (audioLevelController == null) return@ifThen this
                    onPointerEventMultiplatform(PointerEventType.Scroll) { event ->
                        event.changes.firstOrNull()?.scrollDelta?.y?.run {
                            audioLevelController.setMute(false)
                            if (this < 0) audioLevelController.volumeUp()
                            else if (this > 0) audioLevelController.volumeDown()

                            indicatorTasker.launch {
                                indicatorState.showVolumeRange(audioLevelController.volume.value / audioLevelController.maxVolume)
                            }
                        }
                    }
                }
                // Do not remove this as redundant with combinedClickable. Its focus target uses
                // Focusability.SystemDefined, which is not focusable while Android is in touch input mode.
                // This always-focusable child is the fallback that keeps hardware shortcuts working.
                .focusable()
                .fillMaxSize(),
        ) {
            Row(
                Modifier.matchParentSize()
                    .ifThen(
                        family.swipeLhsForBrightness ||
                                family.swipeRhsForVolume ||
                                family.swipeMidForFullscreen ||
                                family.longPressForFastSkip,
                    ) {
                        systemGesturesPadding()
                    }
                    .ifThen(family.longPressForFastSkip) {
                        fastSkipState?.let {
                            longPressFastSkip(it, SkipDirection.FORWARD)
                        }
                    },
            ) {
                Box(
                    Modifier
                        .ifThen(family.swipeLhsForBrightness) {
                            swipeLevelControlWithIndicator(
                                brightnessController,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                Orientation.Vertical,
                                indicatorState,
                                enabled = !seekerState.isSeeking && !adjustingForwardOrBackward,
                                step = 0.01f,
                                setup = {
                                    indicatorState.state = BRIGHTNESS
                                },
                            )
                        }
                        .weight(1f)
                        .fillMaxHeight(),
                )

                Box(
                    Modifier
                        .ifThen(family.swipeMidForFullscreen) {
                            swipeToFullscreen(
                                enabled = !seekerState.isSeeking && !adjustingVolumeOrBrightness && !adjustingForwardOrBackward,
                                onEnterFullscreen = {
                                    if (!systemFullscreen) onToggleFullscreen()
                                },
                                onExitFullscreen = {
                                    if (systemFullscreen) onExitFullscreen()
                                },
                            )
                        }
                        .weight(1f)
                        .fillMaxHeight(),
                )

                Box(
                    Modifier
                        .ifThen(family.swipeRhsForVolume) {
                            swipeLevelControlWithIndicator(
                                audioController,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                Orientation.Vertical,
                                indicatorState,
                                enabled = !seekerState.isSeeking && !adjustingForwardOrBackward,
                                step = 0.05f,
                                setup = {
                                    indicatorState.state = VOLUME
                                },
                            )
                        }
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        if (family.clickToToggleController && systemFullscreen) {
            // 状态栏区域响应点击手势
            Box(
                Modifier.fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.systemGestures)
                    .combineClickableWithFamilyGesture(),
            )
        }
    }
}
