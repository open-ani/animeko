/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentFormattingToolbarPositionProviderTest {
    private val window = IntSize(1000, 2000)
    private val popup = IntSize(300, 50)
    private val anchor = IntRect(left = 100, top = 500, right = 900, bottom = 900)

    private fun position(
        selection: Rect,
        preferAbove: Boolean,
        anchor: IntRect = this.anchor,
        window: IntSize = this.window,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): IntOffset = CommentFormattingToolbarPositionProvider(
        selectionBounds = selection,
        preferAbove = preferAbove,
        spacing = 10,
        belowClearance = if (preferAbove) 0 else 30,
    ).calculatePosition(anchor, window, layoutDirection, popup)

    // 选区在窗口中为 x 300..500, y 600..630
    private val selection = Rect(left = 200f, top = 100f, right = 400f, bottom = 130f)

    @Test
    fun `pointer platforms place the toolbar above the selection`() {
        assertEquals(IntOffset(400 - 150, 600 - 10 - 50), position(selection, preferAbove = true))
    }

    @Test
    fun `pointer platforms flip below when there is no room above`() {
        val nearTop = IntRect(left = 100, top = 0, right = 900, bottom = 400)
        val selection = Rect(left = 200f, top = 20f, right = 400f, bottom = 50f)
        assertEquals(IntOffset(250, 50 + 10), position(selection, preferAbove = true, anchor = nearTop))
    }

    @Test
    fun `touch platforms place the toolbar below the selection handles`() {
        assertEquals(IntOffset(250, 630 + 10 + 30), position(selection, preferAbove = false))
    }

    @Test
    fun `touch platforms flip above when there is no room below`() {
        val nearBottom = IntRect(left = 100, top = 1800, right = 900, bottom = 2000)
        val selection = Rect(left = 200f, top = 150f, right = 400f, bottom = 180f)
        assertEquals(IntOffset(250, 1950 - 10 - 50), position(selection, preferAbove = false, anchor = nearBottom))
    }

    // 选区在锚点的最左和最右端
    private val atStart = Rect(left = 0f, top = 100f, right = 20f, bottom = 130f)
    private val atEnd = Rect(left = 780f, top = 100f, right = 800f, bottom = 130f)

    @Test
    fun `toolbar is clamped horizontally inside the anchor`() {
        assertEquals(anchor.left, position(atStart, preferAbove = true).x)
        assertEquals(anchor.right - popup.width, position(atEnd, preferAbove = true).x)
    }

    @Test
    fun `toolbar is clamped inside the anchor when the window is unbounded`() {
        // Android 的评论面板窗口不限制布局范围, Popup 得到的窗口大小远大于屏幕
        val unbounded = IntSize(100_000, 100_000)
        assertEquals(anchor.left, position(atStart, preferAbove = false, window = unbounded).x)
        assertEquals(anchor.right - popup.width, position(atEnd, preferAbove = false, window = unbounded).x)
    }

    @Test
    fun `toolbar wider than the anchor is centered on the anchor`() {
        val narrow = IntRect(left = 400, top = 500, right = 600, bottom = 900)
        val selection = Rect(left = 0f, top = 100f, right = 20f, bottom = 130f)
        assertEquals(400 - (300 - 200) / 2, position(selection, preferAbove = true, anchor = narrow).x)
    }

    @Test
    fun `toolbar is clamped horizontally inside the window`() {
        val wide = IntRect(left = -100, top = 500, right = 1100, bottom = 900)
        val atStart = Rect(left = 0f, top = 100f, right = 20f, bottom = 130f)
        assertEquals(0, position(atStart, preferAbove = true, anchor = wide).x)
        val atEnd = Rect(left = 1180f, top = 100f, right = 1200f, bottom = 130f)
        assertEquals(window.width - popup.width, position(atEnd, preferAbove = true, anchor = wide).x)
    }

    @Test
    fun `toolbar is clamped vertically when neither side fits`() {
        val tinyWindow = IntSize(1000, 60)
        val anchor = IntRect(0, 0, 1000, 60)
        val selection = Rect(left = 0f, top = 10f, right = 100f, bottom = 40f)
        // 两侧都放不下时仍按偏好的一侧摆放, 再限制在窗口内
        assertEquals(0, position(selection, preferAbove = true, anchor = anchor, window = tinyWindow).y)
        assertEquals(
            tinyWindow.height - popup.height,
            position(selection, preferAbove = false, anchor = anchor, window = tinyWindow).y,
        )
    }

    @Test
    fun `position is centered on the selection in both layout directions`() {
        assertEquals(
            position(selection, preferAbove = true, layoutDirection = LayoutDirection.Ltr),
            position(selection, preferAbove = true, layoutDirection = LayoutDirection.Rtl),
        )
    }
}
