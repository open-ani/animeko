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
import kotlin.test.assertNull
import kotlin.test.assertSame

class CommentImageChipsTest {
    private fun sources(text: String) = parseImageChips(text).map { it.source }

    @Test
    fun `parses image tags in order with numbers ranges and urls`() {
        val text = "a [img]https://a.com/1.png[/img] b [img]https://a.com/2.png[/img]"
        val chips = parseImageChips(text)
        assertEquals(listOf(1, 2), chips.map { it.number })
        assertEquals(listOf("https://a.com/1.png", "https://a.com/2.png"), chips.map { it.url })
        assertEquals(TextRange(2, 32), chips[0].range)
        assertEquals("[img]https://a.com/1.png[/img]", text.substring(2, 32))
        assertEquals(text.length, chips[1].range.end)
    }

    @Test
    fun `tag names are all lowercase or all uppercase like the bbcode grammar`() {
        assertEquals(listOf("[IMG]https://a.com/x[/IMG]"), sources("[IMG]https://a.com/x[/IMG]"))
        assertEquals(listOf("[img]https://a.com/x[/IMG]"), sources("[img]https://a.com/x[/IMG]"))
        assertEquals(listOf("[IMG]https://a.com/x[/img]"), sources("[IMG]https://a.com/x[/img]"))
        assertEquals(emptyList(), sources("[Img]https://a.com/x[/img]"))
        assertEquals(emptyList(), sources("[img]https://a.com/x[/Img]"))
    }

    @Test
    fun `image with size is a chip`() {
        val chips = parseImageChips("[img=300,200]https://a.com/x.png[/img][IMG=640,480]https://a.com/y.jpg[/IMG]")
        assertEquals(listOf("https://a.com/x.png", "https://a.com/y.jpg"), chips.map { it.url })
        assertEquals("[img=300,200]https://a.com/x.png[/img]", chips[0].source)
    }

    @Test
    fun `malformed tags are not chips`() {
        for (text in listOf(
            "[img]https://a.com/x",
            "https://a.com/x[/img]",
            "[img][/img]",
            "[img]https://a.com/x[/img",
            "[img]a b[/img]",
            "[img]a\nb[/img]",
            "[img]a[b][/img]",
            "[img=300]https://a.com/x[/img]",
            "[img=,200]https://a.com/x[/img]",
            "[img=a,b]https://a.com/x[/img]",
            "[url]https://a.com/x[/url]",
        )) {
            assertEquals(emptyList(), sources(text), text)
        }
    }

    @Test
    fun `stray opening tag does not swallow the text before a later image`() {
        val text = "[img] is a tag and [img]https://a.com/x[/img]"
        assertEquals(listOf("[img]https://a.com/x[/img]"), sources(text))
    }

    @Test
    fun `adjacent and repeated images are separate chips`() {
        val tag = "[img]https://a.com/x[/img]"
        val chips = parseImageChips(tag + tag)
        assertEquals(listOf(1, 2), chips.map { it.number })
        assertEquals(TextRange(tag.length, tag.length * 2), chips[1].range)
    }

    @Test
    fun `pending image tags have an upload id`() {
        val chips = parseImageChips(pendingImageTag("7") + "[img]https://a.com/x[/img]")
        assertEquals("[img]ani-upload:7[/img]", chips[0].source)
        assertEquals("7", chips[0].uploadId)
        assertNull(chips[1].uploadId)
    }

    @Test
    fun `contains excludes both ends`() {
        val chip = parseImageChips("a[img]x[/img]b").single()
        assertEquals(false, chip.contains(1))
        assertEquals(true, chip.contains(2))
        assertEquals(true, chip.contains(12))
        assertEquals(false, chip.contains(13))
    }

    @Test
    fun `remove image chip deletes only that occurrence`() {
        assertEquals(
            "a | b [img]x[/img]",
            marked("a [img]x[/img]| b [img]x[/img]").removeImageChip(1).toMarked(),
        )
        assertEquals(
            "a [img]x[/img] b |",
            marked("a [img]x[/img] b [img]y[/img]|").removeImageChip(2).toMarked(),
        )
        assertEquals("a «b» ", marked("a [img]x[/img]«b» ").removeImageChip(1).toMarked())
        assertEquals("a | b", marked("a «[img]x[/img]» b").removeImageChip(1).toMarked())
        assertEquals("a [img]x[/img]|", marked("a [img]x[/img]|").removeImageChip(3).toMarked())
    }

    @Test
    fun `select image chip selects the whole tag`() {
        assertEquals("a [img]x[/img] «[img]y[/img]»", marked("|a [img]x[/img] [img]y[/img]").selectImageChip(2).toMarked())
    }

    @Test
    fun `replace pending image replaces every occurrence and keeps the selection around it`() {
        val pending = pendingImageTag("1")
        assertEquals(
            "a [img]https://a.com/x[/img] b [img]https://a.com/x[/img]|",
            marked("a $pending b $pending|").replacePendingImage("1", "https://a.com/x")?.toMarked(),
        )
        assertEquals(
            "a «[img]https://a.com/x[/img]» b",
            marked("a «$pending» b").replacePendingImage("1", "https://a.com/x")?.toMarked(),
        )
        assertEquals(
            "|a [img]https://a.com/%5Bx%5D[/img] ${pendingImageTag("2")}",
            marked("|a $pending ${pendingImageTag("2")}").replacePendingImage("1", "https://a.com/[x]")?.toMarked(),
        )
    }

    @Test
    fun `replace pending image returns null when the tag was removed`() {
        assertNull(marked("a ${pendingImageTag("2")}|").replacePendingImage("1", "https://a.com/x"))
    }

    @Test
    fun `remove pending image removes every copy and returns null when the tag is gone`() {
        val pending = pendingImageTag("1")
        assertEquals(
            "a  b ${pendingImageTag("2")}|",
            marked("a $pending b ${pendingImageTag("2")}$pending|").removePendingImage("1")?.toMarked(),
        )
        assertNull(marked("a ${pendingImageTag("2")}|").removePendingImage("1"))
    }

    @Test
    fun `resolve pending images replaces only uploaded ones`() {
        val urls = mapOf("1" to "https://a.com/[x]")
        assertEquals(
            "a [img]https://a.com/%5Bx%5D[/img] ${pendingImageTag("2")} [img]https://a.com/%5Bx%5D[/img]",
            resolvePendingImages("a ${pendingImageTag("1")} ${pendingImageTag("2")} ${pendingImageTag("1")}", urls::get),
        )
        val unchanged = "a ${pendingImageTag("2")} [img]https://a.com/y[/img]"
        assertSame(unchanged, resolvePendingImages(unchanged, urls::get))
        assertSame("plain", resolvePendingImages("plain", urls::get))
    }

    @Test
    fun `remove pending images keeps other images`() {
        assertEquals(
            "a  [img]https://a.com/y[/img] ",
            removePendingImages("a ${pendingImageTag("1")} [img]https://a.com/y[/img] ${pendingImageTag("2")}"),
        )
    }

    @Test
    fun `insert at cursor inside an image tag inserts after the tag`() {
        val text = CommentText("a [img]https://a.com/x[/img] b", TextRange(9))
        assertEquals("a [img]https://a.com/x[/img](bgm1)| b", text.insertAtCursor("(bgm1)").toMarked())
    }
}
