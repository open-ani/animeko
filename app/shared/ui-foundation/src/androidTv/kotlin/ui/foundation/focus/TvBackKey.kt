/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.foundation.focus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Complete Back on release so removing an overlay cannot leak its key-up to its parent. */
fun Modifier.tvBackKey(enabled: Boolean = true, onBack: () -> Unit): Modifier = composed {
    var pressed by remember { mutableStateOf<Key?>(null) }
    var releaseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    onPreviewKeyEvent { event ->
        if (event.key != Key.Back && event.key != Key.Escape) return@onPreviewKeyEvent false
        when {
            event.type == KeyEventType.KeyDown && (enabled || pressed != null) -> {
                if (pressed == null) releaseAction = onBack
                pressed = event.key
                true
            }
            event.type == KeyEventType.KeyUp && pressed == event.key -> {
                val action = releaseAction
                pressed = null
                releaseAction = null
                action?.invoke()
                true
            }
            else -> false
        }
    }
}
