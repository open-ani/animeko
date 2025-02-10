/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.platform.findActivity

@Composable
actual fun appColorScheme(
    seedColor: Color,
    useDynamicTheme: Boolean,
    useBlackBackground: Boolean,
    isDark: Boolean,
): ColorScheme {
    return if (useDynamicTheme && Build.VERSION.SDK_INT >= 31) {
        if (isDark) {
            modifyColorSchemeForBlackBackground(
                colorScheme = dynamicDarkColorScheme(LocalContext.current),
                isDark = true,
                useBlackBackground = useBlackBackground,
            )
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    } else {
        dynamicColorScheme(
            primary = seedColor,
            isDark = isDark,
            isAmoled = useBlackBackground,
            style = PaletteStyle.TonalSpot,
            modifyColorScheme = { colorScheme ->
                modifyColorSchemeForBlackBackground(colorScheme, isDark, useBlackBackground)
            },
        )
    }
}

@Composable
fun SystemBarColorEffect(
    isDark: Boolean = when (LocalThemeSettings.current.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkTheme()
    },
) {
    // Set statusBarStyle & navigationBarStyle
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    if (activity != null) {
        DisposableEffect(activity, isDark) {
            if (isDark) {
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                )
            } else {
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ),
                    navigationBarStyle = SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ),
                )
            }
            onDispose { }
        }
    }
}
