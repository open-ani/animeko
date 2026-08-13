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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.ui.foundation.effects.onPointerEventMultiplatform
import kotlin.math.roundToInt


@Composable
fun rememberSwipeSeekerState(
    screenWidthPx: Int,
    swipeSeekerConfig: SwipeSeekerConfig = SwipeSeekerConfig.Default,
    @UiThread onSeek: (offsetSeconds: Int) -> Unit,
): SwipeSeekerState {
    val onSeekState by rememberUpdatedState(onSeek)
    val density = LocalDensity.current
    return remember(swipeSeekerConfig, screenWidthPx, density) {
        SwipeSeekerState(
            screenWidthPx,
            swipeSeekerConfig,
            density,
        ) { onSeekState(it) }
    }
}

@Immutable
data class SwipeSeekerConfig(
    /**
     * 从屏幕左边滑到屏幕的最右边的最大距离
     */
    val maxDragDelta: Float = 0f,
    /**
     * 从屏幕左边滑到屏幕的最右边会跳转的秒数
     */
    // 设计上是从左到右 90 秒正好跳过 op/ed, 而全面屏手机有全面屏手势, 
    // 用户不能从最左边开始滑. 因此稍微留了点余量.
    // 实测差不多可以滑到 87 秒, 看三秒 op 让他知道他完了 op
    val maxDragSeconds: Int = 97,
    /**
     * 向上滑动多少距离后取消本次快进.
     *
     * 快进过程中手指向上移动超过该距离即取消, 滑回该距离以内恢复.
     */
    val cancelVerticalDragDistance: Dp = 144.dp,
) {
    companion object {
        val Default = SwipeSeekerConfig()
    }
}

internal fun isVerticalDragCancelled(
    dragStartY: Float,
    position: Offset,
    cancelVerticalDragDistancePx: Float,
): Boolean {
    return position.isSpecified &&
        dragStartY - position.y > cancelVerticalDragDistancePx
}

private fun Modifier.trackSwipeSeekCancellation(
    seekerState: SwipeSeekerState,
    onCancellationChanged: (Boolean) -> Unit,
): Modifier = this
    .onPointerEventMultiplatform(
        PointerEventType.Press,
        pass = PointerEventPass.Initial,
    ) { event ->
        event.changes.firstOrNull()?.let { seekerState.onPointerDown(it.position) }
    }
    .onPointerEventMultiplatform(
        PointerEventType.Move,
        pass = PointerEventPass.Initial,
    ) { event ->
        val change = event.changes.firstOrNull() ?: return@onPointerEventMultiplatform
        if (seekerState.updateCancellation(change.position)) {
            onCancellationChanged(seekerState.isCancelled)
        }
    }

