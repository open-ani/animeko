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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.format2f
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * DanmakuState holds all params which [Canvas] needs to draw a danmaku text.
 */
@Immutable
data class StyledDanmaku(
    val presentation: DanmakuPresentation,
    internal val measurer: TextMeasurer,
    internal val baseStyle: TextStyle,
    internal val style: DanmakuStyle,
    internal val enableColor: Boolean,
    internal val isDebug: Boolean
) : SizeSpecifiedDanmaku {
    private val danmakuText = presentation.danmaku.run {
        val seconds = playTimeMillis.toFloat().div(1000)
        if (isDebug) "$text (${floor((seconds / 60)).toInt()}:${String.format2f(seconds % 60)})" else text
    }

    // 描边和填充的排版结果完全一致, 只测量一次, 光栅化时用 drawStyle 覆盖画两遍.
    private val textLayout = measurer.measure(
        text = danmakuText,
        style = baseStyle.merge(
            style.styleForText(
                color = if (enableColor) {
                    Color(0xFF_00_00_00L or presentation.danmaku.color.toUInt().toLong())
                } else Color.White,
            ).copy(textDecoration = if (presentation.isSelf) TextDecoration.Underline else null),
        ),
        overflow = TextOverflow.Clip,
        maxLines = 1,
        softWrap = false,
    )

    private var imageBitmap: ImageBitmapWithOffset? = null

    override val danmakuWidth: Int = textLayout.size.width.coerceAtLeast(1)
    override val danmakuHeight: Int = textLayout.size.height.coerceAtLeast(1)

    internal fun DrawScope.draw(screenPosX: Float, screenPosY: Float) {
        val cachedImage = imageBitmap
            ?: createDanmakuImageBitmap(textLayout, style.strokeColor, style.strokeWidth, style.shadow)
                .also { imageBitmap = it }

        drawImage(cachedImage.bitmap, Offset(screenPosX, screenPosY) + cachedImage.offset)
    }
}

class ImageBitmapWithOffset(
    val bitmap: ImageBitmap,
    val offset: Offset,
)

/**
 * Create image snapshot of danmaku text.
 */
internal expect fun createDanmakuImageBitmap(
    textLayout: TextLayoutResult,
    strokeColor: Color,
    strokeWidth: Float,
    shadow: Shadow?,
): ImageBitmapWithOffset

/**
 * 光栅化时留给描边和阴影的边距 (四边相同). 描边有一半宽度会画到字形轮廓外, 阴影则按模糊半径和偏移扩散.
 * [MARGIN_SLACK_PX] 用于容纳少量超出排版盒的字形 (斜体, 变音符号等).
 */
internal fun computeDanmakuBitmapMargin(strokeWidth: Float, shadow: Shadow?): Int {
    var extent = if (strokeWidth > 0f) strokeWidth / 2f else 0f
    if (shadow != null && shadow.color.alpha > 0f) {
        extent = max(extent, shadow.blurRadius + max(abs(shadow.offset.x), abs(shadow.offset.y)))
    }
    return ceil(extent).toInt().coerceAtLeast(0) + MARGIN_SLACK_PX
}

private const val MARGIN_SLACK_PX = 2

/**
 * 用同一份 [textLayout] 先画描边再画填充, 绘制到以 [margin] 为左上角偏移的位置.
 */
internal fun Canvas.paintDanmaku(
    textLayout: TextLayoutResult,
    strokeColor: Color,
    strokeWidth: Float,
    shadow: Shadow?,
    margin: Float,
    canvasSize: Size,
) {
    if (textLayout.size.width == 0 || textLayout.size.height == 0) return
    val topLeft = Offset(margin, margin)
    val canvas = this
    CanvasDrawScope().draw(
        textLayout.layoutInput.density,
        textLayout.layoutInput.layoutDirection,
        canvas,
        canvasSize,
    ) {
        if (strokeWidth > 0f) {
            drawText(
                textLayoutResult = textLayout,
                color = strokeColor,
                topLeft = topLeft,
                shadow = shadow,
                drawStyle = Stroke(width = strokeWidth, miter = 3f, join = StrokeJoin.Round),
            )
        }
        drawText(textLayoutResult = textLayout, topLeft = topLeft)
    }
}

internal fun dummyDanmaku(
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    style: DanmakuStyle,
    dummyText: String = "dummy 占位 攟 の \uD83D\uDE04"
): StyledDanmaku {
    return StyledDanmaku(
        presentation = DanmakuPresentation(
            DanmakuInfo(
                Uuid.randomString(),
                DanmakuServiceId("dummy"), "1",
                DanmakuContent(
                    0L, 0,
                    dummyText, DanmakuLocation.NORMAL,
                ),
            ),
            isSelf = false,
        ),
        measurer = measurer,
        baseStyle = baseStyle,
        style = style,
        enableColor = false,
        isDebug = false,
    )
}