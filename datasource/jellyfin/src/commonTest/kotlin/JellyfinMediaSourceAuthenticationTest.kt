/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.paging.PagedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JellyfinMediaSourceAuthenticationTest {
    @Test
    fun `authentication parameters are visible only for their mode`() {
        assertEquals(
            JellyfinMediaSource.Parameters.authMode.name,
            JellyfinMediaSource.Parameters.userId.visibleWhen?.parameterName,
        )
        assertEquals(
            setOf(JellyfinMediaSource.AUTH_MODE_API_KEY),
            JellyfinMediaSource.Parameters.userId.visibleWhen?.acceptedValues,
        )
        assertEquals(
            setOf(JellyfinMediaSource.AUTH_MODE_API_KEY),
            JellyfinMediaSource.Parameters.apikey.visibleWhen?.acceptedValues,
        )
        assertEquals(
            setOf(JellyfinMediaSource.AUTH_MODE_USERNAME_PASSWORD),
            JellyfinMediaSource.Parameters.username.visibleWhen?.acceptedValues,
        )
        assertEquals(
            setOf(JellyfinMediaSource.AUTH_MODE_USERNAME_PASSWORD),
            JellyfinMediaSource.Parameters.password.visibleWhen?.acceptedValues,
        )
        assertTrue(JellyfinMediaSource.Parameters.password.isSensitive)
    }

    @Test
    fun `password mode logs in once and reuses the returned session`() = runTest {
        var loginCount = 0
        var itemsCount = 0
        val source = JellyfinMediaSource(
            config = passwordConfig(),
            client = mockClient { request ->
                when (request.url.encodedPath) {
                    "/Users/AuthenticateByName" -> {
                        loginCount++
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(
                            """MediaBrowser Client="Animeko", Device="Animeko", DeviceId="animeko-source-instance", Version="1.0"""",
                            request.headers[HttpHeaders.Authorization],
                        )
                        assertFalse(request.url.toString().contains("test-password"))
                        val body = request.jsonBody()
                        assertEquals("test-user", body.getValue("Username").jsonPrimitive.content)
                        assertEquals("test-password", body.getValue("Pw").jsonPrimitive.content)
                        respondJson(
                            """
                            {
                              "AccessToken": "session-token",
                              "User": { "Id": "session-user-id" }
                            }
                            """.trimIndent(),
                        )
                    }

                    "/Items" -> {
                        itemsCount++
                        assertEquals("session-user-id", request.url.parameters["userId"])
                        assertEquals(
                            """MediaBrowser Client="Animeko", Device="Animeko", DeviceId="animeko-source-instance", Version="1.0", Token="session-token"""",
                            request.headers[HttpHeaders.Authorization],
                        )
                        respondJson("""{"Items":[]}""")
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
            instanceId = "source-instance",
        )

        assertEquals(ConnectionStatus.SUCCESS, source.checkConnection())
        assertEquals(ConnectionStatus.SUCCESS, source.checkConnection())
        assertEquals(1, loginCount)
        assertEquals(2, itemsCount)
    }

    @Test
    fun `concurrent requests share one login session`() = runTest {
        var loginCount = 0
        var itemsCount = 0
        val source = JellyfinMediaSource(
            config = passwordConfig(),
            client = mockClient { request ->
                when (request.url.encodedPath) {
                    "/Users/AuthenticateByName" -> {
                        loginCount++
                        respondJson(
                            """
                            {
                              "AccessToken": "session-token",
                              "User": { "Id": "session-user-id" }
                            }
                            """.trimIndent(),
                        )
                    }

                    "/Items" -> {
                        itemsCount++
                        respondJson("""{"Items":[]}""")
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
        )

        coroutineScope {
            List(8) {
                async { source.checkConnection() }
            }.awaitAll().forEach { status ->
                assertEquals(ConnectionStatus.SUCCESS, status)
            }
        }

        assertEquals(1, loginCount)
        assertEquals(8, itemsCount)
    }

    @Test
    fun `password mode reauthenticates once after an unauthorized response`() = runTest {
        var loginCount = 0
        var itemsCount = 0
        val source = JellyfinMediaSource(
            config = passwordConfig(),
            client = mockClient { request ->
                when (request.url.encodedPath) {
                    "/Users/AuthenticateByName" -> {
                        loginCount++
                        respondJson(
                            """
                            {
                              "AccessToken": "session-token-$loginCount",
                              "User": { "Id": "session-user-id" }
                            }
                            """.trimIndent(),
                        )
                    }

                    "/Items" -> {
                        itemsCount++
                        when (itemsCount) {
                            1 -> {
                                assertTrue(
                                    request.headers[HttpHeaders.Authorization]
                                        .orEmpty()
                                        .contains("""Token="session-token-1""""),
                                )
                                respondJson("""{"error":"invalid token"}""", HttpStatusCode.Unauthorized)
                            }

                            2 -> {
                                assertTrue(
                                    request.headers[HttpHeaders.Authorization]
                                        .orEmpty()
                                        .contains("""Token="session-token-2""""),
                                )
                                respondJson("""{"Items":[]}""")
                            }

                            else -> error("Unexpected Items request #$itemsCount")
                        }
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
        )

        assertEquals(ConnectionStatus.SUCCESS, source.checkConnection())
        assertEquals(2, loginCount)
        assertEquals(2, itemsCount)
    }

    @Test
    fun `password mode does not retry indefinitely when reauthentication is rejected`() = runTest {
        var loginCount = 0
        var itemsCount = 0
        val source = JellyfinMediaSource(
            config = passwordConfig(),
            client = mockClient { request ->
                when (request.url.encodedPath) {
                    "/Users/AuthenticateByName" -> {
                        loginCount++
                        respondJson(
                            """
                            {
                              "AccessToken": "session-token-$loginCount",
                              "User": { "Id": "session-user-id" }
                            }
                            """.trimIndent(),
                        )
                    }

                    "/Items" -> {
                        itemsCount++
                        respondJson("""{"error":"not authorized"}""", HttpStatusCode.Unauthorized)
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
        )

        assertEquals(ConnectionStatus.FAILED, source.checkConnection())
        assertEquals(2, loginCount)
        assertEquals(2, itemsCount)
    }

    @Test
    fun `legacy configuration keeps using api key mode without login`() = runTest {
        var itemsCount = 0
        val source = JellyfinMediaSource(
            config = MediaSourceConfig(
                arguments = mapOf(
                    "baseUrl" to TEST_BASE_URL,
                    "userId" to "legacy-user-id",
                    "apikey" to "legacy-api-key",
                ),
            ),
            client = mockClient { request ->
                assertEquals("/Items", request.url.encodedPath)
                itemsCount++
                assertEquals("legacy-user-id", request.url.parameters["userId"])
                assertEquals(
                    """MediaBrowser Token="legacy-api-key"""",
                    request.headers[HttpHeaders.Authorization],
                )
                respondJson("""{"Items":[]}""")
            },
        )

        assertEquals(ConnectionStatus.SUCCESS, source.checkConnection())
        assertEquals(1, itemsCount)
    }

    @Test
    fun `emby keeps its existing api key authorization`() = runTest {
        var itemsCount = 0
        val source = EmbyMediaSource(
            config = MediaSourceConfig(
                arguments = mapOf(
                    "baseUrl" to TEST_BASE_URL,
                    "userId" to "emby-user-id",
                    "apikey" to "emby-api-key",
                ),
            ),
            client = mockClient { request ->
                assertEquals("/Items", request.url.encodedPath)
                itemsCount++
                assertEquals("emby-user-id", request.url.parameters["userId"])
                assertEquals(
                    """MediaBrowser Token="emby-api-key"""",
                    request.headers[HttpHeaders.Authorization],
                )
                respondJson("""{"Items":[]}""")
            },
        )

        assertEquals(ConnectionStatus.SUCCESS, source.checkConnection())
        assertEquals(1, itemsCount)
    }

    @Test
    fun `fetch creates a playable download url from the session token`() = runTest {
        var loginCount = 0
        val source = JellyfinMediaSource(
            config = passwordConfig(),
            client = mockClient { request ->
                when (request.url.encodedPath) {
                    "/Users/AuthenticateByName" -> {
                        loginCount++
                        respondJson(
                            """
                            {
                              "AccessToken": "playback-session-token",
                              "User": { "Id": "session-user-id" }
                            }
                            """.trimIndent(),
                        )
                    }

                    "/Items" -> respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Episode 1",
                              "SeriesName": "Test Anime",
                              "Id": "episode-1",
                              "IndexNumber": 1,
                              "Type": "Episode"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            },
        )

        val pagedSource = assertIs<PagedSource<MediaMatch>>(source.fetch(testRequest()))
        val results = assertNotNull(pagedSource.nextPageOrNull())
        val media = results.single().media
        val download = assertIs<ResourceLocation.HttpStreamingFile>(media.download)

        assertEquals(
            "$TEST_BASE_URL/Items/episode-1/Download?ApiKey=playback-session-token",
            download.uri,
        )
        assertEquals(1, loginCount)
    }

    @Test
    fun `rejected login reports a failed connection without querying items`() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
            var loginCount = 0
            val source = JellyfinMediaSource(
                config = passwordConfig(),
                client = mockClient { request ->
                    assertEquals("/Users/AuthenticateByName", request.url.encodedPath)
                    loginCount++
                    respondJson("""{"error":"invalid credentials"}""", status)
                },
            )

            assertEquals(ConnectionStatus.FAILED, source.checkConnection())
            val pagedSource = assertIs<PagedSource<MediaMatch>>(source.fetch(testRequest()))
            assertFailsWith<JellyfinLoginException> {
                pagedSource.nextPageOrNull()
            }
            assertEquals(2, loginCount)
        }
    }

    private fun passwordConfig() = MediaSourceConfig(
        arguments = mapOf(
            "baseUrl" to TEST_BASE_URL,
            "authMode" to JellyfinMediaSource.AUTH_MODE_USERNAME_PASSWORD,
            "username" to "test-user",
            "password" to "test-password",
        ),
    )

    private fun testRequest() = MediaFetchRequest(
        subjectId = "1",
        episodeId = "1",
        subjectNameCN = "Test Anime",
        subjectNames = listOf("Test Anime"),
        episodeSort = EpisodeSort(1),
        episodeName = "Episode 1",
    )

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }.asScopedHttpClient()

    private fun HttpRequestData.jsonBody() = Json.parseToJsonElement(
        assertIs<OutgoingContent.ByteArrayContent>(body).bytes().decodeToString(),
    ).jsonObject

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        const val TEST_BASE_URL = "https://jellyfin.example.test"
    }
}
