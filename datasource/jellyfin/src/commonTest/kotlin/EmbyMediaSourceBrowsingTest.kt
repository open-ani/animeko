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
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmbyMediaSourceBrowsingTest {
    @Test
    fun `season requests are bounded and keep server order when responses finish out of order`() = runTest {
        var active = 0
        var peak = 0
        val tracker = Mutex()
        client { request ->
            when {
                request.url.encodedPath.endsWith("/Items/series") ->
                    jsonResponse("""{"Name":"Series","Id":"series","Type":"Series"}""")
                request.url.encodedPath.endsWith("Seasons") -> {
                    val seasons = (1..8).joinToString(",") {
                        """{"Name":"Season $it","Id":"$it","Type":"Season"}"""
                    }
                    jsonResponse("""{"Items":[$seasons]}""")
                }
                else -> {
                    val season = request.url.parameters["ParentId"]!!.toInt()
                    tracker.withLock {
                        active++
                        peak = maxOf(peak, active)
                    }
                    try {
                        delay((9 - season) * 10L)
                        jsonResponse("""{"Items":[]}""")
                    } finally {
                        tracker.withLock { active-- }
                    }
                }
            }
        }.use { client ->
            val channels = source(client).browseSubject(BrowseSubject("Series", "https://example.test/emby/Items/series"))
            assertEquals((1..8).map { it.toString() }, channels.map { it.name })
            assertEquals(4, peak)
        }
    }

    @Test
    fun `manual browsing lists a movie separately and selects season 37`() = runTest {
        client { request ->
            when {
                request.url.parameters["SearchTerm"] != null -> {
                    assertEquals("辛普森一家", request.url.parameters["SearchTerm"])
                    assertEquals("Series,Season,Movie", request.url.parameters["IncludeItemTypes"])
                    jsonResponse("""{"Items":[
                        {"Name":"辛普森一家大电影","Id":"movie","Type":"Movie","ProductionYear":2007},
                        {"Name":"辛普森一家","Id":"series","Type":"Series","ProductionYear":1989},
                        {"Name":"第 37 季","SeriesName":"辛普森一家","Id":"season-37","Type":"Season","ProductionYear":2025}
                    ]}""")
                }

                request.url.encodedPath == "/emby/Items/series" ->
                    jsonResponse("""{"Name":"辛普森一家","Id":"series","Type":"Series"}""")

                request.url.encodedPath.endsWith("Seasons") -> jsonResponse("""{"Items":[
                    {"Name":"第 1 季","Id":"season-1","Type":"Season","IndexNumber":1},
                    {"Name":"第 37 季","Id":"season-37","Type":"Season","IndexNumber":37}
                ]}""")

                request.url.parameters["ParentId"] == "season-1" -> jsonResponse("""{"Items":[]}""")
                request.url.parameters["ParentId"] == "season-37" -> jsonResponse("""{"Items":[
                    {"Name":"终身坏胚子？","Id":"s37e05","Type":"Episode","IndexNumber":5},
                    {"Name":"巴特与弗林克","Id":"s37e06","Type":"Episode","IndexNumber":6}
                ]}""")

                request.url.encodedPath.endsWith("PlaybackInfo") ->
                    jsonResponse("""{"MediaSources":[{"DirectStreamUrl":"/videos/s37e05/original.mkv"}]}""")

                else -> error("Unexpected browse request: ${request.url.encodedPath}")
            }
        }.use { client ->
            val source = source(client)
            assertTrue(source.supportsBrowsing)
            val subjects = source.searchSubjects("辛普森一家")
            assertEquals(
                listOf("辛普森一家大电影 (2007)", "辛普森一家 (1989)", "辛普森一家 · 第 37 季 (2025)"),
                subjects.map { it.name },
            )
            val subject = subjects.single { it.url.endsWith("/series") }
            val channel = source.browseSubject(subject).single { it.label == "第 37 季" }
            assertEquals(listOf(EpisodeSort(5), EpisodeSort(6)), channel.episodes.map { it.episodeSort })
            val episode = channel.episodes.first()
            val media = source.createMedia(subject, channel.name, episode, EpisodeSort(12))
            assertEquals("server-one.s37e05", media.mediaId)
            assertEquals(EpisodeRange.single(EpisodeSort(12)), media.episodeRange)
            assertEquals("s37e05", assertIs<ResourceLocation.WebVideo>(media.download).apiParameters?.get("itemId"))
            assertEquals(media.mediaId, source.createMedia(subject, channel.name, episode, null).mediaId)
            val video = source.resolveApiVideo(media)!!
            assertEquals("/videos/s37e05/original.mkv", Url(video.m3u8Url).encodedPath)
            assertEquals("key", video.headers["X-Emby-Token"])
        }
    }

    @Test
    fun `manual search follows server pagination without filtering movie names`() = runTest {
        val offsets = mutableListOf<Int>()
        client { request ->
            val start = request.url.parameters["StartIndex"]!!.toInt()
            val limit = request.url.parameters["Limit"]!!.toInt()
            offsets += start
            val items = (start until minOf(start + limit, 101)).joinToString(",") {
                """{"Name":"Result $it","Id":"$it","Type":"Movie"}"""
            }
            jsonResponse("""{"Items":[$items]}""")
        }.use { client ->
            assertEquals(101, source(client).searchSubjects("unrelated keyword").size)
            assertEquals(listOf(0, 100), offsets)
        }
    }

    @Test
    fun `a selected season or movie opens directly and foreign URLs are rejected`() = runTest {
        client { request ->
            when {
                request.url.encodedPath.endsWith("/season-37") ->
                    jsonResponse("""{"Name":"第 37 季","Id":"season-37","Type":"Season"}""")
                request.url.encodedPath.endsWith("/movie") ->
                    jsonResponse("""{"Name":"Movie","Id":"movie","Type":"Movie"}""")
                request.url.parameters["ParentId"] == "season-37" ->
                    jsonResponse("""{"Items":[{"Name":"Episode","Id":"episode","Type":"Episode","IndexNumber":1}]}""")
                else -> error("Unexpected browse request")
            }
        }.use { client ->
            val source = source(client)
            val season = BrowseSubject("Season", "https://example.test/emby/Items/season-37")
            assertEquals(1, source.browseSubject(season).single().episodes.size)
            val movie = BrowseSubject("Movie", "https://example.test/emby/Items/movie")
            val episode = source.browseSubject(movie).single().episodes.single()
            assertEquals("server-one.movie", source.createMedia(movie, null, episode, null).mediaId)
            assertFailsWith<IllegalArgumentException> {
                source.browseSubject(movie.copy(url = "https://foreign.test/emby/Items/movie"))
            }
            assertFailsWith<IllegalArgumentException> {
                source.createMedia(movie, null, episode.copy(url = "https://example.test/other/Items/movie"), null)
            }
        }
    }

    private fun source(client: HttpClient) = EmbyMediaSource(
        MediaSourceConfig(arguments = mapOf("baseUrl" to "https://example.test/emby", "userId" to "user", "apikey" to "key")),
        client.asScopedHttpClient(),
        "server-one",
    )

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine { request ->
            assertEquals("user", request.url.parameters["UserId"])
            handler(request)
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun MockRequestHandleScope.jsonResponse(body: String) =
        respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
}
