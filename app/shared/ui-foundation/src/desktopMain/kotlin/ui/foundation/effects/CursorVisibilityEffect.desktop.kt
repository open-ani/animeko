/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.platform.window.AwtWindowUtils.Companion.blankCursor

actual fun Modifier.cursorVisibility(visible: Boolean): Modifier {
    return if (visible) {
        testTag(TAG_CURSOR_VISIBILITY_EFFECT_VISIBLE)
    } else {
        (blankCursor?.let {
            pointerHoverIcon(PointerIcon(it))
        } ?: this)
            .testTag(TAG_CURSOR_VISIBILITY_EFFECT_INVISIBLE)
    }
}
