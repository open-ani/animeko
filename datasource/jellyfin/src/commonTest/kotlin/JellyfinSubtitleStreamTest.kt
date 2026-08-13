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
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.paging.PagedSource
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class JellyfinSubtitleStreamTest {
    @Test
    fun `external subtitle streams are mapped to mime types the player recognizes`() = runTest {
        val subtitles = fetchSubtitles()

        // PGSSUB (IsTextSubtitleStream = false) 与音轨都不在结果里
        assertEquals(3, subtitles.size)
        assertEquals(
            listOf("text/x-ssa", "application/x-subrip", "text/vtt"),
            subtitles.map { it.mimeType },
        )
    }

    @Test
    fun `subtitle uri uses a lowercase container extension`() = runTest {
        val subtitles = fetchSubtitles()

        // Jellyfin 按扩展名决定输出格式, 服务器返回的 Codec 可能是大写的 (如 "ASS")
        assertEquals("$TEST_BASE_URL/Videos/episode-1/episode-1/Subtitles/2/0/Stream.ass", subtitles[0].uri)
        assertEquals("$TEST_BASE_URL/Videos/episode-1/episode-1/Subtitles/3/0/Stream.subrip", subtitles[1].uri)
    }

    @Test
    fun `subtitle label falls back to language and then to a placeholder`() = runTest {
        val subtitles = fetchSubtitles()

        assertEquals("chs&jpn", subtitles[0].label) // Title
        assertEquals("eng", subtitles[1].label) // 没有 Title, 用 Language
        assertEquals("Unknown", subtitles[2].label) // 两者都没有
    }

    private suspend fun fetchSubtitles() = run {
        val source = JellyfinMediaSource(
            config = MediaSourceConfig(
                arguments = mapOf(
                    "baseUrl" to TEST_BASE_URL,
                    "userId" to "test-user-id",
                    "apikey" to "test-api-key",
                ),
            ),
            client = mockClient { respondJson(ITEMS_RESPONSE) },
        )

        val pagedSource = assertIs<PagedSource<MediaMatch>>(source.fetch(testRequest()))
        val results = assertNotNull(pagedSource.nextPageOrNull())
        results.single().media.extraFiles.subtitles
    }

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

        // 字段取自真实响应 (datasource/jellyfin/sample-item.json)
        val ITEMS_RESPONSE = """
            {
              "Items": [
                {
                  "Name": "Episode 1",
                  "SeriesName": "Test Anime",
                  "Id": "episode-1",
                  "IndexNumber": 1,
                  "Type": "Episode",
                  "MediaStreams": [
                    {
                      "Type": "Audio", "Codec": "flac", "Index": 1,
                      "IsExternal": false, "IsTextSubtitleStream": false
                    },
                    {
                      "Type": "Subtitle", "Codec": "ASS", "Index": 2, "Title": "chs&jpn", "Language": "chi",
                      "IsExternal": true, "IsTextSubtitleStream": true
                    },
                    {
                      "Type": "Subtitle", "Codec": "subrip", "Index": 3, "Language": "eng",
                      "IsExternal": true, "IsTextSubtitleStream": true
                    },
                    {
                      "Type": "Subtitle", "Codec": "webvtt", "Index": 4,
                      "IsExternal": true, "IsTextSubtitleStream": true
                    },
                    {
                      "Type": "Subtitle", "Codec": "PGSSUB", "Index": 5,
                      "IsExternal": true, "IsTextSubtitleStream": false
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
