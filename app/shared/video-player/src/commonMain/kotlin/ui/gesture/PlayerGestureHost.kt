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
import me.him188.ani.app.ui.foundation.ifNotNullThen
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.input.LocalActiveInputSource
import me.him188.ani.app.ui.foundation.input.asGesturePointerType
import me.him188.ani.app.ui.foundation.input.trackActiveInputSource
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_release_to_cancel
import me.him188.ani.app.utils.fixToString
import me.him188.ani.app.utils.formatSpeedValue
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.BRIGHTNESS
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.FAST_BACKWARD
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.FAST_FORWARD
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.PAUSED_ONCE
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.PLAYBACK_SPEED
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.RESUMED_ONCE
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.SEEKING
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState.State.VOLUME
import me.him188.ani.app.videoplayer.ui.gesture.SwipeSeekerState.Companion.swipeToSeek
import me.him188.ani.app.videoplayer.ui.playerFocusHost
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.isDesktop
import org.jetbrains.compose.resources.stringResource
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
        PLAYBACK_SPEED,
    }

    internal var visible: Boolean by mutableStateOf(false)
    internal var state: State? by mutableStateOf(null)
    internal var progressValue: Float by mutableFloatStateOf(0f)
    internal var deltaSeconds: Int by mutableIntStateOf(0)
    internal var seekCancelled: Boolean by mutableStateOf(false)
    internal var playbackSpeed: Float by mutableFloatStateOf(1f)
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
    suspend fun showPlaybackSpeed(speed: Float) {
        show(PLAYBACK_SPEED, setup = { playbackSpeed = speed }) {
            delay(SHORT)
        }
    }

    @UiThread
    suspend fun showSeeking(
        deltaSeconds: Int,
    ) {
        show(
            SEEKING,
            setup = {
                this.deltaSeconds = deltaSeconds
                seekCancelled = false
            },
        ) {
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

    /**
     * @param speed 长按期间使用的播放速度, 会显示在指示器上.
     */
    @UiThread
    fun startFastForward(speed: Float): Int {
        startShow(FAST_FORWARD, setup = { playbackSpeed = speed })
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

@Immutable
internal data class GestureIndicatorPresentation(
    val state: GestureIndicatorState.State,
    val deltaSeconds: Int,
    val seekCancelled: Boolean,
)

internal fun gestureIndicatorPresentation(
    state: GestureIndicatorState,
    activeSwipeSeekerState: SwipeSeekerState?,
): GestureIndicatorPresentation? {
    if (!state.visible && activeSwipeSeekerState == null) return null
    val presentationState = if (activeSwipeSeekerState != null) SEEKING
    else state.state ?: return null
    return GestureIndicatorPresentation(
        state = presentationState,
        deltaSeconds = activeSwipeSeekerState?.deltaSeconds ?: state.deltaSeconds,
        seekCancelled = activeSwipeSeekerState?.isCancelled ?: state.seekCancelled,
    )
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
    val presentation = gestureIndicatorPresentation(state, activeSwipeSeekerState)
    // 淡出期间 presentation 为 null。滑动 seek 的指示器只由 swipeSeekerState 驱动,
    // GestureIndicatorState.state 全程为 null；不保留最后一帧的话，松手后会淡出一个空 Surface。
    // 在组合结束后才写入, 避免组合被丢弃时留下脏值; 淡出期间读到的是上一帧提交的快照。
    val retainedPresentation = remember { mutableStateOf<GestureIndicatorPresentation?>(null) }
    if (presentation != null) {
        SideEffect { retainedPresentation.value = presentation }
    }
    // presentation 非 null 时不读 retainedPresentation, 因此快进过程中不会因保留帧写入而多一次重组。
    val currentPresentation = presentation ?: retainedPresentation.value

    AniAnimatedVisibility(
        visible = presentation != null,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(tween(durationMillis = 500)),
        label = "SeekPositionIndicator",
    ) {
        currentPresentation ?: return@AniAnimatedVisibility
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

                    when (currentPresentation.state) {
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
                            Icon(
                                when {
                                    currentPresentation.seekCancelled -> Icons.Rounded.Close
                                    currentPresentation.deltaSeconds > 0 -> Icons.Rounded.FastForward
                                    else -> Icons.Rounded.FastRewind
                                },
                                contentDescription = null,
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                text = if (currentPresentation.seekCancelled) {
                                    stringResource(Lang.video_player_release_to_cancel)
                                } else {
                                    renderTime(currentPresentation.deltaSeconds.absoluteValue)
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
                            Text("${state.playbackSpeed.formatSpeedValue()}x", maxLines = 1)
                        }

                        FAST_BACKWARD -> {
                            Icon(Icons.Rounded.FastRewind, null, Modifier.size(iconSize))
                        }

                        PLAYBACK_SPEED -> {
                            Icon(Icons.Rounded.FastForward, null, Modifier.size(iconSize))
                            Text("${state.playbackSpeed.formatSpeedValue()}x", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** 平台默认的手势约定, 仅作为 [gestureFamilyOf] 的回退值. */
@Stable
val Platform.mouseFamily: GestureFamily
    get() = when (this) {
        is Platform.Desktop -> GestureFamily.MOUSE
        is Platform.Android, is Platform.Ios -> GestureFamily.TOUCH
    }

/**
 * 由最近一次使用的指针设备决定手势约定, 尚未收到指针事件时回退到 [fallback].
 *
 * 纯触摸设备只会产生触摸事件、纯键鼠设备只会产生鼠标事件, 两者行为与改动前一致;
 * 只有混合设备才会在两套约定之间切换.
 */
@Stable
fun gestureFamilyOf(activeInputSource: PointerType, fallback: GestureFamily): GestureFamily =
    when (activeInputSource.asGesturePointerType()) {
        PointerType.Touch -> GestureFamily.TOUCH
        PointerType.Mouse -> GestureFamily.MOUSE
        else -> fallback
    }

/**
 * 这台设备有没有鼠标. 常驻 UI 应该用它而不是 [GestureFamily] —— 后者跟着当前输入方式走,
 * 会让控件在用户切换手指/鼠标时反复显隐.
 */
@Stable
fun hasPointerDevice(platform: Platform, hasSeenMouse: Boolean): Boolean =
    hasSeenMouse || platform.isDesktop()

/**
 * 一套点击/双击约定. 滑动类手势的挂载也由它决定 (见 [PlayerGestureHost] 中的 swipeGesturesEnabled):
 * 拖动必须靠 enabled 门控, 不能在事件里按指针类型消费位移, 否则会取消同一区域的点击判定.
 *
 * 长按不走 family, 而是按本次手势 down 事件的 [PointerType] 过滤 (见 [longPressFastSkip]) ——
 * 它不消费任何事件, 因此不会干扰点击, 也就不需要等 family 提交.
 */
@Immutable
enum class GestureFamily(
    val clickToPauseResume: Boolean,
    val clickToToggleController: Boolean,
    val doubleClickToFullscreen: Boolean,
    val doubleClickToPauseResume: Boolean,
    val autoHideController: Boolean,
) {
    TOUCH(
        clickToPauseResume = false,
        clickToToggleController = true,
        doubleClickToFullscreen = false,
        doubleClickToPauseResume = true,
        autoHideController = true,
    ),
    MOUSE(
        clickToPauseResume = true,
        clickToToggleController = false,
        doubleClickToFullscreen = true,
        doubleClickToPauseResume = false,
        autoHideController = false,
    )
}

val VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION = 3.seconds
val VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION = 3.seconds

/**
 * 将屏幕横滑 seek 的状态迁移映射到控制器显隐和进度预览。
 * [SwipeSeekerState] 负责识别手势，本类只响应开始、取消状态变化和结束事件。
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
    isUnderlyingPlayerFullscreen: Boolean,
    modifier: Modifier = Modifier,
    family: GestureFamily = gestureFamilyOf(
        LocalActiveInputSource.current.current,
        LocalPlatform.current.mouseFamily,
    ),
    onTogglePauseResume: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onToggleDanmaku: () -> Unit = {},
    onTogglePlayerStats: () -> Unit = {},
) {
    val onTogglePauseResumeState by rememberUpdatedState(onTogglePauseResume)

    val inputSourceState = LocalActiveInputSource.current

    // 滑动类手势只属于触摸约定, 用组合期的 [family] 门控挂载, 而不是在事件里按指针类型消费位移:
    // 消费会让 combinedClickable 的点击判定被取消, 鼠标按下后漂移一个像素就点不动播放器.
    //
    // 鼠标按下前必然先 hover, family 那时已经切到 MOUSE, 门控对鼠标一定及时. 触摸没有 hover,
    // 类型要到 down 才知道, 而 Compose 在 down 时就为该 pointer 固定了命中路径, 之后挂载的
    // draggable 收不到本次手势的后续事件 —— 从鼠标切到手指的第一次滑动只能切换 family (且会被
    // 判成一次点击), 抬手再滑才进入 seek. 这是门控换来的代价.
    val swipeGesturesEnabled = family == GestureFamily.TOUCH
    BoxWithConstraints(Modifier.trackActiveInputSource(inputSourceState)) {
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
        // 平台问题而非输入方式问题: 桌面没有系统级 AudioManager, 音量由 mediamp 提供.
        // 用 GestureFamily 判断会让混合设备上键盘音量键的作用目标随输入方式跳变.
        val useMediaAudioController = LocalPlatform.current.isDesktop()
        val playerFocusState = controllerState.focusState

        val keyboardModifier = modifier
            .testTag("VideoGestureHost")
            .playerKeyboardShortcuts(
                seekerState = seekerState,
                fastSkipState = fastSkipState,
                currentPlaybackSpeed = playbackSpeedControllerState?.currentSpeed,
                playbackSpeedRange = playbackSpeedControllerState?.speedRange
                    ?: PlaybackSpeedControllerState.DEFAULT_SPEED_RANGE,
                onPlaybackSpeedChanged = {
                    playbackSpeedControllerState?.commitSpeed(it)
                    indicatorTasker.launch { indicatorState.showPlaybackSpeed(it) }
                },
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
                onToggleDanmaku = onToggleDanmaku,
                onTogglePlayerStats = onTogglePlayerStats,
            )
            .playerFocusHost(playerFocusState, isUnderlyingPlayerFullscreen)

        if (family.autoHideController) {
            LaunchedEffect(controllerState.visibility, controllerState.alwaysOn) {
                if (controllerState.alwaysOn) return@LaunchedEffect
                if (controllerState.visibility.bottomBar) {
                    delay(VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION)
                    controllerState.toggleFullVisible(false)
                }
            }
        }

        // 与上面的 Move handler 配套. 同样用「这台设备有没有鼠标」而不是「此刻在用鼠标」:
        // 后者会让隐藏时序随输入方式跳变, 手指点一下就把鼠标模式下的自动隐藏关掉.
        if (hasPointerDevice(LocalPlatform.current, inputSourceState.hasSeenMouse)) {
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
                    onClick = remember(family, playerFocusState, inputSourceState) {
                        {
                            // 按本次事件的指针类型解析, 组合期算好的 family 会慢一拍
                            val tapFamily = gestureFamilyOf(inputSourceState.latest, family)
                            if (tapFamily.clickToPauseResume) {
                                onTogglePauseResumeState()
                            }
                            if (tapFamily.clickToToggleController) {
                                controllerState.toggleFullVisible()
                            }
                            playerFocusState.requestPlayerFocus()
                        }
                    },
                    onDoubleClick = remember(family, onToggleFullscreen, playerFocusState, inputSourceState) {
                        {
                            val tapFamily = gestureFamilyOf(inputSourceState.latest, family)
                            if (tapFamily.doubleClickToFullscreen) {
                                onToggleFullscreen()
                            }
                            if (tapFamily.doubleClickToPauseResume) {
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
                .ifThen(enableSwipeToSeek) {
                    val swipeSeekInteraction = rememberSwipeSeekInteraction(
                        controllerState,
                        seekerState,
                        progressSliderState,
                    )
                    swipeToSeek(
                        seekerState,
                        Orientation.Horizontal,
                        //调节音量/亮度时禁用水平seek
                        enabled = swipeGesturesEnabled && !adjustingVolumeOrBrightness,
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
                // 始终挂载, 再按本次事件过滤. 否则触摸后的第一次 Mouse Move 只会切换 family,
                // 要等第二次 Move 才能显示控制器.
                .onPointerEventMultiplatform(PointerEventType.Move) { event ->
                    if (event.changes.firstOrNull()?.type == PointerType.Mouse) {
                        controllerState.toggleFullVisible(true)
                        mouseMoveTasker.launch {
                            delay(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION)
                            controllerState.toggleFullVisible(false)
                        }
                    }
                }
                // 滚轮只可能来自鼠标, 无需 family 门控; 接了鼠标的 Android 平板同样受益
                .ifThen(audioLevelController != null) {
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
                // 桌面上是零 inset, 无条件挂载可避免混合设备上随 family 增删 padding 造成布局跳变
                Modifier.matchParentSize()
                    .systemGesturesPadding()
                    // 由 down 事件的类型过滤而非 family 门控: 按下不提交 family, 门控会漏掉每次长按
                    .ifNotNullThen(fastSkipState) {
                        longPressFastSkip(it, SkipDirection.FORWARD, requiredPointerType = PointerType.Touch)
                    },
            ) {
                Box(
                    Modifier
                        // 挂载看能力 (桌面没有 BrightnessManager, 传进来是 NoOp), 是否响应看 enabled;
                        // 分开之后切换输入方式后的第一次滑动不会因为修饰符尚未挂上而丢失
                        .ifThen(brightnessController !== NoOpLevelController) {
                            swipeLevelControlWithIndicator(
                                brightnessController,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                Orientation.Vertical,
                                indicatorState,
                                enabled = swipeGesturesEnabled && !seekerState.isSeeking && !adjustingForwardOrBackward,
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
                        .swipeToFullscreen(
                            enabled = swipeGesturesEnabled && !seekerState.isSeeking && !adjustingVolumeOrBrightness &&
                                    !adjustingForwardOrBackward,
                            onEnterFullscreen = {
                                if (!isUnderlyingPlayerFullscreen) onToggleFullscreen()
                            },
                            onExitFullscreen = {
                                if (isUnderlyingPlayerFullscreen) onToggleFullscreen()
                            },
                        )
                        .weight(1f)
                        .fillMaxHeight(),
                )

                Box(
                    Modifier
                        .ifThen(audioController !== NoOpLevelController) {
                            swipeLevelControlWithIndicator(
                                audioController,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                Orientation.Vertical,
                                indicatorState,
                                enabled = swipeGesturesEnabled && !seekerState.isSeeking && !adjustingForwardOrBackward,
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

        if (family.clickToToggleController && isUnderlyingPlayerFullscreen) {
            // 状态栏区域响应点击手势
            Box(
                Modifier.fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.systemGestures)
                    .combineClickableWithFamilyGesture(),
            )
        }
    }
}
