/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * This source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GirigiriSearchRouteTest {
    @Test
    fun `uses vod api and evaluates response with existing selector`() = runTest {
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
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        val route = GirigiriSearchRoute(PageEvaluator())

        val result = route.fetch(
            url = "https://ani.girigirilove.com/search/-------------/?wd=test%20anime",
            expectation = PageExpectation.SearchResults(config),
            http = client.asScopedHttpClient(),
        )

        val subjects = assertIs<PageVerdict.Ok<List<WebSearchSubjectInfo>>>(result).value
        assertEquals(2, subjects.size)
        assertEquals("Test Anime", subjects[0].name)
        assertEquals("https://ani.girigirilove.com/GV123/", subjects[0].fullUrl)
        assertEquals("Test & Season 2", subjects[1].name)
        assertEquals("https://ani.girigirilove.com/GV456/", subjects[1].fullUrl)
    }

    @Test
    fun `does not intercept unrelated host or non-search expectation`() = runTest {
        val route = GirigiriSearchRoute(PageEvaluator())
        val client = HttpClient(MockEngine { error("request must not be sent") }).asScopedHttpClient()

        assertFalse(route.matches("example.com"))
        assertTrue(route.matches("ani.girigirilove.com"))
        assertNull(
            route.fetch(
                url = "https://ani.girigirilove.com/search/?wd=test",
                expectation = PageExpectation.AnyContent,
                http = client,
            ),
        )
    }

    private companion object {
        val config = SelectorSearchConfig(
            searchUrl = "https://ani.girigirilove.com/search/-------------/?wd={keyword}",
            subjectFormatId = SelectorSubjectFormatIndexed.id,
            selectorSubjectFormatIndexed = SelectorSubjectFormatIndexed.Config(
                selectNames = "body > .box-width .vod-detail .detail-info .slide-info-title",
                selectLinks = "body > .box-width .vod-detail .detail-info > a",
            ),
        )
    }
}
