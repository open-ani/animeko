/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.models.preference.OpenSubtitlesSettings

class OpenSubtitlesClientTest {
    @Test
    fun `anonymous search uses the official service without an API key or account credentials`() = runTest {
        val client = mockClient { request ->
            assertEquals("mcp.opensubtitles.com", request.url.host)
            assertNull(request.headers["Api-Key"])
            assertNull(request.headers["X-OpenSubtitles-Username"])
            assertNull(request.headers["X-OpenSubtitles-Password"])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("tools/call", body["method"]?.jsonPrimitive?.content)
            assertEquals("search_subtitles", body["params"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
            """{"jsonrpc":"2.0","id":1,"result":{"structuredContent":{"page":1,"total_pages":1,"results":[{"language":"en","release":"BD","files":[{"file_id":9,"file_name":"Anime.srt"}]}]}}}"""
        }
        try {
            val result = OpenSubtitlesClient(client, OpenSubtitlesSettings(username = "user", password = "password"))
                .search("Anime")
            assertEquals("Anime.srt", result.files.single().name)
        } finally { client.close() }
    }

    @Test
    fun `account login replaces the API key for downloads without sending credentials to the CDN`() = runTest {
        val client = mockClient { request ->
            when (request.url.host) {
                "mcp.opensubtitles.com" -> {
                    assertNull(request.headers["Api-Key"])
                    assertEquals("user", request.headers["X-OpenSubtitles-Username"])
                    assertEquals("password", request.headers["X-OpenSubtitles-Password"])
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    val params = body["params"]!!.jsonObject
                    assertEquals("download_subtitle", params["name"]?.jsonPrimitive?.content)
                    assertEquals("link", params["arguments"]?.jsonObject?.get("delivery")?.jsonPrimitive?.content)
                    assertFalse(body.toString().contains("password"))
                    """{"jsonrpc":"2.0","id":1,"result":{"structuredContent":{"link":"https://download.test/caption.srt"}}}"""
                }
                "download.test" -> {
                    assertNull(request.headers["Api-Key"])
                    assertNull(request.headers[HttpHeaders.Authorization])
                    assertNull(request.headers["X-OpenSubtitles-Username"])
                    assertNull(request.headers["X-OpenSubtitles-Password"])
                    "1\n00:00:01,000 --> 00:00:02,000\nCaption"
                }
                else -> error("Unexpected host")
            }
        }
        try {
            val settings = OpenSubtitlesSettings(username = "user", password = "password")
            assertTrue(settings.isConfigured)
            assertTrue(OpenSubtitlesClient(client, settings).download(OpenSubtitleFile(1, "caption.srt", "en", "")).decodeToString().endsWith("Caption"))
        } finally { client.close() }
    }

    @Test
    fun `disabled search never sends requests and secrets are not in diagnostic strings`() = runTest {
        val settings = OpenSubtitlesSettings.Default
        assertEquals("", settings.apiKey)
        assertFalse(settings.isConfigured)
        val client = mockClient { error("No request expected") }
        try {
            assertFailsWith<IllegalArgumentException> { OpenSubtitlesClient(client, settings.copy(enabled = false)).search("Anime") }
            val configured = settings.copy(apiKey = "secret-key", password = "secret-password")
            assertFalse(configured.toString().contains("secret"))
        } finally { client.close() }
    }

    @Test
    fun `search maps files and sends episode filters and normalized languages without consuming downloads`() = runTest {
        val client = mockClient { request ->
            assertEquals("/api/v1/subtitles", request.url.encodedPath)
            assertEquals("secret", request.headers["Api-Key"])
            assertEquals("Animeko v1", request.headers[HttpHeaders.UserAgent])
            assertEquals("zh-cn,zh-tw", request.url.parameters["languages"])
            assertEquals("3", request.url.parameters["episode_number"])
            assertEquals("2", request.url.parameters["season_number"])
            assertEquals("2", request.url.parameters["page"])
            assertEquals("Anime Title", request.url.parameters["query"])
            """{"total_pages":4,"data":[{"attributes":{"language":"zh-cn","release":"BD", "files":[{"file_id":12,"file_name":"Anime.srt"},{"file_id":13,"file_name":"Anime.ass"}]}}]}"""
        }
        try {
            val result = OpenSubtitlesClient(client, OpenSubtitlesSettings(apiKey = "secret", languages = "zh,zh-cn"))
                .search(" Anime Title ", episode = 3, season = 2, page = 2)
            assertEquals(listOf(12, 13), result.files.map { it.id })
            assertEquals("zh-cn", result.files.first().language)
            assertEquals(4, result.totalPages)
        } finally { client.close() }
    }

    @Test
    fun `download uses the login API host and never sends credentials to the subtitle CDN`() = runTest {
        var requests = 0
        val client = mockClient { request ->
            requests++
            when (request.url.encodedPath) {
                "/api/v1/login" -> {
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("password", body["password"]?.jsonPrimitive?.content)
                    """{"token":"user-token","base_url":"vip-api.opensubtitles.com"}"""
                }
                "/api/v1/download" -> {
                    assertEquals("vip-api.opensubtitles.com", request.url.host)
                    assertEquals("Bearer user-token", request.headers[HttpHeaders.Authorization])
                    assertEquals("secret", request.headers["Api-Key"])
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("12", body["file_id"]?.jsonPrimitive?.content)
                    assertEquals("srt", body["sub_format"]?.jsonPrimitive?.content)
                    """{"link":"https://download.test/caption.srt","remaining":4}"""
                }
                "/caption.srt" -> {
                    assertNull(request.headers["Api-Key"])
                    assertNull(request.headers[HttpHeaders.Authorization])
                    "1\n00:00:01,000 --> 00:00:02,000\nHello"
                }
                else -> error("Unexpected request")
            }
        }
        try {
            val result = OpenSubtitlesClient(client, OpenSubtitlesSettings(apiKey = "secret", username = "user", password = "password"))
                .download(OpenSubtitleFile(12, "caption.srt", "en", ""))
            assertTrue(result.decodeToString().endsWith("Hello"))
            assertEquals(3, requests)
        } finally { client.close() }
    }

    @Test
    fun `quota failures preserve HTTP status without exposing credentials`() = runTest {
        val client = mockClient(HttpStatusCode.NotAcceptable) { "{}" }
        try {
            val failure = assertFailsWith<OpenSubtitlesException> {
                OpenSubtitlesClient(client, OpenSubtitlesSettings(apiKey = "secret"))
                    .download(OpenSubtitleFile(1, "caption.srt", "en", ""))
            }
            assertEquals(406, failure.status)
            assertFalse(failure.message.orEmpty().contains("secret"))
        } finally { client.close() }
    }

    @Test
    fun `untrusted login host and insecure download link are rejected`() = runTest {
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/login" -> """{"token":"token","base_url":"evil.test"}"""
                "/api/v1/download" -> """{"link":"http://download.test/caption.srt"}"""
                else -> error("Credentials must not reach an untrusted host")
            }
        }
        try {
            val settings = OpenSubtitlesSettings(apiKey = "secret")
            val file = OpenSubtitleFile(1, "caption.srt", "en", "")
            assertFailsWith<IllegalArgumentException> { OpenSubtitlesClient(client, settings).download(file) }
            assertFailsWith<IllegalArgumentException> {
                OpenSubtitlesClient(client, settings.copy(username = "user", password = "password")).download(file)
            }
        } finally { client.close() }
    }

    @Test
    fun `canonical API redirects stay on trusted hosts and CDN redirects carry no credentials`() = runTest {
        var searches = 0
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/subtitles" -> {
                            searches++
                            respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/api/v1/canonical"))
                        }
                        "/api/v1/canonical" -> {
                            assertEquals("api.opensubtitles.com", request.url.host)
                            assertEquals("secret", request.headers["Api-Key"])
                            respond("""{"data":[]}""")
                        }
                        "/api/v1/download" -> respond("""{"link":"https://download.test/redirect"}""")
                        "/redirect" -> {
                            assertNull(request.headers["Api-Key"])
                            assertNull(request.headers[HttpHeaders.Authorization])
                            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.test/caption.srt"))
                        }
                        "/caption.srt" -> {
                            assertNull(request.headers["Api-Key"])
                            assertNull(request.headers[HttpHeaders.Authorization])
                            respond("caption")
                        }
                        else -> error("Unexpected request")
                    }
                }
            }
            install(HttpTimeout)
            install(HttpRedirect) { allowHttpsDowngrade = true }
        }
        try {
            val api = OpenSubtitlesClient(client, OpenSubtitlesSettings(apiKey = "secret"))
            assertEquals(emptyList(), api.search("Anime").files)
            assertEquals(1, searches)
            assertEquals("caption", api.download(OpenSubtitleFile(1, "caption.srt", "en", "")).decodeToString())
        } finally { client.close() }
    }

    private fun TestScope.mockClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        response: (HttpRequestData) -> String,
    ) = HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                respond(response(request), status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        install(HttpTimeout)
        expectSuccess = false
        followRedirects = false
    }
}
