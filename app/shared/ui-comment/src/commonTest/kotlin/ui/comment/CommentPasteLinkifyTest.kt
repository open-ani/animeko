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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CommentPasteLinkifyTest {
    /**
     * 模拟把 [pasted] 粘贴到 [before] (用 `«»` 或 `|` 标记选区) 中, 返回结果 (同样带标记).
     *
     * 同时检查以安卓输入法提交 ([imePaste]) 时结果相同.
     */
    private fun paste(before: String, pasted: String): String {
        val original = marked(before)
        val min = original.selection.min
        val max = original.selection.max
        val text = original.text.substring(0, min) + pasted + original.text.substring(max)
        val result = applyChange(
            original,
            originalRange = TextRange(min, max),
            text = text,
            range = TextRange(min, min + pasted.length),
            cursor = min + pasted.length,
        )
        assertEquals(result, imePaste(before, pasted), "IME commit of '$pasted' into '$before'")
        return result
    }

    /**
     * 像安卓输入法提交文字那样粘贴: Compose 的 `TextFieldBuffer.imeReplace` 去掉被替换的文字与 [pasted] 相同的开头和结尾,
     * 只记录剩下的修改.
     */
    private fun imePaste(before: String, pasted: String): String {
        val original = marked(before)
        var start = original.selection.min
        var end = original.selection.max
        var i = 0
        while (start < end && i < pasted.length && pasted[i] == original.text[start]) {
            i++
            start++
        }
        var j = pasted.length
        while (end > start && j > i && pasted[j - 1] == original.text[end - 1]) {
            j--
            end--
        }
        val text = original.text.substring(0, start) + pasted.substring(i, j) + original.text.substring(end)
        return applyChange(
            original,
            originalRange = TextRange(start, end),
            text = text,
            range = TextRange(start, start + (j - i)),
            cursor = original.selection.min + pasted.length,
        )
    }

    /**
     * 对一处修改应用 [linkifyChange]. 不需要转换时光标位于 [cursor].
     */
    private fun applyChange(
        original: CommentText,
        originalRange: TextRange,
        text: String,
        range: TextRange,
        cursor: Int,
    ): String {
        val linkified = linkifyChange(original.text, original.selection, originalRange, text, range)
            ?: return CommentText(text, TextRange(cursor)).toMarked()
        return linkified.applyTo(text).toMarked()
    }

    private fun TextReplacement.applyTo(text: String) = CommentText(
        text.substring(0, range.min) + replacement + text.substring(range.max),
        TextRange(range.min + replacement.length),
    )

    @Test
    fun `single url is wrapped`() {
        assertEquals("abc [url]https://a.com/x?y=1[/url]|", paste("abc |", "https://a.com/x?y=1"))
    }

    @Test
    fun `url pasted over a selection makes the selection a named link`() {
        assertEquals("see [url=https://a.com/x]docs[/url]| here", paste("see «docs» here", "https://a.com/x"))
    }

    @Test
    fun `url with surrounding whitespace pasted over a selection makes a named link`() {
        assertEquals("[url=https://a.com]docs[/url]|", paste("«docs»", " https://a.com\n"))
    }

    @Test
    fun `named link percent encodes parentheses and square brackets`() {
        assertEquals(
            "[url=https://en.wikipedia.org/wiki/A_%28B%29]wiki[/url]|",
            paste("«wiki»", "https://en.wikipedia.org/wiki/A_(B)"),
        )
        assertEquals("[url=https://a.com/%5Bx%5D]x[/url]|", paste("«x»", "https://a.com/[x]"))
    }

    @Test
    fun `plain link keeps parentheses and encodes square brackets`() {
        assertEquals(
            "[url]https://en.wikipedia.org/wiki/A_(B)[/url]|",
            paste("|", "https://en.wikipedia.org/wiki/A_(B)"),
        )
        assertEquals("[url]https://a.com/%5Bx%5D[/url]|", paste("|", "https://a.com/[x]"))
    }

    @Test
    fun `text with multiple urls wraps each url`() {
        assertEquals(
            "a [url]https://x.com[/url] b [url]http://y.org[/url] c|",
            paste("|", "a https://x.com b http://y.org c"),
        )
    }

    @Test
    fun `text with a url pasted over a selection replaces it and wraps the url`() {
        assertEquals("see [url]https://a.com[/url]|", paste("«x»", "see https://a.com"))
    }

    @Test
    fun `urls already in bbcode are kept`() {
        val bbcode = "[url]https://a.com[/url] [img]https://b.com/i.png[/img] [url=https://c.com]c[/url]"
        assertEquals("$bbcode|", paste("|", bbcode))
    }

    @Test
    fun `bare url next to bbcode is wrapped`() {
        assertEquals(
            "[img]https://b.com/i.png[/img] [url]https://a.com[/url]|",
            paste("|", "[img]https://b.com/i.png[/img] https://a.com"),
        )
    }

    @Test
    fun `inside an unfinished named link the url is kept as is`() {
        assertEquals("[url=https://a.com|]text[/url]", paste("[url=|]text[/url]", "https://a.com"))
    }

    @Test
    fun `inside url img and code tags the url is kept as is`() {
        assertEquals("[url]https://a.com|", paste("[url]|", "https://a.com"))
        assertEquals("[img]https://a.com/i.png|[/img]", paste("[img]|[/img]", "https://a.com/i.png"))
        assertEquals("[img=100,200]https://a.com/i.png|", paste("[img=100,200]|", "https://a.com/i.png"))
        assertEquals("[code]x https://a.com|", paste("[code]x |", "https://a.com"))
    }

    @Test
    fun `context check is case insensitive`() {
        assertEquals("[IMG]https://a.com/i.png|", paste("[IMG]|", "https://a.com/i.png"))
        assertEquals("[Url=https://a.com|]", paste("[Url=|]", "https://a.com"))
    }

    @Test
    fun `after a closed tag the url is wrapped again`() {
        assertEquals(
            "[code]x[/code] [url]https://a.com[/url]|",
            paste("[code]x[/code] |", "https://a.com"),
        )
        assertEquals(
            "[url=https://b.com]b[/url] [url]https://a.com[/url]|",
            paste("[url=https://b.com]b[/url] |", "https://a.com"),
        )
    }

    @Test
    fun `cjk punctuation after a url is not part of the link`() {
        assertEquals(
            "详情见[url]https://myani.org/help[/url]。谢谢|",
            paste("|", "详情见https://myani.org/help。谢谢"),
        )
        assertEquals("（[url]https://myani.org[/url]）|", paste("|", "（https://myani.org）"))
    }

    @Test
    fun `trailing ascii punctuation is not part of the link`() {
        assertEquals("see [url]https://a.com/x[/url].|", paste("|", "see https://a.com/x."))
    }

    @Test
    fun `text without urls is not changed`() {
        assertNull(linkifyChange("", TextRange.Zero, TextRange.Zero, "hello", TextRange(0, 5)))
        assertNull(linkifyChange("", TextRange.Zero, TextRange.Zero, "ftp://a.com", TextRange(0, 11)))
    }

    @Test
    fun `typing a single character is not affected`() {
        assertNull(linkifyChange("https://a.co", TextRange(12), TextRange(12), "https://a.com", TextRange(12, 13)))
    }

    @Test
    fun `ime change shrunk inside the selection is widened to the selection`() {
        // 选中 "here" 后从输入法剪贴板提交网址, Compose 去掉相同的开头 'h', 只记录 "ere" -> "ttps://a.com/x"
        val text = "click https://a.com/x"
        val linkified = linkifyChange(
            originalText = "click here",
            originalSelection = TextRange(6, 10),
            originalRange = TextRange(7, 10),
            text = text,
            range = TextRange(7, 21),
        )
        assertNotNull(linkified)
        assertEquals("click [url=https://a.com/x]here[/url]|", linkified.applyTo(text).toMarked())
    }

    @Test
    fun `ime paste over a selection sharing the first or last character`() {
        assertEquals(
            "watch [url=https://youtu.be/abcdo]video[/url]|",
            imePaste("watch «video»", "https://youtu.be/abcdo"),
        )
        assertEquals("[url=https://a.com/k]link[/url]|", imePaste("«link»", "https://a.com/k"))
    }

    @Test
    fun `url pasted over a selected url replaces it`() {
        assertEquals("see [url]https://new.com/b[/url]|", paste("see «https://old.com/a»", "https://new.com/b"))
    }

    @Test
    fun `url pasted over a selected link replaces it`() {
        assertEquals("see [url]https://new.com[/url]|", paste("see «[url]https://old.com[/url]»", "https://new.com"))
        assertEquals("[url]https://new.com[/url]|", paste("«[url=https://old.com]docs[/url]»", "https://new.com"))
        assertEquals("[url]https://new.com[/url]|", paste("«[img]https://old.com/i.png[/img]»", "https://new.com"))
    }

    @Test
    fun `url pasted into the address of a link from the toolbar is encoded`() {
        val linkWithoutAddress = marked("«docs»").toggleLink().toMarked()
        assertEquals("[url=|]docs[/url]", linkWithoutAddress)
        assertEquals(
            "[url=https://en.wikipedia.org/wiki/A_%28B%29|]docs[/url]",
            paste(linkWithoutAddress, "https://en.wikipedia.org/wiki/A_(B)"),
        )
        assertEquals("[url=https://a.com|]docs[/url]", paste(linkWithoutAddress, " https://a.com\n"))
        assertEquals("[img=https://a.com/%5Bx%5D|]", paste("[img=|]", "https://a.com/[x]"))
    }

    @Test
    fun `url pasted into link or image content is encoded for the content`() {
        assertEquals("[url]https://a.com/%5Bx%5D|[/url]", paste("[url]|[/url]", "https://a.com/[x]"))
        assertEquals(
            "[url=https://a.com]https://en.wikipedia.org/wiki/A_(B)|[/url]",
            paste("[url=https://a.com]|[/url]", "https://en.wikipedia.org/wiki/A_(B)"),
        )
        assertEquals("[img]https://a.com/i.png|[/img]", paste("[img]|[/img]", " https://a.com/i.png "))
    }

    @Test
    fun `url pasted into the text of a named link keeps the surrounding whitespace`() {
        assertEquals("[url=https://x]see https://a.com|[/url]", paste("[url=https://x]see|[/url]", " https://a.com"))
        assertEquals(
            "[url=https://x]see https://a.com/%5Bx%5D |[/url]",
            paste("[url=https://x]see|[/url]", " https://a.com/[x] "),
        )
    }

    @Test
    fun `text other than a single url inside url tags is kept as is`() {
        assertEquals("[url=see https://a.com/(x)|]", paste("[url=|]", "see https://a.com/(x)"))
        assertEquals("[url]https://a.com https://b.com|", paste("[url]|", "https://a.com https://b.com"))
    }

    @Test
    fun `url pasted into code is kept as is`() {
        assertEquals("[code]https://a.com/(x) |", paste("[code]|", "https://a.com/(x) "))
        assertEquals("[code][url=https://a.com/(x)|", paste("[code][url=|", "https://a.com/(x)"))
    }

    @Test
    fun `url ends before a bbcode tag`() {
        assertEquals("[b]see [url]https://a.com[/url][/b]|", paste("|", "[b]see https://a.com[/b]"))
        assertEquals("[url]https://a.com[/url][mask]x[/mask]|", paste("|", "https://a.com[mask]x[/mask]"))
        assertEquals(
            "[url]https://a.com[/url][b][url]https://b.com[/url][/b]|",
            paste("|", "https://a.com[b]https://b.com[/b]"),
        )
        assertEquals("[url]https://a.com/x[/url].[/i]|", paste("|", "https://a.com/x.[/i]"))
    }

    @Test
    fun `square brackets that do not start a tag stay in the url`() {
        assertEquals("[url]https://a.com/?a%5B%5D=1[/url]|", paste("|", "https://a.com/?a[]=1"))
    }

    @Test
    fun `opening tag names without a closing tag stay in the url`() {
        assertEquals("[url]https://a.com/?filter%5Bb%5D=x[/url]|", paste("|", "https://a.com/?filter[b]=x"))
        assertEquals(
            "[url]https://a.com/?filter%5Bb%5D=x&y=1[/url]|",
            paste("|", "https://a.com/?filter[b]=x&y=1"),
        )
        assertEquals("[url]https://a.com/?q%5Bi%5D=1[/url]|", paste("|", "https://a.com/?q[i]=1"))
        assertEquals("[url]https://a.com/?a%5Bsize%5D=1[/url]|", paste("|", "https://a.com/?a[size]=1"))
        assertEquals("[url=https://a.com/?q%5Bi%5D=1]x[/url]|", paste("«x»", "https://a.com/?q[i]=1"))
    }

    @Test
    fun `url ends before an opening tag that is closed later`() {
        assertEquals("[url]https://a.com[/url][B]x[/B]|", paste("|", "https://a.com[B]x[/B]"))
        assertEquals(
            "[url]https://a.com[/url][url=https://b.com]b[/url]|",
            paste("|", "https://a.com[url=https://b.com]b[/url]"),
        )
        assertEquals("[url]https://a.com[/url][size=12]x[/size]|", paste("|", "https://a.com[size=12]x[/size]"))
        assertEquals(
            "[url]https://a.com[/url][img=10,10]https://b.com/i.png[/img]|",
            paste("|", "https://a.com[img=10,10]https://b.com/i.png[/img]"),
        )
    }

    @Test
    fun `urls inside pasted raw tags are kept`() {
        assertEquals("[code]see https://a.com[/code]|", paste("|", "[code]see https://a.com[/code]"))
        assertEquals(
            "[url=https://a.com]see https://b.com[/url]|",
            paste("|", "[url=https://a.com]see https://b.com[/url]"),
        )
        assertEquals("[url=\"https://a.com\"]t[/url]|", paste("|", "[url=\"https://a.com\"]t[/url]"))
    }

    @Test
    fun `url after an equals sign outside tags is wrapped`() {
        assertEquals("next=[url]https://a.com[/url]|", paste("|", "next=https://a.com"))
    }

    @Test
    fun `ime composition growth is skipped`() {
        assertNull(
            linkifyInsertion(
                textBefore = "",
                inserted = "https://a.com",
                replaced = "https://a.co",
                replacedSelection = false,
            ),
        )
        assertNull(
            linkifyInsertion(
                textBefore = "",
                inserted = "https://a.com ",
                replaced = "https://a.com",
                replacedSelection = false,
            ),
        )
    }

    @Test
    fun `replacing ime composition with a url wraps it without using the composition as link text`() {
        assertEquals(
            "[url]https://a.com[/url]",
            linkifyInsertion(
                textBefore = "",
                inserted = "https://a.com",
                replaced = "abc",
                replacedSelection = false,
            ),
        )
    }

    @Test
    fun `raw url context detection`() {
        assertEquals(true, isInsideRawUrlContext("[url="))
        assertEquals(true, isInsideRawUrlContext("a [img]x"))
        assertEquals(false, isInsideRawUrlContext("a [img]x[/img] "))
        assertEquals(false, isInsideRawUrlContext("[b]"))
        assertEquals(true, isInsideRawUrlContext("[code][url]x[/url]"))
    }

    @Test
    fun `raw url context kind`() {
        assertEquals(RawUrlContext.Attribute, rawUrlContextAt("[url="))
        assertEquals(RawUrlContext.Attribute, rawUrlContextAt("[img]x[/img] [IMG="))
        assertEquals(RawUrlContext.LinkText, rawUrlContextAt("[url=https://a.com]x"))
        assertEquals(RawUrlContext.LinkText, rawUrlContextAt("[url=https://a.com][img]x[/img] "))
        assertEquals(RawUrlContext.Content, rawUrlContextAt("[url]x"))
        assertEquals(RawUrlContext.Content, rawUrlContextAt("[url=a]b[/url] [img]"))
        assertEquals(RawUrlContext.Content, rawUrlContextAt("[url=https://a.com][img]"))
        assertEquals(RawUrlContext.Content, rawUrlContextAt("[img=100,200]"))
        assertEquals(RawUrlContext.Code, rawUrlContextAt("[code][url="))
        assertEquals(RawUrlContext.Code, rawUrlContextAt("[url=https://a.com][code]"))
        assertNull(rawUrlContextAt("[url=a]b[/url] [code]x[/code]"))
    }
}
