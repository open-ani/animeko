/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.effects.ComposeKey
import me.him188.ani.app.ui.foundation.effects.onKey
import me.him188.ani.app.videoplayer.ui.PlayerKeyboardHandler

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
): Modifier = composed {
    val handler = rememberKeyboardSeekHandler(onSeekBackward, onSeekForward, fastSkipState)
    onPreviewKeyEvent { handler(it) }
}

@Composable
internal fun rememberKeyboardSeekHandler(
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    fastSkipState: FastSkipState?,
): PlayerKeyboardHandler {
    val layoutDirection = LocalLayoutDirection.current
    val onBackwardState by rememberUpdatedState(onSeekBackward)
    val onForwardState by rememberUpdatedState(onSeekForward)
    val scope = rememberCoroutineScope()
    val handler = remember(layoutDirection, fastSkipState, scope) {
        KeyboardSeekHandler(scope, layoutDirection, fastSkipState, { onBackwardState() }, { onForwardState() })
    }
    DisposableEffect(handler) {
        onDispose { handler.cancel() }
    }
    return handler
}

private class KeyboardSeekHandler(
    private val scope: CoroutineScope,
    layoutDirection: LayoutDirection,
    private val fastSkipState: FastSkipState?,
    private val onBackward: () -> Unit,
    private val onForward: () -> Unit,
) : PlayerKeyboardHandler {
    private val backwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionLeft
    } else {
        ComposeKey.DirectionRight
    }
    private val forwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionRight
    } else {
        ComposeKey.DirectionLeft
    }
    private var backwardPressed = false
    private var forwardPressed = false
    private var job: Job? = null
    private var ticket: Int? = null

    override fun invoke(event: KeyEvent): Boolean {
        if (event.key == backwardKey) {
            if (event.type == KeyEventType.KeyDown) {
                backwardPressed = true
                return true
            }
            if (event.type == KeyEventType.KeyUp && backwardPressed) {
                backwardPressed = false
                onBackward()
                return true
            }
        }
        if (event.key == forwardKey) {
            if (event.type == KeyEventType.KeyDown) {
                if (!forwardPressed) {
                    forwardPressed = true
                    job = scope.launch {
                        delay(200)
                        ticket = fastSkipState?.startSkipping(SkipDirection.FORWARD)
                    }
                }
                return true
            }
            if (event.type == KeyEventType.KeyUp && forwardPressed) {
                val wasSkipping = ticket != null
                cancelForward()
                if (!wasSkipping) onForward()
                return true
            }
        }
        return false
    }

    private fun cancelForward() {
        job?.cancel()
        job = null
        ticket?.let { fastSkipState?.stopSkipping(it) }
        ticket = null
        forwardPressed = false
    }

    override fun cancel() {
        backwardPressed = false
        cancelForward()
    }
}
