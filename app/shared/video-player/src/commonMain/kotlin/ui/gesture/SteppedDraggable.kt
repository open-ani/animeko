/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.annotation.MainThread
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope


interface SteppedDraggableState : DraggableState {
    fun onDragStarted(offset: Offset, orientation: Orientation)
    fun onDragStopped(velocity: Float)
}

enum class StepDirection {
    /**
     * - [Orientation.Horizontal]: To the right
     * - [Orientation.Vertical]: Down
     */
    FORWARD,
    BACKWARD,
}

private class SteppedDraggableStateImpl(
    @MainThread private val onStep: (StepDirection) -> Unit,
    private val stepSizePx: Float,
) : SteppedDraggableState {
    var startOffset: Float by mutableFloatStateOf(Float.NaN)
    var currentOffset: Float by mutableFloatStateOf(0f)
    var lastCallbackOffset: Float by mutableFloatStateOf(0f)
    override fun onDragStarted(offset: Offset, orientation: Orientation) {
        startOffset = if (orientation == Orientation.Horizontal) {
            offset.x
        } else {
            offset.y
        }
        currentOffset = startOffset
    }

    override fun onDragStopped(velocity: Float) {
        startOffset = Float.NaN
        currentOffset = 0f
    }

    override fun dispatchRawDelta(delta: Float) {
        draggableState.dispatchRawDelta(delta)
    }

    override suspend fun drag(dragPriority: MutatePriority, block: suspend DragScope.() -> Unit) {
        draggableState.drag(dragPriority, block)
    }

    private val draggableState: DraggableState = DraggableState { delta ->
        currentOffset += delta
        val deltaOffset = currentOffset - startOffset
        val step = (deltaOffset / stepSizePx).toInt()
        val callbackOffset = step * stepSizePx
        if (callbackOffset != lastCallbackOffset) {
            if (callbackOffset > lastCallbackOffset) {
                onStep(StepDirection.BACKWARD) // delta is inverted
            } else {
                onStep(StepDirection.FORWARD)
            }
            lastCallbackOffset = callbackOffset
        }
    }
}

@Composable
fun rememberSteppedDraggableState(
    stepSize: Dp,
    @MainThread onStep: (StepDirection) -> Unit,
): SteppedDraggableState {
    val onStepState by rememberUpdatedState(onStep)
    val stepSizePx by rememberUpdatedState(with(LocalDensity.current) { stepSize.toPx() })
    return remember {
        SteppedDraggableStateImpl(
            onStep = { onStepState(it) },
            stepSizePx = stepSizePx,
        )
    }
}

fun Modifier.steppedDraggable(
    state: SteppedDraggableState,
    orientation: Orientation,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    startDragImmediately: Boolean = false,
    onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit = {},
    onDragStopped: suspend CoroutineScope.(velocity: Float) -> Unit = {},
    reverseDirection: Boolean = false,
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "steppedDraggable"
        properties["state"] = state
        properties["orientation"] = orientation
        properties["enabled"] = enabled
        properties["interactionSource"] = interactionSource
        properties["startDragImmediately"] = startDragImmediately
        properties["onDragStarted"] = onDragStarted
        properties["onDragStopped"] = onDragStopped
        properties["reverseDirection"] = reverseDirection
    },
) {
    val onDragStartedState by rememberUpdatedState(onDragStarted)
    val onDragStoppedState by rememberUpdatedState(onDragStopped)
    draggable(
        state = state,
        orientation = orientation,
        enabled = enabled,
        interactionSource = interactionSource,
        startDragImmediately = startDragImmediately,
        onDragStarted = { offset ->
            state.onDragStarted(offset, orientation)
            onDragStartedState(offset)
        },
        onDragStopped = {
            state.onDragStopped(it)
            onDragStoppedState(it)
        },
        reverseDirection = reverseDirection,
    )
}


//fun Modifier.combinedSteppedDraggable(
//    division: List<Pair<Float, SteppedDraggableState>>,
//    orientation: Orientation,
//    enabled: Boolean = true,
//    interactionSource: MutableInteractionSource? = null,
//    startDragImmediately: Boolean = false,
//    onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit = {},
//    onDragStopped: suspend CoroutineScope.(velocity: Float) -> Unit = {},
//    reverseDirection: Boolean = false,
//): Modifier = composed(
//    inspectorInfo = debugInspectorInfo {
//        name = "steppedDraggable"
//        properties["division"] = division
//        properties["orientation"] = orientation
//        properties["enabled"] = enabled
//        properties["interactionSource"] = interactionSource
//        properties["startDragImmediately"] = startDragImmediately
//        properties["onDragStarted"] = onDragStarted
//        properties["onDragStopped"] = onDragStopped
//        properties["reverseDirection"] = reverseDirection
//    }
//) {
//    val onDragStartedState by rememberUpdatedState(onDragStarted)
//    val onDragStoppedState by rememberUpdatedState(onDragStopped)
//    draggable(
//        state = state.draggableState,
//        orientation = orientation,
//        enabled = enabled,
//        interactionSource = interactionSource,
//        startDragImmediately = startDragImmediately,
//        onDragStarted = { offset ->
//            state.onDragStarted(offset, orientation)
//            onDragStartedState(offset)
//        },
//        onDragStopped = {
//            state.onDragStopped(it)
//            onDragStoppedState(it)
//        },
//        reverseDirection = reverseDirection,
//    )
//}
