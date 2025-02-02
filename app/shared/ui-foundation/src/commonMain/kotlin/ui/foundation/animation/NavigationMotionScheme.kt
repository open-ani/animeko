/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import me.him188.ani.app.ui.foundation.theme.EasingDurations
import kotlin.math.roundToInt

/**
 * @see AniMotionScheme
 */
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
        private const val enterDuration = EasingDurations.emphasizedDecelerate
        private const val exitDuration = EasingDurations.emphasizedAccelerate

        // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#26a169fb-caf3-445e-8267-4f1254e3e8bb
        // https://developer.android.com/develop/ui/compose/animation/shared-elements
        private val enterEasing = EmphasizedDecelerateEasing
        private val exitEasing = EmphasizedAccelerateEasing

        fun calculate(useSlide: Boolean): NavigationMotionScheme {
            val slideInMargin = 1f / 16
            val slideOutMargin = 1f / 16

            val enterTransition: EnterTransition = run {
                if (useSlide) {
                    val delay = exitDuration
                    val slideIn = slideInHorizontally(
                        tween(enterDuration, delayMillis = delay, easing = enterEasing),
                        initialOffsetX = { (it * slideInMargin).roundToInt() },
                    )
                    val fadeIn = fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing))
                    slideIn.plus(fadeIn)
                } else {
                    fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing))
                }
            }

            val exitTransition: ExitTransition = kotlin.run {
                val fadeOut = fadeOut(tween(exitDuration, easing = exitEasing))
                if (useSlide) {
                    slideOutHorizontally(
                        tween(exitDuration, easing = exitEasing),
                        targetOffsetX = { -(it * slideOutMargin).roundToInt() },
                    ).plus(fadeOut)
                } else {
                    fadeOut
                }
            }

            val popEnterTransition = run {
                val fadeIn = fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing))
                if (useSlide) {
                    slideInHorizontally(
                        tween(enterDuration, delayMillis = exitDuration, easing = enterEasing),
                        initialOffsetX = { -(it * slideInMargin).roundToInt() },
                    ) + fadeIn
                } else {
                    fadeIn // clean fade
                }
            }

            // 从页面 A 回到上一个页面 B, 切走页面 A 的动画
            val popExitTransition: ExitTransition = run {
                val fadeOut = fadeOut(tween(exitDuration, easing = exitEasing))
                if (useSlide) {
                    val slide = slideOutHorizontally(
                        tween(exitDuration, easing = exitEasing),
                        targetOffsetX = { (it * slideOutMargin).roundToInt() },
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
val LocalNavigationMotionScheme = staticCompositionLocalOf<NavigationMotionScheme> {
    error("No LocalNavigationMotionScheme provided")
}
