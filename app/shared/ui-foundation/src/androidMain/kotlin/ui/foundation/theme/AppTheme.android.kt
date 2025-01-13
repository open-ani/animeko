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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import me.him188.ani.app.platform.findActivity

@Composable
actual fun appColorScheme(
    seedColor: Int,
    useDynamicTheme: Boolean,
    useBlackBackground: Boolean,
    isDark: Boolean,
): ColorScheme {
    return if (useDynamicTheme && Build.VERSION.SDK_INT >= 31) {
        if (isDark) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    } else {
        dynamicColorScheme(
            primary = Color(seedColor),
            isDark = isDark,
            isAmoled = useBlackBackground,
            style = PaletteStyle.TonalSpot,
            modifyColorScheme = { colorScheme ->
                if (useBlackBackground && isDark) {
                    colorScheme.copy(
//                    surface = Color.Black,
//                    background = Color.Black,
//                    surfaceContainerLowest = Color.Black,
//                    surfaceContainerLow = Color.Black.copy(alpha = 0.1f),
//                    surfaceContainer = Color.Black.copy(alpha = 0.2f),
//                    surfaceContainerHigh = Color.Black.copy(alpha = 0.3f),
//                    surfaceContainerHighest = Color.Black.copy(alpha = 0.4f),
//                    primary = Color.Black,
//                    primaryContainer = Color.Black,
//                    secondary = Color.Black,
//                    secondaryContainer = Color.Black,
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceVariant = Color(0xFF121212),
                        onSurfaceVariant = Color.White,
                    )
                } else colorScheme
            }
        )
    }
}

@Composable
actual fun AniTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
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

    MaterialTheme(
        colorScheme = appColorScheme(isDark = isDark),
        content = content,
    )
}
