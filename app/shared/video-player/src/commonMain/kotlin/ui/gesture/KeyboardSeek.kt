/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.effects.ComposeKey
import me.him188.ani.app.ui.foundation.effects.onKey

@Stable
class KeyboardHorizontalDirectionState(
    val onBackward: () -> Unit,
    val onForward: () -> Unit,
)


fun Modifier.onKeyboardHorizontalDirection(
    state: KeyboardHorizontalDirectionState,
): Modifier = onKeyboardHorizontalDirection(
    onBackward = state.onBackward,
    onForward = state.onForward,
)

fun Modifier.onKeyboardHorizontalDirection(
    onBackward: () -> Unit,
    onForward: () -> Unit,
): Modifier = composed(
    inspectorInfo = {
        name = "keyboardSeek"
    },
) {
    val layoutDirection = LocalLayoutDirection.current
    val backwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionLeft
    } else {
        ComposeKey.DirectionRight
    }
    val forwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionRight
    } else {
        ComposeKey.DirectionLeft
    }

    val onBackwardState by rememberUpdatedState(onBackward)
    val onForwardState by rememberUpdatedState(onForward)
    onKey(backwardKey) {
        onBackwardState()
    }.onKey(forwardKey) {
        onForwardState()
    }
}

fun Modifier.keyboardSeekAndFastForward(
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    fastSkipState: FastSkipState?,
): Modifier = composed(
    inspectorInfo = {
        name = "keyboardSeekAndFastForward"
    },
) {
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val backwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionLeft
    } else {
        ComposeKey.DirectionRight
    }
    val forwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionRight
    } else {
        ComposeKey.DirectionLeft
    }

    val onBackwardState by rememberUpdatedState(onSeekBackward)
    val onForwardState by rememberUpdatedState(onSeekForward)

    var isLongPressing by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var ticket by remember { mutableStateOf<Int?>(null) }

    onPreviewKeyEvent { event ->
        if (event.key == backwardKey) {
            if (event.type == KeyEventType.KeyDown) {
                return@onPreviewKeyEvent true
            } else if (event.type == KeyEventType.KeyUp) {
                onBackwardState()
                return@onPreviewKeyEvent true
            }
        }

        if (event.key == forwardKey) {
            if (event.type == KeyEventType.KeyDown) {
                if (longPressJob == null && !isLongPressing) {
                    longPressJob = scope.launch {
                        delay(200)
                        isLongPressing = true
                        fastSkipState?.let {
                            ticket = it.startSkipping(SkipDirection.FORWARD)
                        }
                    }
                }
                return@onPreviewKeyEvent true
            } else if (event.type == KeyEventType.KeyUp) {
                longPressJob?.cancel()
                longPressJob = null

                if (isLongPressing) {
                    ticket?.let {
                        fastSkipState?.stopSkipping(it)
                    }
                    ticket = null
                    isLongPressing = false
                } else {
                    onForwardState()
                }
                return@onPreviewKeyEvent true
            }
        }
        false
    }
}

