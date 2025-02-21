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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.platform.features.AudioManager
import me.him188.ani.app.platform.features.BrightnessManager
import me.him188.ani.app.platform.features.StreamType
import me.him188.ani.app.tools.MonoTasker

interface LevelController {
    val level: Float

    @MainThread
    fun increaseLevel(step: Float = 0.05f)

    @MainThread
    fun decreaseLevel(step: Float = 0.05f)
}

object NoOpLevelController : LevelController {
    override val level: Float
        get() = 0f

    override fun increaseLevel(step: Float) {
    }

    override fun decreaseLevel(step: Float) {
    }
}

fun AudioManager.asLevelController(
    streamType: StreamType,
): LevelController = object : LevelController {
    override val level: Float
        get() = getVolume(streamType)

    override fun increaseLevel(step: Float) {
        val current = getVolume(streamType)
        setVolume(streamType, (current + step).coerceAtMost(1f))
    }

    override fun decreaseLevel(step: Float) {
        val current = getVolume(streamType)
        setVolume(streamType, (current - step).coerceAtLeast(0f))
    }
}

fun BrightnessManager.asLevelController(): LevelController = object : LevelController {
    override val level: Float
        get() = getBrightness()

    override fun increaseLevel(step: Float) {
        val current = getBrightness()
        setBrightness((current + step).coerceAtMost(1f))
    }

    override fun decreaseLevel(step: Float) {
        val current = getBrightness()
        setBrightness((current - step).coerceAtLeast(0f))
    }
}

fun Modifier.swipeLevelControlWithIndicator(
    controller: LevelController,
    stepSize: Dp,
    orientation: Orientation,
    indicatorState: GestureIndicatorState,
    indicatorTasker: MonoTasker,
    step: Float = 0.05f,
    setup: () -> Unit = {}
): Modifier = this then swipeLevelControl(
    controller = controller, stepSize = stepSize, orientation = orientation, step = step,
    afterStep = {
        indicatorTasker.launch {
            setup()
            indicatorState.progressValue = controller.level
            indicatorState.visible = true
        }
    },
    onDragStarted = {
        indicatorTasker.launch {
            indicatorTasker.cancel()
            indicatorState.visible = true
        }
    },
    onDragStopped = {
        indicatorTasker.launch {
            indicatorState.visible = false
        }
    },
)

fun Modifier.swipeLevelControl(
    controller: LevelController,
    stepSize: Dp,
    orientation: Orientation,
    step: Float = 0.05f,
    afterStep: (StepDirection) -> Unit = {},
    onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit = {},
    onDragStopped: suspend CoroutineScope.(velocity: Float) -> Unit = {},
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "swipeLevelControl"
        properties["controller"] = controller
        properties["stepSize"] = stepSize
        properties["orientation"] = orientation
    },
) {
    steppedDraggable(
        rememberSteppedDraggableState(
            stepSize = stepSize,
            onStep = { direction ->
                when (direction) {
                    StepDirection.FORWARD -> controller.increaseLevel(step)
                    StepDirection.BACKWARD -> controller.decreaseLevel(step)
                }
                afterStep(direction)
            },
        ),
        orientation = orientation,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
    )

}