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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

//This controller should only be created and passed when the caption button is in the top end position.
val LocalTitleBarThemeController = compositionLocalOf<TitleBarThemeController?> { null }

class TitleBarThemeController {
    // We use state, because it can trigger recomposition that make CaptionButton color change.
    var isDark: Boolean by mutableStateOf(currentSystemTheme == SystemTheme.DARK)
}