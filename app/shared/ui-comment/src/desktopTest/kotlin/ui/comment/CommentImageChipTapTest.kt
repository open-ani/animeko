/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [imageChipTapCursor]: 点击图片标签时光标放在点击的那一行上较近的一端.
 */
class CommentImageChipTapTest {
    /**
     * 文本 [text] 显示为 [display] 中的标签后的文字布局 [layout].
     */
    private class Measured(
        val text: String,
        val display: ImageChipDisplay,
        val layout: TextLayoutResult,
    ) {
        val shown: String get() = layout.layoutInput.text.text

        fun chip(index: Int): CommentImageChip = display.chips[index]
        fun visual(index: Int): TextRange = display.visualRanges[index]

        /**
         * 第 [index] 个标签可见的字符在每一行占据的范围.
         */
        fun visibleBounds(index: Int): List<Rect> =
            layout.lineBoundsOf(visual(index).start, visual(index).end) { !shown[it].isImageChipHiddenChar() }

        fun lineCenterY(line: Int): Float = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2

        /**
         * 第 [index] 个标签可见的部分所在的行.
         */
        fun chipLine(index: Int): Int = layout.getLineForOffset(visual(index).start + 1)

        /**
         * 第 [index] 个标签是一行最后的内容, 紧跟其后的文字在下一行.
         */
        fun chipEndsLine(index: Int): Boolean {
            val visual = visual(index)
            return layout.getLineForOffset(visual.end) == layout.getLineForOffset(visual.end - 1) + 1 &&
                    layout.getLineForOffset(visual.start + 1) == layout.getLineForOffset(visual.end - 1)
        }

        /**
         * 第 [index] 个标签在一行的开头, 开头的 ZERO WIDTH SPACE 留在上一行末尾.
         */
        fun chipStartsLine(index: Int): Boolean {
            val visual = visual(index)
            return layout.getLineForOffset(visual.start + 1) == layout.getLineForOffset(visual.start) + 1
        }

        fun tap(position: Offset, proposedOffset: Int = layout.getOffsetForPosition(position)): Int? =
            imageChipTapCursor(layout, position, display, proposedOffset)?.cursor
    }

    private fun ComposeUiTest.measurer(): TextMeasurer {
        lateinit var measurer: TextMeasurer
        setContent { measurer = rememberTextMeasurer() }
        waitForIdle()
        return measurer
    }

    private fun TextMeasurer.measureChips(
        text: String,
        width: Int = Width,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): Measured {
        val display = buildImageChipDisplay(text, ImageChipRegistry()) { "图片 $it" }
        val shown = buildString {
            var cursor = 0
            for ((index, chip) in display.chips.withIndex()) {
                append(text, cursor, chip.range.start)
                append(display.labels[index])
                cursor = chip.range.end
            }
            append(text, cursor, text.length)
        }
        val layout = measure(
            shown,
            Style,
            constraints = Constraints(maxWidth = width),
            layoutDirection = layoutDirection,
        )
        return Measured(text, display, layout)
    }

    /**
     * 依次尝试由不同个数的 [unit] 组成的前缀, 返回第一个满足 [matches] 的布局.
     */
    private fun TextMeasurer.findLayout(
        text: (prefix: String) -> String,
        unit: String = "一",
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        matches: (Measured) -> Boolean,
    ): Measured {
        for (length in 1..80) {
            val measured = measureChips(text(unit.repeat(length)), layoutDirection = layoutDirection)
            if (matches(measured)) return measured
        }
        error("No prefix produces the expected line breaks")
    }

    @Test
    fun `tap beside a chip that ends a wrapped line places the cursor after it`() = runAniComposeUiTest {
        val m = measurer().findLayout({ "$it${image(1)}中文测试" }) { it.chipEndsLine(0) }
        val chip = m.chip(0)
        val visual = m.visual(0)
        val line = m.chipLine(0)
        val bounds = m.visibleBounds(0).single()
        val y = m.lineCenterY(line)
        // 标签终点在自动换行处, 它的光标矩形在下一行的行首. 按距离光标矩形的远近会选中标签起点
        assertEquals(line + 1, m.layout.getLineForVerticalPosition(m.layout.getCursorRect(visual.end).center.y))

        // 点击这一行右侧的空白: Android 取这一行最后一个字符之前的位置, 落在标签内部
        val end = Offset(Width.toFloat(), y)
        assertEquals(chip.range.end, m.tap(end, proposedOffset = m.layout.getLineEnd(line) - 1))
        assertEquals(chip.range.end, m.tap(end, proposedOffset = visual.start + 1))
        // 点击标签的右半边和左半边
        assertEquals(chip.range.end, m.tap(Offset(bounds.right - 1f, y)))
        assertEquals(chip.range.end, m.tap(Offset(bounds.center.x + 1f, y)))
        assertEquals(chip.range.start, m.tap(Offset(bounds.center.x - 1f, y)))
        assertEquals(chip.range.start, m.tap(Offset(bounds.left + 1f, y)))
        // 点击下一行时标签在这一行之前, 放在标签之后
        assertEquals(chip.range.end, m.tap(Offset(0f, m.lineCenterY(line + 1)), proposedOffset = visual.end - 1))
    }

