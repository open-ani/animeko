/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
    val predictivePopEnterTransition: EnterTransition,
    val predictivePopExitTransition: ExitTransition,
    val predictiveSharedContainer: PredictiveBackSharedTransitionMotionScheme
) {
    companion object {
        inline val current
            @Composable get() = LocalNavigationMotionScheme.current

        // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#e5b958f0-435d-4e84-aed4-8d1ea395fa5c
        private const val enterDuration = EasingDurations.emphasizedDecelerate
        private const val exitDuration = EasingDurations.emphasizedAccelerate

        const val PredictiveTotalDurationMillis = EasingDurations.emphasizedDecelerate

        // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#26a169fb-caf3-445e-8267-4f1254e3e8bb
        // https://developer.android.com/develop/ui/compose/animation/shared-elements
        private val enterEasing = EmphasizedDecelerateEasing
        private val exitEasing = EmphasizedAccelerateEasing

        /**
         * @param useSlide 是否使用水平滑动. 窄屏才滑动, 宽屏只淡入淡出.
         */
        fun calculate(
            useSlide: Boolean,
            usePredictiveBackMotion: Boolean,
        ): NavigationMotionScheme {
            val slideInMargin = 1f / 16
            val slideOutMargin = 1f / 16

            val predictiveFadeOutDuration = PredictiveBackMotion.FadeOutDurationMillis
            val predictiveFadeInDuration = PredictiveTotalDurationMillis - predictiveFadeOutDuration
            val sharedContainerPredictiveFadeOutDuration = PredictiveBackMotion.SharedContainerFadeOutDurationMillis
            val sharedContainerPredictiveFadeInDuration =
                PredictiveTotalDurationMillis - sharedContainerPredictiveFadeOutDuration

            val fadeIn = fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing))
            val fadeOut = fadeOut(tween(exitDuration, easing = exitEasing))

            val enterTransition = if (useSlide) {
                val delay = exitDuration
                val slideIn = slideInHorizontally(
                    tween(enterDuration, delayMillis = delay, easing = enterEasing),
                    initialOffsetX = { (it * slideInMargin).roundToInt() },
                )
                slideIn.plus(fadeIn)
            } else {
                fadeIn
            }

            val exitTransition = if (useSlide) {
                slideOutHorizontally(
                    tween(exitDuration, easing = exitEasing),
                    targetOffsetX = { -(it * slideOutMargin).roundToInt() },
                ).plus(fadeOut)
            } else {
                fadeOut
            }

            val popEnterTransition = if (useSlide) {
                slideInHorizontally(
                    tween(enterDuration, delayMillis = exitDuration, easing = enterEasing),
                    initialOffsetX = { -(it * slideInMargin).roundToInt() },
                ) + fadeIn
            } else {
                fadeIn // clean fade
            }

            val popExitTransition = if (useSlide) {
                val slide = slideOutHorizontally(
                    tween(exitDuration, easing = exitEasing),
                    targetOffsetX = { (it * slideOutMargin).roundToInt() },
                )
                slide.plus(fadeOut)
            } else {
                fadeOut
            }

            val predictivePopEnterTransition = scaleIn(
                tween(PredictiveTotalDurationMillis, easing = PredictiveBackMotion.ProgressEasing),
                initialScale = PredictiveBackMotion.EnterScale,
            ) + fadeIn(
                // alpha 从 35% 处才开始
                tween(
                    predictiveFadeInDuration,
                    delayMillis = predictiveFadeOutDuration,
                    easing = StandardDecelerateEasing,
                ),
            )

            val predictivePopExitTransition = scaleOut(
                tween(PredictiveTotalDurationMillis, easing = PredictiveBackMotion.ProgressEasing),
                targetScale = PredictiveBackMotion.ExitScale,
            ) + fadeOut(
                // alpha 在 35% 处结束
                tween(predictiveFadeOutDuration, easing = StandardAccelerateEasing),
            )

            val sharedContainerMotion = if (usePredictiveBackMotion) {
                PredictiveBackSharedTransitionMotionScheme(
                    enterTransition = EnterTransition.None, // 由 containerEnterTransition 决定
                    exitTransition = scaleOut(
                        tween(PredictiveTotalDurationMillis, easing = PredictiveBackMotion.ProgressEasing),
                        targetScale = PredictiveBackMotion.ExitScale,
                    ),
                    popEnterTransition = scaleIn(
                        tween(PredictiveTotalDurationMillis, easing = PredictiveBackMotion.ProgressEasing),
                        initialScale = PredictiveBackMotion.ExitScale,
                    ),
                    popExitTransition = ExitTransition.None, // 由 containerPopExitTransition 决定
                    containerEnterTransition = fadeIn(
                        tween(
                            sharedContainerPredictiveFadeInDuration,
                            delayMillis = sharedContainerPredictiveFadeOutDuration,
                            easing = StandardDecelerateEasing,
                        ),
                    ),
                    containerExitTransition = fadeOut(
                        tween(sharedContainerPredictiveFadeOutDuration, easing = StandardAccelerateEasing),
                    ),
                    containerPopEnterTransition = fadeIn(
                        tween(
                            predictiveFadeInDuration,
                            delayMillis = predictiveFadeOutDuration,
                            easing = StandardDecelerateEasing,
                        ),
                    ),
                    containerPopExitTransition = fadeOut(
                        tween(sharedContainerPredictiveFadeInDuration, easing = StandardAccelerateEasing),
                    ),
                )
            } else {
                PredictiveBackSharedTransitionMotionScheme(
                    enterTransition = enterTransition,
                    exitTransition = exitTransition,
                    popEnterTransition = popEnterTransition,
                    popExitTransition = popExitTransition,
                    containerEnterTransition = EnterTransition.None,
                    containerExitTransition = ExitTransition.None,
                    containerPopEnterTransition = EnterTransition.None,
                    containerPopExitTransition = ExitTransition.None,
                )
            }

            return NavigationMotionScheme(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
                predictivePopEnterTransition = if (usePredictiveBackMotion) predictivePopEnterTransition else popEnterTransition,
                predictivePopExitTransition = if (usePredictiveBackMotion) predictivePopExitTransition else popExitTransition,
                predictiveSharedContainer = sharedContainerMotion,
            )
        }
    }
}

@Stable
@Immutable
class PredictiveBackSharedTransitionMotionScheme(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val popEnterTransition: EnterTransition,
    val popExitTransition: ExitTransition,
    val containerEnterTransition: EnterTransition,
    val containerExitTransition: ExitTransition,
    val containerPopEnterTransition: EnterTransition,
    val containerPopExitTransition: ExitTransition,
)

@Stable
val LocalNavigationMotionScheme = staticCompositionLocalOf<NavigationMotionScheme> {
    error("No LocalNavigationMotionScheme provided")
}
