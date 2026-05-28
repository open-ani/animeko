package me.him188.ani.app.ui.foundation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

@Composable
actual fun appColorScheme(
    seedColor: Color,
    useDynamicTheme: Boolean,
    useBlackBackground: Boolean,
    isDark: Boolean,
): ColorScheme = dynamicColorScheme(
    primary = seedColor,
    isDark = isDark,
    isAmoled = useBlackBackground,
    style = PaletteStyle.TonalSpot,
    modifyColorScheme = { colorScheme ->
        modifyColorSchemeForBlackBackground(colorScheme, isDark, useBlackBackground)
    },
)

@Composable
actual fun isPlatformSupportDynamicTheme(): Boolean = false
