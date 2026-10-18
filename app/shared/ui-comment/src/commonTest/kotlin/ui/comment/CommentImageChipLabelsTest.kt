/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.substring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CommentImageChipLabelsTest {
    private val label: (Int) -> String = { "图片 $it" }

    /**
     * 把 [text] 中的图片标签替换为显示的文字, 与 [CommentImageChipOutputTransformation] 相同.
     */
    private fun ImageChipDisplay.visualText(text: String): String {
        var result = text
        for (index in chips.indices.reversed()) {
            result = result.replaceRange(chips[index].range.start, chips[index].range.end, labels[index])
        }
        return result
    }

    @Test
    fun `label has an icon slot padding a hidden id and a line break opportunity only before it`() {
        // 边距 2 个、图标空位 4 个、间距 1 个不换行空格
        val leading = "\u00A0".repeat(7)
        assertEquals(
            "\u200B\u2063${leading}图\u2060片\u00A01\u00A0\u00A0\u2064\u2062\u2064\u2063",
            encodeImageChipLabel("图片 1", 5),
        )
        assertEquals("\u200B\u2063${leading}Image\u00A012\u00A0\u00A0\u2062\u2063", encodeImageChipLabel("Image 12", 0))
        assertEquals(TextRange(4, 8), ImageChipIconRange)
    }

    @Test
    fun `hidden ranges cover the leading zero width space the marks the id and word joiners`() {
        assertEquals(
            listOf(TextRange(0, 2), TextRange(10, 11), TextRange(16, 20)),
            imageChipHiddenRanges(encodeImageChipLabel("图片 1", 5)),
        )
        assertEquals(emptyList(), imageChipHiddenRanges("\u00A0Image\u00A01\u00A0"))
    }

    @Test
    fun `decoding restores the source for every id`() {
        for (id in listOf(0, 1, 2, 3, 7, 8, 255, 1000, 123456)) {
            val decoded = decodeImageChipLabels(encodeImageChipLabel("图片 3", id)) { if (it == id) "SRC$id" else null }
            assertEquals("SRC$id", decoded)
        }
    }

    @Test
    fun `decoding keeps the surrounding text and handles adjacent labels`() {
        val registry = ImageChipRegistry()
        val a = encodeImageChipLabel("图片 1", registry.idOf("[img]a[/img]"))
        val b = encodeImageChipLabel("图片 2", registry.idOf("[IMG=1,2]b[/IMG]"))
        assertEquals(
            "看[img]a[/img]和[IMG=1,2]b[/IMG][img]a[/img]!",
            decodeImageChipLabels("看${a}和$b$a!", registry::sourceOf),
        )
    }

    @Test
    fun `unknown id keeps the visible label`() {
        assertEquals("a 图片 3 b", decodeImageChipLabels("a ${encodeImageChipLabel("图片 3", 9)} b") { null })
    }

    @Test
    fun `text without markers is returned as is`() {
        val text = "plain\u00A0text [img]a[/img]"
        assertSame(text, decodeImageChipLabels(text) { error("no label to decode") })
    }

    @Test
    fun `stray markers are removed`() {
        assertEquals("abc", decodeImageChipLabels("a\u2063b\u2062c\u2064") { null })
    }

    @Test
    fun `hidden characters of a partial label never remain`() {
        val label = encodeImageChipLabel("图片 1", 5)
        // 只剩开头: ZERO WIDTH SPACE, 开头标记, 边距和图标空位去掉, 可见的文字保留, 其中的 WORD JOINER 去掉
        assertEquals("a图片", decodeImageChipLabels("a" + label.substring(0, label.indexOf('片') + 1)) { "SRC" })
        // 只剩结尾: 边距、编号和结尾标记都去掉
        assertEquals("1b", decodeImageChipLabels(label.substring(label.indexOf('1')) + "b") { "SRC" })
        // 用户自己的 ZERO WIDTH SPACE 和不换行空格不在标签旁边时保留
        assertEquals("\u200Bx\u00A0y", decodeImageChipLabels("\u200Bx\u00A0y\u2063") { null })
    }

    @Test
    fun `label tail at the start of the text is found`() {
        val label = encodeImageChipLabel("图片 1", 5)
        val tail = label.substring(label.lastIndexOf('\u00A0') + 1)
        assertEquals("\u2064\u2062\u2064\u2063", tail)
        assertEquals(tail.length, imageChipLabelTailLength(tail + "abc"))
        // 从可见的文字中间开始, 或缺少开头标记
        val inside = label.indexOf('片')
        assertEquals(label.length - inside, imageChipLabelTailLength(label.substring(inside) + "abc"))
        assertEquals(label.length - 2, imageChipLabelTailLength(label.substring(2) + "abc"))
        // 完整的标签和普通文字不是残片
        assertEquals(0, imageChipLabelTailLength(label + "abc"))
        assertEquals(0, imageChipLabelTailLength(label.substring(1) + "abc"))
        assertEquals(0, imageChipLabelTailLength("abc"))
        assertEquals(0, imageChipLabelTailLength("abc\u2063"))
    }

    @Test
    fun `label head at the end of the text is found`() {
        val label = encodeImageChipLabel("图片 1", 5)
        val visibleEnd = label.indexOf('片') + 1
        // 从开头的 ZERO WIDTH SPACE 算起
        assertEquals(3, imageChipLabelHeadStart("abc" + label.substring(0, 2)))
        assertEquals(3, imageChipLabelHeadStart("abc" + label.substring(0, visibleEnd)))
        assertEquals(3, imageChipLabelHeadStart("abc" + label.substring(1, visibleEnd)))
        // 完整的标签, 带有编号的残片, 以及结尾标记都不是开头的残片
        assertEquals(-1, imageChipLabelHeadStart("abc$label"))
        assertEquals(-1, imageChipLabelHeadStart("abc" + label.substring(0, label.indexOf('⁤') + 1)))
        assertEquals(-1, imageChipLabelHeadStart("x\u2063y"))
        assertEquals(-1, imageChipLabelHeadStart("abc"))
    }

    @Test
    fun `registry gives each source a stable id`() {
        val registry = ImageChipRegistry()
        val a = registry.idOf("[img]a[/img]")
        val b = registry.idOf("[img]b[/img]")
        assertNotEquals(a, b)
        assertEquals(a, registry.idOf("[img]a[/img]"))
        assertEquals("[img]b[/img]", registry.sourceOf(b))
        assertNull(registry.sourceOf(b + 1))
        assertNull(registry.sourceOf(-1))
    }

    private val mappingText = "ab[img]x[/img]cd[img]y[/img]e"
    private val mapping = ImageChipDisplay(parseImageChips(mappingText), listOf("P", "QQ"))

    @Test
    fun `visual ranges and length delta`() {
        assertEquals(listOf(TextRange(2, 3), TextRange(5, 7)), mapping.visualRanges)
        assertEquals(-21, mapping.lengthDelta)
        assertEquals("abPcdQQe", mapping.visualText(mappingText))
    }

    @Test
    fun `offsets map to the visual text and offsets inside a chip map to its start`() {
        val expected = mapOf(0 to 0, 2 to 2, 3 to 2, 13 to 2, 14 to 3, 15 to 4, 16 to 5, 20 to 5, 27 to 5, 28 to 7, 29 to 8)
        for ((offset, visual) in expected) {
            assertEquals(visual, mapping.toVisual(offset), "offset $offset")
        }
    }

    @Test
    fun `ranges map to the visual text and ends inside chips expand outward`() {
        assertEquals(TextRange(2, 3), mapping.toVisual(TextRange(2, 14)))
        assertEquals(TextRange(3, 5), mapping.toVisual(TextRange(14, 16)))
        assertEquals(TextRange(2, 7), mapping.toVisual(TextRange(3, 20)))
        assertEquals(TextRange(7, 2), mapping.toVisual(TextRange(20, 3)))
        assertEquals(TextRange(0, 8), mapping.toVisual(TextRange(0, 29)))
        assertEquals(TextRange(2), mapping.toVisual(TextRange(5)))
        assertEquals(TextRange(4), mapping.toVisual(TextRange(15)))
    }

    @Test
    fun `visual offsets map back and offsets inside a label map to the whole chip`() {
        assertEquals(TextRange(0), mapping.toUnderlying(0))
        assertEquals(TextRange(2), mapping.toUnderlying(2))
        assertEquals(TextRange(14), mapping.toUnderlying(3))
        assertEquals(TextRange(16), mapping.toUnderlying(5))
        assertEquals(TextRange(16, 28), mapping.toUnderlying(6))
        assertEquals(TextRange(28), mapping.toUnderlying(7))
        assertEquals(TextRange(29), mapping.toUnderlying(8))
    }

    @Test
    fun `built display matches the replaced text`() {
        val text = "看[img]https://a.com/1.png[/img]\n[IMG=1,2]https://a.com/2.png[/IMG][img]ani-upload:1[/img]尾"
        val display = buildImageChipDisplay(text, ImageChipRegistry(), label)
        val visual = display.visualText(text)
        assertEquals(text.length + display.lengthDelta, visual.length)
        for (index in display.chips.indices) {
            assertEquals(display.labels[index], visual.substring(display.visualRanges[index]))
        }
        assertEquals("看图片 1\n图片 2图片 3尾", decodeImageChipLabels(visual) { null })
    }

    @Test
    fun `copying any chip aligned selection of the visual text restores the bbcode`() {
        val text = "a[img]https://a.com/1.png[/img]b [img=1,2]https://a.com/2.png[/img][img]x[/img]"
        val registry = ImageChipRegistry()
        val display = buildImageChipDisplay(text, registry, label)
        val visual = display.visualText(text)
        val offsets = (0..text.length).filter { offset -> display.chips.none { it.contains(offset) } }
        for (start in offsets) {
            for (end in offsets) {
                if (end < start) continue
                val selection = TextRange(start, end)
                val copied = visual.substring(display.toVisual(selection))
                assertEquals(text.substring(selection), decodeImageChipLabels(copied, registry::sourceOf), "$selection")
            }
        }
    }
}
