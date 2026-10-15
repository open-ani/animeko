/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiTemplate.Escaping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonApiTemplateTest {
    @Test
    fun `substitute keeps unknown variables verbatim`() {
        assertEquals(
            "https://a/{missing}/3252",
            JsonApiTemplate.substitute("https://a/{missing}/{id}", mapOf("id" to "3252"), Escaping.NONE),
        )
    }

    @Test
    fun `substitute url component encodes query characters`() {
        assertEquals(
            "https://a/search?wd=%E8%91%AC%E9%80%81%20%26%20x%3Dy",
            JsonApiTemplate.substitute(
                "https://a/search?wd={keyword}",
                mapOf("keyword" to "葬送 & x=y"),
                Escaping.URL_COMPONENT,
            ),
        )
    }

    @Test
    fun `substitute json string escapes quotes backslashes and control characters`() {
        assertEquals(
            """{"keyword":"a\"b\\c\nd","page_number":1}""",
            JsonApiTemplate.substitute(
                """{"keyword":"{keyword}","page_number":{page}}""",
                mapOf("keyword" to "a\"b\\c\nd", "page" to "1"),
                Escaping.JSON_STRING,
            ),
        )
    }

    @Test
    fun `substitute json string leaves non ascii and digits untouched`() {
        assertEquals(
            """{"q":"葬送的芙莉莲","n":47463}""",
            JsonApiTemplate.substitute(
                """{"q":"{keyword}","n":{id}}""",
                mapOf("keyword" to "葬送的芙莉莲", "id" to "47463"),
                Escaping.JSON_STRING,
            ),
        )
    }

    @Test
    fun `variableNames lists distinct names in order`() {
        assertEquals(
            listOf("subjectId", "id"),
            JsonApiTemplate.variableNames("https://a/anime/{subjectId}/play/{id}?x={id}"),
        )
    }

    @Test
    fun `extract recovers a single trailing variable`() {
        assertEquals(
            mapOf("id" to "3252"),
            JsonApiTemplate.extract("https://example.com/anime/{id}", "https://example.com/anime/3252"),
        )
    }

    @Test
    fun `extract recovers variables separated by literals`() {
        assertEquals(
            mapOf("subjectId" to "3252", "id" to "47463"),
            JsonApiTemplate.extract(
                "https://a/anime/{subjectId}/play/{id}.html",
                "https://a/anime/3252/play/47463.html",
            ),
        )
    }

    @Test
    fun `extract gives earlier variables the shortest match`() {
        assertEquals(
            mapOf("a" to "1", "b" to "2-3"),
            JsonApiTemplate.extract("{a}-{b}", "1-2-3"),
        )
    }

    @Test
    fun `extract rejects text that does not match the literal parts`() {
        assertNull(JsonApiTemplate.extract("https://a/anime/{id}", "https://a/play/3252"))
        assertNull(JsonApiTemplate.extract("https://a/anime/{id}", "https://a/anime/"))
    }

    @Test
    fun `extract treats regex metacharacters in the template literally`() {
        assertEquals(
            mapOf("id" to "7"),
            JsonApiTemplate.extract("https://a/x.php?id={id}&v=(1)", "https://a/x.php?id=7&v=(1)"),
        )
        assertNull(JsonApiTemplate.extract("https://a/x.php?id={id}", "https://a/xXphp?id=7"))
    }

    @Test
    fun `extract requires repeated variables to agree`() {
        assertEquals(mapOf("id" to "5"), JsonApiTemplate.extract("{id}/{id}", "5/5"))
        assertNull(JsonApiTemplate.extract("{id}/{id}", "5/6"))
    }

    @Test
    fun `extract without variables requires exact equality`() {
        assertEquals(emptyMap(), JsonApiTemplate.extract("https://a/b", "https://a/b"))
        assertNull(JsonApiTemplate.extract("https://a/b", "https://a/c"))
    }

    @Test
    fun `substitute then extract round trips a plain id`() {
        val template = "https://example.com/play/{id}"
        val url = JsonApiTemplate.substitute(template, mapOf("id" to "47463"), Escaping.NONE)
        assertEquals(mapOf("id" to "47463"), JsonApiTemplate.extract(template, url))
    }
}
