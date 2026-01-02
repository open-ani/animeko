/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.theme.EasingDurations
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform

/**
 * APP 统一动画方案
 *
 * @see NavigationMotionScheme
 */
@Immutable
// use object equality
class AniMotionScheme(
    /**
     * This pattern is used to navigate between top-level destinations of an app, like tapping a destination in a Navigation bar.
     *
     * Commonly used with: Navigation bar, navigation rail, and navigation drawer
     *
     * [M3 Spec](https://m3.material.io/styles/motion/transitions/transition-patterns#f852afd2-396f-49fd-a265-5f6d96680e16)
     */
    val topLevelTransition: ContentTransform,
    /**
     * @see LazyItemScope.animateItem
     */
    val feedItemFadeInSpec: FiniteAnimationSpec<Float>,
    /**
     * @see LazyItemScope.animateItem
     */
    val feedItemPlacementSpec: FiniteAnimationSpec<IntOffset>,
    /**
     * @see LazyItemScope.animateItem
     */
    val feedItemFadeOutSpec: FiniteAnimationSpec<Float>,

    /**
     * [AnimatedContent] 默认动画:
     * 1. fade out 旧内容, 同时 animate 到新内容的大小
     * 2. fade in
     *
     * @see AnimatedContent
     */
    val animatedContent: AnimatedContentMotionScheme,
    val animatedVisibility: AnimatedVisibilityMotionScheme,
    val carouselAutoAdvanceSpec: FiniteAnimationSpec<Float> = tween(
        durationMillis = 500, // 修改动画效果和时长，避免卡顿效果
        easing = LinearEasing,
    ),
) {
    companion object {
        fun calculate(density: Density): AniMotionScheme {
            val feedItemFadeOutTime = EasingDurations.standardAccelerate
            val feedItemFadeInTime = EasingDurations.standardDecelerate

            val topLevelTransition = run {
                val outTime = 50
                val inTime = 150
                fadeIn(
                    animationSpec = tween(
                        durationMillis = inTime,
                        delayMillis = outTime,
                        easing = StandardDecelerateEasing,
                    ),
                ).togetherWith(
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = outTime,
                            delayMillis = 0,
                            easing = StandardAccelerateEasing,
                        ),
                    ),
                )
            }
            val animatedVisibility = calculateAnimatedVisibilityMotionScheme(density)
            val animatedContent = calculateAnimatedContentMotionScheme(density, topLevelTransition)
            return AniMotionScheme(
                topLevelTransition = topLevelTransition,
                feedItemFadeInSpec = tween(
                    durationMillis = feedItemFadeInTime,
                    delayMillis = feedItemFadeOutTime,
                    easing = StandardDecelerateEasing,
                ),
                feedItemFadeOutSpec = kotlin.run {
                    // Workaround for CMP-7160: 
                    //   https://youtrack.jetbrains.com/issue/CMP-7160/Compose-Desktop-crash-after-update-to-1.7.0#focus=Comments-27-11539861.0-0
                    // Fixed by https://github.com/JetBrains/compose-multiplatform-core/pull/1839
                    // TODO: remove the workaround on CMP 1.8.0-beta01 or later
                    if (currentPlatform() is Platform.Desktop) {
                        snap()
                    } else {
                        tween(
                            durationMillis = feedItemFadeOutTime,
                            delayMillis = 0,
                            easing = StandardAccelerateEasing,
                        )
                    }
                },
                feedItemPlacementSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntOffset.VisibilityThreshold,
                ),
                animatedContent = animatedContent,
                animatedVisibility = animatedVisibility,
            )
        }

        private fun calculateAnimatedContentMotionScheme(
            density: Density,
            topLevelTransition: ContentTransform
        ): AnimatedContentMotionScheme {
            return AnimatedContentMotionScheme(
                standard = {
                    val outTime = EasingDurations.standardAccelerate
                    val inTime = EasingDurations.standardDecelerate

                    val fadeIn = fadeIn(
                        tween(
                            durationMillis = inTime,
                            delayMillis = outTime,
                            easing = StandardDecelerateEasing,
                        ),
                    )
                    val fadeOut = fadeOut(
                        tween(
                            durationMillis = outTime,
                            delayMillis = 0,
                            easing = StandardAccelerateEasing,
                        ),
                    )
                    fadeIn.togetherWith(fadeOut).using(
                        SizeTransform(clip = true),
                    )
                },
                topLevel = {
                    topLevelTransition
                },
                screenEnter = {
                    fadeIn(
                        tween(
                            EasingDurations.emphasizedDecelerate,
                            delayMillis = EasingDurations.emphasizedAccelerate,
                            easing = EmphasizedDecelerateEasing,
                        ),
                    ) + slideInVertically(
                        tween(EasingDurations.emphasizedDecelerate),
                        initialOffsetY = { with(density) { 32.dp.toPx() }.coerceAtMost(it.toFloat()).toInt() },
                    ) togetherWith fadeOut(
                        tween(
                            EasingDurations.emphasizedAccelerate,
                            delayMillis = 0,
                            easing = EmphasizedAccelerateEasing,
                        ),
                    )
                },
            )
        }

        private fun calculateAnimatedVisibilityMotionScheme(density: Density): AnimatedVisibilityMotionScheme {
            val outTime = EasingDurations.standardAccelerate
            val inTime = EasingDurations.standardDecelerate
            fun <T> enterTween() = tween<T>(
                durationMillis = inTime,
                delayMillis = 0,
                easing = StandardDecelerateEasing,
            )

            fun <T> exitTween() = tween<T>(
                durationMillis = outTime,
                delayMillis = 0,
                easing = StandardAccelerateEasing,
            )

            // For normal, use fade in/out.
            // For Row/Column, use expand and shrink without fade.
            val expandShrinkSpring = spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntSize.VisibilityThreshold,
            )
            return AnimatedVisibilityMotionScheme(
                standardEnter = fadeIn(enterTween()),
                standardExit = fadeOut(exitTween()),
                rowEnter = expandHorizontally(expandShrinkSpring, expandFrom = Alignment.Start),
                rowExit = shrinkHorizontally(expandShrinkSpring, shrinkTowards = Alignment.Start),
                columnEnter = expandVertically(expandShrinkSpring, expandFrom = Alignment.Top),
                columnExit = shrinkVertically(expandShrinkSpring, shrinkTowards = Alignment.Top),
                screenEnter = fadeIn(
                    tween(
                        EasingDurations.emphasized,
                        delayMillis = 0,
                        easing = EmphasizedEasing,
                    ),
                ) + slideInVertically(
                    tween(EasingDurations.emphasized),
                    initialOffsetY = { with(density) { 32.dp.toPx() }.coerceAtMost(it.toFloat()).toInt() },
                ),
                screenExit = fadeOut(snap()),
            )
        }
    }
}

