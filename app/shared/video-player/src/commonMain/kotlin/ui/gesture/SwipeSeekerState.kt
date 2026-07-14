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
import androidx.compose.ui.unit.IntSize
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
    return remember(swipeSeekerConfig, screenWidthPx) {
        SwipeSeekerState(
            screenWidthPx,
            swipeSeekerConfig,
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
     * 屏幕顶部作为取消区域的高度占比.
     */
    val cancelAreaHeightRatio: Float = 0.25f,
    /**
     * 屏幕左右两侧各自作为取消区域的宽度占比.
     */
    val cancelAreaWidthRatio: Float = 0.25f,
) {
    companion object {
        val Default = SwipeSeekerConfig()
    }
}

fun SwipeSeekerConfig.isInCancelArea(position: Offset, containerSize: IntSize): Boolean {
    if (!position.isSpecified || containerSize.width <= 0 || containerSize.height <= 0) return false

    val inTopArea = position.y <= containerSize.height * cancelAreaHeightRatio
    val inLeftArea = position.x <= containerSize.width * cancelAreaWidthRatio
    val inRightArea = position.x >= containerSize.width * (1f - cancelAreaWidthRatio)
    return inTopArea && (inLeftArea || inRightArea)
}

// draggable 继续负责识别单轴 seek；这里只观察二维位置，并在进入或离开取消区域时通知上层。
private fun Modifier.trackSwipeSeekCancellation(
    seekerState: SwipeSeekerState,
    onCancellationChanged: (Boolean) -> Unit,
): Modifier = onPointerEventMultiplatform(
    PointerEventType.Move,
    pass = PointerEventPass.Initial,
) { event ->
    val change = event.changes.firstOrNull() ?: return@onPointerEventMultiplatform
    if (seekerState.updateCancellation(change.position, size)) {
        onCancellationChanged(seekerState.isCancelled)
    }
}

@Stable
class SwipeSeekerState(
    /**
     * 可滑动区域宽度
     */
    private val screenWidthPx: Int,
    private val swipeSeekerConfig: SwipeSeekerConfig = SwipeSeekerConfig.Default,
    /**
     * 当一次滑动结束时的回调. `offsetSeconds` 为本次快进的秒数
     */
    @UiThread val onSeek: (offsetSeconds: Int) -> Unit,
) {
    /**
     * [Float.NaN] iff not dragging
     */
    private var seekDelta: Float by mutableFloatStateOf(Float.NaN)

    /**
     * 当前滑动是否已进入取消区域.
     */
    var isCancelled: Boolean by mutableStateOf(false)
        private set

    private var lastPointerPosition: Offset = Offset.Unspecified
    private var lastPointerContainerSize: IntSize = IntSize.Zero

    @UiThread
    internal fun onSwipeStarted() {
        seekDelta = 0f
        isCancelled = isInCancelArea(lastPointerPosition, lastPointerContainerSize)
    }

    @UiThread
    internal fun onSwipeStopped() {
        if (seekDelta.isNaN()) return
        if (!isCancelled) {
            onSeek(deltaSeconds)
        }
        seekDelta = Float.NaN
        isCancelled = false
    }

    @UiThread
    internal fun onSwipeOffset(offsetPx: Float) {
        seekDelta += offsetPx
    }

    @UiThread
    internal fun updateCancellation(position: Offset, containerSize: IntSize): Boolean {
        val wasCancelled = isCancelled
        lastPointerPosition = position
        lastPointerContainerSize = containerSize
        if (isSeeking) {
            isCancelled = isInCancelArea(position, containerSize)
        }
        return isCancelled != wasCancelled
    }

    private fun isInCancelArea(position: Offset, containerSize: IntSize): Boolean =
        swipeSeekerConfig.isInCancelArea(position, containerSize)

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
                draggable(
                    rememberDraggableState {
                        seekerState.onSwipeOffset(it)
                        onDelta(it)
                    },
                    orientation,
                    onDragStarted = {
                        seekerState.onSwipeStarted()
                        onDragStarted(it)
                    },
                    onDragStopped = {
                        val cancelled = seekerState.isCancelled
                        seekerState.onSwipeStopped()
                        onDragStopped(it, cancelled)
                    },
                    enabled = enabled,
                    interactionSource = interactionSource,
                    reverseDirection = reverseDirection,
                ).trackSwipeSeekCancellation(seekerState, onCancellationChanged)
            }
        }
    }
}