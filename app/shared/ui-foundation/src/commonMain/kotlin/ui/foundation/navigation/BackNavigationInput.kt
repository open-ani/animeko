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
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.node.ModifierNodeElement
import me.him188.ani.app.ui.foundation.effects.onPointerEventMultiplatform

/**
 * Handles keyboard and pointer inputs that mean "navigate back".
 *
 * Key events are handled during bubbling so a focused child can consume Escape first, for example
 * to close an editor without navigating away. See [BackKeyEventHandler] for the key-up rule.
 *
 * Key events only reach this modifier while some node inside it has focus. When nothing is focused
 * (for example right after a sheet dismissal cleared focus), the window has to route Escape itself,
 * also via [BackKeyEventHandler].
 */
fun Modifier.onBackNavigationInput(onBack: () -> Unit): Modifier =
    this.then(BackKeyNavigationElement(onBack))
        .onPointerEventMultiplatform(PointerEventType.Press) { event ->
            if (event.buttons.isBackPressed && event.changes.none { it.isConsumed }) {
                event.changes.forEach { it.consume() }
                onBack()
            }
        }

/**
 * Treats Escape as "navigate back": the key-down is consumed and `onBack` runs on key-up.
 *
 * `onBack` only fires when this handler also saw the matching key-down. A key-up on its own is
 * ignored: it means someone else took the key-down, e.g. a focused child that consumed it, or
 * another window (the image viewer) that closed itself on key-down and let the key-up land on the
 * window behind it. Without this rule closing the image viewer with Escape also popped the page.
 *
 * Besides [onBackNavigationInput], this is also used as the window-level fallback on desktop: Compose
 * Desktop forwards an Escape that no node consumed straight into Navigation 3's back dispatcher,
 * which pops the page and bypasses every [BackHandler] (fullscreen, image viewer, ...). Routing the
 * unconsumed key through the app's own dispatcher instead keeps both paths consistent.
 */
class BackKeyEventHandler {
    private var escapePressed = false

    /**
     * Returns whether [event] was consumed. Any key other than Escape is left alone.
     */
    fun onKeyEvent(event: KeyEvent, onBack: () -> Unit): Boolean {
        if (event.key != Key.Escape) return false
        return when (event.type) {
            KeyEventType.KeyDown -> {
                escapePressed = true
                true
            }

            KeyEventType.KeyUp -> {
                if (!escapePressed) return false
                escapePressed = false
                onBack()
                true
            }

            else -> false
        }
    }

    fun reset() {
        escapePressed = false
    }
}

private data class BackKeyNavigationElement(
    val onBack: () -> Unit,
) : ModifierNodeElement<BackKeyNavigationNode>() {
    override fun create() = BackKeyNavigationNode(onBack)

    override fun update(node: BackKeyNavigationNode) {
        node.onBack = onBack
    }
}

private class BackKeyNavigationNode(
    var onBack: () -> Unit,
) : Modifier.Node(), KeyInputModifierNode {
    private val handler = BackKeyEventHandler()

    override fun onKeyEvent(event: KeyEvent): Boolean = handler.onKeyEvent(event, onBack)

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun onDetach() {
        handler.reset()
    }
}
