/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.navigationevent.NavigationEvent
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
    /**
     * 全屏页面导航 (`NavDisplay`) 使用的动画.
     *
     * 在支持 predictive back 的平台上, 页面**退出**动画换成 [PredictiveBackMotion] 的参数, 进入动画
     * 与上面的一致; 其他平台上它就是上面四个动画.
     */
    val screen: ScreenNavigationMotionScheme,
) {
    companion object {
        inline val current
            @Composable get() = LocalNavigationMotionScheme.current

        // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#e5b958f0-435d-4e84-aed4-8d1ea395fa5c
        private const val enterDuration = EasingDurations.emphasizedDecelerate
        private const val exitDuration = EasingDurations.emphasizedAccelerate

        /**
         * 一次页面导航动画的总时长: 旧页面先退出, 新页面延迟同样长的时间再进入.
         *
         * [PredictiveBackMotion] 和 [SubjectContainerTransform] 都按它取比例, 保证同一次导航里所有动画
         * 的进度是对齐的 (predictive back 手势会把整段动画 seek 到手势进度上).
         */
        const val TotalDurationMillis = exitDuration + enterDuration

        // https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration#26a169fb-caf3-445e-8267-4f1254e3e8bb
        // https://developer.android.com/develop/ui/compose/animation/shared-elements
        private val enterEasing = EmphasizedDecelerateEasing
        private val exitEasing = EmphasizedAccelerateEasing

        /**
         * @param useSlide 是否使用水平滑动. 窄屏才滑动, 宽屏只淡入淡出.
         * @param usePredictiveBack 页面导航 ([NavigationMotionScheme.screen]) 是否使用 predictive back
         * 动效参数. 见 [isPlatformSupportPredictiveBack].
         */
        fun calculate(
            useSlide: Boolean,
            usePredictiveBack: Boolean,
            density: Density,
        ): NavigationMotionScheme {
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
                screen = if (usePredictiveBack) {
                    calculatePredictiveBackScreenScheme(
                        density,
                        enterTransition = enterTransition,
                        popEnterTransition = popEnterTransition,
                    )
                } else {
                    ScreenNavigationMotionScheme(
                        enterTransition = enterTransition,
                        exitTransition = exitTransition,
                        popEnterTransition = popEnterTransition,
                        popExitTransition = popExitTransition,
                        predictivePopTransition = { popEnterTransition togetherWith popExitTransition },
                    )
                },
            )
        }
    }
}

/**
 * 全屏页面导航 (`NavDisplay`) 的动画方案.
 *
 * @see NavigationMotionScheme.screen
 */
@Stable
@Immutable
class ScreenNavigationMotionScheme(
    /**
     * 前进到新页面时, 新页面的进入动画.
     */
    val enterTransition: EnterTransition,
    /**
     * 前进到新页面时, 旧页面的退出动画.
     */
    val exitTransition: ExitTransition,
    /**
     * 返回上一个页面时, 上一个页面的进入动画.
     */
    val popEnterTransition: EnterTransition,
    /**
     * 返回上一个页面时, 当前页面的退出动画.
     */
    val popExitTransition: ExitTransition,
    /**
     * 手势驱动的返回动画. 动画会被手势进度 seek, 所以时长只影响松手之后的收尾.
     *
     * @param swipeEdge 手势从哪条边缘划入, 取值为 [NavigationEvent.EDGE_LEFT],
     * [NavigationEvent.EDGE_RIGHT] 或 [NavigationEvent.EDGE_NONE].
     */
    val predictivePopTransition: (swipeEdge: Int) -> ContentTransform,
)

@Stable
val LocalNavigationMotionScheme = staticCompositionLocalOf<NavigationMotionScheme> {
    error("No LocalNavigationMotionScheme provided")
}