@Immutable
class AnimatedContentMotionScheme(
    /**
     * [StandardAccelerateEasing] fade out, then [StandardDecelerateEasing] fade in. **同时** animate 到新内容的大小 (spring).
     * Total duration = 450ms.
     *
     * 适合一个页面里的小组件.
     */
    val standard: AnimatedContentTransitionScope<*>.() -> ContentTransform,
    /**
     * [Top-level transition][AniMotionScheme.topLevelTransition] without size transform.
     *
     * 适合 navigation 内容, List-Detail pane.
     * @see AniMotionScheme.topLevelTransition
     */
    val topLevel: AnimatedContentTransitionScope<*>.() -> ContentTransform,
    val screenEnter: AnimatedContentTransitionScope<*>.() -> ContentTransform
)

@Immutable
class AnimatedVisibilityMotionScheme(
    val standardEnter: EnterTransition,
    val standardExit: ExitTransition,
    val rowEnter: EnterTransition,
    val rowExit: ExitTransition,
    val columnEnter: EnterTransition,
    val columnExit: ExitTransition,

    /**
     * 用来 animate 整个页面初始进入动画.
     *
     * 从下往上滑动, 同时 fade in.
     */
    val screenEnter: EnterTransition,
    /**
     * 用来 animate 整个页面的退出动画.
     */
    val screenExit: ExitTransition,
)

@Stable
val LocalAniMotionScheme: ProvidableCompositionLocal<AniMotionScheme> =
    staticCompositionLocalOf { error("No AniMotionScheme provided") }


@Composable
fun ProvideAniMotionCompositionLocals(
    content: @Composable () -> Unit
) {
    val density by rememberUpdatedState(LocalDensity.current)
    val windowSizeClass by rememberUpdatedState(currentWindowAdaptiveInfo1().windowSizeClass)

    val isWidthCompact by remember {
        derivedStateOf {
            windowSizeClass.isWidthCompact // reduce recompositions
        }
    }
    val navigationMotionScheme by remember {
        derivedStateOf {
            NavigationMotionScheme.calculate(useSlide = isWidthCompact)
        }
    }
    val aniMotionScheme by remember {
        derivedStateOf {
            AniMotionScheme.calculate(density)
        }
    }
    CompositionLocalProvider(
        LocalNavigationMotionScheme provides navigationMotionScheme,
        LocalAniMotionScheme provides aniMotionScheme,
        content = content,
    )
}
