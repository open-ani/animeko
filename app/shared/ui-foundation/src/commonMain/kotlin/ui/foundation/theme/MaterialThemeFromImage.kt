/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.themeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.resize

/**
 * Generate a MaterialTheme from an image
 */
@Composable
fun MaterialThemeFromImage(
    bitmap: ImageBitmap?,
    content: @Composable () -> Unit
) {
    val themeSettings = LocalThemeSettings.current
    val isDark = when (themeSettings.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkTheme()
    }
    val useBlackBackground = themeSettings.useBlackBackground
    var colorScheme by remember { mutableStateOf<ColorScheme?>(null) }
    val fallbackSeedColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(bitmap) {
        if (bitmap == null) return@LaunchedEffect

        val computedColorScheme = withContext(Dispatchers.Default) {
            val resizedBitmap = bitmap.resize(64)
            val primaryColor = resizedBitmap.themeColor(
                fallback = fallbackSeedColor,
                filter = false,
                maxColors = 16,
            )
            dynamicColorScheme(
                primary = primaryColor,
                isDark = isDark,
                isAmoled = useBlackBackground,
                style = PaletteStyle.TonalSpot,
                modifyColorScheme = { colorScheme ->
                    modifyColorSchemeForBlackBackground(
                        colorScheme,
                        isDark,
                        useBlackBackground,
                    )
                },
            )
        }
        colorScheme = computedColorScheme
    }

    MaterialTheme(
        colorScheme = animateColorScheme(
            targetColorScheme = colorScheme ?: MaterialTheme.colorScheme,
        ),
        content = content,
    )
}