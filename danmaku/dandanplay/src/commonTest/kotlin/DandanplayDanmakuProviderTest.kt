/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.dandanplay

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class DandanplayDanmakuProviderTest {
    @Test
    fun `fetchAutomatic uses Bangumi subject id mapping before existing matching`() = runTest {
        val seenPaths = mutableListOf<String>()
        val provider = createProvider { path ->
            seenPaths += path
            when (path) {
                "/api/v2/bangumi/bgmtv/113906" -> respondJson(
                    """
                    {
                      "success": true,
                      "errorCode": 0,
                      "errorMessage": "",
                      "bangumi": {
                        "animeTitle": "网球优等生 第二期",
                        "type": "tvseries",
                        "episodes": [
                          {
                            "episodeId": 108470001,
                            "episodeTitle": "第1话 世界与鸿沟",
                            "episodeNumber": "1",
                            "lastWatched": null,
                            "airDate": "2015-04-05T00:00:00"
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )

                "/api/v2/comment/108470001" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 113906,
                subjectName = "ベイビーステップ 第2シリーズ",
                episodeSort = EpisodeSort(1),
                episodeName = "世界と壁",
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Exact>(result.matchInfo.method)
        assertEquals("网球优等生 第二期", method.subjectTitle)
        assertEquals("第1话 世界与鸿沟", method.episodeTitle)
        assertEquals(
            listOf(
                "/api/v2/bangumi/bgmtv/113906",
                "/api/v2/comment/108470001",
            ),
            seenPaths,
        )
    }

    @Test
    fun `fetchAutomatic falls back to existing matching when Bangumi subject id mapping is missing`() = runTest {
        val seenPaths = mutableListOf<String>()
        val provider = createProvider { path ->
            seenPaths += path
            when (path) {
                "/api/v2/bangumi/bgmtv/999999999" -> respondJson(
                    """
                    {
                      "success": false,
                      "errorCode": 7,
                      "errorMessage": "无法找到指定的资源",
                      "bangumi": null
                    }
                    """.trimIndent(),
                )

                "/api/v2/search/episodes" -> respondJson(
                    """
                    {
                      "success": true,
                      "errorCode": 0,
                      "errorMessage": "",
                      "hasMore": false,
                      "animes": [
                        {
                          "animeId": 1,
                          "animeTitle": "fallback subject",
                          "episodes": [
                            {
                              "episodeId": 123456,
                              "episodeTitle": "fallback episode",
                              "episodeNumber": "1"
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                "/api/v2/comment/123456" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 999999999,
                subjectName = "fallback subject",
                episodeSort = EpisodeSort(1),
                episodeName = "fallback episode",
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Exact>(result.matchInfo.method)
        assertEquals("fallback subject", method.subjectTitle)
        assertEquals("fallback episode", method.episodeTitle)
        assertEquals(
            listOf(
                "/api/v2/bangumi/bgmtv/999999999",
                "/api/v2/search/episodes",
                "/api/v2/comment/123456",
            ),
            seenPaths,
        )
    }

    private fun createProvider(handler: MockRequestHandleScope.(path: String) -> HttpResponseData): DandanplayDanmakuProvider {
        val engine = MockEngine { request ->
            assertEquals("app-id", request.headers["X-AppId"])
            handler(request.url.encodedPath)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return DandanplayDanmakuProvider(
            dandanplayAppId = "app-id",
            dandanplayAppSecret = "app-secret",
            client = client.asScopedHttpClient(),
        )
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun request(
        subjectId: Int,
        subjectName: String,
        episodeSort: EpisodeSort,
        episodeName: String,
    ) = DanmakuFetchRequest(
        subjectId = subjectId,
        subjectPrimaryName = subjectName,
        subjectNames = listOf(subjectName),
        subjectPublishDate = PackedDate.Invalid,
        episodeId = 1,
        episodeSort = episodeSort,
        episodeEp = null,
        episodeName = episodeName,
        filename = null,
        fileHash = null,
        fileSize = null,
        videoDuration = 24.minutes,
    )
}
