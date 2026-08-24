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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Stable
import kotlin.math.roundToInt

/**
 * Google 官方 predictive back 设计指南给出的**全屏页面** (full screen surface) 动效参数.
 *
 * https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back
 *
 * | 参数 | 值 |
 * |---|---|
 * | progress easing | `cubic-bezier(0.1, 0.1, 0, 1)` |
 * | 当前页面 exit scale | `1.0 -> 0.9` |
 * | 上一个页面 enter scale | `1.1 -> 1.0` |
 * | exit alpha | `1 -> 0`, 在 35% 处结束 |
 * | enter alpha | `0 -> 1`, 从 35% 处开始 |
 * | pivot | `(0.5, 0.5)` |
 *
 * 两个缩放都由同一条 eased progress 驱动 (`lerp(1f, 0.9f, p)` / `lerp(1.1f, 1f, p)`), 只有 alpha
 * 有 35% 的 fade through 分界点.
 *
 * 只在支持 predictive back 手势的平台 (Android 13+ 与 iOS) 上使用, 见 [isPlatformSupportPredictiveBack];
 * 其他平台 (Desktop, Android 13 以下) 继续使用 [NavigationMotionScheme] 里旧的滑动 + 淡入淡出动画.
 * 而且只有**返回**方向用这里的参数, 前进方向沿用原来的动画.
 *
 * ### 不适用于 shared element / container transform
 *
 * 参与 [subjectContainerTransform] 的页面**不能**再叠加这里的全屏 0.9 缩放, 否则会和 shared bounds
 * 自己的缩放叠成"双重缩放". 那些页面用 [SharedTransitionNavTransition], 位移和缩放全部交给
 * shared element 完成.
 *
 * 另外指南里 `((width / 20) - 8) dp` 的边缘位移属于**手动实现** shared surface predictive back 的那套
 * 参数 (progress easing 是 `cubic-bezier(0, 0, 0, 1)`), 和这里的全屏参数不是一套, 不要混用.
 */
@Stable
object PredictiveBackMotion {
    /**
     * Progress 插值器. 指南给的是 `(.1, .1, 0, 1)`, 与 AOSP `BackGestureInterpolator` /
     * SystemUI 的 `PathInterpolator(0.1f, 0.1f, 0f, 1f)` 一致.
     */
    val ProgressEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    /**
     * 返回时, 当前页面缩小到的比例. 指南 "Exit Scale: 100% -> 90%".
     */
    const val ExitScale = 0.9f

    /**
     * 返回时, 上一个页面进入的起始比例. 指南 "Enter Scale: 110% -> 100%".
     */
    const val EnterScale = 1.1f

    /**
     * Fade through 分界点: 到 35% 时旧页面已经完全透明, 新页面此时才开始出现.
     *
     * 这不是手势的 commit threshold, 只是视觉转场的分界点. 它是按**手势进度**算的, 所以淡出时长要按
     * 整个导航动画的总时长 ([NavigationMotionScheme.TotalDurationMillis]) 取比例.
     */
    const val FadeThroughThreshold = 0.35f

    /**
     * 旧页面淡出所占的时长, 对应 [FadeThroughThreshold] 的手势进度.
     */
    val FadeOutDurationMillis =
        (NavigationMotionScheme.TotalDurationMillis * FadeThroughThreshold).roundToInt()
}

/**
 * 按 [PredictiveBackMotion] 的参数构造全屏页面导航动画.
 *
 * 只有返回方向用指南的参数; 前进方向直接沿用调用方传进来的原有动画.
 *
 * 手势过程中整段动画会被 `seekTo(progress)`, 所以这里的时长不是"用户必须拖这么久", 而是一条可以按
 * 手势进度 seek 的 timeline; 松手取消时 AndroidX 会按剩余 fraction 回弹.
 *
 * @param enterTransition 前进时新页面的进入动画, 保持原样
 * @param exitTransition 前进时旧页面的退出动画, 保持原样
 */
internal fun calculatePredictiveBackScreenScheme(
    enterTransition: EnterTransition,
    exitTransition: ExitTransition,
): ScreenNavigationMotionScheme {
    val totalDuration = NavigationMotionScheme.TotalDurationMillis
    val fadeOutDuration = PredictiveBackMotion.FadeOutDurationMillis
    val fadeInDuration = totalDuration - fadeOutDuration

    // 两个缩放都贯穿整个手势, 用同一条 progress easing
    val popEnterTransition = scaleIn(
        tween(totalDuration, easing = PredictiveBackMotion.ProgressEasing),
        initialScale = PredictiveBackMotion.EnterScale,
    ) + fadeIn(
        // alpha 从 35% 处才开始
        tween(fadeInDuration, delayMillis = fadeOutDuration, easing = StandardDecelerateEasing),
    )

    val popExitTransition = scaleOut(
        tween(totalDuration, easing = PredictiveBackMotion.ProgressEasing),
        targetScale = PredictiveBackMotion.ExitScale,
    ) + fadeOut(
        // alpha 在 35% 处结束
        tween(fadeOutDuration, easing = StandardAccelerateEasing),
    )

    return ScreenNavigationMotionScheme(
        // 前进方向保持原样
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        // 返回方向用指南的参数
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
    )
}
