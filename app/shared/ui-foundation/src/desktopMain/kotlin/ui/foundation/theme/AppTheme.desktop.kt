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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.utils.platform.isWindows

@Composable
actual fun appColorScheme(
    seedColor: Color,
    useDynamicTheme: Boolean,
    useBlackBackground: Boolean,
    isDark: Boolean,
): ColorScheme {
    val actualSeedColor = if (useDynamicTheme && isPlatformSupportDynamicTheme()) {
        val currentWindowColor by LocalPlatformWindow.current.accentColor.collectAsState()
        //fallback to default color, if platform can't get the accent color
        currentWindowColor.takeOrElse { seedColor }
    } else {
        seedColor
    }
    return dynamicColorScheme(
        primary = actualSeedColor,
        isDark = isDark,
        isAmoled = useBlackBackground,
        style = PaletteStyle.TonalSpot,
        modifyColorScheme = { colorScheme ->
            modifyColorSchemeForBlackBackground(colorScheme, isDark, useBlackBackground)
        },
    )
}

@Composable
actual fun isPlatformSupportDynamicTheme(): Boolean {
    return LocalPlatform.current.isWindows()
}