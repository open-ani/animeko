/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.ceil

/** Keeps group opacity, including overlapping comments, within the occupied bitmap bounds. */
internal class DanmakuRenderer {
    private val alphaPaint = Paint()
    private var layerBounds = Rect.Zero

    fun DrawScope.draw(state: DanmakuHostState, hostWidth: Int) {
        val alpha = state.canvasAlpha.coerceIn(0f, 1f)
        if (alpha == 0f) return
        if (alpha == 1f) {
            drawComments(state, hostWidth)
            return
        }

        var bottom = 0f
        state.forEachFloatingDanmaku {
            if (!it.y.isNaN()) {
                val image = it.danmaku.cachedImage()
                bottom = maxOf(bottom, it.y + image.offset.y + image.bitmap.height)
            }
        }
        state.forEachFixedDanmaku {
            if (!it.y.isNaN()) {
                val image = it.danmaku.cachedImage()
                bottom = maxOf(bottom, it.y + image.offset.y + image.bitmap.height)
            }
        }
        bottom = ceil(bottom).coerceIn(0f, size.height)
        if (bottom == 0f) return

        // Track y coordinates are stable while comments move horizontally. Reuse the bounds
        // between occupancy changes, and include bitmap margins for strokes and shadows.
        if (layerBounds.right != size.width || layerBounds.bottom != bottom) {
            layerBounds = Rect(0f, 0f, size.width, bottom)
        }
        alphaPaint.setDanmakuLayerAlpha(alpha)
        drawIntoCanvas { canvas ->
            canvas.save()
            try {
                // Android treats saveLayer bounds as a hint. The existing clip must also
                // be narrowed before allocating the transparency layer.
                canvas.clipRect(layerBounds)
                canvas.saveLayer(layerBounds, alphaPaint)
                try {
                    drawComments(state, hostWidth)
                } finally {
                    canvas.restore()
                }
            } finally {
                canvas.restore()
            }
        }
    }

    private fun DrawScope.drawComments(state: DanmakuHostState, hostWidth: Int) {
        state.forEachFloatingDanmaku {
            if (it.y.isNaN()) return@forEachFloatingDanmaku
            with(it.danmaku) {
                draw(screenPosX = hostWidth - it.distanceX, screenPosY = it.y)
            }
        }
        state.forEachFixedDanmaku {
            if (it.y.isNaN()) return@forEachFixedDanmaku
            with(it.danmaku) {
                draw(screenPosX = (hostWidth - danmakuWidth) / 2f, screenPosY = it.y)
            }
        }
    }
}

/** Uses the same opacity conversion as the platform's graphics layer. */
internal expect fun Paint.setDanmakuLayerAlpha(alpha: Float)
