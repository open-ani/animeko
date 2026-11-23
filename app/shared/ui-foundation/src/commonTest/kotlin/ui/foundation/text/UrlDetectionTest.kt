/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.text

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlDetectionTest {
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
    fun `url ends at chinese quotes ellipsis and dash`() {
        assertEquals(listOf("https://a.com/x"), urls("他说“https://a.com/x”很好"))
        assertEquals(listOf("https://a.com/x"), urls("‘https://a.com/x’"))
        assertEquals(listOf("https://a.com/x"), urls("https://a.com/x……"))
        assertEquals(listOf("https://a.com/x"), urls("https://a.com/x——见上"))
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
}
