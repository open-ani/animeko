/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntOffset

/**
 * iOS 页面导航动画参数.
 *
 * ### 关于"官方规范"
 *
 * Apple **没有**像 Material 那样公开导航转场的数值规范. HIG 的 Motion 一章只有定性描述
 * (<https://developer.apple.com/design/human-interface-guidelines/motion>), 没有时长和曲线.
 * 能引用到确切数值的官方来源是 SwiftUI 的动画预设:
 *
 * - `Animation.default` (iOS 17+) = `spring(response: 0.55, dampingFraction: 1.0, blendDuration: 0.0)`;
 *   iOS 17 之前是 `easeInOut`.
 *   <https://developer.apple.com/documentation/swiftui/animation/default>
 * - `.smooth` = perceptual duration `0.5s`, bounce `0.0`;
 *   `.snappy` = `0.5s` / `0.15`; `.bouncy` = `0.5s` / `0.3`.
 *   <https://developer.apple.com/documentation/swiftui/animation/smooth(duration:extrabounce:)>
 * - WWDC23 *Animate with springs*: 拿不准就用 bounce `0` ("a great general purpose spring"),
 *   bounce 超过 `0.4` 对 UI 元素来说太夸张.
 *
 * 也就是说 iOS 现在的动效语言是**临界阻尼弹簧**, 不是 tween + 缓动曲线. 所以这里用
 * `Animation.default` 那条弹簧, 而不是自己编一条时长曲线.
 *
 * ### 关于几何
 *
 * "新页面整宽从右侧划入 / 旧页面视差左移约三分之一 / 旧页面被压暗" 是 UIKit
 * `UINavigationController` 的**实际行为**, Apple 文档里同样没有给数值. [ParallaxFraction] 的
 * `1/3` 是按实际观感取的近似值, 压暗那一层没有做 (见 [ParallaxFraction] 的说明).
 */
@Stable
object IosNavigationMotion {
    /**
     * Apple `Animation.default` 的 `response: 0.55` 换算成 Compose 的 stiffness.
     *
     * Compose 的 `SpringSimulation` 里 `naturalFreq = sqrt(stiffness)` (单位质量), 而 Apple 的
     * `response` 是无阻尼振荡周期, `ω = 2π / response`, 所以
     * `stiffness = (2π / 0.55)² ≈ 130.5`.
     */
    const val Stiffness = 130.5f

    /**
     * `dampingFraction: 1.0`, 临界阻尼, 不回弹.
     */
    const val DampingRatio = Spring.DampingRatioNoBouncy

    /**
     * 前进时旧页面 / 返回时新页面的视差位移, 占容器宽度的比例.
     *
     * UIKit 的 `UINavigationController` 会让下层页面反向移动大约三分之一屏宽, 同时盖一层压暗蒙版.
     * 压暗这里没做: `ExitTransition` 只能改 alpha, 而降低 alpha 是和背景混合, 浅色主题下会变亮而不是
     * 变暗, 方向反了. 需要的话得在页面外面单独叠一层随进度变化的黑色蒙版.
     */
    const val ParallaxFraction = 1f / 3f

    /**
     * 下层页面被完全盖住时压暗蒙版的最大不透明度.
     *
     * UIKit 那层很淡, 这里取一个接近的保守值.
     */
    const val DimMaxAlpha = 0.12f

    /**
     * 位移动画的 spec.
     */
    val SlideSpec: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = DampingRatio,
        stiffness = Stiffness,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    /**
     * 压暗蒙版的 spec. 与 [SlideSpec] 同一条弹簧, 不然蒙版和视差会脱节.
     */
    val DimSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = DampingRatio,
        stiffness = Stiffness,
    )
}
