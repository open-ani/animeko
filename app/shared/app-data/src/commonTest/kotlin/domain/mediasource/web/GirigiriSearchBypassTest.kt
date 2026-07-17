/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * This source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GirigiriSearchBypassTest {
    @Test
    fun `girigiri search uses vod api and adapts response for existing selector`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("m3u8.girigirilove.com", request.url.host)
            assertEquals("/api.php/provide/vod/", request.url.encodedPath)
            assertEquals("detail", request.url.parameters["ac"])
            assertEquals("test anime", request.url.parameters["wd"])
            assertEquals(
                "Girigiri/1.0 (https://github.com/MareDevi/girigiri)",
                request.headers[HttpHeaders.UserAgent],
            )
            assertEquals("https://bgm.girigirilove.com/", request.headers[HttpHeaders.Referrer])
            assertEquals("application/json, text/plain, */*", request.headers[HttpHeaders.Accept])

            respond(
                content = """
                    {
                      "list": [
                        { "vod_id": 123, "vod_name": "Test Anime" },
                        { "vod_id": "456", "vod_name": "Test & Season 2" }
                      ]
                    }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        })
        val engine = DefaultSelectorMediaSourceEngine(client.asScopedHttpClient())

        val result = engine.searchSubjects(
            searchUrl = "https://ani.girigirilove.com/search/-------------/?wd={keyword}",
            subjectName = "test anime",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )
        val document = assertNotNull(result.document)
        val subjects = assertNotNull(
            engine.selectSubjects(
                document,
                SelectorSearchConfig(
                    searchUrl = "https://ani.girigirilove.com/search/-------------/?wd={keyword}",
                    subjectFormatId = SelectorSubjectFormatIndexed.id,
                    selectorSubjectFormatIndexed = SelectorSubjectFormatIndexed.Config(
                        selectNames = "body > .box-width .vod-detail .detail-info .slide-info-title",
                        selectLinks = "body > .box-width .vod-detail .detail-info > a",
                    ),
                ),
            ),
        )

        assertEquals(2, subjects.size)
        assertEquals("Test Anime", subjects[0].name)
        assertEquals("https://ani.girigirilove.com/GV123/", subjects[0].fullUrl)
        assertEquals("Test & Season 2", subjects[1].name)
        assertEquals("https://ani.girigirilove.com/GV456/", subjects[1].fullUrl)
    }

    @Test
    fun `non girigiri search keeps original request`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("example.com", request.url.host)
            assertEquals("text/html", request.headers[HttpHeaders.Accept])
            respond("<html><body>ok</body></html>")
        })
        val engine = DefaultSelectorMediaSourceEngine(client.asScopedHttpClient())

        val result = engine.searchSubjects(
            searchUrl = "https://example.com/search?wd={keyword}",
            subjectName = "test",
            useOnlyFirstWord = false,
            removeSpecial = false,
        )

        assertNotNull(result.document)
    }
}
