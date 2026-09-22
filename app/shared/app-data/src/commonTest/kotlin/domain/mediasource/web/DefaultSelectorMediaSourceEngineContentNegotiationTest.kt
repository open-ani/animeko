/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultSelectorMediaSourceEngineContentNegotiationTest {
    private val jsonConfig = SelectorSearchConfig(
        searchUrl = "https://example.com/search?keyword={keyword}",
        rawBaseUrl = "https://example.com/subjects/",
        subjectFormatId = SelectorSubjectFormatJsonPathIndexed.id,
        selectorSubjectFormatJsonPathIndexed = SelectorSubjectFormatJsonPathIndexed.Config(
            selectNames = "$[*].title",
            selectLinks = "$[*].id",
        ),
        selectorChannelFormatNoChannel = SelectorChannelFormatNoChannel.Config(selectEpisodes = ".episodes a"),
    )

    private fun createEngine(handler: MockRequestHandler): DefaultSelectorMediaSourceEngine {
        val client = HttpClient(MockEngine(handler)) {
            expectSuccess = true
        }
        return DefaultSelectorMediaSourceEngine(client.asScopedHttpClient(), EmptyCoroutineContext)
    }

    @Test
    fun `json search negotiates JSON and resolves numeric subject ids`() = runTest {
        val engine = createEngine { request ->
            val acceptsJson = request.headers.getAll(HttpHeaders.Accept).orEmpty()
                .flatMap { it.split(',') }
                .any { ContentType.parse(it).withoutParameters() == ContentType.Application.Json }
            if (acceptsJson) {
                respond(
                    """[{"id":123,"title":"Example Subject"}]""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("JSON response required", HttpStatusCode.NotAcceptable)
            }
        }

        val result = engine.searchSubjects(
            searchUrl = jsonConfig.searchUrl,
            subjectName = "Example Subject",
            useOnlyFirstWord = false,
            removeSpecial = false,
            subjectFormatId = jsonConfig.subjectFormatId,
        )
        val subject = assertNotNull(engine.selectSubjects(assertNotNull(result.document), jsonConfig)).single()

        assertNull(result.captchaKind)
        assertEquals("Example Subject", subject.name)
        assertEquals("123", subject.internalId)
        assertEquals("https://example.com/subjects/123", subject.fullUrl)
    }

    @Test
    fun `json search still accepts and detects HTML captcha pages`() = runTest {
        val engine = createEngine { request ->
            assertEquals(
                listOf("application/json", "text/html; q=0.9"),
                request.headers.getAll(HttpHeaders.Accept),
            )
            respond(
                "<html><body><div class=\"cf-turnstile\"></div></body></html>",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }

        val result = engine.searchSubjects(
            searchUrl = jsonConfig.searchUrl,
            subjectName = "test",
            useOnlyFirstWord = false,
            removeSpecial = false,
            subjectFormatId = jsonConfig.subjectFormatId,
        )

        assertEquals(WebCaptchaKind.CloudflareTurnstile, result.captchaKind)
        assertNull(result.document)
    }

    @Test
    fun `default search keeps HTML accept and parses subjects`() = runTest {
        val engine = createEngine { request ->
            assertEquals(listOf("text/html"), request.headers.getAll(HttpHeaders.Accept))
            respond(
                """<html><body><a class="subject" href="/subjects/123">Example Subject</a></body></html>""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }
        val config = SelectorSearchConfig(
            searchUrl = jsonConfig.searchUrl,
            selectorSubjectFormatA = SelectorSubjectFormatA.Config(selectLists = "a.subject"),
        )

        val result = engine.searchSubjects(config.searchUrl, "test", useOnlyFirstWord = false, removeSpecial = false)
        val subject = assertNotNull(engine.selectSubjects(assertNotNull(result.document), config)).single()

        assertEquals("Example Subject", subject.name)
        assertEquals("https://example.com/subjects/123", subject.fullUrl)
    }

    @Test
    fun `subject details keep HTML accept with JSON search config`() = runTest {
        val engine = createEngine { request ->
            assertEquals(listOf("text/html"), request.headers.getAll(HttpHeaders.Accept))
            respond(
                """<html><body><div class="episodes"><a href="/play/123/1">第1集</a></div></body></html>""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }
        val subjectUrl = "https://example.com/subjects/123"

        val document = assertNotNull(engine.searchEpisodes(subjectUrl))
        val episode = assertNotNull(engine.selectEpisodes(document, subjectUrl, jsonConfig)).episodes.single()

        assertEquals(EpisodeSort(1), episode.episodeSortOrEp)
        assertEquals("https://example.com/play/123/1", episode.playUrl)
    }
}
