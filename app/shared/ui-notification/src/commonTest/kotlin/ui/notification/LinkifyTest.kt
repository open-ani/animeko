/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkifyTest {
    private fun urls(text: String) = findUrls(text).map { it.url }

    @Test
    fun `finds http and https urls`() {
        assertEquals(
            listOf("https://myani.org/a?b=1#c", "http://example.com"),
            urls("见 https://myani.org/a?b=1#c 和 http://example.com"),
        )
    }

    @Test
    fun `url ends at cjk punctuation`() {
        assertEquals(listOf("https://myani.org/help"), urls("详情见https://myani.org/help。谢谢"))
        assertEquals(listOf("https://myani.org"), urls("（https://myani.org）"))
        assertEquals(listOf("https://myani.org"), urls("地址：https://myani.org，欢迎"))
    }

    @Test
    fun `trailing ascii punctuation is not part of the url`() {
        assertEquals(listOf("https://myani.org/a"), urls("See https://myani.org/a."))
        assertEquals(listOf("https://myani.org/a"), urls("(see https://myani.org/a)"))
        assertEquals(listOf("https://en.wikipedia.org/wiki/A_(B)"), urls("https://en.wikipedia.org/wiki/A_(B)!"))
    }

    @Test
    fun `ignores non http schemes and bare scheme`() {
        assertEquals(emptyList(), urls("ftp://example.com ani://subjects/1 https://"))
    }

    @Test
    fun `ranges point into the original text`() {
        val text = "a https://x.org b"
        val detected = findUrls(text).single()
        assertEquals("https://x.org", text.substring(detected.range))
    }

    @Test
    fun `linkified text keeps all characters and links urls`() {
        val text = "前 https://myani.org/a。后\n第二行 http://b.org"
        val annotated = linkifyText(text)
        assertEquals(text, annotated.text)
        val links = annotated.getLinkAnnotations(0, text.length)
        assertEquals(
            listOf("https://myani.org/a", "http://b.org"),
            links.map { (it.item as LinkAnnotation.Url).url },
        )
        assertEquals(
            listOf("https://myani.org/a", "http://b.org"),
            links.map { text.substring(it.start, it.end) },
        )
    }

    @Test
    fun `text without urls has no links`() {
        val annotated = linkifyText("没有链接")
        assertEquals("没有链接", annotated.text)
        assertEquals(emptyList(), annotated.getLinkAnnotations(0, annotated.length))
    }
}
