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
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentImageChipEditingTest {
    private val registry = ImageChipRegistry()
    private val decode: (String) -> String = { decodeImageChipLabels(it, registry::sourceOf) }

    /**
     * 模拟用户的一处修改: 把 [before] (用 `«»` 或 `|` 标记选区) 中 [originalRange] 的文字替换为 [inserted],
     * 然后像 [CommentImageChipInputTransformation] 一样应用 [guardImageChipChange] 并把选区移出图片标签. 返回结果 (同样带标记).
     * 不需要调整时光标位于插入的文字之后.
     */
    private fun edit(before: String, originalRange: TextRange, inserted: String): String {
        val original = marked(before)
        val text = original.text.substring(0, originalRange.min) + inserted + original.text.substring(originalRange.max)
        val range = TextRange(originalRange.min, originalRange.min + inserted.length)
        val replacement = guardImageChipChange(
            originalText = original.text,
            originalSelection = original.selection,
            originalRange = originalRange,
            text = text,
            range = range,
            widenToSelection = true,
            decodeLabels = decode,
        )
        val result = if (replacement == null) {
            CommentText(text, TextRange(range.max))
        } else {
            CommentText(
                text.replaceRange(replacement.range.min, replacement.range.max, replacement.replacement),
                TextRange(replacement.range.min + replacement.cursor),
            )
        }
        return result.copy(selection = snapSelectionToImageChips(result.text, result.selection)).toMarked()
    }

    private fun delete(before: String, start: Int, end: Int) = edit(before, TextRange(start, end), "")

    // "ab[img]x[/img]cd": 图片标签位于 [2, 14)
    private val chip = "ab[img]x[/img]cd|"

    @Test
    fun `deleting across the start of a chip deletes the whole chip`() {
        assertEquals("a|cd", delete(chip, 1, 5))
    }

    @Test
    fun `deleting across the end of a chip deletes the whole chip`() {
        assertEquals("ab|d", delete(chip, 10, 15))
    }

    @Test
    fun `deleting inside a chip deletes the whole chip`() {
        assertEquals("ab|cd", delete(chip, 7, 8))
    }

    @Test
    fun `deleting a prefix or a suffix of a chip deletes the whole chip`() {
        assertEquals("ab|cd", delete(chip, 2, 5))
        assertEquals("ab|cd", delete(chip, 8, 14))
    }

    @Test
    fun `deleting exactly or around a whole chip is unchanged`() {
        assertEquals("ab|cd", delete(chip, 2, 14))
        assertEquals("a|d", delete(chip, 1, 15))
    }

    @Test
    fun `deleting text touching a chip keeps the chip`() {
        assertEquals("ab[img]x[/img]|d", delete(chip, 14, 15))
        assertEquals("a|[img]x[/img]cd", delete(chip, 1, 2))
    }

    @Test
    fun `deleting from inside one chip to inside another deletes both chips`() {
        // 图片标签位于 [1, 13) 和 [14, 26)
        assertEquals("a|c", delete("a[img]x[/img]b[img]y[/img]c|", 5, 20))
    }

    @Test
    fun `inserting inside a chip moves the insertion after the chip`() {
        assertEquals("ab[img]x[/img]zz|cd", edit(chip, TextRange(7), "zz"))
        assertEquals("ab[img]x[/img][img]y[/img]|cd", edit(chip, TextRange(10), "[img]y[/img]"))
    }

    @Test
    fun `replacing part of a chip that still forms an image keeps it`() {
        // 输入法提交另一个图片标签时去掉相同的开头和结尾, 只剩地址中不同的部分
        assertEquals(
            "a[img]https://a.com/2.png[/img]|b",
            edit("a[img]https://a.com/1.png[/img]b|", TextRange(20, 21), "2"),
        )
    }

    @Test
    fun `replacing part of a chip that breaks it deletes the chip and keeps the inserted text`() {
        assertEquals("abQ|cd", edit(chip, TextRange(8, 10), "Q"))
        assertEquals("aQ|cd", edit(chip, TextRange(1, 4), "Q"))
    }

    @Test
    fun `change inside a selection is treated as replacing the whole selection`() {
        // 选中图片后粘贴 "[img", 输入法去掉相同的开头后记录的是删除了标签的后一部分
        assertEquals("ab[img|cd", edit("ab«[img]x[/img]»cd", TextRange(6, 14), ""))
    }

    @Test
    fun `labels in inserted text are restored to chips`() {
        val label = encodeImageChipLabel("图片 1", registry.idOf("[img]q[/img]"))
        assertEquals("abx[img]q[/img]y|cd", edit("ab|cd", TextRange(2), "x${label}y"))
    }

    @Test
    fun `input method committing a label over its chip keeps the chip`() {
        val source = "[img]q[/img]"
        val label = encodeImageChipLabel("图片 1", registry.idOf(source))
        assertEquals("ab$source|cd", edit("ab$source|cd", TextRange(2, 14), label))
    }

    // 输入法把标签的隐藏字符当作单词的一部分时, Compose 把组合范围扩展到整个标签, 输入法提交的却只有标签显示文字的一部分

    private val source = "[img]https://a.com/1.png[/img]"

    // "x " + 图片标签 [2, 32) + "abc"
    private val composing = "x $source" + "abc|"

    private fun label() = encodeImageChipLabel("图片 1", registry.idOf(source))

    @Test
    fun `input method committing a label tail over a widened composition keeps the chip`() {
        val label = label()
        val tail = label.substring(label.lastIndexOf('\u00A0') + 1)
        assertEquals("x $source" + "abcd|", edit(composing, TextRange(2, 35), tail + "abcd"))
        // 编号对应的不是这个标签 (编号被截断) 时仍还原为组合范围开头的标签
        assertEquals("x $source" + "abcd|", edit(composing, TextRange(2, 35), "\u2064\u2063abcd"))
    }

    @Test
    fun `input method committing a label without its leading mark keeps the chip and leaves no label characters`() {
        assertEquals("x $source" + "abcd|", edit(composing, TextRange(2, 35), label().substring(2) + "abcd"))
    }

    @Test
    fun `input method committing text before a label head keeps the chip after the cursor`() {
        // "abc" + 图片标签 [3, 33) + "x": 在标签前输入, 组合文字是 "abc" 和标签的开头标记
        val before = "abc$source" + "x|"
        assertEquals("abcd|$source" + "x", edit(before, TextRange(0, 33), "abcd" + label().substring(0, 2)))
        assertEquals("abcd|$source" + "x", edit(before, TextRange(0, 33), "abcd" + label().substring(0, 6)))
    }

    @Test
    fun `input method dropping the label characters it read keeps the chip`() {
        // 只剩编号: 还原不出标签, 但输入法复述的是标签的文字, 用户没有删除图片
        assertEquals("x $source" + "ab|", edit(composing, TextRange(2, 35), "\u2064\u2062ab"))
        // 不含隐藏字符时按用户删除了图片处理
        assertEquals("x ab|", edit(composing, TextRange(2, 35), "ab"))
    }

    @Test
    fun `stray markers in inserted text are removed`() {
        assertEquals("abxy|cd", edit("ab|cd", TextRange(2), "x\u2063y\u2064"))
    }

    @Test
    fun `plain typing is unchanged`() {
        assertEquals("abz|cd", edit("ab|cd", TextRange(2), "z"))
        assertEquals("ab[img]x[/img]z|cd", edit("ab[img]x[/img]|cd", TextRange(14), "z"))
    }

    @Test
    fun `cursor inside a chip snaps to the nearer end`() {
        val text = "ab[img]x[/img]cd"
        assertEquals(TextRange(2), snapSelectionToImageChips(text, TextRange(4)))
        assertEquals(TextRange(14), snapSelectionToImageChips(text, TextRange(12)))
        assertEquals(TextRange(14), snapSelectionToImageChips(text, TextRange(8)))
        assertEquals(TextRange(2), snapSelectionToImageChips(text, TextRange(2)))
        assertEquals(TextRange(15), snapSelectionToImageChips(text, TextRange(15)))
    }

    @Test
    fun `selection ends inside chips expand outward keeping the direction`() {
        val text = "ab[img]x[/img]cd"
        assertEquals(TextRange(2, 15), snapSelectionToImageChips(text, TextRange(4, 15)))
        assertEquals(TextRange(1, 14), snapSelectionToImageChips(text, TextRange(1, 9)))
        assertEquals(TextRange(14, 1), snapSelectionToImageChips(text, TextRange(9, 1)))
        assertEquals(TextRange(2, 14), snapSelectionToImageChips(text, TextRange(5, 9)))
        assertEquals(TextRange(0, 16), snapSelectionToImageChips(text, TextRange(0, 16)))
        assertEquals(TextRange(1, 3), snapSelectionToImageChips("abc", TextRange(1, 3)))
    }

    @Test
    fun `selected text and deleting the selection include whole chips`() {
        val text = CommentText("ab[img]x[/img]cd", TextRange(4, 15))
        assertEquals("[img]x[/img]c", text.selectedText())
        assertEquals("ab|d", text.deleteSelection().toMarked())
    }

    @Test
    fun `pasting bbcode with an image keeps the image`() {
        assertEquals(
            "ab看[img]https://a.com/x.png[/img]|cd",
            marked("ab|cd").paste("看[img]https://a.com/x.png[/img]", decode).toMarked(),
        )
        assertEquals(
            "[img=1,2]https://a.com/x.png[/img]|",
            marked("|").paste("[img=1,2]https://a.com/x.png[/img]", decode).toMarked(),
        )
    }

    @Test
    fun `pasting a url links it like pasting in the text field`() {
        assertEquals("a [url]https://a.com[/url]|", marked("a |").paste("https://a.com", decode).toMarked())
        assertEquals(
            "[img]x[/img][url]https://a.com[/url]|",
            marked("[img]x[/img]|").paste("https://a.com", decode).toMarked(),
        )
        assertEquals("read [url=https://a.com]docs[/url]|", marked("read «docs»").paste("https://a.com", decode).toMarked())
    }

    @Test
    fun `pasting a url over a selected image replaces it with a plain link`() {
        assertEquals("a[url]https://a.com[/url]|b", marked("a«[img]x[/img]»b").paste("https://a.com", decode).toMarked())
    }

    @Test
    fun `pasting restores labels and replaces whole chips`() {
        val label = encodeImageChipLabel("图片 1", registry.idOf("[img]q[/img]"))
        assertEquals("ab[img]q[/img]|cd", marked("ab|cd").paste(label, decode).toMarked())
        assertEquals("abz|d", CommentText("ab[img]x[/img]cd", TextRange(4, 15)).paste("z", decode).toMarked())
    }
}
