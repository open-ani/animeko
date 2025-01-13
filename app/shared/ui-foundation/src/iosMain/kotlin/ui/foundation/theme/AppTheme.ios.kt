/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

@Composable
actual fun appColorScheme(
    seedColor: Int,
    useDynamicTheme: Boolean,
    useBlackBackground: Boolean,
    isDark: Boolean,
): ColorScheme {
    return dynamicColorScheme(
        primary = Color(seedColor),
        isDark = isDark,
        isAmoled = useBlackBackground,
        style = PaletteStyle.TonalSpot,
        modifyColorScheme = { colorScheme ->
            if (useBlackBackground && isDark) {
                colorScheme.copy(
                    surface = Color.Black,
                    background = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color.Black.copy(alpha = 0.1f),
                    surfaceContainer = Color.Black.copy(alpha = 0.2f),
                    surfaceContainerHigh = Color.Black.copy(alpha = 0.3f),
                    surfaceContainerHighest = Color.Black.copy(alpha = 0.4f),
                )
            } else colorScheme
        },
    )
}

@Composable
actual fun AniTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = appColorScheme(isDark = isDark),
        content = content,
    )
}