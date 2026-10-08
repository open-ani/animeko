/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor

/**
 * Dispatches playback shortcuts throughout this subtree, except during text input.
 * Focus cleared inside the subtree returns to the player.
 * Nested scopes share the controller's handler and input sessions.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerKeyboardScope(
    controllerState: PlayerControllerState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val keyboard = controllerState.keyboard
    val focusState = controllerState.focusState
    val interceptor = remember(keyboard) {
        PlatformTextInputInterceptor { request, nextHandler ->
            keyboard.startTextInput()
            try {
                nextHandler.startInputMethod(request)
            } finally {
                keyboard.endTextInput()
            }
        }
    }
    LaunchedEffect(keyboard, focusState.preferredTarget) {
        if (focusState.preferredTarget == PlayerFocusTarget.TEXT_INPUT) keyboard.cancel()
    }
    InterceptPlatformTextInput(interceptor) {
        Box(
            modifier.onPreviewKeyEvent { keyboard.dispatch(it, focusState.preferredTarget) }
                .restorePlayerFocusWhenCleared(focusState),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

internal interface PlayerKeyboardHandler {
    operator fun invoke(event: KeyEvent): Boolean
    fun cancel()
}

/** Owns one command registration and the key pairs accepted by it. */
@Stable
internal class PlayerKeyboardState {
    private var handler: PlayerKeyboardHandler? = null
    private val pressed = mutableSetOf<Key>()
    private val cancelled = mutableSetOf<Key>()
    private val rejected = mutableSetOf<Key>()
    internal var activeTextInputSessions = 0
        private set

    fun register(handler: PlayerKeyboardHandler) {
        cancel()
        this.handler = handler
    }

    fun unregister(handler: PlayerKeyboardHandler) {
        if (this.handler === handler) {
            cancel()
            this.handler = null
        }
    }

    fun startTextInput() {
        activeTextInputSessions++
        cancel()
    }

    fun endTextInput() {
        activeTextInputSessions--
    }

    fun cancel() {
        handler?.cancel()
        cancelled.addAll(pressed)
        pressed.clear()
    }

    fun dispatch(event: KeyEvent, preferredTarget: PlayerFocusTarget): Boolean {
        if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)) {
            rejected.add(event.key)
            if (event.key in pressed) cancel()
            return false
        }
        if (event.key in rejected) {
            if (event.type == KeyEventType.KeyUp) {
                rejected.remove(event.key)
                cancelled.remove(event.key)
            }
            return false
        }
        if (activeTextInputSessions > 0 || preferredTarget == PlayerFocusTarget.TEXT_INPUT) {
            cancel()
            if (event.type == KeyEventType.KeyUp) cancelled.remove(event.key)
            return false
        }
        if (event.key in cancelled) {
            if (event.type == KeyEventType.KeyUp) cancelled.remove(event.key)
            return true
        }
        val handler = handler ?: return false
        if (event.type == KeyEventType.KeyUp) {
            if (!pressed.remove(event.key)) return false
            handler(event)
            return true
        }
        if (event.type == KeyEventType.KeyDown && handler(event)) {
            pressed.add(event.key)
            return true
        }
        return false
    }
}
