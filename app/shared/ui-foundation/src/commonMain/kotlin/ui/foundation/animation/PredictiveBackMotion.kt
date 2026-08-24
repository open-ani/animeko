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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import kotlin.math.roundToInt

/**
 * Google 官方 predictive back 设计指南给出的动效参数.
 *
 * https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back
 *
 * 只在支持 predictive back 手势的平台 (Android 13+ 与 iOS) 上使用, 见 [isPlatformSupportPredictiveBack].
 * 其他平台 (Desktop, Android 13 以下) 继续使用 [NavigationMotionScheme] 里旧的滑动 + 淡入淡出动画.
 *
 * 注意只有**页面退出**动画用这里的参数, 页面进入动画仍然沿用 [NavigationMotionScheme] 原来的滑动 + 淡入.
 */
@Stable
object PredictiveBackMotion {
    /**
     * 全屏页面 (full screen surface) 退出时使用的插值器.
     *
     * 指南原文: *"The interpolator used ensures the screen quickly exits.
     * The parameters are (.1, .1, 0, 1) to match the interpolator used for the SystemUI animations"*.
     */
    val SurfaceEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    /**
     * 返回时旧页面缩小到的比例. 指南 "Exit Scale: 100% -> 90%".
     */
    const val ExitScale = 0.9f

    /**
     * 前进时旧页面放大到的比例. 指南 "Enter Scale: 110% -> 100%" 的镜像.
     */
    const val EnterScale = 1.1f

    /**
     * Fade through 阈值. 指南 *"the exiting screen fully fades out and the entering screen starts to
     * fade in [...] At the 35% mark, neither screen is showing"*.
     *
     * 阈值是按**手势进度**算的, 所以退出动画的淡出时长要按整个导航动画的总时长
     * ([NavigationMotionScheme.TotalDurationMillis]) 取比例, 而不是自己定一个时长.
     */
    const val FadeThroughThreshold = 0.35f

    /**
     * 手势预览时页面距离屏幕边缘保留的最小间距. 指南 "leaves 8dp margin".
     */
    val MinEdgeGap = 8.dp

    /**
     * 旧页面淡出所占的时长, 对应 [FadeThroughThreshold] 的手势进度.
     */
    val FadeOutDurationMillis =
        (NavigationMotionScheme.TotalDurationMillis * FadeThroughThreshold).roundToInt()
}

/**
 * 按 [PredictiveBackMotion] 的参数构造全屏页面导航动画.
 *
 * 只有退出动画 (旧页面离开) 用指南的参数; 进入动画直接沿用调用方传进来的原有动画.
 *
 * @param enterTransition 前进时新页面的进入动画, 保持原样
 * @param popEnterTransition 返回时上一个页面的进入动画, 保持原样
 */
internal fun calculatePredictiveBackScreenScheme(
    density: Density,
    enterTransition: EnterTransition,
    popEnterTransition: EnterTransition,
): ScreenNavigationMotionScheme {
    // 旧页面的缩放贯穿整个手势 (立刻响应手势), 但内容在 fade through 阈值处就已经完全淡出
    fun exitTransition(targetScale: Float) = scaleOut(
        tween(NavigationMotionScheme.TotalDurationMillis, easing = PredictiveBackMotion.SurfaceEasing),
        targetScale = targetScale,
    ) + fadeOut(
        tween(PredictiveBackMotion.FadeOutDurationMillis, easing = StandardAccelerateEasing),
    )

    val popExitTransition = exitTransition(PredictiveBackMotion.ExitScale)

    return ScreenNavigationMotionScheme(
        enterTransition = enterTransition,
        // 前进: 旧页面放大离开
        exitTransition = exitTransition(PredictiveBackMotion.EnterScale),
        popEnterTransition = popEnterTransition,
        // 返回: 旧页面缩小离开
        popExitTransition = popExitTransition,
        predictivePopTransition = { swipeEdge ->
            popEnterTransition togetherWith
                    (popExitTransition + surfaceShift(swipeEdge, density))
        },
    )
}

/**
 * 手势预览时旧页面朝手势对侧的水平位移.
 *
 * 页面缩放到 [PredictiveBackMotion.ExitScale] 之后两侧各空出 `width / 20`, 把它平移到手势一侧只留
 * [PredictiveBackMotion.MinEdgeGap], 也就是指南给出的 `((screen width / 20) - 8) dp`.
 * 从左边缘滑动时页面右移, 从右边缘滑动时页面左移 (与 Material Components 的实现一致).
 */
private fun surfaceShift(
    @NavigationEvent.SwipeEdge swipeEdge: Int,
    density: Density,
): ExitTransition {
    val direction = when (swipeEdge) {
        NavigationEvent.EDGE_LEFT -> 1
        NavigationEvent.EDGE_RIGHT -> -1
        else -> return ExitTransition.None // 非手势触发的返回不做位移
    }
    val minEdgeGapPx = with(density) { PredictiveBackMotion.MinEdgeGap.toPx() }
    return slideOutHorizontally(
        tween(NavigationMotionScheme.TotalDurationMillis, easing = PredictiveBackMotion.SurfaceEasing),
    ) { fullWidth ->
        val maxShift = (fullWidth * (1f - PredictiveBackMotion.ExitScale) / 2f - minEdgeGapPx)
            .coerceAtLeast(0f)
        (direction * maxShift).roundToInt()
    }
}
