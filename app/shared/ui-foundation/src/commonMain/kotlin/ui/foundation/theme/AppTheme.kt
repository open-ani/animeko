/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.animation.EmphasizedAccelerateEasing
import me.him188.ani.app.ui.foundation.animation.EmphasizedDecelerateEasing
import me.him188.ani.app.ui.foundation.animation.StandardAccelerate
import me.him188.ani.app.ui.foundation.animation.StandardDecelerate
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isCompact
import kotlin.math.roundToInt

@Composable
expect fun AniTheme(
    themeSettings: ThemeSettings,
    content: @Composable () -> Unit
)

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


    @Stable
    val feedItemFadeInSpec: FiniteAnimationSpec<Float> = tween(
        EasingDurations.standardAccelerate,
        delayMillis = EasingDurations.standardDecelerate,
        easing = StandardAccelerate,
    )

    @Stable
    val feedItemPlacementSpec = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    @Stable
    val feedItemFadeOutSpec: FiniteAnimationSpec<Float> =
        tween(EasingDurations.standardDecelerate, easing = StandardDecelerate)

    /**
     * 适用中小型组件.
     */
    @Stable
    val standardAnimatedContentTransition: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        // Follow M3 Clean fades
        val fadeIn = fadeIn(
            tween(
                EasingDurations.standardAccelerate,
                delayMillis = EasingDurations.standardDecelerate,
                easing = StandardAccelerate,
            ),
        )
        val fadeOut = fadeOut(feedItemFadeOutSpec)
        fadeIn.togetherWith(fadeOut)
    }

    @Stable
    val emphasizedAnimatedContentTransition: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        // Follow M3 Clean fades
        val fadeIn = fadeIn(
            animationSpec = tween(
                EasingDurations.emphasizedAccelerate,
                delayMillis = EasingDurations.emphasizedDecelerate,
                easing = EmphasizedAccelerateEasing,
            ),
        )
        val fadeOut =
            fadeOut(animationSpec = tween(EasingDurations.emphasizedDecelerate, easing = EmphasizedDecelerateEasing))
        fadeIn.togetherWith(fadeOut)
    }
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


@Stable
@Immutable
data class NavigationMotionScheme(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val popEnterTransition: EnterTransition,
    val popExitTransition: ExitTransition,
) {
    companion object {
        inline val current
            @Composable get() = LocalNavigationMotionScheme.current

        // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#e5b958f0-435d-4e84-aed4-8d1ea395fa5c
        private const val enterDuration = 500
        private const val exitDuration = 200

        // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#26a169fb-caf3-445e-8267-4f1254e3e8bb
        // https://developer.android.com/develop/ui/compose/animation/shared-elements
        private val enterEasing = EmphasizedDecelerateEasing
        private val exitEasing = LinearOutSlowInEasing

        @Composable
        fun calculate(windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass): NavigationMotionScheme {
            val useSlide = windowSizeClass.windowWidthSizeClass.isCompact

            val enterTransition: EnterTransition = run {
                if (useSlide) {
                    val slideIn = slideInHorizontally(
                        tween(
                            enterDuration,
                            easing = enterEasing,
                        ),
                        initialOffsetX = { (it * (1f / 5)).roundToInt() },
                    )
                    val fadeIn = fadeIn(tween(enterDuration, easing = enterEasing))
                    slideIn.plus(fadeIn)
                } else {
                    fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing))
                }
            }

            val exitTransition: ExitTransition = fadeOut(tween(exitDuration, easing = exitEasing))

            val popEnterTransition = run {
                if (useSlide) {
                    fadeIn(tween(enterDuration, easing = enterEasing))
                } else {
                    fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing)) // clean fade
                }
            }

            // 从页面 A 回到上一个页面 B, 切走页面 A 的动画
            val popExitTransition: ExitTransition = run {
                val fadeOut = fadeOut(tween(exitDuration, easing = exitEasing))
                if (useSlide) {
                    val slide = slideOutHorizontally(
                        tween(
                            exitDuration,
                            easing = exitEasing,
                        ),
                        targetOffsetX = { (it * (1f / 7)).roundToInt() },
                    )
                    slide.plus(fadeOut)
                } else {
                    fadeOut
                }
            }

            return NavigationMotionScheme(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            )
        }
    }
}

@Stable
val LocalNavigationMotionScheme = compositionLocalOf<NavigationMotionScheme> {
    error("No LocalNavigationMotionScheme provided")
}
