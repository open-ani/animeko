/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max

internal actual fun createDanmakuImageBitmap(
    solidTextLayout: TextLayoutResult,
    borderTextLayout: TextLayoutResult?,
): ImageBitmapWithOffset {
    val width = max(borderTextLayout?.size?.width ?: 0, solidTextLayout.size.width).coerceAtLeast(1)
    val height = max(borderTextLayout?.size?.height ?: 0, solidTextLayout.size.height).coerceAtLeast(1)
    val extraMargin = height shr 1
    val extraMarginFloat = extraMargin.toFloat()

    val imageBitmap = ImageBitmap(width + extraMargin * 2, height + extraMargin * 2)
    val canvas = Canvas(imageBitmap)
    canvas.translate(extraMarginFloat, extraMarginFloat)
    borderTextLayout?.let { canvas.paintIfNotEmpty(it) }
    canvas.paintIfNotEmpty(solidTextLayout)

    return ImageBitmapWithOffset(imageBitmap.apply { prepareToDraw() }, Offset(-extraMarginFloat, -extraMarginFloat))
}
