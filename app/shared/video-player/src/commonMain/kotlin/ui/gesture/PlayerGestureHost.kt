/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.effects.ComposeKey
import me.him188.ani.app.ui.foundation.effects.onKey
import me.him188.ani.app.ui.foundation.effects.onPointerEventMultiplatform
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.isSystemInFullscreen
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
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.app.videoplayer.ui.rememberAlwaysOnRequester
import me.him188.ani.app.videoplayer.ui.top.needWorkaroundForFocusManager
import me.him188.ani.utils.platform.Platform
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AudioLevelController
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
        show(SEEKING, setup = { this.deltaSeconds = deltaSeconds }) {
            delay(SHORT)
        }
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
) {
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    var lastDelta by remember(state) {
        mutableIntStateOf(state.deltaSeconds)
    }

    AniAnimatedVisibility(
        visible = state.visible,
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

                    when (state.state) {
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
                            val deltaDuration = state.deltaSeconds
                            // 记忆变为 0 之前的 delta, 这样在快进/快退结束后, 会显示上一次的 delta, 而不是显示 0
                            val duration = if (deltaDuration == 0) {
                                lastDelta
                            } else {
                                deltaDuration.also {
                                    lastDelta = deltaDuration
                                }
                            }

                            Icon(
                                if (duration > 0) {
                                    Icons.Rounded.FastForward
                                } else {
                                    Icons.Rounded.FastRewind
                                },
                                null,
                                Modifier.size(iconSize),
                            )
                            val text = renderTime(duration.absoluteValue)
                            Text(
                                text,
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
    val useDesktopGestureLayoutWorkaround: Boolean,
    val clickToPauseResume: Boolean,
    val clickToToggleController: Boolean,
    val doubleClickToFullscreen: Boolean,
    val doubleClickToPauseResume: Boolean,
    val swipeToSeek: Boolean,
    val swipeRhsForVolume: Boolean,
    val swipeLhsForBrightness: Boolean,
    val longPressForFastSkip: Boolean,
    val scrollForVolume: Boolean,
    val autoHideController: Boolean,
    val volumeControllerOnBottomBar: Boolean,
    val keyboardSpaceForPauseResume: Boolean = true,
    val keyboardUpDownForVolume: Boolean = true,
    val keyboardLeftRightToSeek: Boolean = true,
    val mouseHoverForController: Boolean = true, // not supported on mobile
    val keyboardControlFullscreen: Boolean = true,
    val keyboardControlSpeed: Boolean = true,
    val keyboardToggleDanmaku: Boolean = true,
) {
    TOUCH(
        useDesktopGestureLayoutWorkaround = false,
        clickToPauseResume = false,
        clickToToggleController = true,
        doubleClickToFullscreen = false,
        doubleClickToPauseResume = true,
        swipeToSeek = true,
        swipeRhsForVolume = true,
        swipeLhsForBrightness = true,
        longPressForFastSkip = true,
        volumeControllerOnBottomBar = false,
        scrollForVolume = false,
        autoHideController = true,
        mouseHoverForController = false,
    ),
    MOUSE(
        useDesktopGestureLayoutWorkaround = true,
        clickToPauseResume = true,
        clickToToggleController = false,
        doubleClickToFullscreen = true,
        doubleClickToPauseResume = false,
        swipeToSeek = false,
        swipeRhsForVolume = false,
        swipeLhsForBrightness = false,
        longPressForFastSkip = false,
        scrollForVolume = true,
        autoHideController = false,
        volumeControllerOnBottomBar = true,
    )
}

val VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION = 3.seconds
val VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION = 3.seconds

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
) {
    val onTogglePauseResumeState by rememberUpdatedState(onTogglePauseResume)

    BoxWithConstraints {
        Row(
            Modifier.align(Alignment.TopCenter)
                .systemGesturesPadding()
                .padding(top = 16.dp),
        ) {
            LaunchedEffect(seekerState.deltaSeconds) {
                if (seekerState.isSeeking) {
                    indicatorState.showSeeking(seekerState.deltaSeconds)
                }
            }
            GestureIndicator(indicatorState)
        }
        val maxHeight = maxHeight
        val adjustingVolumeOrBrightness =
            indicatorState.visible && (indicatorState.state == VOLUME || indicatorState.state == BRIGHTNESS)
        val adjustingForwardOrBackward =
            indicatorState.visible && (indicatorState.state == FAST_FORWARD || indicatorState.state == FAST_BACKWARD)

        // TODO: 临时解决方案, 安卓和 PC 需要不同的组件层级关系才能实现各种快捷手势
        val needWorkaroundForFocusManager = needWorkaroundForFocusManager
        if (family.useDesktopGestureLayoutWorkaround) {
            val indicatorTasker = rememberUiMonoTasker()
            val focusRequester = remember { FocusRequester() }
            val manager = LocalFocusManager.current
            val keyboardFocus = remember { FocusRequester() } // focus 了才能用键盘快捷键

            val audioLevelController = playerState.features[AudioLevelController]
            Box(
                modifier
                    .focusRequester(keyboardFocus)
                    .ifThen(family.swipeToSeek) {
                        swipeToSeek(
                            seekerState,
                            Orientation.Horizontal,
                            //调节音量/亮度时禁用水平seek
                            enabled = !adjustingVolumeOrBrightness,
                        )
                    }
                    .ifThen(family.keyboardLeftRightToSeek) {
                        onKeyboardHorizontalDirection(
                            onBackward = {
                                seekerState.onSeek(-5)
                            },
                            onForward = {
                                seekerState.onSeek(5)
                            },
                        )
                    }
                    .ifThen(family.keyboardUpDownForVolume && audioLevelController != null) {
                        if (audioLevelController == null) return@ifThen this
                        onKeyEvent {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent false
                            val consumed = when {
                                it.isShiftPressed && it.key == ComposeKey.DirectionUp -> {
                                    audioLevelController.volumeUp(0.01f)
                                    true
                                }

                                it.isShiftPressed && it.key == ComposeKey.DirectionDown -> {
                                    audioLevelController.volumeDown(0.01f)
                                    true
                                }

                                it.key == ComposeKey.DirectionUp -> {
                                    audioLevelController.volumeUp()
                                    true
                                }

                                it.key == ComposeKey.DirectionDown -> {
                                    audioLevelController.volumeDown()
                                    true
                                }

                                else -> false
                            }
                            if (consumed) {
                                audioLevelController.setMute(false)
                                indicatorTasker.launch {
                                    indicatorState.showVolumeRange(audioLevelController.volume.value / audioLevelController.maxVolume)
                                }
                            }
                            consumed
                        }
                    }
                    .ifThen(family.keyboardSpaceForPauseResume) {
                        onKey(ComposeKey.Spacebar) {
                            onTogglePauseResumeState()
                        }
                    }
                    .ifThen(family.mouseHoverForController) {
                        val scope = rememberUiMonoTasker()
                        // 没有人请求 alwaysOn 时自动隐藏控制器
                        LaunchedEffect(true) {
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
                        // 这里不能用 hover, 因为在当控制器隐藏后, hover 状态仍然有, 于是下次移动鼠标时不会重复触发 hover 事件, 也就无法显示
                        // See test case: `mouse - mouseHoverForController - center screen twice`
                        onPointerEventMultiplatform(PointerEventType.Move) { _ ->
                            controllerState.toggleFullVisible(true)
                            keyboardFocus.requestFocus()
                            scope.launch {
                                delay(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION)
                                controllerState.toggleFullVisible(false)
                            }
                        }
                    }
                    .ifThen(family.keyboardControlFullscreen) {
                        onKey(ComposeKey.Escape) {
                            if (needWorkaroundForFocusManager) {
                                manager.clearFocus()
                            }
                            onExitFullscreen()
                        }.onKey(ComposeKey.F) {
                            if (needWorkaroundForFocusManager) {
                                manager.clearFocus()
                            }
                            onToggleFullscreen()
                        }
                    }
                    .ifThen(family.keyboardControlSpeed && playbackSpeedControllerState != null) {
                        if (playbackSpeedControllerState == null) return@ifThen this
                        onKey(ComposeKey.A) { playbackSpeedControllerState.speedDown() }
                            .onKey(ComposeKey.D) { playbackSpeedControllerState.speedUp() }
                            .onKey(ComposeKey.S) { playbackSpeedControllerState.reset() }
                    }
                    .ifThen(family.keyboardToggleDanmaku) {
                        onKey(ComposeKey.B, onToggleDanmaku)
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
                    .fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .ifThen(needWorkaroundForFocusManager) {
                            onFocusEvent {
                                if (it.hasFocus) {
                                    focusRequester.requestFocus()
                                }
                            }
                        }
                        .matchParentSize()
                        .combinedClickable(
                            remember { MutableInteractionSource() },
                            indication = null,
                            onClick = remember(family) {
                                {
                                    if (family.clickToPauseResume) {
                                        onTogglePauseResumeState()
                                    }
                                    if (family.clickToToggleController) {
                                        controllerState.toggleFullVisible()
                                    }
                                }
                            },
                            onDoubleClick = remember(family, onToggleFullscreen) {
                                {
                                    if (needWorkaroundForFocusManager) {
                                        manager.clearFocus()
                                    }
                                    if (family.doubleClickToFullscreen) {
                                        onToggleFullscreen()
                                    }
                                    if (family.doubleClickToPauseResume) {
                                        onTogglePauseResumeState()
                                    }
                                }
                            },
                        ),

                    )

                Row(Modifier.focusRequester(focusRequester).matchParentSize()) {
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

                    Box(Modifier.weight(1f).fillMaxHeight())

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

                SideEffect {
                    focusRequester.requestFocus()
                }
            }
        } else {

            val indicatorTasker = rememberUiMonoTasker()
            val focusManager by rememberUpdatedState(LocalFocusManager.current) // workaround for #288

            if (family.autoHideController) {
                LaunchedEffect(controllerState.visibility, controllerState.alwaysOn) {
                    if (controllerState.alwaysOn) return@LaunchedEffect
                    if (controllerState.visibility.bottomBar) {
                        delay(VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION)
                        controllerState.toggleFullVisible(false)
                    }
                }
            }

            @Composable
            fun Modifier.combineClickableWithFamilyGesture() = this then
                    combinedClickable(
                        remember { MutableInteractionSource() },
                        indication = null,
                        onClick = remember(family) {
                            {
                                if (family.clickToPauseResume) {
                                    onTogglePauseResumeState()
                                }
                                if (family.clickToToggleController) {
                                    focusManager.clearFocus()
                                    controllerState.toggleFullVisible()
                                }
                            }
                        },
                        onDoubleClick = remember(family, onToggleFullscreen) {
                            {
                                if (family.doubleClickToFullscreen) {
                                    onToggleFullscreen()
                                }
                                if (family.doubleClickToPauseResume) {
                                    onTogglePauseResumeState()
                                }
                            }
                        },
                    )
            Box(
                modifier
                    .testTag("VideoGestureHost")
                    .ifThen(needWorkaroundForFocusManager) {
                        onFocusEvent {
                            if (it.hasFocus) {
                                focusManager.clearFocus()
                            }
                        }
                    }
                    .combineClickableWithFamilyGesture()
                    .ifThen(family.swipeToSeek && enableSwipeToSeek) {
                        val swipeToSeekRequester = rememberAlwaysOnRequester(controllerState, "swipeToSeek")
                        swipeToSeek(
                            seekerState,
                            Orientation.Horizontal,
                            //调节音量/亮度时禁用水平seek
                            enabled = !adjustingVolumeOrBrightness,
                            onDragStarted = {
                                if (controllerState.visibility.bottomBar) {
                                    swipeToSeekRequester.request()
                                }
                                controllerState.setRequestProgressBar(swipeToSeekRequester)
                            },
                            onDragStopped = {
                                if (controllerState.visibility.bottomBar) {
                                    swipeToSeekRequester.cancelRequest()
                                }
                                controllerState.cancelRequestProgressBarVisible(swipeToSeekRequester)
                                progressSliderState.finishPreview()
                            },
                        ) {
                            progressSliderState.run {
                                if (totalDurationMillis == 0L) return@run
                                val offsetRatio =
                                    (currentPositionMillis + seekerState.deltaSeconds.times(1000)).toFloat() / totalDurationMillis
                                previewPositionRatio(offsetRatio.coerceIn(0f, 1f))
                            }
                        }
                    }
                    .ifThen(family.keyboardLeftRightToSeek) {
                        onKeyboardHorizontalDirection(
                            onBackward = {
                                seekerState.onSeek(-5)
                            },
                            onForward = {
                                seekerState.onSeek(5)
                            },
                        )
                    }
                    .ifThen(family.keyboardUpDownForVolume) {
                        audioController.let { controller ->
                            onKey(ComposeKey.DirectionUp) {
                                controller.increaseLevel(0.10f)
                            }
                            onKey(ComposeKey.DirectionDown) {
                                controller.decreaseLevel(0.10f)
                            }
                        }
                    }
                    .ifThen(family.keyboardSpaceForPauseResume) {
                        onKey(ComposeKey.Spacebar) {
                            onTogglePauseResumeState()
                        }
                    }
                    .fillMaxSize(),
            ) {
                Row(
                    Modifier.matchParentSize()
                        .systemGesturesPadding()
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

                    Box(Modifier.weight(1f).fillMaxHeight())

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

            // 状态栏区域响应点击手势
            Box(
                Modifier.fillMaxWidth()
                    .ifThen(isSystemInFullscreen()) {
                        windowInsetsTopHeight(WindowInsets.systemGestures)
                    }
                    .combineClickableWithFamilyGesture(),
            )
        }
    }
}
