/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

@Composable
actual fun AniTheme(
    seedColor: Color,
    darkTheme: Boolean,
    isAmoled: Boolean,
    useDynamicTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = rememberDynamicColorScheme(
        primary = seedColor,
        isDark = darkTheme,
        isAmoled = isAmoled,
        style = PaletteStyle.TonalSpot,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}