    @Test
    fun `tap at the start of a line that begins with a chip places the cursor before it`() = runAniComposeUiTest {
        val m = measurer().findLayout({ "$it${image(1)}中文测试" }) { it.chipStartsLine(0) }
        val chip = m.chip(0)
        val visual = m.visual(0)
        val line = m.chipLine(0)
        val bounds = m.visibleBounds(0).single()
        val y = m.lineCenterY(line)
        // 标签起点 (ZERO WIDTH SPACE 之前) 在上一行, 它的光标矩形在上一行末尾. 按距离光标矩形的远近会选中标签终点
        assertEquals(line - 1, m.layout.getLineForVerticalPosition(m.layout.getCursorRect(visual.start).center.y))

        // 点击这一行的左边距, 位置移到文字区域的左边缘
        assertEquals(chip.range.start, m.tap(Offset(0f, y)))
        assertEquals(chip.range.start, m.tap(Offset(0f, y), proposedOffset = m.layout.getLineStart(line)))
        assertEquals(chip.range.start, m.tap(Offset(bounds.center.x - 1f, y)))
        assertEquals(chip.range.end, m.tap(Offset(bounds.center.x + 1f, y)))
        assertEquals(chip.range.end, m.tap(Offset(bounds.right - 1f, y)))

        // 点击上一行右侧的空白: 取到标签开头的 ZERO WIDTH SPACE 之前, 不在标签内部, 由 Compose 处理
        val previousLineEnd = Offset(Width.toFloat(), m.lineCenterY(line - 1))
        assertNull(m.tap(previousLineEnd, proposedOffset = visual.start))
        // 取到标签内部时, 上一行在标签之前, 放在标签之前
        assertEquals(chip.range.start, m.tap(previousLineEnd, proposedOffset = visual.start + 1))
    }

    @Test
    fun `tap between a chip ending a line and a chip starting the next line`() = runAniComposeUiTest {
        // 第一个标签在一行的末尾, 第二个标签在下一行的开头
        val m = measurer().findLayout({ "$it${image(1)}${image(2)}中文测试" }) {
            it.chipStartsLine(1) && it.chipLine(0) + 1 == it.chipLine(1)
        }
        val first = m.chip(0)
        val second = m.chip(1)
        val line = m.chipLine(0)
        assertEquals(line + 1, m.chipLine(1))
        val firstBounds = m.visibleBounds(0).single()
        val secondBounds = m.visibleBounds(1).single()

        // 点击第一行右侧的空白: Android 取到第二个标签开头的 ZERO WIDTH SPACE 之前, 即两个标签之间, 由 Compose 处理
        val lineEnd = Offset(Width.toFloat(), m.lineCenterY(line))
        assertNull(m.tap(lineEnd, proposedOffset = m.layout.getLineEnd(line) - 1))
        assertEquals(first.range.end, m.tap(Offset(firstBounds.right - 1f, m.lineCenterY(line))))
        // 点击第二行的开头
        assertEquals(second.range.start, m.tap(Offset(0f, m.lineCenterY(line + 1))))
        assertEquals(second.range.start, m.tap(Offset(secondBounds.left + 1f, m.lineCenterY(line + 1))))
        assertEquals(second.range.end, m.tap(Offset(secondBounds.right - 1f, m.lineCenterY(line + 1))))
    }

    @Test
    fun `tap on a chip in the middle of a line places the cursor at the nearer end`() = runAniComposeUiTest {
        val m = measurer().measureChips("ab ${image(1)} cd ${image(2)} ef", width = 2000)
        for (index in 0..1) {
            val chip = m.chip(index)
            val bounds = m.visibleBounds(index).single()
            val y = bounds.center.y
            assertEquals(chip.range.start, m.tap(Offset(bounds.left + 1f, y)))
            assertEquals(chip.range.start, m.tap(Offset(bounds.center.x - 1f, y)))
            assertEquals(chip.range.end, m.tap(Offset(bounds.center.x + 1f, y)))
            assertEquals(chip.range.end, m.tap(Offset(bounds.right - 1f, y)))
        }
    }

