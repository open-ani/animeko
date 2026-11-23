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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 用 `«` `»` 标出选区, 用 `|` 标出光标.
 */
internal fun marked(value: String): CommentText {
    val start = value.indexOf('«')
    if (start >= 0) {
        val end = value.indexOf('»')
        return CommentText(value.replace("«", "").replace("»", ""), TextRange(start, end - 1))
    }
    val cursor = value.indexOf('|')
    check(cursor >= 0) { "No selection marker in '$value'" }
    return CommentText(value.replace("|", ""), TextRange(cursor))
}

internal fun CommentText.toMarked(): String {
    val min = selection.min
    val max = selection.max
    return if (min == max) {
        text.substring(0, min) + "|" + text.substring(min)
    } else {
        text.substring(0, min) + "«" + text.substring(min, max) + "»" + text.substring(max)
    }
}

class CommentTextEditingTest {
    private fun String.toggle(format: CommentFormat) = marked(this).toggleFormat(format).toMarked()
    private fun String.toggleLink() = marked(this).toggleLink().toMarked()

    @Test
    fun `bold wraps selection and keeps it selected`() {
        assertEquals("a [b]«bold»[/b] c", "a «bold» c".toggle(CommentFormat.Bold))
    }

    @Test
    fun `each format wraps with its own tag`() {
        assertEquals("[i]«x»[/i]", "«x»".toggle(CommentFormat.Italic))
        assertEquals("[u]«x»[/u]", "«x»".toggle(CommentFormat.Underline))
        assertEquals("[s]«x»[/s]", "«x»".toggle(CommentFormat.Strikethrough))
        assertEquals("[mask]«x»[/mask]", "«x»".toggle(CommentFormat.Mask))
    }

    @Test
    fun `toggling twice restores the original text`() {
        for (format in CommentFormat.entries) {
            val once = marked("a «text» b").toggleFormat(format)
            assertEquals("a «text» b", once.toggleFormat(format).toMarked(), format.name)
        }
    }

    @Test
    fun `unwraps when tags are right outside the selection`() {
        assertEquals("a «bold» c", "a [b]«bold»[/b] c".toggle(CommentFormat.Bold))
        assertEquals("«spoiler»", "[mask]«spoiler»[/mask]".toggle(CommentFormat.Mask))
    }

    @Test
    fun `unwraps when the selection includes the tags`() {
        assertEquals("a «bold» c", "a «[b]bold[/b]» c".toggle(CommentFormat.Bold))
        assertEquals("«spoiler»", "«[mask]spoiler[/mask]»".toggle(CommentFormat.Mask))
    }

    @Test
    fun `unwrap is case insensitive`() {
        assertEquals("«x»", "[B]«x»[/B]".toggle(CommentFormat.Bold))
        assertEquals("«x»", "«[MASK]x[/mask]»".toggle(CommentFormat.Mask))
        assertTrue(marked("[I]«x»[/i]").isFormatActive(CommentFormat.Italic))
    }

    @Test
    fun `tags that do not pair up are not treated as wrapping`() {
        // 首尾虽是 [b] 和 [/b], 但它们分属两对标签
        assertEquals(
            "[b]«[b]x[/b] and [b]y[/b]»[/b]",
            "«[b]x[/b] and [b]y[/b]»".toggle(CommentFormat.Bold),
        )
        assertEquals(
            "[b][b]«a[/b] [b]b»[/b][/b]",
            "[b]«a[/b] [b]b»[/b]".toggle(CommentFormat.Bold),
        )
    }

    @Test
    fun `nested pairs inside the selection are allowed when unwrapping`() {
        assertEquals("«x [b]y[/b] z»", "[b]«x [b]y[/b] z»[/b]".toggle(CommentFormat.Bold))
    }

    @Test
    fun `adjacent formatted text is not considered active`() {
        assertEquals("[b]a[/b][b]«b»[/b][b]c[/b]", "[b]a[/b]«b»[b]c[/b]".toggle(CommentFormat.Bold))
        assertFalse(marked("[b]a[/b]«b»[b]c[/b]").isFormatActive(CommentFormat.Bold))
    }

    @Test
    fun `different formats nest and are detected independently`() {
        val bold = "[i]«x»[/i]".toggle(CommentFormat.Bold)
        assertEquals("[i][b]«x»[/b][/i]", bold)
        assertTrue(marked(bold).isFormatActive(CommentFormat.Bold))
        assertFalse(marked(bold).isFormatActive(CommentFormat.Italic))
        assertFalse(marked("[b]«x»[/b]").isFormatActive(CommentFormat.Strikethrough))
    }

    @Test
    fun `s tag is not confused with other tags`() {
        assertFalse(marked("[size=3]«x»[/size]").isFormatActive(CommentFormat.Strikethrough))
        assertFalse(marked("[mask]«x»[/mask]").isFormatActive(CommentFormat.Strikethrough))
    }

    @Test
    fun `empty selection inserts empty tags with the cursor inside`() {
        assertEquals("a[b]|[/b]b", "a|b".toggle(CommentFormat.Bold))
        assertEquals("[mask]|[/mask]", "|".toggle(CommentFormat.Mask))
    }

    @Test
    fun `empty selection between empty tags removes them`() {
        assertEquals("a|b", "a[b]|[/b]b".toggle(CommentFormat.Bold))
    }

    @Test
    fun `reversed selection is handled`() {
        val reversed = CommentText("a bold c", TextRange(6, 2))
        assertEquals("a [b]«bold»[/b] c", reversed.toggleFormat(CommentFormat.Bold).toMarked())
    }

