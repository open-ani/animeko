/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageViewerWindowBoundsTest {
    // 1920x1080 屏幕, 顶部 25 点菜单栏
    private val screen = DpRect(left = 0.dp, top = 25.dp, right = 1920.dp, bottom = 1080.dp)

    @Test
    fun `small image window matches original size and is centered`() {
        val bounds = computeImageViewerWindowBounds(IntSize(600, 500), 1f, screen)
        assertEquals(DpSize(600.dp, 500.dp), bounds.size)
        assertEquals(DpOffset(((1920 - 600) / 2f).dp, 25.dp + ((1055 - 500) / 2f).dp), bounds.position)
    }

    @Test
    fun `large image is capped to the screen fraction keeping aspect ratio`() {
        val bounds = computeImageViewerWindowBounds(IntSize(849, 1200), 1f, screen)
        val maxHeight = 1055 * 0.9f
        assertEquals(maxHeight.dp, bounds.size.height)
        val expectedWidth = 849f * (maxHeight / 1200f)
        assertEquals(expectedWidth, bounds.size.width.value, absoluteTolerance = 0.01f)
    }

    @Test
    fun `retina pixels are halved to dp`() {
        val bounds = computeImageViewerWindowBounds(IntSize(1200, 1000), 2f, screen)
        assertEquals(DpSize(600.dp, 500.dp), bounds.size)
    }

    @Test
    fun `tiny image is enlarged to the minimum window size`() {
        val bounds = computeImageViewerWindowBounds(IntSize(64, 64), 1f, screen)
        assertEquals(IMAGE_VIEWER_WINDOW_MIN_SIZE, bounds.size)
    }

    @Test
    fun `screen smaller than minimum size does not overflow the screen`() {
        val tiny = DpRect(left = 100.dp, top = 0.dp, right = 500.dp, bottom = 300.dp)
        val bounds = computeImageViewerWindowBounds(IntSize(4000, 3000), 1f, tiny)
        assertEquals(400 * 0.9f, bounds.size.width.value, absoluteTolerance = 0.01f)
        assertEquals(300 * 0.9f, bounds.size.height.value, absoluteTolerance = 0.01f)
        assertEquals(100 + (400 - 360) / 2f, bounds.position.x.value, absoluteTolerance = 0.01f)
    }

    @Test
    fun `zero sized image does not crash`() {
        val bounds = computeImageViewerWindowBounds(IntSize(0, 0), 1f, screen)
        assertEquals(IMAGE_VIEWER_WINDOW_MIN_SIZE, bounds.size)
    }
}