    @Test
    fun `tap on a chip between right-to-left words places the cursor at the nearer end`() = runAniComposeUiTest {
        // 从右向左的段落中标签前后是从右向左的文字: 标签两端的不换行空格随段落方向排列, 标签起点在右侧
        val m = measurer().measureChips("مرحبا ${image(1)} مرحبا", width = 2000, layoutDirection = LayoutDirection.Rtl)
        val chip = m.chip(0)
        val visual = m.visual(0)
        val bounds = m.visibleBounds(0).single()
        val y = bounds.center.y
        assertTrue(m.layout.getCursorRect(visual.start).center.x > bounds.center.x, "chip start should be on the right")
        assertTrue(m.layout.getCursorRect(visual.end).center.x < bounds.center.x, "chip end should be on the left")

        val inside = (visual.start + visual.end) / 2
        assertEquals(chip.range.end, m.tap(Offset(bounds.left + 1f, y), proposedOffset = inside))
        assertEquals(chip.range.end, m.tap(Offset(bounds.center.x - 1f, y), proposedOffset = inside))
        assertEquals(chip.range.start, m.tap(Offset(bounds.center.x + 1f, y), proposedOffset = inside))
        assertEquals(chip.range.start, m.tap(Offset(bounds.right - 1f, y), proposedOffset = inside))
    }

    @Test
    fun `tap on a chip between left-to-right words in a right-to-left paragraph`() = runAniComposeUiTest {
        // 标签前后是中文时整行从左向右排列, 标签起点在左侧
        val m = measurer().measureChips("中文${image(1)}中文", width = 2000, layoutDirection = LayoutDirection.Rtl)
        val chip = m.chip(0)
        val visual = m.visual(0)
        val bounds = m.visibleBounds(0).single()
        val y = bounds.center.y
        assertTrue(m.layout.getCursorRect(visual.start).center.x < bounds.center.x, "chip start should be on the left")

        val inside = (visual.start + visual.end) / 2
        assertEquals(chip.range.start, m.tap(Offset(bounds.center.x - 1f, y), proposedOffset = inside))
        assertEquals(chip.range.end, m.tap(Offset(bounds.center.x + 1f, y), proposedOffset = inside))
    }

    @Test
    fun `tap at the start of a right-to-left line that begins with a chip places the cursor before it`() =
        runAniComposeUiTest {
            val m = measurer().findLayout(
                { "$it${image(1)}مرحبا" },
                unit = "ب ",
                layoutDirection = LayoutDirection.Rtl,
            ) { it.chipStartsLine(0) }
            val chip = m.chip(0)
            val visual = m.visual(0)
            val line = m.chipLine(0)
            val bounds = m.visibleBounds(0).single()
            val y = m.lineCenterY(line)
            // 标签起点的光标矩形在上一行. 这一行从右侧开始, 标签起点在右侧
            assertEquals(line - 1, m.layout.getLineForVerticalPosition(m.layout.getCursorRect(visual.start).center.y))
            assertTrue(m.layout.getCursorRect(visual.end).center.x < bounds.center.x, "chip end should be on the left")

            // 点击这一行右侧的边距, 位置移到文字区域的右边缘
            val lineStart = Offset(Width.toFloat(), y)
            assertEquals(chip.range.start, m.tap(lineStart, proposedOffset = m.layout.getLineStart(line)))
            val inside = (visual.start + visual.end) / 2
            assertEquals(chip.range.start, m.tap(Offset(bounds.right - 1f, y), proposedOffset = inside))
            assertEquals(chip.range.end, m.tap(Offset(bounds.left + 1f, y), proposedOffset = inside))
        }

    @Test
    fun `tap outside chips is left to compose`() = runAniComposeUiTest {
        val measurer = measurer()
        val m = measurer.measureChips("ab ${image(1)} cd", width = 2000)
        val visual = m.visual(0)
        val y = m.lineCenterY(0)
        // 点击普通文字
        assertNull(m.tap(m.layout.getBoundingBox(m.shown.indexOf('a')).center))
        assertNull(m.tap(m.layout.getBoundingBox(m.shown.indexOf('d')).center))
        // 位置恰好在标签的一端
        assertNull(m.tap(Offset(0f, y), proposedOffset = visual.start))
        assertNull(m.tap(Offset(0f, y), proposedOffset = visual.end))
        // 没有图片标签
        assertNull(measurer.measureChips("abc").tap(Offset(1f, 1f)))
    }

    @Test
    fun `layout out of date with the text is ignored`() = runAniComposeUiTest {
        val measurer = measurer()
        val m = measurer.measureChips("ab ${image(1)} cd", width = 2000)
        val bounds = m.visibleBounds(0).single()
        // 文本已经变化, 文字布局还是之前的
        val newDisplay = buildImageChipDisplay("a ${image(1)} cd", ImageChipRegistry()) { "图片 $it" }
        assertNull(imageChipTapCursor(m.layout, bounds.center, newDisplay))
        val renumbered = buildImageChipDisplay("ab ${image(1)} cd", ImageChipRegistry()) { "图片 ${it + 1}" }
        assertNull(imageChipTapCursor(m.layout, bounds.center, renumbered))
    }

    private companion object {
        const val Width = 300
        val Style = TextStyle(fontSize = 16.sp)

        fun image(id: Int) = "[img]https://img.myani.org/comments/$id.webp[/img]"
    }
}
