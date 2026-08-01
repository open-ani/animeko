/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextLayoutResult
import org.jetbrains.skia.Surface

internal actual fun createDanmakuImageBitmap(
    textLayout: TextLayoutResult,
    strokeColor: Color,
    strokeWidth: Float,
    shadow: Shadow?,
): ImageBitmapWithOffset {
    // We must ensure the size is at least 1x1, otherwise there may be an exception, see #1838.
    val width = textLayout.size.width.coerceAtLeast(1)
    val height = textLayout.size.height.coerceAtLeast(1)
    val margin = computeDanmakuBitmapMargin(strokeWidth, shadow)
    val marginFloat = margin.toFloat()

    val bitmapWidth = width + margin * 2
    val bitmapHeight = height + margin * 2

    val destSurface = Surface.makeRasterN32Premul(bitmapWidth, bitmapHeight)
    val destCanvas = destSurface.canvas.asComposeCanvas()

    destCanvas.paintDanmaku(
        textLayout, strokeColor, strokeWidth, shadow, marginFloat,
        Size(bitmapWidth.toFloat(), bitmapHeight.toFloat()),
    )

    return ImageBitmapWithOffset(
        destSurface.makeImageSnapshot().toComposeImageBitmap().apply { prepareToDraw() },
        Offset(-marginFloat, -marginFloat),
    )
}