@Stable
class SwipeSeekerState internal constructor(
    /**
     * 可滑动区域宽度
     */
    private val screenWidthPx: Int,
    private val swipeSeekerConfig: SwipeSeekerConfig,
    density: Density,
    /**
     * 当一次滑动结束时的回调. `offsetSeconds` 为本次快进的秒数
     */
    @UiThread val onSeek: (offsetSeconds: Int) -> Unit,
) {
    private val cancelVerticalDragDistancePx =
        with(density) { swipeSeekerConfig.cancelVerticalDragDistance.toPx() }

    /**
     * [Float.NaN] iff not dragging
     */
    private var seekDelta: Float by mutableFloatStateOf(Float.NaN)

    /**
     * 当前滑动是否已取消, 即手指是否已向上移动超过取消距离.
     */
    var isCancelled: Boolean by mutableStateOf(false)
        private set

    /**
     * 手指按下位置的 Y 坐标, 作为取消判定的基准线. [Float.NaN] 表示未在滑动.
     *
     * 基准取按下点而不是拖动手势识别点: 滑动大概率不是直的, 手势识别 (越过 touch slop)
     * 时手指可能已经有垂直偏移, 以识别点为基准会把这部分偏移吃掉.
     */
    private var dragStartY: Float = Float.NaN

    @UiThread
    internal fun onPointerDown(position: Offset) {
        if (!isSeeking && position.isSpecified) {
            dragStartY = position.y
        }
    }

    @UiThread
    internal fun onSwipeStarted() {
        seekDelta = 0f
        isCancelled = false
    }

    @UiThread
    internal fun onSwipeStopped() {
        if (seekDelta.isNaN()) return
        if (!isCancelled) {
            onSeek(deltaSeconds)
        }
        seekDelta = Float.NaN
        isCancelled = false
        dragStartY = Float.NaN
    }

    @UiThread
    internal fun onSwipeOffset(offsetPx: Float) {
        seekDelta += offsetPx
    }

    @UiThread
    internal fun updateCancellation(position: Offset): Boolean {
        val wasCancelled = isCancelled
        if (isSeeking) {
            isCancelled = isVerticalDragCancelled(dragStartY, position, cancelVerticalDragDistancePx)
        }
        return isCancelled != wasCancelled
    }

    /**
     * 是否正在快进, 即用户是否正在滑动屏幕
     */
    val isSeeking: Boolean by derivedStateOf {
        !seekDelta.isNaN()
    }

    /**
     * 当前正在快进的秒数.
     *
     * 当用户手指在屏幕上滑动时, [deltaSeconds] 将更新, 反映假如用户此时松开手指, 将会跳转的秒数.
     * - 若用户从屏幕左边滑到屏幕的右边, [deltaSeconds] 将会是 [SwipeSeekerConfig.maxDragSeconds].
     *
     * 当未在滑动时, [deltaSeconds] 为 `0`.
     *
     * 负数表示快退, 正数表示快进
     */
    val deltaSeconds: Int by derivedStateOf {
        if (seekDelta.isNaN()) {
            0
        } else {
            val percentage = seekDelta / screenWidthPx
            (percentage * swipeSeekerConfig.maxDragSeconds).roundToInt()
        }
    }


    companion object {
        fun Modifier.swipeToSeek(
            seekerState: SwipeSeekerState,
            orientation: Orientation,
            enabled: Boolean = true,
            interactionSource: MutableInteractionSource? = null,
            reverseDirection: Boolean = false,
            onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit = {},
            onDragStopped: suspend CoroutineScope.(velocity: Float, cancelled: Boolean) -> Unit = { _, _ -> },
            onCancellationChanged: (cancelled: Boolean) -> Unit = {},
            onDelta: (Float) -> Unit = {},
        ): Modifier {
            return composed(
                inspectorInfo = {
                    name = "videoSeeker"
                    properties["seekerState"] = seekerState
                },
            ) {
                val currentOnDelta by rememberUpdatedState(onDelta)
                val currentOnDragStarted by rememberUpdatedState(onDragStarted)
                val currentOnDragStopped by rememberUpdatedState(onDragStopped)

                // 传给 draggable 的这两个回调必须在重组之间保持同一实例. 每次重组新建 lambda 会让
                // DraggableElement 不相等, 节点被 update 并重置正在识别的手势 —— 播放页在输入设备
                // 切换时整体重组 (gestureFamily 默认值读 LocalActiveInputSource), 正在进行的滑动
                // seek 会断在半路.
                val handleDragStarted: suspend CoroutineScope.(Offset) -> Unit = remember(seekerState) {
                    {
                        seekerState.onSwipeStarted()
                        currentOnDragStarted(it)
                    }
                }
                val handleDragStopped: suspend CoroutineScope.(Float) -> Unit = remember(seekerState) {
                    {
                        val cancelled = seekerState.isCancelled
                        seekerState.onSwipeStopped()
                        currentOnDragStopped(it, cancelled)
                    }
                }
                draggable(
                    rememberDraggableState {
                        seekerState.onSwipeOffset(it)
                        currentOnDelta(it)
                    },
                    orientation,
                    onDragStarted = handleDragStarted,
                    onDragStopped = handleDragStopped,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    reverseDirection = reverseDirection,
                ).trackSwipeSeekCancellation(seekerState, onCancellationChanged)
            }
        }
    }
}