/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf

//This controller should only be created and passed when the caption button is in the top end position.
val LocalTitleBarThemeController = compositionLocalOf<TitleBarThemeController?> { null }

//This controller only used in Windows transparent window frame.
class TitleBarThemeController {
    // We use state, because it can trigger recomposition that make CaptionButton color change.

    val isDark by derivedStateOf { requesterStack.lastOrNull()?.second == true }

    private val requesterStack = mutableStateListOf<Pair<Any, Boolean>>()

    fun requestTheme(owner: Any, isDark: Boolean) {
        val index = requesterStack.indexOfLast { it.first == owner }
        if (index >= 0) {
            requesterStack[index] = owner to isDark
        } else {
            requesterStack.add(owner to isDark)
        }
    }

    fun removeTheme(owner: Any) {
        requesterStack.removeIf { it.first == owner }
    }
}