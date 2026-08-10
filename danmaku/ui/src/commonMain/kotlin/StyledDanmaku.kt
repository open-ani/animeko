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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.format2f
import kotlin.math.floor

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
    private val danmakuText = danmakuDisplayText(presentation, isDebug)

    private val solidTextLayout = measurer.measure(
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

    private val borderTextLayout = if (style.strokeWidth == 0f) null else measurer.measure(
        text = danmakuText,
        style = baseStyle.merge(style.styleForBorder())
            .copy(textDecoration = if (presentation.isSelf) TextDecoration.Underline else null),
        overflow = TextOverflow.Clip,
        maxLines = 1,
        softWrap = false,
    )

    private var imageBitmap: ImageBitmapWithOffset? = null

    override val danmakuWidth: Int = solidTextLayout.size.width.coerceAtLeast(1)
    override val danmakuHeight: Int = solidTextLayout.size.height.coerceAtLeast(1)

    internal fun DrawScope.draw(screenPosX: Float, screenPosY: Float) {
        val cachedImage = imageBitmap ?: createDanmakuImageBitmap(solidTextLayout, borderTextLayout)
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
    solidTextLayout: TextLayoutResult,
    borderTextLayout: TextLayoutResult?,
): ImageBitmapWithOffset

internal fun Canvas.paintIfNotEmpty(layout: TextLayoutResult) {
    if (layout.size.width != 0 && layout.size.height != 0) {
        TextPainter.paint(this, layout)
    }
}

/**
 * 弹幕的显示文本. Debug 模式下附加发送时间.
 */
internal fun danmakuDisplayText(presentation: DanmakuPresentation, isDebug: Boolean): String =
    presentation.danmaku.run {
        val seconds = playTimeMillis.toFloat().div(1000)
        if (isDebug) "$text (${floor((seconds / 60)).toInt()}:${String.format2f(seconds % 60)})" else text
    }

/**
 * 仅测量弹幕文本宽度, 不创建 [StyledDanmaku].
 *
 * 与 [StyledDanmaku] 内部的实体文本测量使用完全相同的样式合并逻辑
 * (颜色与下划线不影响宽度), 因此结果与 [StyledDanmaku.danmakuWidth] 一致.
 */
internal fun measureDanmakuTextWidth(
    measurer: TextMeasurer,
    presentation: DanmakuPresentation,
    baseStyle: TextStyle,
    style: DanmakuStyle,
    isDebug: Boolean,
): Int {
    val layout = measurer.measure(
        text = danmakuDisplayText(presentation, isDebug),
        style = baseStyle.merge(style.styleForText(color = Color.White)),
        overflow = TextOverflow.Clip,
        maxLines = 1,
        softWrap = false,
    )
    return layout.size.width.coerceAtLeast(1)
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