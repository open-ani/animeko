/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.semantics

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * 决定模糊、遮罩、光晕与持续动画的视觉状态。
 *
 * 这些效果无法从布局或无障碍语义推断；组件把驱动效果的状态值写入语义树，UI 测试断言状态值而不比较像素。
 * 自定义语义属性不会传给无障碍服务。
 *
 * 只暴露随过渡变化的状态，不暴露逐帧变化的值，避免语义树每帧失效。
 */
object TvVisualSemantics {
    /** 过渡进度：0 为初始状态，1 为完成状态。 */
    val Progress = SemanticsPropertyKey<Float>("TvVisualProgress")

    /** 节点内容的不透明度。 */
    val Alpha = SemanticsPropertyKey<Float>("TvVisualAlpha")

    /** 节点是否模糊其后方的内容。 */
    val BackdropBlur = SemanticsPropertyKey<Boolean>("TvVisualBackdropBlur")

    /** 持续动画是否由帧时钟推进。 */
    val Animating = SemanticsPropertyKey<Boolean>("TvVisualAnimating")
}

var SemanticsPropertyReceiver.tvVisualProgress by TvVisualSemantics.Progress
var SemanticsPropertyReceiver.tvVisualAlpha by TvVisualSemantics.Alpha
var SemanticsPropertyReceiver.tvBackdropBlur by TvVisualSemantics.BackdropBlur
var SemanticsPropertyReceiver.tvAnimating by TvVisualSemantics.Animating
