/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("ConstPropertyName")

package me.him188.ani.app.ui.foundation.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults.pageContentBackgroundColor

val LocalThemeSettings = compositionLocalOf<ThemeSettings> {
    error("LocalThemeSettings not provided")
}

/**
 * Create a [ColorScheme] based on the current [ThemeSettings].
 * You should prefer [AniTheme] if possible.
 */
@Composable
expect fun appColorScheme(
    seedColor: Color = LocalThemeSettings.current.seedColor,
    useDynamicTheme: Boolean = LocalThemeSettings.current.useDynamicTheme,
    useBlackBackground: Boolean = LocalThemeSettings.current.useBlackBackground,
    isDark: Boolean = when (LocalThemeSettings.current.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkTheme()
    },
): ColorScheme

/**
 * AniApp MaterialTheme.
 * @param isDark Used for overriding [DarkMode] in specific situations.
 */
@Composable
fun AniTheme(
    isDark: Boolean = when (LocalThemeSettings.current.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = appColorScheme(isDark = isDark),
        content = content,
    )
}

@Stable
object AniThemeDefaults {
    // 参考 M3 配色方案:
    // https://m3.material.io/styles/color/roles#63d6db08-59e2-4341-ac33-9509eefd9b4f

    /**
     * Navigation rail on desktop, bottom navigation on mobile.
     */
    val navigationContainerColor
        @Composable get() = MaterialTheme.colorScheme.surfaceContainer

    val pageContentBackgroundColor
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest

    /**
     * 默认的 [TopAppBarColors], 期望用于 [pageContentBackgroundColor] 的容器之内
     */
    @Composable
    fun topAppBarColors(containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )

    /**
     * 透明背景颜色, 注意不能用在可滚动的场景, 因为滚动后 TopAppBar 背景将能看到后面的其他元素
     */
    @Composable
    fun transparentAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )

    /**
     * 仅充当背景作用的卡片颜色, 例如 RSS 设置页中的那些圆角卡片背景
     */
    @Composable
    fun backgroundCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainerLow),
    )

    /**
     * 适用于整个 pane 都是一堆卡片, 而且这些卡片有一定的作用. 例如追番列表的卡片.
     */
    @Composable
    fun primaryCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh),
    )


    // These are kept for PR migration in 4.5.0. Safe to remove in 4.6.0.

    @Stable
    @Deprecated(
        "Use LocalAniMotionScheme instead",
        ReplaceWith(
            "LocalAniMotionScheme.current.feedItemFadeInSpec",
            "me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme",
        ),
        level = DeprecationLevel.ERROR,
    )
    val feedItemFadeInSpec: FiniteAnimationSpec<Float>
        @Composable
        get() = LocalAniMotionScheme.current.feedItemFadeInSpec

    @Stable
    @Deprecated(
        "Use LocalAniMotionScheme instead",
        ReplaceWith(
            "LocalAniMotionScheme.current.feedItemPlacementSpec",
            "me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme",
        ),
        level = DeprecationLevel.ERROR,
    )
    val feedItemPlacementSpec: FiniteAnimationSpec<IntOffset>
        @Composable
        get() = LocalAniMotionScheme.current.feedItemPlacementSpec

    @Stable
    @Deprecated(
        "Use LocalAniMotionScheme instead",
        ReplaceWith(
            "LocalAniMotionScheme.current.feedItemFadeOutSpec",
            "me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme",
        ),
        level = DeprecationLevel.ERROR,
    )
    val feedItemFadeOutSpec: FiniteAnimationSpec<Float>
        @Composable
        get() = LocalAniMotionScheme.current.feedItemFadeOutSpec

    /**
     * 适用中小型组件.
     */
    @Stable
    @Deprecated(
        "Use LocalAniMotionScheme instead",
        ReplaceWith(
            "LocalAniMotionScheme.current.standardAnimatedContentTransition",
            "me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme",
        ),
        level = DeprecationLevel.ERROR,
    )
    val standardAnimatedContentTransition: AnimatedContentTransitionScope<*>.() -> ContentTransform
        @Composable
        get() = LocalAniMotionScheme.current.animatedContent.standard
}

/**
 * M3 推荐的 [tween] 动画时长
 */
@Stable
object EasingDurations {
    // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#6409707e-1253-449c-b588-d27fe53bd025
    const val emphasized = 500
    const val emphasizedDecelerate = 400
    const val emphasizedAccelerate = 200
    const val standard = 300
    const val standardDecelerate = 250
    const val standardAccelerate = 200
}

fun modifyColorSchemeForBlackBackground(
    colorScheme: ColorScheme,
    isDark: Boolean,
    useBlackBackground: Boolean,
): ColorScheme {
    return if (isDark && useBlackBackground) {
        colorScheme.copy(
            background = Color.Black,
            onBackground = Color.White,

            surface = Color.Black,
            onSurface = Color.White,
            surfaceContainerLowest = Color.Black,

            surfaceVariant = Color.Black,
            onSurfaceVariant = Color.White,
        )
    } else colorScheme
}

