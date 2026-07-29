/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState

@Composable
fun rememberPlayerFastSkipState(
    playbackSpeedControllerState: PlaybackSpeedControllerState,
    gestureIndicatorState: GestureIndicatorState,
    fastForwardSpeed: Float = 3f,
): FastSkipState {
    return remember(playbackSpeedControllerState, fastForwardSpeed) {
        PlayerFastSkipState(playbackSpeedControllerState, gestureIndicatorState, fastForwardSpeed).fastSkipState
    }
}

/**
 * 长按临时快进: 按住期间以 [fastForwardSpeed] 播放, 松手回到用户的基础倍速.
 *
 * 这里刻意不快照「按下时播放器上的倍速」. 快进倍速是 `PlaybackSpeedController` 意义上的
 * **临时倍速**, 松手只是把它丢弃, 基础倍速自始至终由 owner 持有.
 * 快照的做法在 onStart/onStop 不配对时会把快进倍速 (或初值 `0f`) 当成基础倍速存下来.
 */
class PlayerFastSkipState(
    private val playbackSpeedControllerState: PlaybackSpeedControllerState,
    private val gestureIndicatorState: GestureIndicatorState,
    private val fastForwardSpeed: Float = 3f,
) {
    private var gestureIndicatorTicket = 0
    val fastSkipState: FastSkipState = FastSkipState(
        onStart = { skipDirection ->
            playbackSpeedControllerState.beginTemporarySpeed(
                when (skipDirection) {
                    SkipDirection.FORWARD -> fastForwardSpeed
                    SkipDirection.BACKWARD -> error("Backward skipping is not supported")
                },
            )
            gestureIndicatorTicket = gestureIndicatorState.startFastForward(fastForwardSpeed)
        },
        onStop = {
            playbackSpeedControllerState.endTemporarySpeed()
            gestureIndicatorState.stopFastForward(gestureIndicatorTicket)
        },
    )
}

@Stable
class FastSkipState(
    private val onStart: (skipDirection: SkipDirection) -> Unit,
    private val onStop: () -> Unit,
) {
    private var skippingDirection: SkipDirection? by mutableStateOf(null)
    private var ticket: Int = 0

    fun startSkipping(direction: SkipDirection): Int {
        skippingDirection = direction
        onStart(direction)
        return ++ticket
    }

    fun stopSkipping(ticket: Int) {
        if (ticket == this.ticket) {
            skippingDirection = null
            onStop()
        }
    }
}

enum class SkipDirection {
    FORWARD, BACKWARD
}

fun Modifier.longPressFastSkip(
    state: FastSkipState,
    direction: SkipDirection,
): Modifier {
    var ticket = 0
    return detectLongPressGesture(
        onStart = {
            ticket = state.startSkipping(direction)
        },
        onEnd = {
            state.stopSkipping(ticket)
        },
    )
}
//    pointerInput(Unit) {
//    detectLongPressGesture()
////    detectTapGestures(
////        onPress = {
////            val ticket = state.startSkipping(direction)
////            awaitPointerEventScope {
////                var event = awaitPointerEvent()
////                while (event.changes.any { it.pressed }) {
////                    event = awaitPointerEvent()
////                }
////
////                state.stopSkipping(ticket)
////            }
////        }
////    )
//}

fun Modifier.detectLongPressGesture(
    onStart: () -> Unit,
    onEnd: () -> Unit,
    longPressTimeout: Long = 500L
): Modifier = pointerInput(Unit) {
    coroutineScope {
        val touchSlop = viewConfiguration.touchSlop
        var isLongPressDetected = false

        awaitEachGesture {
            val initialPosition = awaitFirstDown(requireUnconsumed = false).position
            // note: we don't consume the down event

            // Starts a job to mark long press detected if the user does not move the pointer, 
            // i.e. is holding at the same position for a certain time).
            val longPressJob = launch {
                delay(longPressTimeout)
                onStart()
                isLongPressDetected = true
            }

            var change = awaitPointerEvent()
            while (change.changes.any { it.pressed }) { // Pointer is still down
                val pointer = change.changes[0]
                if (isLongPressDetected) {
                    // Consume all events so that we won't trigger other gestures like swiping
                    change.changes.forEach { it.consume() }
                }
                if ((pointer.position - initialPosition).getDistance() > touchSlop) {
                    // User is swiping.
                    // Note, this can also happen if the long press has already been detected.
                    longPressJob.cancel() // Stop detecting long press if it hasn't been detected yet
                }
                change = awaitPointerEvent()
            }
            // Not pressing anymore
            if (isLongPressDetected) {
                // Consume the pointer up event
                change.changes.forEach { it.consume() }
            }

            longPressJob.cancel()
            if (isLongPressDetected) {
                onEnd()
                isLongPressDetected = false
            }
        }
    }
}
