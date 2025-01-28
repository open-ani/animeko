/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import com.kmpalette.DominantColorState
import com.kmpalette.PaletteState
import com.kmpalette.color
import com.kmpalette.loader.ImageBitmapLoader
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.Dispatchers
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import kotlin.coroutines.CoroutineContext

@Composable
fun rememberVibrantThemeState(
    initialColorScheme: ColorScheme,
    coroutineContext: CoroutineContext = Dispatchers.Default
): VibrantThemeState {
    return remember(coroutineContext) {
        VibrantThemeState(initialColorScheme, coroutineContext)
    }
}

@Stable
class VibrantThemeState(
    initialColorScheme: ColorScheme,
    private val coroutineContext: CoroutineContext = Dispatchers.Default
) {
    private var prevColorSchemeState = mutableStateOf<ColorScheme?>(null)
    private var currColorSchemeState = mutableStateOf(initialColorScheme)

    private val paletteState = object : PaletteState<ImageBitmap>(
        DominantColorState.DEFAULT_CACHE_SIZE,
        coroutineContext,
        { },
    ) {
        @Suppress("INVISIBLE_MEMBER")
        override val loader: ImageBitmapLoader<ImageBitmap> =
            object : ImageBitmapLoader<ImageBitmap> {
                override suspend fun load(input: ImageBitmap): ImageBitmap {
                    return input
                }
            }
    }
    private val animatable = Animatable(0f)

    val progress by animatable.asState()
    val prevColorScheme by prevColorSchemeState
    val colorScheme by currColorSchemeState

    suspend fun applyPalette(
        image: ImageBitmap,
        currentThemeSettings: ThemeSettings,
        useDarkTheme: Boolean,
    ) {
        paletteState.generate(image)

        prevColorSchemeState.value = currColorSchemeState.value
        animatable.snapTo(0f) // snap to initial state

        val targetColor =
            paletteState.palette?.vibrantSwatch?.color ?: currentThemeSettings.seedColor

        currColorSchemeState.value = dynamicColorScheme(
            primary = targetColor,
            isDark = useDarkTheme,
            isAmoled = currentThemeSettings.useBlackBackground,
            style = PaletteStyle.TonalSpot,
            modifyColorScheme = { colorScheme ->
                modifyColorSchemeForBlackBackground(
                    colorScheme,
                    useDarkTheme,
                    currentThemeSettings.useBlackBackground,
                )
            },
        )
        animatable.animateTo(1f)
    }
}

/**
 * Apply dynamic vibrant color to the material 3 theme.
 */
@Composable
fun VibrantMaterialTheme(
    state: VibrantThemeState,
    content: @Composable () -> Unit
) {
    val prev by remember {
        derivedStateOf { state.prevColorScheme ?: state.colorScheme }
    }
    val curr = state.colorScheme
    val prog = state.progress

    MaterialTheme(
        colorScheme = ColorScheme(
            primary = lerp(prev.primary, curr.primary, prog),
            onPrimary = lerp(prev.onPrimary, curr.onPrimary, prog),
            primaryContainer = lerp(prev.primaryContainer, curr.primaryContainer, prog),
            onPrimaryContainer = lerp(prev.onPrimaryContainer, curr.onPrimaryContainer, prog),
            inversePrimary = lerp(prev.inversePrimary, curr.inversePrimary, prog),
            secondary = lerp(prev.secondary, curr.secondary, prog),
            onSecondary = lerp(prev.onSecondary, curr.onSecondary, prog),
            secondaryContainer = lerp(prev.secondaryContainer, curr.secondaryContainer, prog),
            onSecondaryContainer = lerp(prev.onSecondaryContainer, curr.onSecondaryContainer, prog),
            tertiary = lerp(prev.tertiary, curr.tertiary, prog),
            onTertiary = lerp(prev.onTertiary, curr.onTertiary, prog),
            tertiaryContainer = lerp(prev.tertiaryContainer, curr.tertiaryContainer, prog),
            onTertiaryContainer = lerp(prev.onTertiaryContainer, curr.onTertiaryContainer, prog),
            background = lerp(prev.background, curr.background, prog),
            onBackground = lerp(prev.onBackground, curr.onBackground, prog),
            surface = lerp(prev.surface, curr.surface, prog),
            onSurface = lerp(prev.onSurface, curr.onSurface, prog),
            surfaceVariant = lerp(prev.surfaceVariant, curr.surfaceVariant, prog),
            onSurfaceVariant = lerp(prev.onSurfaceVariant, curr.onSurfaceVariant, prog),
            surfaceTint = lerp(prev.surfaceTint, curr.surfaceTint, prog),
            inverseSurface = lerp(prev.inverseSurface, curr.inverseSurface, prog),
            inverseOnSurface = lerp(prev.inverseOnSurface, curr.inverseOnSurface, prog),
            surfaceBright = lerp(prev.surfaceBright, curr.surfaceBright, prog),
            surfaceDim = lerp(prev.surfaceDim, curr.surfaceDim, prog),
            surfaceContainer = lerp(prev.surfaceContainer, curr.surfaceContainer, prog),
            surfaceContainerHigh = lerp(prev.surfaceContainerHigh, curr.surfaceContainerHigh, prog),
            surfaceContainerHighest = lerp(prev.surfaceContainerHighest, curr.surfaceContainerHighest, prog),
            surfaceContainerLow = lerp(prev.surfaceContainerLow, curr.surfaceContainerLow, prog),
            surfaceContainerLowest = lerp(prev.surfaceContainerLowest, curr.surfaceContainerLowest, prog),
            error = lerp(prev.error, curr.error, prog),
            onError = lerp(prev.onError, curr.onError, prog),
            errorContainer = lerp(prev.errorContainer, curr.errorContainer, prog),
            onErrorContainer = lerp(prev.onErrorContainer, curr.onErrorContainer, prog),
            outline = lerp(prev.outline, curr.outline, prog),
            outlineVariant = lerp(prev.outlineVariant, curr.outlineVariant, prog),
            scrim = lerp(prev.scrim, curr.scrim, prog),
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/**
 * Apply dynamic vibrant color to the material 3 theme.
 */
@Composable
fun VibrantMaterialTheme(
    bitmap: ImageBitmap?,
    content: @Composable () -> Unit
) {
    val state = rememberVibrantThemeState(MaterialTheme.colorScheme)
    val themeSettings = LocalThemeSettings.current
    val useDarkTheme = when (themeSettings.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkTheme()
    }

    LaunchedEffect(bitmap) {
        if (bitmap == null) return@LaunchedEffect
        state.applyPalette(bitmap, themeSettings, useDarkTheme)
    }

    VibrantMaterialTheme(state, content)
}