@Composable
fun ColorScheme.animate(
    currentColorScheme: ColorScheme = MaterialTheme.colorScheme,
    targetColorScheme: ColorScheme,
    animationSpec: AnimationSpec<Float> = tween(EasingDurations.standard),
): ColorScheme {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(currentColorScheme, targetColorScheme) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec)
    }

    return ColorScheme(
        primary = lerp(currentColorScheme.primary, targetColorScheme.primary, animationProgress.value),
        onPrimary = lerp(currentColorScheme.onPrimary, targetColorScheme.onPrimary, animationProgress.value),
        primaryContainer = lerp(
            currentColorScheme.primaryContainer,
            targetColorScheme.primaryContainer,
            animationProgress.value,
        ),
        onPrimaryContainer = lerp(
            currentColorScheme.onPrimaryContainer,
            targetColorScheme.onPrimaryContainer,
            animationProgress.value,
        ),
        inversePrimary = lerp(
            currentColorScheme.inversePrimary,
            targetColorScheme.inversePrimary,
            animationProgress.value,
        ),
        secondary = lerp(currentColorScheme.secondary, targetColorScheme.secondary, animationProgress.value),
        onSecondary = lerp(currentColorScheme.onSecondary, targetColorScheme.onSecondary, animationProgress.value),
        secondaryContainer = lerp(
            currentColorScheme.secondaryContainer,
            targetColorScheme.secondaryContainer,
            animationProgress.value,
        ),
        onSecondaryContainer = lerp(
            currentColorScheme.onSecondaryContainer,
            targetColorScheme.onSecondaryContainer,
            animationProgress.value,
        ),
        tertiary = lerp(currentColorScheme.tertiary, targetColorScheme.tertiary, animationProgress.value),
        onTertiary = lerp(currentColorScheme.onTertiary, targetColorScheme.onTertiary, animationProgress.value),
        tertiaryContainer = lerp(
            currentColorScheme.tertiaryContainer,
            targetColorScheme.tertiaryContainer,
            animationProgress.value,
        ),
        onTertiaryContainer = lerp(
            currentColorScheme.onTertiaryContainer,
            targetColorScheme.onTertiaryContainer,
            animationProgress.value,
        ),
        background = lerp(currentColorScheme.background, targetColorScheme.background, animationProgress.value),
        onBackground = lerp(currentColorScheme.onBackground, targetColorScheme.onBackground, animationProgress.value),
        surface = lerp(currentColorScheme.surface, targetColorScheme.surface, animationProgress.value),
        onSurface = lerp(currentColorScheme.onSurface, targetColorScheme.onSurface, animationProgress.value),
        surfaceVariant = lerp(
            currentColorScheme.surfaceVariant,
            targetColorScheme.surfaceVariant,
            animationProgress.value,
        ),
        onSurfaceVariant = lerp(
            currentColorScheme.onSurfaceVariant,
            targetColorScheme.onSurfaceVariant,
            animationProgress.value,
        ),
        surfaceTint = lerp(currentColorScheme.surfaceTint, targetColorScheme.surfaceTint, animationProgress.value),
        inverseSurface = lerp(
            currentColorScheme.inverseSurface,
            targetColorScheme.inverseSurface,
            animationProgress.value,
        ),
        inverseOnSurface = lerp(
            currentColorScheme.inverseOnSurface,
            targetColorScheme.inverseOnSurface,
            animationProgress.value,
        ),
        error = lerp(currentColorScheme.error, targetColorScheme.error, animationProgress.value),
        onError = lerp(currentColorScheme.onError, targetColorScheme.onError, animationProgress.value),
        errorContainer = lerp(
            currentColorScheme.errorContainer,
            targetColorScheme.errorContainer,
            animationProgress.value,
        ),
        onErrorContainer = lerp(
            currentColorScheme.onErrorContainer,
            targetColorScheme.onErrorContainer,
            animationProgress.value,
        ),
        outline = lerp(currentColorScheme.outline, targetColorScheme.outline, animationProgress.value),
        outlineVariant = lerp(
            currentColorScheme.outlineVariant,
            targetColorScheme.outlineVariant,
            animationProgress.value,
        ),
        scrim = lerp(currentColorScheme.scrim, targetColorScheme.scrim, animationProgress.value),
        surfaceBright = lerp(
            currentColorScheme.surfaceBright,
            targetColorScheme.surfaceBright,
            animationProgress.value,
        ),
        surfaceDim = lerp(currentColorScheme.surfaceDim, targetColorScheme.surfaceDim, animationProgress.value),
        surfaceContainer = lerp(
            currentColorScheme.surfaceContainer,
            targetColorScheme.surfaceContainer,
            animationProgress.value,
        ),
        surfaceContainerHigh = lerp(
            currentColorScheme.surfaceContainerHigh,
            targetColorScheme.surfaceContainerHigh,
            animationProgress.value,
        ),
        surfaceContainerHighest = lerp(
            currentColorScheme.surfaceContainerHighest,
            targetColorScheme.surfaceContainerHighest,
            animationProgress.value,
        ),
        surfaceContainerLow = lerp(
            currentColorScheme.surfaceContainerLow,
            targetColorScheme.surfaceContainerLow,
            animationProgress.value,
        ),
        surfaceContainerLowest = lerp(
            currentColorScheme.surfaceContainerLowest,
            targetColorScheme.surfaceContainerLowest,
            animationProgress.value,
        ),
    )
}
