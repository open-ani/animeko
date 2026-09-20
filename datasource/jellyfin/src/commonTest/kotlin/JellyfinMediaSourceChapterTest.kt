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
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaChapter
import me.him188.ani.datasources.api.MediaChapterKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JellyfinMediaSourceChapterTest {
    @Test
    fun `native opening survives plugin network failure`() = runTest {
        var pluginRequested = false
        val source = createSource { request ->
            when (request.url.encodedPath) {
                "/Items" -> respondJson(items("episode-1"))
                "/MediaSegments/episode-1" -> respondJson(INTRO_ONLY)
                "/Episode/episode-1/IntroSkipperSegments" -> {
                    pluginRequested = true
                    throw IOException("Plugin connection was reset")
                }

                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }

        val media = source.fetch(query()).results.toList().single().media

        assertTrue(pluginRequested)
        assertEquals(listOf(EMBEDDED_CHAPTER, OPENING), media.extraFiles.chapters)
    }

    @Test
    fun `completed candidate retains segments when another candidate times out`() = runTest {
        var slowRequested = false
        val source = createSource { request ->
            when (request.url.encodedPath) {
                "/Items" -> respondJson(items("fast", "slow"))
                "/MediaSegments/fast" -> respondJson(INTRO_AND_OUTRO)
                "/MediaSegments/slow" -> {
                    slowRequested = true
                    awaitCancellation()
                }

                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }

        val matches = source.fetch(query()).results.toList().associateBy { it.media.mediaId }

        assertTrue(slowRequested)
        assertEquals(5_000L, testScheduler.currentTime)
        assertEquals(setOf("fast", "slow"), matches.keys)
        assertEquals(
            listOf(EMBEDDED_CHAPTER, OPENING, ENDING),
            matches.getValue("fast").media.extraFiles.chapters,
        )
        assertEquals(listOf(EMBEDDED_CHAPTER), matches.getValue("slow").media.extraFiles.chapters)
    }

    @Test
    fun `native opening survives a timeout while filling the missing ending`() = runTest {
        var pluginRequested = false
        val source = createSource { request ->
            when (request.url.encodedPath) {
                "/Items" -> respondJson(items("episode-1"))
                "/MediaSegments/episode-1" -> respondJson(INTRO_ONLY)
                "/Episode/episode-1/IntroSkipperSegments" -> {
                    pluginRequested = true
                    awaitCancellation()
                }

                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }

        val media = source.fetch(query()).results.toList().single().media

        assertTrue(pluginRequested)
        assertEquals(5_000L, testScheduler.currentTime)
        assertEquals(listOf(EMBEDDED_CHAPTER, OPENING), media.extraFiles.chapters)
    }

    @Test
    fun `chapter authentication timeout returns media without starting another login`() = runTest {
        var loginCount = 0
        val source = createSource(password = true) { request ->
            when (request.url.encodedPath) {
                "/Users/AuthenticateByName" -> {
                    loginCount++
                    if (loginCount > 1) awaitCancellation()
                    respondJson(login("token-1"))
                }

                "/Items" -> respondJson(items("episode-1"))
                "/MediaSegments/episode-1" -> respondJson("{}", HttpStatusCode.Unauthorized)
                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }

        val matches = withTimeoutOrNull(6_000L) { source.fetch(query()).results.toList() }
        val media = assertNotNull(matches).single().media

        assertEquals(2, loginCount)
        assertEquals(5_000L, testScheduler.currentTime)
        assertEquals(listOf(EMBEDDED_CHAPTER), media.extraFiles.chapters)
        assertEquals(
            "$BASE_URL/Items/episode-1/Download?ApiKey=token-1",
            assertIs<ResourceLocation.HttpStreamingFile>(media.download).uri,
        )
    }

    @Test
    fun `completed authentication refresh survives another candidate timeout`() = runTest {
        var loginCount = 0
        var fastRequestCount = 0
        val source = createSource(password = true) { request ->
            when (request.url.encodedPath) {
                "/Users/AuthenticateByName" -> respondJson(login("token-${++loginCount}"))
                "/Items" -> respondJson(items("fast", "slow"))
                "/MediaSegments/fast" -> {
                    if (++fastRequestCount == 1) {
                        respondJson("{}", HttpStatusCode.Unauthorized)
                    } else {
                        assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().contains("token-2"))
                        respondJson(INTRO_AND_OUTRO)
                    }
                }

                "/MediaSegments/slow" -> awaitCancellation()
                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }

        val matches = source.fetch(query()).results.toList()

        assertEquals(2, loginCount)
        assertEquals(2, fastRequestCount)
        assertEquals(5_000L, testScheduler.currentTime)
        assertEquals(setOf("fast", "slow"), matches.map { it.media.mediaId }.toSet())
        matches.forEach { match ->
            assertEquals(
                "$BASE_URL/Items/${match.media.mediaId}/Download?ApiKey=token-2",
                assertIs<ResourceLocation.HttpStreamingFile>(match.media.download).uri,
            )
        }
        assertEquals(
            listOf(EMBEDDED_CHAPTER, OPENING, ENDING),
            matches.single { it.media.mediaId == "fast" }.media.extraFiles.chapters,
        )
    }

    @Test
    fun `queued candidates share the time budget and keep embedded chapters`() = runTest {
        val ids = (1..6).map { "episode-$it" }
        val requestedIds = mutableSetOf<String>()
        var activeRequests = 0
        var maximumActiveRequests = 0
        val source = createSource { request ->
            when {
                request.url.encodedPath == "/Items" -> respondJson(items(*ids.toTypedArray()))
                request.url.encodedPath.startsWith("/MediaSegments/") -> {
                    requestedIds += request.url.encodedPath.substringAfterLast('/')
                    activeRequests++
                    maximumActiveRequests = maxOf(maximumActiveRequests, activeRequests)
                    try {
                        awaitCancellation()
                    } finally {
                        activeRequests--
                    }
                }

                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }

        val matches = source.fetch(query()).results.toList()

        assertEquals(5_000L, testScheduler.currentTime)
        assertEquals(4, requestedIds.size)
        assertEquals(4, maximumActiveRequests)
        assertEquals(0, activeRequests)
        assertEquals(ids.toSet(), matches.map { it.media.mediaId }.toSet())
        matches.forEach { assertEquals(listOf(EMBEDDED_CHAPTER), it.media.extraFiles.chapters) }
    }

    @Test
    fun `caller cancellation propagates during chapter enrichment`() = runTest {
        var chapterRequested = false
        val source = createSource { request ->
            when (request.url.encodedPath) {
                "/Items" -> respondJson(items("episode-1"))
                "/MediaSegments/episode-1" -> {
                    chapterRequested = true
                    delay(1_000L)
                    throw CancellationException("Chapter request cancelled")
                }

                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }

        val failure = assertFailsWith<CancellationException> { source.fetch(query()).results.toList() }

        assertTrue(chapterRequested)
        assertEquals("Chapter request cancelled", failure.message)
    }

    private fun TestScope.createSource(
        password: Boolean = false,
        handler: MockRequestHandler,
    ): JellyfinMediaSource {
        val arguments = if (password) {
            mapOf(
                "authMode" to JellyfinMediaSource.AUTH_MODE_USERNAME_PASSWORD,
                "username" to "test-user",
                "password" to "test-password",
            )
        } else {
            mapOf(
                "authMode" to JellyfinMediaSource.AUTH_MODE_API_KEY,
                "userId" to "user",
                "apikey" to "token",
            )
        }
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }.asScopedHttpClient()
        return JellyfinMediaSource(
            config = MediaSourceConfig(arguments = arguments + ("baseUrl" to BASE_URL)),
            client = client,
        )
    }

    private fun query() = MediaFetchRequest(
        subjectId = "1",
        episodeId = "1",
        subjectNameCN = "Test Anime",
        subjectNames = listOf("Test Anime"),
        episodeSort = EpisodeSort(1),
        episodeName = "Episode 1",
    )

    private fun items(vararg ids: String): String = ids.joinToString(
        prefix = """{"Items":[""",
        postfix = "]}",
    ) { id ->
        """
        {
          "Id": "$id", "Type": "Episode", "Name": "Episode 1", "SeriesName": "Test Anime",
          "IndexNumber": 1, "RunTimeTicks": 13000000000,
          "Chapters": [{"Name": "Chapter 1", "StartPositionTicks": 0}]
        }
        """.trimIndent()
    }

    private fun login(token: String) = """{"AccessToken":"$token","User":{"Id":"user"}}"""

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(content, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private companion object {
        const val BASE_URL = "https://jellyfin.example.test"
        const val INTRO_ONLY = """{"Items":[{"Type":"Intro","StartTicks":0,"EndTicks":900000000}]}"""
        val INTRO_AND_OUTRO = """
            {"Items":[
              {"Type":"Intro","StartTicks":0,"EndTicks":900000000},
              {"Type":"Outro","StartTicks":12000000000,"EndTicks":12900000000}
            ]}
        """.trimIndent()
        val EMBEDDED_CHAPTER = MediaChapter("Chapter 1", 1_300_000L, 0L)
        val OPENING = MediaChapter("OP", 90_000L, 0L, MediaChapterKind.OPENING)
        val ENDING = MediaChapter("ED", 90_000L, 1_200_000L, MediaChapterKind.ENDING)
    }
}
