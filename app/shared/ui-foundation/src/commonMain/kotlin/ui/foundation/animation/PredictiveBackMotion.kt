/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import me.him188.ani.app.ui.foundation.theme.EasingDurations
import kotlin.math.roundToInt

/**
 * Google 官方 predictive back 设计指南给出的动效参数.
 *
 * https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back
 *
 * 只在支持 predictive back 手势的平台 (Android 13+ 与 iOS) 上使用, 见 [isPlatformSupportPredictiveBack].
 * 其他平台 (Desktop, Android 13 以下) 继续使用 [NavigationMotionScheme] 里旧的滑动 + 淡入淡出动画.
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
     * 退出页面缩小到的比例. 指南 "Exit Scale: 100% -> 90%".
     */
    const val ExitScale = 0.9f

    /**
     * 进入页面的起始比例. 指南 "Enter Scale: 110% -> 100%".
     */
    const val EnterScale = 1.1f

    /**
     * Fade through 阈值. 指南 *"the exiting screen fully fades out and the entering screen starts to
     * fade in [...] At the 35% mark, neither screen is showing"*.
     */
    const val FadeThroughThreshold = 0.35f

    /**
     * 手势预览时页面距离屏幕边缘保留的最小间距. 指南 "leaves 8dp margin".
     */
    val MinEdgeGap = 8.dp

    /**
     * 一次完整导航动画的总时长.
     *
     * 手势过程中这个值不影响观感 (动画是被手势进度 seek 的), 它决定的是:
     * 1. 松手之后剩余进度的收尾时长 (`(1 - progress) * DurationMillis`);
     * 2. 非手势导航 (点返回按钮/前进) 的时长.
     */
    const val DurationMillis = EasingDurations.emphasized

    /**
     * 旧页面淡出所占的时长, 对应 [FadeThroughThreshold] 的手势进度.
     */
    val FadeOutDurationMillis = (DurationMillis * FadeThroughThreshold).roundToInt()

    /**
     * 新页面淡入所占的时长, 从 [FadeThroughThreshold] 开始直到手势完成.
     */
    val FadeInDurationMillis = DurationMillis - FadeOutDurationMillis
}

/**
 * 按 [PredictiveBackMotion] 的参数构造全屏页面导航动画.
 *
 * 返回动画 (pop) 直接对应指南给出的 motion spec; 前进动画 (push) 采用它的镜像 (页面放大离开 / 从小放大进入),
 * 保证同一个页面进出的方向一致.
 */
internal fun calculatePredictiveBackScreenScheme(density: Density): ScreenNavigationMotionScheme {
    val fadeOutDuration = PredictiveBackMotion.FadeOutDurationMillis
    val fadeInDuration = PredictiveBackMotion.FadeInDurationMillis

    // 新页面在 fade through 阈值之后才开始出现, 所以缩放和淡入都延迟到那时候
    fun enterTransition(initialScale: Float) = scaleIn(
        tween(fadeInDuration, delayMillis = fadeOutDuration, easing = EmphasizedDecelerateEasing),
        initialScale = initialScale,
    ) + fadeIn(
        tween(fadeInDuration, delayMillis = fadeOutDuration, easing = EmphasizedDecelerateEasing),
    )

    // 旧页面的缩放贯穿整个手势 (立刻响应手势), 但内容在阈值处就已经完全淡出
    fun exitTransition(targetScale: Float) = scaleOut(
        tween(PredictiveBackMotion.DurationMillis, easing = PredictiveBackMotion.SurfaceEasing),
        targetScale = targetScale,
    ) + fadeOut(
        tween(fadeOutDuration, easing = StandardAccelerateEasing),
    )

    val popEnterTransition = enterTransition(PredictiveBackMotion.EnterScale)
    val popExitTransition = exitTransition(PredictiveBackMotion.ExitScale)

    return ScreenNavigationMotionScheme(
        // 前进: 旧页面放大离开, 新页面从 90% 放大进入
        enterTransition = enterTransition(PredictiveBackMotion.ExitScale),
        exitTransition = exitTransition(PredictiveBackMotion.EnterScale),
        // 返回: 旧页面缩小离开, 新页面从 110% 缩小进入
        popEnterTransition = popEnterTransition,
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
        tween(PredictiveBackMotion.DurationMillis, easing = PredictiveBackMotion.SurfaceEasing),
    ) { fullWidth ->
        val maxShift = (fullWidth * (1f - PredictiveBackMotion.ExitScale) / 2f - minEdgeGapPx)
            .coerceAtLeast(0f)
        (direction * maxShift).roundToInt()
    }
}
