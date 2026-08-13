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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
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

        /**
         * 全屏页面间的 dissolve (无位移): 只有**上层**那一页做透明度动画, 下层全程不透明.
         *
         * ## 为什么不能两页同时淡
         *
         * 直觉上"A 淡出的同时 B 淡入"是互补的, 但那是**相加**合成才成立的算法; Compose 里两页
         * 是父子叠放, 走的是 `over`:
         *
         * ```
         * 结果 = 上层·b + (1-b)·(下层·a + (1-a)·窗口底色)
         * ```
         *
         * 两个 alpha 都在 (0,1) 时, `(1-b)(1-a)` 这一份**漏的是窗口底色**(TV 上近乎全黑).
         * 中点 a=b=0.5 时四分之一的画面是底色, 于是整屏亮度塌一块, 再在动画结束、上层被移除
         * 的那一帧弹回来 —— 真机测到的就是"掉到 43% 再跳回", 观感即"闪 / 瞬变".
         *
         * 让下层保持不透明后, 式子退化成干净的两页混合 `上层·b + (1-b)·下层`, **底色一点也漏
         * 不进来**, 全程没有亮度坑, 结束那一帧上层已经是 0 (或 1), 移除不可见.
         *
         * ## 谁在上层
         *
         * navigation-compose 按后退栈序给 z 序 (`NavHost.kt` 的 `targetZIndex`): 前进时新页
         * 在上, 返回时旧页 (正在退出的那个) 在上. 因此**前进动 enter、返回动 popExit**,
         * 另一侧只用一个"时长占位"的恒定动画 —— 时长必须留着, 否则 `AnimatedContent` 会当场
         * 把下层内容移除, 上层还没淡出来就先露底.
         *
         * ## 缓动
         *
         * 用 in-out (两端慢中间快): 亮度已经不会塌, 缓动便可以只服务于"少一点重影" ——
         * 两页都是全屏海报且文字位置相近, 停在半透明区间越久, 双影越明显. in-out 快速穿过
         * 中段, 把"两页都清晰可读"的窗口压掉一半以上, 首尾又足够柔和.
         */
        fun calculateCrossfade(): NavigationMotionScheme {
            val spec = tween<Float>(CROSSFADE_DURATION, easing = DissolveEasing)
            // 时长占位: alpha 恒为 1, 只为让下层内容在整个转场期间留在组合里
            val holdSpec = tween<Float>(CROSSFADE_DURATION, easing = LinearEasing)
            return NavigationMotionScheme(
                enterTransition = fadeIn(spec), // 前进: 新页在上, 0 -> 1
                exitTransition = fadeOut(holdSpec, targetAlpha = 1f), // 前进: 旧页在下, 恒不透明
                popEnterTransition = fadeIn(holdSpec, initialAlpha = 1f), // 返回: 旧页在下, 恒不透明
                popExitTransition = fadeOut(spec), // 返回: 当前页在上, 1 -> 0
            )
        }

        /**
         * dissolve 用的对称 in-out 缓动: 两端慢、中段快.
         *
         * 目的不是"好看", 是**压缩两页都清晰可读的那段时间**. 线性时 alpha 有一半的时长落在
         * 0.25~0.75 之间 (400ms 里的 200ms), 这条曲线把同一区间压到约 100ms.
         */
        private val DissolveEasing: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

        /**
         * 取 M3 的 `emphasizedDecelerate` (400ms), 与非 crossfade 那条路的入场时长一致.
         *
         * 原值是 **1000ms**, 而 M3 最长的时长 token 也才 500ms: 全屏转场的建议区间是
         * 300ms (手机基准) ~ 390ms (大屏, 手机 +30%), 超过 400ms 就开始显得迟钝 ——
         * 用户反馈"点卡片进详情页的过渡不舒服"正是这一条.
         *
         * 它同时也是**卡顿**的来源: 全屏 crossfade 期间上下两个页面都在组合并绘制,
         * 4K UI 下每帧成本翻倍, 而这一整秒恰好压在详情页最重的首屏工作上 (backdrop
         * 取图/解码、剧照分集匹配). 缩到 400ms 把这个重叠窗口砍掉六成.
         *
         * 电视端不用官方那套 card → 详情的**共享元素** (container transform):
         * `SharedTransitionLayout` 的 scope provider 在 `AniAppContent` 里是注释掉的,
         * 全应用都没启用, 要接是另一件事 (见该处注释).
         */
        private const val CROSSFADE_DURATION = EasingDurations.emphasizedDecelerate

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
