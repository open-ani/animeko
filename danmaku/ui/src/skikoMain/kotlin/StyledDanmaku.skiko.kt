/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextLayoutResult
import org.jetbrains.skia.Surface
import kotlin.math.max

internal actual fun createDanmakuImageBitmap(
    solidTextLayout: TextLayoutResult,
    borderTextLayout: TextLayoutResult?,
): ImageBitmap {
    // We must ensure the size is at least 1x1, otherwise there may be an exception, see #1838.
    val destSurface = Surface.makeRasterN32Premul(
        max(borderTextLayout?.size?.width ?: 0, solidTextLayout.size.width).coerceAtLeast(1),
        max(borderTextLayout?.size?.height ?: 0, solidTextLayout.size.height).coerceAtLeast(1),
    )
    val destCanvas = destSurface.canvas.asComposeCanvas()

    borderTextLayout?.let { destCanvas.paintIfNotEmpty(it) }
    destCanvas.paintIfNotEmpty(solidTextLayout)

    return destSurface.makeImageSnapshot().toComposeImageBitmap().apply {
        prepareToDraw()
    }
}