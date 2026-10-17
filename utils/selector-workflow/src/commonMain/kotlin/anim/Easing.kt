/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow.anim

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Stable

/**
 * 这条时间线用到的缓动曲线, 取自 Material 3 motion spec.
 *
 * 曲线本身直接用 Compose 的 [CubicBezierEasing], 不自己算贝塞尔.
 * 与 `me.him188.ani.app.ui.foundation.animation.MaterialEasing` 是同一批 token ——
 * 那个文件在 `:app:shared:ui-foundation` 里, utils 模块不能反向依赖, 所以这里按同样的值另立一份.
 *
 * https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
 */
object Easings {
    @Stable
    val Linear: Easing = LinearEasing

    /** md.sys.motion.easing.standard. 一般的位置/尺寸变化, 也是列表滚动用的曲线. */
    @Stable
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** md.sys.motion.easing.standard.decelerate. */
    @Stable
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    /** md.sys.motion.easing.standard.accelerate. */
    @Stable
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    /** md.sys.motion.easing.emphasized.decelerate. 进场、弹一下再落定. */
    @Stable
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** md.sys.motion.easing.emphasized.accelerate. */
    @Stable
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** 遍历 cursor 在格子之间移动用的曲线: 起步快, 收尾稳. */
    @Stable
    val CursorHop: Easing = CubicBezierEasing(0.25f, 0.0f, 0.15f, 1.0f)
}
