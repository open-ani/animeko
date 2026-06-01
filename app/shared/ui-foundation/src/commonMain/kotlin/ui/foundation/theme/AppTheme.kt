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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.LocalPlatformFontFamily
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.copyWithPlatformFontFamily
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

@Composable
expect fun isPlatformSupportDynamicTheme(): Boolean

/**
 * 深色配色的 `onSurface`, 供盖在深色遮罩上的内容取前景色: 这类内容与当前明暗无关, 恒定用深色前景.
 *
 * 由 [AniTheme] 生成一次向下提供. 取这个颜色要生成整套配色, 而取用方通常在列表项里,
 * 各自调用会按项重复生成.
 *
 * [Color.Unspecified] 表示不在 [AniTheme] 作用域内, 如 Preview, 取用方需自行回退.
 */
val LocalDarkOnSurface: ProvidableCompositionLocal<Color> = compositionLocalOf { Color.Unspecified }

/**
 * AniApp MaterialTheme.
 * @param darkModeOverride Used for overriding [DarkMode] in specific situations.
 */
@Composable
fun AniTheme(
    darkModeOverride: DarkMode? = null,
    content: @Composable () -> Unit,
) {
    val platformFontFamily = LocalPlatformFontFamily.current
    val isDark = when (darkModeOverride ?: LocalThemeSettings.current.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkTheme()
    }
    val colorScheme = appColorScheme(isDark = isDark)
    // 深色主题直接复用当前配色; 浅色主题需额外生成一套, 界面上没有取用方时这次生成是多余的,
    // 换来的是取用方不再按列表项各自生成.
    val darkOnSurface = if (isDark) colorScheme.onSurface else appColorScheme(isDark = true).onSurface

    CompositionLocalProvider(LocalDarkOnSurface provides darkOnSurface) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography.copyWithPlatformFontFamily(platformFontFamily),
            content = content,
        )
    }
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
     * 沉浸式外壳的全屏背景色 (外壳统一背景与侧边栏展开面板底色共用, 两处必须一致):
     * 深色主题用页面背景色 [pageContentBackgroundColor]; 浅色主题用 surface, 带主题色而非死白.
     */
    val shellBackgroundColor: Color
        @Composable get() {
            val surface = MaterialTheme.colorScheme.surface
            return if (surface.luminance() < 0.5f) pageContentBackgroundColor else surface
        }

    /**
     * 默认的 [TopAppBarColors], 期望用于 [pageContentBackgroundColor] 的容器之内
     */
    @Composable
    fun topAppBarColors(containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
fun animateColorScheme(
    targetColorScheme: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(EasingDurations.standard),
): ColorScheme {
    return ColorScheme(
        primary = animateColorAsState(targetColorScheme.primary, animationSpec).value,
        onPrimary = animateColorAsState(targetColorScheme.onPrimary, animationSpec).value,
        primaryContainer = animateColorAsState(targetColorScheme.primaryContainer, animationSpec).value,
        onPrimaryContainer = animateColorAsState(targetColorScheme.onPrimaryContainer, animationSpec).value,
        inversePrimary = animateColorAsState(targetColorScheme.inversePrimary, animationSpec).value,
        secondary = animateColorAsState(targetColorScheme.secondary, animationSpec).value,
        onSecondary = animateColorAsState(targetColorScheme.onSecondary, animationSpec).value,
        secondaryContainer = animateColorAsState(targetColorScheme.secondaryContainer, animationSpec).value,
        onSecondaryContainer = animateColorAsState(targetColorScheme.onSecondaryContainer, animationSpec).value,
        tertiary = animateColorAsState(targetColorScheme.tertiary, animationSpec).value,
        onTertiary = animateColorAsState(targetColorScheme.onTertiary, animationSpec).value,
        tertiaryContainer = animateColorAsState(targetColorScheme.tertiaryContainer, animationSpec).value,
        onTertiaryContainer = animateColorAsState(targetColorScheme.onTertiaryContainer, animationSpec).value,
        background = animateColorAsState(targetColorScheme.background, animationSpec).value,
        onBackground = animateColorAsState(targetColorScheme.onBackground, animationSpec).value,
        surface = animateColorAsState(targetColorScheme.surface, animationSpec).value,
        onSurface = animateColorAsState(targetColorScheme.onSurface, animationSpec).value,
        surfaceVariant = animateColorAsState(targetColorScheme.surfaceVariant, animationSpec).value,
        onSurfaceVariant = animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec).value,
        surfaceTint = animateColorAsState(targetColorScheme.surfaceTint, animationSpec).value,
        inverseSurface = animateColorAsState(targetColorScheme.inverseSurface, animationSpec).value,
        inverseOnSurface = animateColorAsState(targetColorScheme.inverseOnSurface, animationSpec).value,
        surfaceBright = animateColorAsState(targetColorScheme.surfaceBright, animationSpec).value,
        surfaceDim = animateColorAsState(targetColorScheme.surfaceDim, animationSpec).value,
        surfaceContainer = animateColorAsState(targetColorScheme.surfaceContainer, animationSpec).value,
        surfaceContainerHigh = animateColorAsState(targetColorScheme.surfaceContainerHigh, animationSpec).value,
        surfaceContainerHighest = animateColorAsState(targetColorScheme.surfaceContainerHighest, animationSpec).value,
        surfaceContainerLow = animateColorAsState(targetColorScheme.surfaceContainerLow, animationSpec).value,
        surfaceContainerLowest = animateColorAsState(targetColorScheme.surfaceContainerLowest, animationSpec).value,
        error = animateColorAsState(targetColorScheme.error, animationSpec).value,
        onError = animateColorAsState(targetColorScheme.onError, animationSpec).value,
        errorContainer = animateColorAsState(targetColorScheme.errorContainer, animationSpec).value,
        onErrorContainer = animateColorAsState(targetColorScheme.onErrorContainer, animationSpec).value,
        outline = animateColorAsState(targetColorScheme.outline, animationSpec).value,
        outlineVariant = animateColorAsState(targetColorScheme.outlineVariant, animationSpec).value,
        scrim = animateColorAsState(targetColorScheme.scrim, animationSpec).value,
    )
}
