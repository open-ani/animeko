/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DanmakuBitmapMarginTest {
    @Test
    fun `no stroke and no shadow - only slack`() {
        assertEquals(2, computeDanmakuBitmapMargin(strokeWidth = 0f, shadow = null))
    }

    @Test
    fun `stroke extends half its width outside the glyph`() {
        assertEquals(2 + 2, computeDanmakuBitmapMargin(strokeWidth = 4f, shadow = null))
        assertEquals(1 + 2, computeDanmakuBitmapMargin(strokeWidth = 2f, shadow = null))
        // 向上取整
        assertEquals(2 + 2, computeDanmakuBitmapMargin(strokeWidth = 3f, shadow = null))
    }

    @Test
    fun `shadow blur and offset are accounted for`() {
        val shadow = Shadow(Color.Black, Offset(3f, 5f), blurRadius = 4f)
        assertEquals(5 + 4 + 2, computeDanmakuBitmapMargin(strokeWidth = 0f, shadow = shadow))
    }

    @Test
    fun `negative shadow offset uses absolute value`() {
        val shadow = Shadow(Color.Black, Offset(-6f, 1f), blurRadius = 0f)
        assertEquals(6 + 2, computeDanmakuBitmapMargin(strokeWidth = 0f, shadow = shadow))
    }

    @Test
    fun `stroke and shadow take the larger extent`() {
        val shadow = Shadow(Color.Black, Offset(1f, 1f), blurRadius = 1f)
        // stroke/2 = 10 > shadow extent 2
        assertEquals(10 + 2, computeDanmakuBitmapMargin(strokeWidth = 20f, shadow = shadow))
    }

    @Test
    fun `fully transparent shadow is ignored`() {
        val shadow = Shadow(Color.Transparent, Offset(50f, 50f), blurRadius = 50f)
        assertEquals(2, computeDanmakuBitmapMargin(strokeWidth = 0f, shadow = shadow))
    }

    @Test
    fun `default style margin is far smaller than the old half-height margin`() {
        // 旧实现固定用 height/2, 24px 高的弹幕会有 12px 边距
        val default = computeDanmakuBitmapMargin(DanmakuStyle.Default.strokeWidth, DanmakuStyle.Default.shadow)
        assertTrue(default < 12, "expected margin < 12 but was $default")
    }
}
