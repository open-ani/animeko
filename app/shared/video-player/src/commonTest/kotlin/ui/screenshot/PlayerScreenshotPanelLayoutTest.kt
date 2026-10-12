/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerScreenshotPanelLayoutTest {
    private fun geometry(
        playerWidth: Int,
        playerHeight: Int,
        imageWidth: Int = 1920,
        imageHeight: Int = 1080,
        insets: WindowInsets = WindowInsets(0),
        bottomOffset: Float = 0f,
        thumbnailHeight: Float = 112f,
        maxThumbnailWidth: Float = playerWidth * 0.4f,
    ) = computePlayerScreenshotPanelGeometry(
        playerSize = IntSize(playerWidth, playerHeight),
        imageSize = IntSize(imageWidth, imageHeight),
        insets = insets,
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        margin = 16f,
        bottomOffset = bottomOffset,
        thumbnailHeight = thumbnailHeight,
        maxThumbnailWidth = maxThumbnailWidth,
    )

    @Test
    fun `landscape layouts dock at bottom left and portrait at bottom right`() {
        assertEquals(PlayerScreenshotPanelCorner.BottomLeft, playerScreenshotPanelCorner(800, 450))
        assertEquals(PlayerScreenshotPanelCorner.BottomLeft, playerScreenshotPanelCorner(500, 500))
        assertEquals(PlayerScreenshotPanelCorner.BottomRight, playerScreenshotPanelCorner(450, 800))
    }

    @Test
    fun `start rect fits the image into the player`() {
        assertRect(Rect(0f, 0f, 800f, 450f), geometry(800, 450).startRect)
        // 竖屏: 宽度铺满, 垂直居中
        assertRect(Rect(0f, 273.4375f, 450f, 526.5625f), geometry(450, 800).startRect)
        // 4:3 画面在 16:9 区域中水平居中
        assertRect(Rect(100f, 0f, 700f, 450f), geometry(800, 450, imageWidth = 640, imageHeight = 480).startRect)
    }

    @Test
    fun `landscape thumbnail sits at the bottom left margin`() {
        val geometry = geometry(800, 450)
        assertEquals(PlayerScreenshotPanelCorner.BottomLeft, geometry.corner)
        assertRect(Rect(16f, 450f - 16f - 112f, 16f + 112f * 16f / 9f, 450f - 16f), geometry.thumbnailRect)
    }

    @Test
    fun `portrait thumbnail avoids insets and the bottom offset and caps its width`() {
        val geometry = geometry(
            450, 800,
            insets = WindowInsets(left = 0, top = 0, right = 10, bottom = 20),
            bottomOffset = 50f,
        )
        assertEquals(PlayerScreenshotPanelCorner.BottomRight, geometry.corner)
        // 112dp 高的 16:9 缩略图宽 199 > 450 * 0.4 = 180, 按最大宽度整体缩小
        val width = 180f
        val height = width * 9f / 16f
        val right = 450f - 10f - 16f
        val bottom = 800f - 20f - 50f - 16f
        assertRect(Rect(right - width, bottom - height, right, bottom), geometry.thumbnailRect)
    }

    @Test
    fun `empty player yields empty rects`() {
        val geometry = geometry(0, 0)
        assertEquals(Rect.Zero, geometry.startRect)
        assertEquals(Rect.Zero, geometry.thumbnailRect)
    }
}
