/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Transient

/**
 * Configuration for the presentation of each `me.him188.ani.danmaku.api.Danmaku`.
 */
@Immutable
data class DanmakuConfig(
    // 备注: 增加新的属性后还要修改 [DanmakuConfigData]
    /**
     * Controls the text styles of the [Danmaku].
     * For example, font size, stroke width.
     */
    val style: DanmakuStyle = DanmakuStyle.Default,
    /**
     * Time for the [Danmaku] to move from the right edge to the left edge of the screen.
     * In other words, it controls the movement speed of a [Danmaku].
     *
     * Unit: dp/s
     */
    val speed: Float = 88f,
    /**
     * The minimum distance between two [Danmaku]s so that they don't overlap.
     */
    val safeSeparation: Dp = 36.dp,
    /**
     * 弹幕在屏幕中的显示区域. 0.1 表示屏幕的 10%.
     *
     * 范围: `[0, 1]`
     */
    val displayArea: Float = 0.25f,
    /**
     * 允许彩色弹幕. 禁用时将会把所有彩色弹幕都显示为白色.
     */
    val enableColor: Boolean = true,
    /**
     * @since 3.1.0-beta04
     */
    val enableTop: Boolean = true,
    /**
     * @since 3.1.0-beta04
     */
    val enableFloating: Boolean = true,
    /**
     * @since 3.1.0-beta04
     * 4.4.0 改为默认关闭
     */
    val enableBottom: Boolean = false,
    /**
     * 调试模式, 启用发送弹幕的信息和弹幕处理信息.
     */
    val isDebug: Boolean = false,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @Stable
        val Default = DanmakuConfig()
    }
}

@Immutable
class DanmakuStyle(
    val fontSize: TextUnit = 18.sp,
    val fontWeight: FontWeight = FontWeight.W600,
    val alpha: Float = 0.8f,
    val strokeColor: Color = Color.Black,
    val strokeWidth: Float = 4f,
    val shadow: Shadow? = null,
) {
    @Stable
    fun styleForBorder(): TextStyle = TextStyle(
        fontSize = fontSize,
        color = strokeColor,
        fontWeight = fontWeight,
        drawStyle = Stroke(
            miter = 3f,
            width = strokeWidth,
            join = StrokeJoin.Round,
        ),
        textMotion = TextMotion.Animated,
        shadow = shadow,
    )

    // 'inside' the border
    @Stable
    fun styleForText(color: Color = Color.White): TextStyle = TextStyle(
        fontSize = fontSize,
        color = color,
        fontWeight = fontWeight,
        textMotion = TextMotion.Animated,
    )

    fun copy(
        fontSize: TextUnit = this.fontSize,
        fontWeight: FontWeight = this.fontWeight,
        alpha: Float = this.alpha,
        strokeColor: Color = this.strokeColor,
        strokeWidth: Float = this.strokeWidth,
        shadow: Shadow? = this.shadow,
    ): DanmakuStyle {
        if (fontSize == this.fontSize &&
            fontWeight == this.fontWeight &&
            alpha == this.alpha &&
            strokeColor == this.strokeColor &&
            strokeWidth == this.strokeWidth &&
            shadow == this.shadow
        ) {
            return this
        }
        return DanmakuStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            alpha = alpha,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            shadow = shadow,
        )
    }

    override fun toString(): String {
        return "DanmakuStyle(fontSize=$fontSize, fontWeight=$fontWeight, alpha=$alpha, strokeColor=$strokeColor, strokeMiter=$strokeWidth, shadow=$shadow)"
    }

    companion object {
        @Stable
        val Default = DanmakuStyle()
    }
}