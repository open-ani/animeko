/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings

@Composable
actual fun aniColorTheme(
    themeSettings: ThemeSettings,
    isDark: Boolean
): ColorScheme {
    return rememberDynamicColorScheme(
        primary = Color(themeSettings.seedColor),
        isDark = isDark,
        isAmoled = themeSettings.isAmoled,
        style = PaletteStyle.TonalSpot,
    )
}

@Composable
actual fun AniTheme(
    themeSettings: ThemeSettings,
    content: @Composable () -> Unit
) {
    val isDark = when (themeSettings.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = aniColorTheme(themeSettings, isDark),
        content = content,
    )
}