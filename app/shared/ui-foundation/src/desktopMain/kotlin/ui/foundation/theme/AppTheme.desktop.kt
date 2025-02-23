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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import me.him188.ani.app.platform.window.WindowsWindowUtils
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.utils.coroutines.combine
import me.him188.ani.utils.platform.isWindows

@Composable
actual fun appColorScheme(
    seedColor: Color,
    useDynamicTheme: Boolean,
    useBlackBackground: Boolean,
    isDark: Boolean,
): ColorScheme {
    return DesktopDynamicTheme(
        useDynamicTheme = useDynamicTheme,
        useBlackBackground = useBlackBackground,
        isDark = isDark,
    ) ?: dynamicColorScheme(
        primary = seedColor,
        isDark = isDark,
        isAmoled = useBlackBackground,
        style = PaletteStyle.TonalSpot,
        modifyColorScheme = { colorScheme ->
            modifyColorSchemeForBlackBackground(colorScheme, isDark, useBlackBackground)
        },
    )
}

@Composable
private fun DesktopDynamicTheme(
    useDynamicTheme: Boolean,
    useBlackBackground: Boolean,
    isDark: Boolean,
): ColorScheme? {
    return when {
        !useDynamicTheme -> null
        LocalPlatform.current.isWindows() -> {
            val platformWindow = LocalPlatformWindow.current
            val isDarkState = rememberUpdatedState(isDark)
            val useBlackBackgroundState = rememberUpdatedState(useBlackBackground)
            remember(platformWindow, isDarkState, useBlackBackgroundState) {
                WindowsWindowUtils.instance.windowAccentColor(platformWindow)
                    .combine(
                        snapshotFlow { isDarkState.value },
                        snapshotFlow { useBlackBackgroundState.value },
                    ) { color, isDark, useBlackBackground ->
                        if (color != Color.Unspecified) {
                            dynamicColorScheme(
                                primary = color,
                                isDark = isDark,
                                isAmoled = useBlackBackground,
                                style = PaletteStyle.TonalSpot,
                                modifyColorScheme = { colorScheme ->
                                    modifyColorSchemeForBlackBackground(colorScheme, isDark, useBlackBackground)
                                },
                            )
                        } else {
                            null
                        }
                    }
            }.collectAsState(null).value
        }

        else -> null
    }
}

@Composable
actual fun isPlatformSupportDynamicTheme(): Boolean {
    return if (LocalPlatform.current.isWindows()) {
        val platformWindow = LocalPlatformWindow.current
        remember(platformWindow) {
            WindowsWindowUtils.instance.isExtendToTitleBar(platformWindow)
        }.collectAsState(false).value
    } else {
        false
    }
}