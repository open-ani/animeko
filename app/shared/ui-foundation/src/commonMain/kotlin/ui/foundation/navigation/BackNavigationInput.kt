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
 */
fun Modifier.onBackNavigationInput(onBack: () -> Unit): Modifier =
    onKeyEvent { event ->
        when {
            event.key != Key.Escape -> false
            event.type == KeyEventType.KeyDown -> true
            event.type == KeyEventType.KeyUp -> {
                onBack()
                true
            }

            else -> false
        }
    }.onPointerEventMultiplatform(PointerEventType.Press) { event ->
        if (event.buttons.isBackPressed && event.changes.none { it.isConsumed }) {
            event.changes.forEach { it.consume() }
            onBack()
        }
    }
