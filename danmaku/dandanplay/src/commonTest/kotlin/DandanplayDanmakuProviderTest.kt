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
import me.him188.ani.danmaku.api.provider.DanmakuSplitSeasonMatcher
import me.him188.ani.danmaku.dandanplay.data.DandanplayMatchVideoResponse
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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

    /**
     * 弹弹 play 把 Re:Zero 第四季的丧失篇和夺还篇合并为一个番剧 (1-19 话), 只映射到丧失篇的 Bangumi 条目.
     * 夺还篇的 Bangumi 条目没有映射, 只能走剧集搜索; 搜索结果没有集数, 且中文名与弹弹的日文标题不同.
     */
    @Test
    fun `fetchAutomatic matches merged split cour episode by original title from episode search`() = runTest {
        val provider = createProvider { path ->
            when (path) {
                "/api/v2/bangumi/bgmtv/633836" -> respondJson(
                    """{"success": false, "errorCode": 7, "errorMessage": "无法找到指定的资源", "bangumi": null}""",
                )

                "/api/v2/search/episodes" -> respondJson(
                    """
                    {
                      "success": true, "errorCode": 0, "errorMessage": "", "hasMore": false,
                      "animes": [
                        {
                          "animeId": 19242,
                          "animeTitle": "Re：从零开始的异世界生活 第四季",
                          "episodes": [
                            {"episodeId": 192420007, "episodeTitle": "第7话 コンビニを出ると, そこは不思議の世界でした"},
                            {"episodeId": 192420009, "episodeTitle": "第9话 残骸"},
                            {"episodeId": 192420018, "episodeTitle": "第18话 ラム"},
                            {"episodeId": 192420019, "episodeTitle": "第19话"}
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                "/api/v2/comment/192420018" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 633836,
                subjectName = "Re：从零开始的异世界生活 第四季 夺还篇",
                episodeSort = EpisodeSort(84),
                episodeEp = EpisodeSort(7),
                episodeName = "拉姆",
                episodeNames = listOf("ラム", "拉姆"),
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Exact>(result.matchInfo.method)
        assertEquals("第18话 ラム", method.episodeTitle)
    }

    /**
     * 条目的几个名字都搜到同一部番剧. 重复的结果不能让标题匹配以为有多个候选.
     */
    @Test
    fun `fetchAutomatic matches title when several names find the same anime`() = runTest {
        val provider = createProvider { path ->
            when (path) {
                "/api/v2/bangumi/bgmtv/633836" -> respondJson(NO_MAPPING_RESPONSE)
                "/api/v2/search/episodes" -> respondJson(
                    """
                    {
                      "success": true, "errorCode": 0, "errorMessage": "", "hasMore": false,
                      "animes": [
                        {
                          "animeId": 19242,
                          "animeTitle": "Re：从零开始的异世界生活 第四季",
                          "episodes": [
                            {"episodeId": 192420005, "episodeTitle": "第5话 棒振り"},
                            {"episodeId": 192420012, "episodeTitle": "第12话 これからの話"}
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                "/api/v2/comment/192420012" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 633836,
                subjectName = "Re：从零开始的异世界生活 第四季 夺还篇",
                episodeSort = EpisodeSort(78),
                episodeEp = EpisodeSort(1),
                episodeName = "接下来的事",
                episodeNames = listOf("これからの話", "接下来的事"),
                subjectNames = listOf("Re：从零开始的异世界生活 第四季 夺还篇", "Re：從零開始的異世界生活 第四季 奪還篇"),
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Exact>(result.matchInfo.method)
        assertEquals("第12话 これからの話", method.episodeTitle)
    }

    @Test
    fun `fetchAutomatic prefers title over episode number when Bangumi mapping covers merged cours`() = runTest {
        val provider = createProvider { path ->
            when (path) {
                "/api/v2/bangumi/bgmtv/633836" -> respondJson(
                    """
                    {
                      "success": true, "errorCode": 0, "errorMessage": "",
                      "bangumi": {
                        "animeTitle": "Re：从零开始的异世界生活 第四季",
                        "type": "tvseries",
                        "episodes": [
                          {"episodeId": 192420007, "episodeTitle": "第7话 コンビニを出ると, そこは不思議の世界でした", "episodeNumber": "7", "lastWatched": null, "airDate": "2026-09-23T00:00:00"},
                          {"episodeId": 192420018, "episodeTitle": "第18话 ラム", "episodeNumber": "18", "lastWatched": null, "airDate": "2026-09-23T00:00:00"}
                        ]
                      }
                    }
                    """.trimIndent(),
                )

                "/api/v2/comment/192420018" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 633836,
                subjectName = "Re：从零开始的异世界生活 第四季 夺还篇",
                episodeSort = EpisodeSort(84),
                episodeEp = EpisodeSort(7),
                episodeName = "拉姆",
                episodeNames = listOf("ラム", "拉姆"),
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Exact>(result.matchInfo.method)
        assertEquals("第18话 ラム", method.episodeTitle)
    }

    @Test
    fun `fetchAutomatic falls back to episode number when title is unknown`() = runTest {
        val provider = createProvider { path ->
            when (path) {
                "/api/v2/bangumi/bgmtv/633836" -> respondJson(
                    """
                    {
                      "success": true, "errorCode": 0, "errorMessage": "",
                      "bangumi": {
                        "animeTitle": "Re：从零开始的异世界生活 第四季",
                        "type": "tvseries",
                        "episodes": [
                          {"episodeId": 192420007, "episodeTitle": "第7话 コンビニを出ると, そこは不思議の世界でした", "episodeNumber": "7", "lastWatched": null, "airDate": "2026-09-23T00:00:00"},
                          {"episodeId": 192420019, "episodeTitle": "第19话", "episodeNumber": "19", "lastWatched": null, "airDate": "2026-09-23T00:00:00"}
                        ]
                      }
                    }
                    """.trimIndent(),
                )

                "/api/v2/comment/192420007" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 633836,
                subjectName = "Re：从零开始的异世界生活 第四季 夺还篇",
                episodeSort = EpisodeSort(84),
                episodeEp = EpisodeSort(7),
                episodeName = "",
                episodeNames = emptyList(),
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Exact>(result.matchInfo.method)
        assertEquals("第7话 コンビニを出ると, そこは不思議の世界でした", method.episodeTitle)
    }

    /**
     * 夺还篇的条目在弹弹没有映射, 合并的番剧关联在丧失篇上. 第 6 集在合并番剧上是第 17 话, 弹弹还没有它的标题.
     */
    @Test
    fun `fetchAutomatic matches split season episode by season number on merged anime of previous part`() = runTest {
        val seenPaths = mutableListOf<String>()
        val provider = createProvider { path ->
            seenPaths += path
            when (path) {
                "/api/v2/bangumi/bgmtv/633836" -> respondJson(NO_MAPPING_RESPONSE)
                "/api/v2/bangumi/bgmtv/547888" -> respondJson(REZERO_S4_MERGED_RESPONSE)
                "/api/v2/comment/192420017" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }
        val splitSeason = ReZeroS4Part2Matcher(seasonNumber = 17)

        val result = provider.fetchAutomatic(
            request(
                subjectId = 633836,
                subjectName = "Re：从零开始的异世界生活 第四季 夺还篇",
                episodeSort = EpisodeSort(83),
                episodeEp = EpisodeSort(6),
                episodeName = "Good Loser",
                episodeNames = listOf("グッドルーザー", "Good Loser"),
                splitSeason = splitSeason,
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Exact>(result.matchInfo.method)
        assertEquals("第17话", method.episodeTitle)
        assertEquals(
            listOf(
                "/api/v2/bangumi/bgmtv/633836",
                "/api/v2/bangumi/bgmtv/547888",
                "/api/v2/comment/192420017",
            ),
            seenPaths,
        )
        // 特别篇不算正片集号
        assertEquals(listOf(setOf(1, 5, 6, 8, 16, 17)), splitSeason.seenPages.distinct())
    }

    @Test
    fun `fetchAutomatic passes Bangumi mapping of current subject as mapped to self`() = runTest {
        val provider = createProvider { path ->
            when (path) {
                "/api/v2/bangumi/bgmtv/633836" -> respondJson(
                    """
                    {
                      "success": true, "errorCode": 0, "errorMessage": "",
                      "bangumi": {
                        "animeTitle": "Re：从零开始的异世界生活 第四季 夺还篇",
                        "type": "tvseries",
                        "episodes": [
                          {"episodeId": 200000005, "episodeTitle": "第5话", "episodeNumber": "5", "lastWatched": null, "airDate": "2026-09-23T00:00:00"},
                          {"episodeId": 200000006, "episodeTitle": "第6话", "episodeNumber": "6", "lastWatched": null, "airDate": "2026-09-23T00:00:00"}
                        ]
                      }
                    }
                    """.trimIndent(),
                )

                "/api/v2/comment/200000006" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 633836,
                subjectName = "Re：从零开始的异世界生活 第四季 夺还篇",
                episodeSort = EpisodeSort(83),
                episodeEp = EpisodeSort(6),
                episodeName = "Good Loser",
                splitSeason = ReZeroS4Part2Matcher(seasonNumber = 17, ownEp = 6),
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Exact>(result.matchInfo.method)
        assertEquals("第6话", method.episodeTitle)
    }

    /**
     * 合并番剧上还没有当前集时, 按集号或集名模糊匹配都会选到前半的同号集. 拆分季宁可匹配不到.
     */
    @Test
    fun `fetchAutomatic does not fall back to episode number or fuzzy name for split season`() = runTest {
        val seenPaths = mutableListOf<String>()
        val provider = createProvider { path ->
            seenPaths += path
            when (path) {
                "/api/v2/bangumi/bgmtv/633836" -> respondJson(NO_MAPPING_RESPONSE)
                "/api/v2/bangumi/bgmtv/547888" -> respondJson(REZERO_S4_MERGED_RESPONSE)
                "/api/v2/search/episodes" -> respondJson(
                    """
                    {
                      "success": true, "errorCode": 0, "errorMessage": "", "hasMore": false,
                      "animes": [
                        {
                          "animeId": 1,
                          "animeTitle": "Re：从零开始的异世界生活",
                          "episodes": [
                            {"episodeId": 10019, "episodeTitle": "第19话 白鯨攻略戦"}
                          ]
                        },
                        {
                          "animeId": 19242,
                          "animeTitle": "Re：从零开始的异世界生活 第四季",
                          "episodes": [
                            {"episodeId": 192420008, "episodeTitle": "第8话 オマエハダレダ"},
                            {"episodeId": 192420017, "episodeTitle": "第17话"},
                            {"episodeId": 192429001, "episodeTitle": "S1 Re:ゼロから始める休憩時間[ブレイクタイム] #1 エミリア陣営奮闘記"}
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                "/api/v2/match" -> respondJson(
                    """
                    {
                      "isMatched": false,
                      "matches": [
                        {
                          "episodeId": 192420008, "animeId": 19242, "animeTitle": "Re：从零开始的异世界生活 第四季",
                          "episodeTitle": "第8话 オマエハダレダ", "type": "tvseries", "typeDescription": "TV动画",
                          "shift": 0, "imageUrl": "https://example.com/19242.jpg"
                        }
                      ],
                      "errorCode": 0, "success": true, "errorMessage": ""
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 633836,
                subjectName = "Re：从零开始的异世界生活 第四季 夺还篇",
                episodeSort = EpisodeSort(86),
                episodeEp = EpisodeSort(8),
                episodeName = "白鯨攻略戦",
                filename = "Re：从零开始的异世界生活 第四季 夺还篇 第08集",
                splitSeason = ReZeroS4Part2Matcher(seasonNumber = 20),
            ),
        ).single()

        assertEquals(DanmakuMatchMethod.NoMatch, result.matchInfo.method)
        assertEquals(
            listOf(
                "/api/v2/bangumi/bgmtv/633836",
                "/api/v2/bangumi/bgmtv/547888",
                "/api/v2/search/episodes",
                "/api/v2/match",
            ),
            seenPaths,
        )
    }

    @Test
    fun `DandanplayMatchVideoResponse accepts null matches when match request is rejected`() {
        val response = json.decodeFromString<DandanplayMatchVideoResponse>(REJECTED_MATCH_RESPONSE)

        assertFalse(response.success)
        assertFalse(response.isMatched)
        assertEquals(2, response.errorCode)
        assertEquals("一个或多个参数不符合规则", response.errorMessage)
        assertNull(response.matches)
    }

    @Test
    fun `fetchAutomatic returns no match when file match request is rejected`() = runTest {
        val seenPaths = mutableListOf<String>()
        val provider = createProvider { path ->
            seenPaths += path
            when (path) {
                "/api/v2/bangumi/bgmtv/999999999" -> respondJson(
                    """{"success": false, "errorCode": 7, "errorMessage": "无法找到指定的资源", "bangumi": null}""",
                )

                "/api/v2/search/episodes" -> respondJson(EMPTY_EPISODE_SEARCH_RESPONSE)
                "/api/v2/match" -> respondJson(REJECTED_MATCH_RESPONSE)
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 999999999,
                subjectName = "unknown subject",
                episodeSort = EpisodeSort(1),
                episodeName = "unknown episode",
                filename = "[Group] unknown subject - 01 [1080P]",
            ),
        ).single()

        assertEquals(DanmakuMatchMethod.NoMatch, result.matchInfo.method)
        assertEquals(
            listOf(
                "/api/v2/bangumi/bgmtv/999999999",
                "/api/v2/search/episodes",
                "/api/v2/match",
            ),
            seenPaths,
        )
    }

    @Test
    fun `fetchAutomatic uses file match when other matching fails`() = runTest {
        val provider = createProvider { path ->
            when (path) {
                "/api/v2/bangumi/bgmtv/999999999" -> respondJson(
                    """{"success": false, "errorCode": 7, "errorMessage": "无法找到指定的资源", "bangumi": null}""",
                )

                "/api/v2/search/episodes" -> respondJson(EMPTY_EPISODE_SEARCH_RESPONSE)
                "/api/v2/match" -> respondJson(
                    """
                    {
                      "isMatched": false,
                      "matches": [
                        {
                          "episodeId": 176170001, "animeId": 17617, "animeTitle": "葬送的芙莉莲",
                          "episodeTitle": "第1话 冒险的结束", "type": "tvseries", "typeDescription": "TV动画",
                          "shift": 0, "imageUrl": "https://example.com/17617.jpg"
                        }
                      ],
                      "errorCode": 0, "success": true, "errorMessage": ""
                    }
                    """.trimIndent(),
                )

                "/api/v2/comment/176170001" -> respondJson("""{"count":0,"comments":[]}""")
                else -> error("Unexpected request: $path")
            }
        }

        val result = provider.fetchAutomatic(
            request(
                subjectId = 999999999,
                subjectName = "葬送的芙莉莲",
                episodeSort = EpisodeSort(1),
                episodeName = "冒险的结束",
                filename = "[Group] Sousou no Frieren - 01 [1080P]",
            ),
        ).single()

        val method = assertIs<DanmakuMatchMethod.Fuzzy>(result.matchInfo.method)
        assertEquals("葬送的芙莉莲", method.subjectTitle)
        assertEquals("第1话 冒险的结束", method.episodeTitle)
    }

    @Test
    fun `normalizeEpisodeTitle strips number prefix and unifies width and spaces`() {
        assertEquals("ラム", normalizeEpisodeTitle("第18话 ラム"))
        assertEquals("ラム", normalizeEpisodeTitle("ラム"))
        assertEquals("", normalizeEpisodeTitle("第19话"))
        assertEquals(
            normalizeEpisodeTitle("君を連れ出す理由／ゴージャス・タイガー・リローデッド"),
            normalizeEpisodeTitle("第1话 君を連れ出す理由 / ゴージャス・タイガー・リローデッド"),
        )
        assertEquals("straightbet", normalizeEpisodeTitle("第14话 STRAIGHT BET"))
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
        subjectNames: List<String> = listOf(subjectName),
        episodeEp: EpisodeSort? = null,
        episodeNames: List<String> = listOf(episodeName),
        filename: String? = null,
        splitSeason: DanmakuSplitSeasonMatcher? = null,
    ) = DanmakuFetchRequest(
        subjectId = subjectId,
        subjectPrimaryName = subjectName,
        subjectNames = subjectNames,
        subjectPublishDate = PackedDate.Invalid,
        episodeId = 1,
        episodeSort = episodeSort,
        episodeEp = episodeEp,
        episodeName = episodeName,
        episodeNames = episodeNames,
        filename = filename,
        fileHash = null,
        fileSize = null,
        videoDuration = 24.minutes,
        splitSeason = splitSeason,
    )

    /**
     * Re:0 第四季夺还篇的拆分季规则: 番剧名是季名的合并番剧认季内序号 [seasonNumber], 关联到当前条目的番剧认 [ownEp],
     * 第一季是其他季. 记下被询问过的番剧的集号.
     */
    private class ReZeroS4Part2Matcher(
        private val seasonNumber: Int,
        private val ownEp: Int? = null,
    ) : DanmakuSplitSeasonMatcher {
        val seenPages = mutableListOf<Set<Int>>()

        override val previousPartSubjectIds: List<Int> get() = listOf(547888)

        override fun isOtherSeason(subjectName: String): Boolean = subjectName == "Re：从零开始的异世界生活"

        override fun isCurrentEpisode(subjectName: String, numbers: Set<Int>, number: Int, mappedToSelf: Boolean): Boolean {
            check(!isOtherSeason(subjectName)) { "Other season must be excluded before numbering: $subjectName" }
            seenPages += numbers
            return when {
                mappedToSelf -> number == ownEp
                subjectName == "Re：从零开始的异世界生活 第四季" -> number == seasonNumber
                else -> false
            }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /**
         * 弹弹 play 拒绝匹配请求时的真实响应, `matches` 为 `null`.
         */
        const val REJECTED_MATCH_RESPONSE =
            """{"isMatched":false,"matches":null,"errorCode":2,"success":false,"errorMessage":"一个或多个参数不符合规则"}"""

        const val NO_MAPPING_RESPONSE =
            """{"success": false, "errorCode": 7, "errorMessage": "无法找到指定的资源", "bangumi": null}"""

        /**
         * 弹弹把丧失篇和夺还篇合成的番剧, 关联在丧失篇上. 第 17 话还没有标题, S1 是特别篇.
         */
        const val REZERO_S4_MERGED_RESPONSE = """
            {
              "success": true, "errorCode": 0, "errorMessage": "",
              "bangumi": {
                "animeTitle": "Re：从零开始的异世界生活 第四季",
                "type": "tvseries",
                "episodes": [
                  {"episodeId": 192420001, "episodeTitle": "第1话 君を連れ出す理由", "episodeNumber": "1", "lastWatched": null, "airDate": "2026-04-08T00:00:00"},
                  {"episodeId": 192420005, "episodeTitle": "第5话 棒振り", "episodeNumber": "5", "lastWatched": null, "airDate": "2026-05-06T00:00:00"},
                  {"episodeId": 192420006, "episodeTitle": "第6话 ユリウス・ユークリウス", "episodeNumber": "6", "lastWatched": null, "airDate": "2026-05-13T00:00:00"},
                  {"episodeId": 192420008, "episodeTitle": "第8话 オマエハダレダ", "episodeNumber": "8", "lastWatched": null, "airDate": "2026-05-27T00:00:00"},
                  {"episodeId": 192420016, "episodeTitle": "第16话 ナツキ・スバル", "episodeNumber": "16", "lastWatched": null, "airDate": "2026-09-09T00:00:00"},
                  {"episodeId": 192420017, "episodeTitle": "第17话", "episodeNumber": "17", "lastWatched": null, "airDate": "2026-09-16T00:00:00"},
                  {"episodeId": 192429001, "episodeTitle": "S1 Re:ゼロから始める休憩時間[ブレイクタイム] #1", "episodeNumber": "S1", "lastWatched": null, "airDate": "2026-04-15T00:00:00"}
                ]
              }
            }
        """

        const val EMPTY_EPISODE_SEARCH_RESPONSE =
            """{"success": true, "errorCode": 0, "errorMessage": "", "hasMore": false, "animes": []}"""
    }
}
