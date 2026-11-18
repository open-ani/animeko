/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EmbyMediaSourceAuthenticationTest {
    @Test
    fun `password playback authenticates with a token header without login metadata`() = runTest {
        mockClient { request ->
            when {
                request.url.encodedPath.endsWith("AuthenticateByName") ->
                    jsonResponse("""{"AccessToken":"playback-token","User":{"Id":"user"}}""")

                request.url.encodedPath.endsWith("PlaybackInfo") -> {
                    assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().contains("DeviceId=\"animeko-server-one\""))
                    jsonResponse("""{"MediaSources":[{"DirectStreamUrl":"/videos/123/original.mkv","RequiredHttpHeaders":{"Referer":"https://example.test/"}}]}""")
                }

                else -> jsonResponse("""{"Items":[{"Name":"Movie","Id":"123","Type":"Movie"}]}""")
            }
        }.use { client ->
            val source = EmbyMediaSource(passwordConfig(), client.asScopedHttpClient(), "server-one")
            val media = source.fetch(movieRequest()).results.toList().single().media
            val video = source.resolveApiVideo(media)!!
            assertEquals("/videos/123/original.mkv", Url(video.m3u8Url).encodedPath)
            assertEquals(
                mapOf(HttpHeaders.Referrer to "https://example.test/", "X-Emby-Token" to "playback-token"),
                video.headers,
            )
        }
    }

    @Test
    fun `playback uses the server direct URL and required headers with PascalCase parameters`() = runTest {
        var playbackRequests = 0
        mockClient { request ->
            assertEquals("user", request.url.parameters["UserId"])
            val queryNames = request.url.encodedQuery.split('&').map { it.substringBefore('=') }
            assertTrue("UserId" in queryNames)
            assertFalse("userId" in queryNames)
            if (request.url.encodedPath.endsWith("PlaybackInfo")) {
                playbackRequests++
                jsonResponse("""{"MediaSources":[{"DirectStreamUrl":"/videos/123/original.mkv?MediaSourceId=source","AddApiKeyToDirectStreamUrl":true,"RequiredHttpHeaders":{"Referer":"https://example.test/"}}]}""")
            } else {
                assertEquals("false", request.url.parameters["EnableImages"])
                jsonResponse("""{"Items":[{"Name":"Movie","Id":"123","Type":"Movie"}]}""")
            }
        }.use { client ->
            val source = EmbyMediaSource(apiKeyConfig(), client.asScopedHttpClient(), "instance")
            val media = source.fetch(movieRequest()).results.toList().single().media
            val video = source.resolveApiVideo(media)!!
            val url = Url(video.m3u8Url)
            assertEquals("/videos/123/original.mkv", url.encodedPath)
            assertEquals("source", url.parameters["MediaSourceId"])
            assertEquals("key", url.parameters["api_key"])
            assertEquals("https://example.test/", video.headers[HttpHeaders.Referrer])
            assertEquals("key", video.headers["X-Emby-Token"])
            source.resolveApiVideo(media)
            assertEquals(2, playbackRequests)
        }
    }

    @Test
    fun `external playback URL receives only server supplied headers`() = runTest {
        mockClient { request ->
            if (request.url.encodedPath.endsWith("PlaybackInfo")) {
                jsonResponse("""{"MediaSources":[{"DirectStreamUrl":"https://cdn.test/video.mkv?signature=abc","AddApiKeyToDirectStreamUrl":true,"RequiredHttpHeaders":{"User-Agent":"VideoClient"}}]}""")
            } else {
                jsonResponse("""{"Items":[{"Name":"Movie","Id":"123","Type":"Movie"}]}""")
            }
        }.use { client ->
            val source = EmbyMediaSource(apiKeyConfig(), client.asScopedHttpClient())
            val media = source.fetch(movieRequest()).results.toList().single().media
            val video = source.resolveApiVideo(media)!!
            assertEquals("https://cdn.test/video.mkv?signature=abc", video.m3u8Url)
            assertEquals(mapOf(HttpHeaders.UserAgent to "VideoClient"), video.headers)
        }
    }

    @Test
    fun `servers without playback information use the static stream`() = runTest {
        mockClient { request ->
            if (request.url.encodedPath.endsWith("PlaybackInfo")) {
                jsonResponse("{}", HttpStatusCode.NotFound)
            } else {
                jsonResponse("""{"Items":[{"Name":"Movie","Id":"123","Type":"Movie"}]}""")
            }
        }.use { client ->
            val source = EmbyMediaSource(apiKeyConfig(), client.asScopedHttpClient())
            val media = source.fetch(movieRequest()).results.toList().single().media
            val video = source.resolveApiVideo(media)!!
            assertEquals("/emby/Videos/123/stream", Url(video.m3u8Url).encodedPath)
            assertEquals("key", Url(video.m3u8Url).parameters["api_key"])
        }
    }

    @Test
    fun `empty password login is shared by concurrent requests and used for playback`() = runTest {
        var logins = 0
        mockClient { request ->
            when (request.url.encodedPath) {
                "/emby/Users/AuthenticateByName" -> {
                    logins++
                    val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                    assertEquals("test-user", body.getValue("Username").jsonPrimitive.content)
                    assertEquals("", body.getValue("Pw").jsonPrimitive.content)
                    assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().contains("animeko-server-one"))
                    jsonResponse("""{"AccessToken":"token+with&symbols","User":{"Id":"returned-user"}}""")
                }

                "/emby/Items" -> {
                    assertEquals("returned-user", request.url.parameters["UserId"])
                    val queryNames = request.url.encodedQuery.split('&').map { it.substringBefore('=') }
                    assertTrue("UserId" in queryNames)
                    assertFalse("userId" in queryNames)
                    assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().contains("Token=\"token+with&symbols\""))
                    jsonResponse("""{"Items":[{"Name":"Movie","Id":"123","Type":"Movie"}]}""")
                }

                "/emby/Items/123/PlaybackInfo" -> jsonResponse("""{"MediaSources":[]}""")

                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }.use { client ->
            val source = EmbyMediaSource(passwordConfig(), client.asScopedHttpClient(), "server-one")
            val statuses = List(3) { async { source.checkConnection() } }.awaitAll()
            assertTrue(statuses.all { it == ConnectionStatus.SUCCESS })
            val media = source.fetch(movieRequest()).results.toList().single().media
            val url = Url(source.resolveApiVideo(media)!!.m3u8Url)
            assertEquals("/emby/Videos/123/stream", url.encodedPath)
            assertEquals("true", url.parameters["static"])
            assertEquals("token+with&symbols", url.parameters["api_key"])
            assertEquals("server-one", media.mediaSourceId)
            assertEquals("server-one.123", media.mediaId)
            assertEquals(1, logins)
            assertEquals("My server", source.info.displayName)
        }
    }

    @Test
    fun `expired session is renewed once`() = runTest {
        var logins = 0
        var items = 0
        mockClient { request ->
            if (request.url.encodedPath.endsWith("AuthenticateByName")) {
                logins++
                jsonResponse("""{"AccessToken":"token-$logins","User":{"Id":"user"}}""")
            } else {
                items++
                if (items == 1) jsonResponse("{}", HttpStatusCode.Unauthorized)
                else {
                    assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().contains("token-2"))
                    jsonResponse("""{"Items":[]}""")
                }
            }
        }.use { client ->
            assertEquals(ConnectionStatus.SUCCESS, EmbyMediaSource(passwordConfig(), client.asScopedHttpClient()).checkConnection())
            assertEquals(2, logins)
            assertEquals(2, items)
        }
    }

    @Test
    fun `repeated unauthorized response stops after one renewal`() = runTest {
        var logins = 0
        var items = 0
        mockClient { request ->
            if (request.url.encodedPath.endsWith("AuthenticateByName")) {
                logins++
                jsonResponse("""{"AccessToken":"token-$logins","User":{"Id":"user"}}""")
            } else {
                items++
                jsonResponse("{}", HttpStatusCode.Unauthorized)
            }
        }.use { client ->
            assertEquals(ConnectionStatus.FAILED, EmbyMediaSource(passwordConfig(), client.asScopedHttpClient()).checkConnection())
            assertEquals(2, logins)
            assertEquals(2, items)
        }
    }

    @Test
    fun `rejected login does not request the library`() = runTest {
        var logins = 0
        mockClient { request ->
            assertTrue(request.url.encodedPath.endsWith("AuthenticateByName"))
            logins++
            jsonResponse("{}", HttpStatusCode.Forbidden)
        }.use { client ->
            assertEquals(ConnectionStatus.FAILED, EmbyMediaSource(passwordConfig(), client.asScopedHttpClient()).checkConnection())
            assertEquals(1, logins)
        }
    }

    @Test
    fun `factories keep server names and resource identities separate`() = runTest {
        mockClient { jsonResponse("""{"Items":[{"Name":"Movie","Id":"123","Type":"Movie"}]}""") }.use { client ->
            val factory = EmbyMediaSource.Factory()
            fun config(name: String) = MediaSourceConfig(arguments = mapOf("name" to name, "userId" to "user", "apikey" to "key"))
            val first = factory.create("one", config("First"), client.asScopedHttpClient())
            val second = factory.create("two", config("Second"), client.asScopedHttpClient())
            assertEquals("First", first.info.displayName)
            assertEquals("Second", second.info.displayName)
            assertNotEquals(first.mediaSourceId, second.mediaSourceId)
            assertNotEquals(first.fetch(movieRequest()).results.toList().single().media.mediaId,
                            second.fetch(movieRequest()).results.toList().single().media.mediaId)
            assertEquals("Emby", factory.create("empty", config("  "), client.asScopedHttpClient()).info.displayName)
        }
    }

    @Test
    fun `password mode has optional password and conditional credentials`() {
        assertEquals(setOf(EmbyMediaSource.AUTH_MODE_API_KEY), EmbyMediaSource.Parameters.userId.visibleWhen?.acceptedValues)
        assertEquals(setOf(EmbyMediaSource.AUTH_MODE_USERNAME_PASSWORD), EmbyMediaSource.Parameters.username.visibleWhen?.acceptedValues)
        assertTrue(EmbyMediaSource.Parameters.password.validate(""))
        assertTrue(EmbyMediaSource.Parameters.password.isSensitive)
        assertFalse(EmbyMediaSource.Parameters.username.validate(""))
    }

    private fun passwordConfig() = MediaSourceConfig(
        arguments = mapOf(
            "name" to " My server ",
            "baseUrl" to "https://example.test/emby/",
            "authMode" to EmbyMediaSource.AUTH_MODE_USERNAME_PASSWORD,
            "username" to "test-user",
        ),
    )

    private fun apiKeyConfig() = MediaSourceConfig(
        arguments = mapOf("baseUrl" to "https://example.test/emby/", "userId" to "user", "apikey" to "key"),
    )

    private fun movieRequest() = MediaFetchRequest(
        subjectId = "1", episodeId = "2", subjectNameCN = "Movie", subjectNames = listOf("Movie"),
        episodeSort = EpisodeSort(1), episodeName = "Movie",
    )

    private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
}
