/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import me.him188.ani.app.ui.foundation.effects.onPointerEventMultiplatform

/**
 * Handles keyboard and pointer inputs that mean "navigate back".
 *
 * Key events are handled during bubbling so a focused child can consume Escape first, for example
 * to close an editor without navigating away.
 *
 * Key events only reach this modifier while some node inside it has focus. When nothing is focused
 * (for example right after a sheet dismissal cleared focus), the window has to route Escape itself,
 * see [handleBackKeyEvent].
 */
fun Modifier.onBackNavigationInput(onBack: () -> Unit): Modifier =
    onKeyEvent { event -> handleBackKeyEvent(event, onBack) }
        .onPointerEventMultiplatform(PointerEventType.Press) { event ->
            if (event.buttons.isBackPressed && event.changes.none { it.isConsumed }) {
                event.changes.forEach { it.consume() }
                onBack()
            }
        }

/**
 * Treats Escape as "navigate back": the key-down is consumed and [onBack] runs on key-up, so a
 * child that consumed the key-down never sees the action fire on release.
 *
 * Returns whether [event] was consumed. Any other key is left alone.
 *
 * Besides [onBackNavigationInput], this is also used as the window-level fallback on desktop: Compose
 * Desktop forwards an Escape that no node consumed straight into Navigation 3's back dispatcher,
 * which pops the page and bypasses every [BackHandler] (fullscreen, image viewer, ...). Routing the
 * unconsumed key through the app's own dispatcher instead keeps both paths consistent.
 */
fun handleBackKeyEvent(event: KeyEvent, onBack: () -> Unit): Boolean = when {
    event.key != Key.Escape -> false
    event.type == KeyEventType.KeyDown -> true
    event.type == KeyEventType.KeyUp -> {
        onBack()
        true
    }

    else -> false
}