    @Test
    fun `link on a url uses the plain url tag and keeps the url selected`() {
        assertEquals("see [url]«https://a.com/x»[/url] ok", "see «https://a.com/x» ok".toggleLink())
    }

    @Test
    fun `link on a url keeps surrounding whitespace outside the tag`() {
        assertEquals(" [url]«https://a.com»[/url] ", "« https://a.com »".toggleLink())
    }

    @Test
    fun `link on a url encodes square brackets`() {
        assertEquals("[url]«https://a.com/%5Bx%5D»[/url]", "«https://a.com/[x]»".toggleLink())
    }

    @Test
    fun `link on plain text puts the cursor after the equals sign`() {
        assertEquals("a [url=|]text[/url] b", "a «text» b".toggleLink())
    }

    @Test
    fun `link on text containing a url and more is a named link`() {
        assertEquals("[url=|]see https://a.com[/url]", "«see https://a.com»".toggleLink())
    }

    @Test
    fun `link with empty selection inserts an empty named link`() {
        assertEquals("a[url=|][/url]", "a|".toggleLink())
    }

    @Test
    fun `link toggles off the plain url tag`() {
        assertEquals("«https://a.com»", "[url]«https://a.com»[/url]".toggleLink())
        assertEquals("«https://a.com»", "«[URL]https://a.com[/URL]»".toggleLink())
    }

    @Test
    fun `link toggles off the named url tag`() {
        assertEquals("a «text» b", "a [url=https://a.com]«text»[/url] b".toggleLink())
        assertEquals("a «text» b", "a «[url=https://a.com]text[/url]» b".toggleLink())
        assertEquals("«text»", "[Url=]«text»[/url]".toggleLink())
    }

    @Test
    fun `link is active only for whole link tags`() {
        assertTrue(marked("[url]«https://a.com»[/url]").isLinkActive())
        assertTrue(marked("[url=https://a.com]«text»[/url]").isLinkActive())
        assertTrue(marked("«[url=https://a.com]text[/url]»").isLinkActive())
        assertFalse(marked("[url=https://a.com]te«x»t[/url]").isLinkActive())
        assertFalse(marked("«[url]a[/url] [url]b[/url]»").isLinkActive())
        assertFalse(marked("[b]«text»[/b]").isLinkActive())
    }

    @Test
    fun `links without an address keep only their text`() {
        assertEquals("x", removeEmptyLinks("[url=]x[/url]"))
        assertEquals("x", removeEmptyLinks("[url=\"\"]x[/url]"))
        assertEquals("x", removeEmptyLinks("[URL=]x[/URL]"))
        assertEquals("[b]docs[/b] a\nb", removeEmptyLinks("[b][url=]docs[/url][/b] [url=]a\nb[/url]"))
        assertEquals("ab", removeEmptyLinks("[url=]a[/url][url=]b[/url]"))
        assertEquals("", removeEmptyLinks("[url=][/url]"))
    }

    @Test
    fun `links with an address and incomplete links are kept`() {
        val text = "[url=https://a.com]a[/url] [url]https://b.com[/url] [url=]c"
        assertEquals(text, removeEmptyLinks(text))
        val nested = "[url=]a [url]https://b.com[/url][/url]"
        assertEquals(nested, removeEmptyLinks(nested))
    }

    @Test
    fun `link from the toolbar without an address keeps its text`() {
        val link = marked("a «docs» b").toggleLink()
        assertEquals("a [url=]docs[/url] b", link.text)
        assertEquals("a docs b", removeEmptyLinks(link.text))
    }

    @Test
    fun `insert at cursor moves the cursor after the inserted text`() {
        assertEquals("a(bgm1)|b", marked("a|b").insertAtCursor("(bgm1)").toMarked())
    }

    @Test
    fun `insert image at the cursor`() {
        assertEquals(
            "ab[img]https://img.myani.org/1.webp[/img]|",
            marked("ab|").insertImage("https://img.myani.org/1.webp").toMarked(),
        )
    }

    @Test
    fun `insert image after the selection without deleting it`() {
        assertEquals(
            "ab[img]https://img.myani.org/1.webp[/img]|c",
            marked("«ab»c").insertImage("https://img.myani.org/1.webp").toMarked(),
        )
    }

    @Test
    fun `contains image finds the image tag of the url`() {
        val url = "https://img.myani.org/1.webp"
        assertTrue(CommentText("a [img]$url[/img] b").containsImage(url))
        assertTrue(CommentText("[IMG]$url[/Img]").containsImage(url))
        assertTrue(CommentText("$url [img]$url[/img]").containsImage(url))
    }

    @Test
    fun `contains image ignores the url outside an image tag`() {
        val url = "https://img.myani.org/1.webp"
        assertFalse(CommentText("see $url").containsImage(url))
        assertFalse(CommentText("[url]$url[/url]").containsImage(url))
        assertFalse(CommentText("[img]$url?x=1[/img]").containsImage(url))
        assertFalse(CommentText("[img]https://IMG.myani.org/1.webp[/img]").containsImage(url))
        assertFalse(CommentText("[img]$url[/img]").containsImage("https://img.myani.org/2.webp"))
        assertFalse(CommentText("[img][/img]").containsImage(""))
    }

    @Test
    fun `contains image matches the encoded url written by insert image`() {
        val url = "https://img.myani.org/a[1].webp"
        val inserted = marked("|").insertImage(url)
        assertTrue(inserted.containsImage(url))
    }
}
