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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseJellyfinMediaSourceTest {
    @Test
    fun `queries aliases until an exact season yields the requested episode`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = createSource { request ->
            requests += request
            when {
                request.url.parameters["searchTerm"] == "吹响吧！上低音号 第三季" -> {
                    assertDiscoveryRequest(request)
                    respondJson("""{ "Items": [] }""")
                }

                request.url.parameters["searchTerm"] == "吹响吧！上低音号" -> {
                    assertDiscoveryRequest(request)
                    respondJson("""{ "Items": [] }""")
                }

                request.url.parameters["searchTerm"] == "響け！ユーフォニアム3" -> {
                    assertDiscoveryRequest(request)
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "響け！ユーフォニアム3",
                              "SeriesName": "響け！ユーフォニアム",
                              "Id": "season-3",
                              "Type": "Season"
                            },
                            {
                              "Name": "響け！ユーフォニアム",
                              "Id": "series",
                              "Type": "Series"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["parentId"] == "season-3" -> {
                    assertEquals("ProviderIds", request.url.parameters["fields"])
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "あらたなユーフォニアム",
                              "SeriesName": "響け！ユーフォニアム",
                              "SeasonName": "響け！ユーフォニアム3",
                              "Id": "episode-1",
                              "IndexNumber": 1,
                              "Type": "Episode",
                              "ProviderIds": {
                                "Bangumi": "456",
                                "BangumiSubject": "123"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["ids"] == "episode-1" -> {
                    assertEquals("false", request.url.parameters["recursive"])
                    assertEquals("MediaStreams", request.url.parameters["fields"])
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "あらたなユーフォニアム",
                              "SeriesName": "響け！ユーフォニアム",
                              "SeasonName": "響け！ユーフォニアム3",
                              "Id": "episode-1",
                              "IndexNumber": 1,
                              "Type": "Episode",
                              "MediaStreams": [
                                {
                                  "Title": "简体中文",
                                  "Language": "chi",
                                  "Type": "Subtitle",
                                  "Codec": "ass",
                                  "Index": 2,
                                  "IsExternal": true,
                                  "IsTextSubtitleStream": true
                                }
                              ]
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(
                subjectNames = listOf(
                    "吹响吧！上低音号 第三季",
                    "響け！ユーフォニアム3",
                    "unused alias",
                ),
            ),
        ).results.toList()

        val media = result.single().media
        assertEquals("episode-1", media.mediaId)
        assertEquals("響け！ユーフォニアム3", media.properties.subjectName)
        assertEquals(1, media.extraFiles.subtitles.size)
        assertFalse(requests.any { it.url.parameters["parentId"] == "series" })
        assertFalse(requests.any { it.url.parameters["searchTerm"] == "unused alias" })
        assertFalse(requests.any { it.url.parameters["sortBy"] != null })
        assertTrue(requests.any { it.url.parameters["fields"] == "ProviderIds" })
    }

    @Test
    fun `uses hierarchical series search for a parsed season title`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val subjectName = "无职转生 第三季 ～到了异世界就拿出真本事～"
        val source = createSource { request ->
            requests += request
            when {
                request.url.parameters["searchTerm"] == subjectName -> {
                    assertDiscoveryRequest(request)
                    respondJson("""{ "Items": [] }""")
                }

                request.url.parameters["searchTerm"] == "无职转生" -> {
                    assertDiscoveryRequest(request)
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "无职转生",
                              "Id": "series",
                              "Type": "Series"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.encodedPath == "/Shows/series/Seasons" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Season 2",
                          "Id": "season-2",
                          "IndexNumber": 2,
                          "Type": "Season"
                        },
                        {
                          "Name": "$subjectName",
                          "Id": "season-3",
                          "IndexNumber": 3,
                          "Type": "Season"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.encodedPath == "/Shows/series/Episodes" -> {
                    assertEquals("3", request.url.parameters["Season"])
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Episode one",
                              "SeriesName": "无职转生",
                              "SeasonName": "$subjectName",
                              "Id": "episode-1",
                              "IndexNumber": 1,
                              "Type": "Episode"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["ids"] == "episode-1" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "无职转生",
                          "SeasonName": "$subjectName",
                          "Id": "episode-1",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "MediaStreams": []
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf(subjectName)),
        ).results.toList()

        assertEquals("episode-1", result.single().media.mediaId)
        assertEquals(subjectName, result.single().media.properties.subjectName)
        assertTrue(requests.any { it.url.encodedPath == "/Shows/series/Seasons" })
        assertTrue(requests.any { it.url.encodedPath == "/Shows/series/Episodes" })
    }

    @Test
    fun `filters direct season candidates by parsed target season without provider ids`() = runTest {
        val requestedParentIds = mutableListOf<String>()
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "作品 第三季" ->
                    respondJson("""{ "Items": [] }""")

                request.url.parameters["searchTerm"] == "作品" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Season 2",
                          "SeriesName": "作品",
                          "Id": "season-2",
                          "IndexNumber": 2,
                          "Type": "Season"
                        },
                        {
                          "Name": "Season 3",
                          "SeriesName": "作品",
                          "Id": "season-3",
                          "IndexNumber": 3,
                          "Type": "Season"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["parentId"] != null -> {
                    val parentId = request.url.parameters["parentId"]!!
                    requestedParentIds += parentId
                    assertEquals("season-3", parentId)
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Episode one",
                              "SeriesName": "作品",
                              "SeasonName": "Season 3",
                              "Id": "episode-3-1",
                              "IndexNumber": 1,
                              "ParentIndexNumber": 3,
                              "Type": "Episode"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["ids"] == "episode-3-1" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "Season 3",
                          "Id": "episode-3-1",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 3,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("作品 第三季")),
        ).results.toList()

        assertEquals("episode-3-1", result.single().media.mediaId)
        assertEquals("作品 第三季", result.single().media.properties.subjectName)
        assertEquals(listOf("season-3"), requestedParentIds)
    }

    @Test
    fun `filters direct episode candidates by parsed target season without provider ids`() = runTest {
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "作品 第三季" ->
                    respondJson("""{ "Items": [] }""")

                request.url.parameters["searchTerm"] == "作品" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品 第二季",
                          "Id": "episode-2-1",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 2,
                          "Type": "Episode"
                        },
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品 第三季",
                          "Id": "episode-3-1",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 3,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "episode-3-1" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品 第三季",
                          "Id": "episode-3-1",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 3,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("作品 第三季")),
        ).results.toList()

        assertEquals("episode-3-1", result.single().media.mediaId)
    }

    @Test
    fun `keeps a parsed target season across seasonless aliases`() = runTest {
        val searchedNames = mutableListOf<String>()
        val source = createSource { request ->
            request.url.parameters["searchTerm"]?.let(searchedNames::add)
            when {
                request.url.parameters["searchTerm"] == "作品 第三季" ->
                    respondJson("""{ "Items": [] }""")

                request.url.parameters["searchTerm"] == "作品" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品 第二季",
                          "Id": "episode-2-1",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 2,
                          "Type": "Episode"
                        },
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品 第三季",
                          "Id": "episode-3-1",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 3,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] != null -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品 第二季",
                          "Id": "episode-2-1",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 2,
                          "Type": "Episode"
                        },
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品 第三季",
                          "Id": "episode-3-1",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 3,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("作品 第三季", "作品")),
        ).results.toList()

        assertEquals(listOf("episode-3-1"), result.map { it.media.mediaId })
        assertEquals(1, searchedNames.count { it == "作品" })
    }

    @Test
    fun `continues with the next alias when an exact season lacks the requested episode`() = runTest {
        val searchedNames = mutableListOf<String>()
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] != null -> {
                    val name = checkNotNull(request.url.parameters["searchTerm"])
                    searchedNames += name
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "$name",
                              "Id": "season-${searchedNames.size}",
                              "Type": "Season"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["parentId"] == "season-1" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode two",
                          "SeriesName": "Generic title",
                          "SeasonName": "First alias",
                          "Id": "episode-2",
                          "IndexNumber": 2,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["parentId"] == "season-2" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "Generic title",
                          "SeasonName": "Second alias",
                          "Id": "episode-1",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "ProviderIds": {
                            "Bangumi": "456",
                            "BangumiSubject": "123"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "episode-1" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "Generic title",
                          "SeasonName": "Second alias",
                          "Id": "episode-1",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "MediaStreams": []
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("First alias", "Second alias", "unused")),
        ).results.toList()

        assertEquals(listOf("First alias", "Second alias"), searchedNames)
        assertEquals("episode-1", result.single().media.mediaId)
        assertEquals("Second alias", result.single().media.properties.subjectName)
    }

    @Test
    fun `continues past an exact title without provider evidence`() = runTest {
        val searchedNames = mutableListOf<String>()
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] != null -> {
                    val searchName = request.url.parameters["searchTerm"]!!
                    searchedNames += searchName
                    when (searchName) {
                        "Shared title" -> respondJson(
                            """
                            {
                              "Items": [
                                {
                                  "Name": "Episode one",
                                  "SeriesName": "Shared title",
                                  "SeasonName": "Shared title",
                                  "Id": "unverified-episode",
                                  "IndexNumber": 1,
                                  "Type": "Episode"
                                }
                              ]
                            }
                            """.trimIndent(),
                        )

                        "Verified alias" -> respondJson(
                            """
                            {
                              "Items": [
                                {
                                  "Name": "Episode one",
                                  "SeriesName": "Verified alias",
                                  "SeasonName": "Verified alias",
                                  "Id": "verified-episode",
                                  "IndexNumber": 1,
                                  "Type": "Episode",
                                  "ProviderIds": {
                                    "Bangumi": "456",
                                    "BangumiSubject": "123"
                                  }
                                }
                              ]
                            }
                            """.trimIndent(),
                        )

                        else -> error("Unexpected search name: $searchName")
                    }
                }

                request.url.parameters["ids"] != null -> {
                    val id = request.url.parameters["ids"]!!
                    val title = if (id == "verified-episode") "Verified alias" else "Shared title"
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Episode one",
                              "SeriesName": "$title",
                              "SeasonName": "$title",
                              "Id": "$id",
                              "IndexNumber": 1,
                              "Type": "Episode"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("Shared title", "Verified alias")),
        ).results.toList()

        assertEquals(listOf("Shared title", "Verified alias"), searchedNames)
        assertEquals("verified-episode", result.single().media.mediaId)
        assertEquals(MatchKind.EXACT, result.single().kind)
    }

    @Test
    fun `paginates typed title searches past the first 50 results`() = runTest {
        val startIndexes = mutableListOf<String>()
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "Paged title" -> {
                    startIndexes += checkNotNull(request.url.parameters["startIndex"])
                    when (request.url.parameters["startIndex"]) {
                        "0" -> {
                            val unrelatedItems = (1..50).joinToString(",") { index ->
                                """
                                {
                                  "Name": "Unrelated $index",
                                  "Id": "unrelated-$index",
                                  "Type": "Audio"
                                }
                                """.trimIndent()
                            }
                            respondJson("""{ "Items": [$unrelatedItems] }""")
                        }

                        "50" -> respondJson(
                            """
                            {
                              "Items": [
                                {
                                  "Name": "Episode one",
                                  "SeriesName": "Paged title",
                                  "SeasonName": "Paged title",
                                  "Id": "episode-1",
                                  "IndexNumber": 1,
                                  "Type": "Episode",
                                  "ProviderIds": {
                                    "Bangumi": "456",
                                    "BangumiSubject": "123"
                                  }
                                }
                              ]
                            }
                            """.trimIndent(),
                        )

                        else -> error("Unexpected page: ${request.url}")
                    }
                }

                request.url.parameters["ids"] == "episode-1" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "Paged title",
                          "SeasonName": "Paged title",
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
        }

        val result = source.fetch(
            request(subjectNames = listOf("Paged title")),
        ).results.toList()

        assertEquals(listOf("0", "50"), startIndexes)
        assertEquals("episode-1", result.single().media.mediaId)
    }

    @Test
    fun `bounds paginated title searches`() = runTest {
        val startIndexes = mutableListOf<String>()
        val source = createSource { request ->
            val startIndex = checkNotNull(request.url.parameters["startIndex"])
            startIndexes += startIndex
            val unrelatedItems = (1..50).joinToString(",") { index ->
                """
                {
                  "Name": "Unrelated $startIndex-$index",
                  "Id": "unrelated-$startIndex-$index",
                  "Type": "Audio"
                }
                """.trimIndent()
            }
            respondJson("""{ "Items": [$unrelatedItems] }""")
        }

        val result = source.fetch(
            request(subjectNames = listOf("Paged title")),
        ).results.toList()

        assertTrue(result.isEmpty())
        assertEquals(listOf("0", "50", "100", "150"), startIndexes)
    }

    @Test
    fun `retains a non-exact title result as fallback while trying remaining aliases`() = runTest {
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "First alias" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Renamed season",
                          "Id": "renamed-season",
                          "Type": "Season"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["searchTerm"] == "Second alias" -> respondJson(
                    """{ "Items": [] }""",
                )

                request.url.parameters["parentId"] == "renamed-season" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "Generic title",
                          "SeasonName": "Renamed season",
                          "Id": "episode-1",
                          "IndexNumber": 1,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "episode-1" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "Generic title",
                          "SeasonName": "Renamed season",
                          "Id": "episode-1",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "MediaStreams": []
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("First alias", "Second alias")),
        ).results.toList()

        assertEquals("episode-1", result.single().media.mediaId)
        assertEquals("First alias", result.single().media.properties.subjectName)
        assertEquals(MatchKind.FUZZY, result.single().kind)
    }

    @Test
    fun `uses episode provider ids to reject a conflicting fallback and mark an exact match`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = createSource { request ->
            requests += request
            when {
                request.url.parameters["searchTerm"] == "Fallback title" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeasonName": "Wrong renamed season",
                          "Id": "wrong-episode",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "ProviderIds": {
                            "Bangumi": "999",
                            "BangumiSubject": "123"
                          }
                        },
                        {
                          "Name": "Episode one",
                          "SeasonName": "Right renamed season",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "ProviderIds": {
                            "Bangumi": "456",
                            "BangumiSubject": "123"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "right-episode" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeasonName": "Right renamed season",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "MediaStreams": []
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("Fallback title")),
        ).results.toList()

        assertEquals("right-episode", result.single().media.mediaId)
        assertEquals(MatchKind.EXACT, result.single().kind)
        assertFalse(requests.any { it.url.parameters["ids"] == "wrong-episode" })
    }

    @Test
    fun `uses a matching season provider id before checking its index number`() = runTest {
        val requestedSeasonNumbers = mutableListOf<String>()
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "作品 第三季" ->
                    respondJson("""{ "Items": [] }""")

                request.url.parameters["searchTerm"] == "作品" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "作品",
                          "Id": "series",
                          "Type": "Series"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.encodedPath == "/Shows/series/Seasons" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Correct season with wrong number",
                          "Id": "right-season",
                          "IndexNumber": 2,
                          "Type": "Season",
                          "ProviderIds": {
                            "Bangumi": "123"
                          }
                        },
                        {
                          "Name": "Wrong season with expected number",
                          "Id": "wrong-season",
                          "IndexNumber": 3,
                          "Type": "Season",
                          "ProviderIds": {
                            "Bangumi": "999"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.encodedPath == "/Shows/series/Episodes" -> {
                    requestedSeasonNumbers += checkNotNull(request.url.parameters["Season"])
                    assertEquals("2", request.url.parameters["Season"])
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Episode one",
                              "SeriesName": "作品",
                              "SeasonName": "Correct season with wrong number",
                              "Id": "right-episode",
                              "IndexNumber": 1,
                              "ParentIndexNumber": 2,
                              "Type": "Episode",
                              "ProviderIds": {
                                "Bangumi": "456"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["ids"] == "right-episode" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "Correct season with wrong number",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 2,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("作品 第三季")),
        ).results.toList()

        assertEquals("right-episode", result.single().media.mediaId)
        assertEquals(MatchKind.EXACT, result.single().kind)
        assertEquals(listOf("2"), requestedSeasonNumbers)
    }

    @Test
    fun `uses an exact episode provider id before checking its parent season number`() = runTest {
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "作品 第三季" ->
                    respondJson("""{ "Items": [] }""")

                request.url.parameters["searchTerm"] == "作品" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "作品",
                          "Id": "series",
                          "Type": "Series"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.encodedPath == "/Shows/series/Seasons" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Season with wrong number",
                          "Id": "season",
                          "IndexNumber": 2,
                          "Type": "Season"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["parentId"] == "series" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "Season with wrong number",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 2,
                          "Type": "Episode",
                          "ProviderIds": {
                            "Bangumi": "456"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "right-episode" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "Season with wrong number",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 2,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("作品 第三季")),
        ).results.toList()

        assertEquals("right-episode", result.single().media.mediaId)
        assertEquals(MatchKind.EXACT, result.single().kind)
    }

    @Test
    fun `does not let a seasonless exact title override an explicit season conflict`() = runTest {
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "作品 第三季" ->
                    respondJson("""{ "Items": [] }""")

                request.url.parameters["searchTerm"] == "作品" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品",
                          "Id": "wrong-season-episode",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 2,
                          "Type": "Episode"
                        },
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品",
                          "Id": "right-season-episode",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 3,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] != null -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品",
                          "Id": "wrong-season-episode",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 2,
                          "Type": "Episode"
                        },
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "作品",
                          "Id": "right-season-episode",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 3,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("作品 第三季", "作品")),
        ).results.toList()

        assertEquals(listOf("right-season-episode"), result.map { it.media.mediaId })
    }

    @Test
    fun `searches a matching provider season when its index number is missing`() = runTest {
        val requestedParentIds = mutableListOf<String>()
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "作品 第三季" ->
                    respondJson("""{ "Items": [] }""")

                request.url.parameters["searchTerm"] == "作品" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "作品",
                          "Id": "series",
                          "Type": "Series"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.encodedPath == "/Shows/series/Seasons" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Renamed season without number",
                          "Id": "matching-season",
                          "Type": "Season",
                          "ProviderIds": {
                            "Bangumi": "123"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["parentId"] == "matching-season" -> {
                    requestedParentIds += "matching-season"
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Episode one",
                              "SeriesName": "作品",
                              "SeasonName": "Renamed season without number",
                              "Id": "right-episode",
                              "IndexNumber": 1,
                              "Type": "Episode",
                              "ProviderIds": {
                                "Bangumi": "456"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["ids"] == "right-episode" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeriesName": "作品",
                          "SeasonName": "Renamed season without number",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("作品 第三季")),
        ).results.toList()

        assertEquals("right-episode", result.single().media.mediaId)
        assertEquals(MatchKind.EXACT, result.single().kind)
        assertEquals(listOf("matching-season"), requestedParentIds)
    }

    @Test
    fun `uses an exact episode provider id when the jellyfin episode number is stale`() = runTest {
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "Renumbered title" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode two in Jellyfin",
                          "SeasonName": "Renumbered title",
                          "Id": "right-episode",
                          "IndexNumber": 2,
                          "Type": "Episode",
                          "ProviderIds": {
                            "Bangumi": "456",
                            "BangumiSubject": "123"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "right-episode" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode two in Jellyfin",
                          "SeasonName": "Renumbered title",
                          "Id": "right-episode",
                          "IndexNumber": 2,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("Renumbered title")),
        ).results.toList()

        val match = result.single()
        assertEquals("right-episode", match.media.mediaId)
        assertEquals(MatchKind.EXACT, match.kind)
        assertEquals(EpisodeRange.single(EpisodeSort(1)), match.media.episodeRange)
    }

    @Test
    fun `rejects a conflicting movie provider id even when its title is exact`() = runTest {
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "同名电影" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "同名电影",
                          "Id": "wrong-movie",
                          "Type": "Movie",
                          "ProviderIds": {
                            "Bangumi": "999"
                          }
                        },
                        {
                          "Name": "同名电影",
                          "Id": "right-movie",
                          "Type": "Movie",
                          "ProviderIds": {
                            "Bangumi": "123"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "right-movie" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "同名电影",
                          "Id": "right-movie",
                          "Type": "Movie"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("同名电影")),
        ).results.toList()

        assertEquals(listOf("right-movie"), result.map { it.media.mediaId })
    }

    @Test
    fun `matches provider id keys case insensitively and trims their values`() = runTest {
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "Alias" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeasonName": "Alias",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "ProviderIds": {
                            "bangumi": " 456 ",
                            "BANGUMISUBJECT": " 123 "
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "right-episode" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeasonName": "Alias",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("Alias")),
        ).results.toList()

        assertEquals("right-episode", result.single().media.mediaId)
        assertEquals(MatchKind.EXACT, result.single().kind)
    }

    @Test
    fun `does not let an exact episode id override a conflicting subject id`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = createSource { request ->
            requests += request
            when {
                request.url.parameters["searchTerm"] == "Alias" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeasonName": "Alias",
                          "Id": "conflicting-episode",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "ProviderIds": {
                            "Bangumi": "456",
                            "BangumiSubject": "999"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("Alias")),
        ).results.toList()

        assertTrue(result.isEmpty())
        assertFalse(requests.any { it.url.parameters["ids"] == "conflicting-episode" })
    }

    @Test
    fun `separates mushoku tensei season two second cour from season three`() = runTest {
        val cases = listOf(
            MushokuTenseiCase(
                subjectId = "444557",
                episodeId = "1233194",
                subjectNames = listOf(
                    "无职转生 第二季 下半",
                    "無職転生Ⅱ ～異世界行ったら本気だす～ 第2クール",
                ),
                seasonNumber = 2,
                episodeSort = EpisodeSort(13),
                episodeEp = EpisodeSort(1),
                jellyfinEpisodeId = "mushoku-season-2-cour-2-episode-1",
                jellyfinEpisodeName = "夢のマイホーム",
            ),
            MushokuTenseiCase(
                subjectId = "501963",
                episodeId = "1704816",
                subjectNames = listOf(
                    "无职转生 第三季",
                    "無職転生Ⅲ ～異世界行ったら本気だす～",
                ),
                seasonNumber = 3,
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                jellyfinEpisodeId = "mushoku-season-3-episode-1",
                jellyfinEpisodeName = "燃えよ狂犬",
            ),
        )

        cases.forEach { case ->
            val requestedSeasonNumbers = mutableListOf<String>()
            val source = createSource { request ->
                when {
                    request.url.parameters["searchTerm"] == case.subjectNames.first() ->
                        respondJson("""{ "Items": [] }""")

                    request.url.parameters["searchTerm"] == "无职转生" -> respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "無職転生 ～異世界行ったら本気だす～",
                              "Id": "mushoku-series",
                              "Type": "Series",
                              "ProviderIds": {
                                "Bangumi": "277554"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )

                    request.url.encodedPath == "/Shows/mushoku-series/Seasons" -> respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "無職転生Ⅱ ～異世界行ったら本気だす～ 第2クール",
                              "Id": "mushoku-season-2-cour-2",
                              "IndexNumber": 2,
                              "Type": "Season",
                              "ProviderIds": {
                                "Bangumi": "444557"
                              }
                            },
                            {
                              "Name": "無職転生Ⅲ ～異世界行ったら本気だす～",
                              "Id": "mushoku-season-3",
                              "IndexNumber": 3,
                              "Type": "Season",
                              "ProviderIds": {
                                "Bangumi": "501963"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )

                    request.url.encodedPath == "/Shows/mushoku-series/Episodes" -> {
                        val seasonNumber = checkNotNull(request.url.parameters["Season"])
                        requestedSeasonNumbers += seasonNumber
                        assertEquals(case.seasonNumber.toString(), seasonNumber)
                        respondJson(
                            """
                            {
                              "Items": [
                                {
                                  "Name": "${case.jellyfinEpisodeName}",
                                  "SeriesName": "無職転生 ～異世界行ったら本気だす～",
                                  "SeasonName": "${case.subjectNames[1]}",
                                  "Id": "${case.jellyfinEpisodeId}",
                                  "IndexNumber": 1,
                                  "ParentIndexNumber": ${case.seasonNumber},
                                  "Type": "Episode",
                                  "ProviderIds": {
                                    "Bangumi": "${case.episodeId}",
                                    "BangumiSubject": "${case.subjectId}"
                                  }
                                }
                              ]
                            }
                            """.trimIndent(),
                        )
                    }

                    request.url.parameters["ids"] == case.jellyfinEpisodeId -> respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "${case.jellyfinEpisodeName}",
                              "SeriesName": "無職転生 ～異世界行ったら本気だす～",
                              "SeasonName": "${case.subjectNames[1]}",
                              "Id": "${case.jellyfinEpisodeId}",
                              "IndexNumber": 1,
                              "ParentIndexNumber": ${case.seasonNumber},
                              "Type": "Episode"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            }

            val result = source.fetch(
                request(
                    subjectNames = case.subjectNames,
                    subjectId = case.subjectId,
                    episodeId = case.episodeId,
                    episodeSort = case.episodeSort,
                    episodeEp = case.episodeEp,
                ),
            ).results.toList()

            assertEquals(case.jellyfinEpisodeId, result.single().media.mediaId)
            assertEquals(MatchKind.EXACT, result.single().kind)
            assertEquals(listOf(case.seasonNumber.toString()), requestedSeasonNumbers)
        }
    }

    @Test
    fun `supports one series per mushoku tensei season with local season numbering`() = runTest {
        val cases = listOf(
            IsolatedSeriesCase(
                subjectId = "444557",
                episodeId = "1233194",
                subjectName = "无职转生 第二季 下半",
                seriesName = "无职转生 第二季 下半",
                seriesProviderId = null,
                episodeSort = EpisodeSort(13),
                episodeEp = EpisodeSort(1),
                jellyfinEpisodeId = "isolated-mushoku-season-2-cour-2-episode-1",
                jellyfinEpisodeName = "夢のマイホーム",
            ),
            IsolatedSeriesCase(
                subjectId = "501963",
                episodeId = "1704816",
                subjectName = "无职转生 第三季",
                seriesName = "無職転生Ⅲ ～異世界行ったら本気だす～",
                seriesProviderId = "501963",
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                jellyfinEpisodeId = "isolated-mushoku-season-3-episode-1",
                jellyfinEpisodeName = "燃えよ狂犬",
            ),
        )

        cases.forEach { case ->
            assertIsolatedSeriesCase(case)
        }
    }

    @Test
    fun `does not treat a generic one season series as the requested later season`() = runTest {
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == "作品 第三季" ->
                    respondJson("""{ "Items": [] }""")

                request.url.parameters["searchTerm"] == "作品" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "作品",
                          "Id": "generic-isolated-series",
                          "Type": "Series"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.encodedPath == "/Shows/generic-isolated-series/Seasons" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Season 1",
                          "Id": "generic-season-1",
                          "IndexNumber": 1,
                          "Type": "Season"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["parentId"] == "generic-isolated-series" ->
                    respondJson("""{ "Items": [] }""")

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("作品 第三季")),
        ).results.toList()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `keeps inspecting a multi-season series whose provider id belongs to season one`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = createSource { request ->
            requests += request
            when {
                request.url.parameters["searchTerm"] == "Generic series" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Generic series",
                          "Id": "series",
                          "Type": "Series",
                          "ProviderIds": {
                            "Bangumi": "first-season-subject"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.encodedPath == "/Shows/series/Seasons" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Season one",
                          "Id": "season-1",
                          "IndexNumber": 1,
                          "Type": "Season",
                          "ProviderIds": {
                            "Bangumi": "first-season-subject"
                          }
                        },
                        {
                          "Name": "Season three",
                          "Id": "season-3",
                          "IndexNumber": 3,
                          "Type": "Season",
                          "ProviderIds": {
                            "Bangumi": "123"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.encodedPath == "/Shows/series/Episodes" -> {
                    assertEquals("3", request.url.parameters["Season"])
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Right season episode",
                              "SeasonName": "Season three",
                              "Id": "right-episode",
                              "IndexNumber": 1,
                              "Type": "Episode",
                              "ProviderIds": {
                                "Bangumi": "456",
                                "BangumiSubject": "123"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["ids"] == "right-episode" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Right season episode",
                          "SeasonName": "Season three",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "MediaStreams": []
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("Generic series")),
        ).results.toList()

        assertEquals("right-episode", result.single().media.mediaId)
        assertEquals(MatchKind.EXACT, result.single().kind)
        assertFalse(
            requests.any {
                it.url.encodedPath == "/Shows/series/Episodes" &&
                        it.url.parameters["Season"] == "1"
            },
        )
    }

    @Test
    fun `uses a matching season provider id when episode provider ids are missing`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = createSource { request ->
            requests += request
            when {
                request.url.parameters["searchTerm"] == "Alias" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Wrong renamed season",
                          "Id": "wrong-season",
                          "Type": "Season",
                          "ProviderIds": {
                            "Bangumi": "999"
                          }
                        },
                        {
                          "Name": "Right renamed season",
                          "Id": "right-season",
                          "Type": "Season",
                          "ProviderIds": {
                            "Bangumi": "123"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["parentId"] == "right-season" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeasonName": "Right renamed season",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["ids"] == "right-episode" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Episode one",
                          "SeasonName": "Right renamed season",
                          "Id": "right-episode",
                          "IndexNumber": 1,
                          "Type": "Episode",
                          "MediaStreams": []
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(subjectNames = listOf("Alias")),
        ).results.toList()

        assertEquals("right-episode", result.single().media.mediaId)
        assertEquals(MatchKind.FUZZY, result.single().kind)
        assertFalse(requests.any { it.url.parameters["parentId"] == "wrong-season" })
    }

    @Test
    fun `keeps the season titles for the four affected library entries`() = runTest {
        val cases = listOf(
            TestCase(
                subjectId = "622206",
                episodeId = "1701421",
                subjectName = "ヤニねこ",
                seriesName = "ヤニねこ",
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
            ),
            TestCase(
                subjectId = "283643",
                episodeId = "1296938",
                subjectName = "響け！ユーフォニアム3",
                seriesName = "響け！ユーフォニアム",
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
            ),
            TestCase(
                subjectId = "501963",
                episodeId = "1704816",
                subjectName = "無職転生Ⅲ ～異世界行ったら本気だす～",
                seriesName = "無職転生 ～異世界行ったら本気だす～",
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
            ),
            TestCase(
                subjectId = "547888",
                episodeId = "1656866",
                subjectName = "Re:ゼロから始める異世界生活 4th season 喪失編",
                seriesName = "Re:ゼロから始める異世界生活",
                episodeSort = EpisodeSort(67),
                episodeEp = EpisodeSort(1),
            ),
        )

        cases.forEachIndexed { index, case ->
            val seasonId = "season-$index"
            val episodeId = "episode-$index"
            val source = createSource { request ->
                when {
                    request.url.parameters["searchTerm"] == case.subjectName -> respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "${case.subjectName}",
                              "SeriesName": "${case.seriesName}",
                              "Id": "$seasonId",
                              "Type": "Season",
                              "ProviderIds": {
                                "Bangumi": "${case.subjectId}"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )

                    request.url.parameters["parentId"] == seasonId -> respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Episode one",
                              "SeriesName": "${case.seriesName}",
                              "SeasonName": "${case.subjectName}",
                              "Id": "$episodeId",
                              "IndexNumber": 1,
                              "Type": "Episode",
                              "ProviderIds": {
                                "Bangumi": "${case.episodeId}",
                                "BangumiSubject": "${case.subjectId}"
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )

                    request.url.parameters["ids"] == episodeId -> respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "Episode one",
                              "SeriesName": "${case.seriesName}",
                              "SeasonName": "${case.subjectName}",
                              "Id": "$episodeId",
                              "IndexNumber": 1,
                              "Type": "Episode",
                              "MediaStreams": []
                            }
                          ]
                        }
                        """.trimIndent(),
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            }

            val result = source.fetch(
                request(
                    subjectNames = listOf(case.subjectName),
                    subjectId = case.subjectId,
                    episodeId = case.episodeId,
                    episodeSort = case.episodeSort,
                    episodeEp = case.episodeEp,
                ),
            ).results.toList()

            assertEquals(
                case.subjectName,
                result.single().media.properties.subjectName,
                case.subjectName,
            )
            assertEquals(MatchKind.EXACT, result.single().kind, case.subjectName)
        }
    }

    private fun createSource(
        handler: MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData,
    ): JellyfinMediaSource {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("MediaBrowser Token=\"api-key\"", request.headers[HttpHeaders.Authorization])
                assertEquals("user-id", request.url.parameters["userId"])
                if (request.url.parameters["searchTerm"] != null) {
                    assertEquals(
                        "Series,Season,Episode,Movie",
                        request.url.parameters["includeItemTypes"],
                    )
                } else {
                    assertFalse(request.url.parameters.contains("includeItemTypes"))
                }
                handler(request)
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return JellyfinMediaSource(
            config = MediaSourceConfig(
                arguments = mapOf(
                    "baseUrl" to "https://jellyfin.example",
                    "userId" to "user-id",
                    "apikey" to "api-key",
                ),
            ),
            client = client.asScopedHttpClient(),
        )
    }

    private fun request(
        subjectNames: List<String>,
        subjectId: String = "123",
        episodeId: String = "456",
        episodeSort: EpisodeSort = EpisodeSort(1),
        episodeEp: EpisodeSort? = EpisodeSort(1),
    ) = MediaFetchRequest(
        subjectId = subjectId,
        episodeId = episodeId,
        subjectNameCN = subjectNames.firstOrNull(),
        subjectNames = subjectNames,
        episodeSort = episodeSort,
        episodeName = "Expected episode",
        episodeEp = episodeEp,
    )

    private fun assertDiscoveryRequest(request: HttpRequestData) {
        assertEquals("50", request.url.parameters["limit"])
        assertTrue(request.url.parameters.contains("startIndex"))
        assertEquals("false", request.url.parameters["enableTotalRecordCount"])
        assertEquals("ProviderIds", request.url.parameters["fields"])
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private suspend fun assertIsolatedSeriesCase(case: IsolatedSeriesCase) {
        val requestedSeasonNumbers = mutableListOf<String>()
        val seriesProviderIds = case.seriesProviderId?.let { providerId ->
            """
            ,
            "ProviderIds": {
              "Bangumi": "$providerId"
            }
            """.trimIndent()
        }.orEmpty()
        val source = createSource { request ->
            when {
                request.url.parameters["searchTerm"] == case.subjectName -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "${case.seriesName}",
                          "Id": "isolated-series",
                          "Type": "Series"
                          $seriesProviderIds
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["searchTerm"] == "无职转生" ->
                    respondJson("""{ "Items": [] }""")

                request.url.encodedPath == "/Shows/isolated-series/Seasons" -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "Season 1",
                          "Id": "isolated-season-1",
                          "IndexNumber": 1,
                          "Type": "Season"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                request.url.parameters["parentId"] == "isolated-series" ->
                    respondJson("""{ "Items": [] }""")

                request.url.encodedPath == "/Shows/isolated-series/Episodes" -> {
                    val seasonNumber = checkNotNull(request.url.parameters["Season"])
                    requestedSeasonNumbers += seasonNumber
                    assertEquals("1", seasonNumber)
                    respondJson(
                        """
                        {
                          "Items": [
                            {
                              "Name": "${case.jellyfinEpisodeName}",
                              "SeriesName": "${case.seriesName}",
                              "SeasonName": "Season 1",
                              "Id": "${case.jellyfinEpisodeId}",
                              "IndexNumber": 1,
                              "ParentIndexNumber": 1,
                              "Type": "Episode"
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                request.url.parameters["ids"] == case.jellyfinEpisodeId -> respondJson(
                    """
                    {
                      "Items": [
                        {
                          "Name": "${case.jellyfinEpisodeName}",
                          "SeriesName": "${case.seriesName}",
                          "SeasonName": "Season 1",
                          "Id": "${case.jellyfinEpisodeId}",
                          "IndexNumber": 1,
                          "ParentIndexNumber": 1,
                          "Type": "Episode"
                        }
                      ]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val result = source.fetch(
            request(
                subjectNames = listOf(case.subjectName),
                subjectId = case.subjectId,
                episodeId = case.episodeId,
                episodeSort = case.episodeSort,
                episodeEp = case.episodeEp,
            ),
        ).results.toList()

        assertEquals(case.jellyfinEpisodeId, result.single().media.mediaId)
        assertEquals(listOf("1"), requestedSeasonNumbers)
    }

    private data class TestCase(
        val subjectId: String,
        val episodeId: String,
        val subjectName: String,
        val seriesName: String,
        val episodeSort: EpisodeSort,
        val episodeEp: EpisodeSort?,
    )

    private data class MushokuTenseiCase(
        val subjectId: String,
        val episodeId: String,
        val subjectNames: List<String>,
        val seasonNumber: Int,
        val episodeSort: EpisodeSort,
        val episodeEp: EpisodeSort?,
        val jellyfinEpisodeId: String,
        val jellyfinEpisodeName: String,
    )

    private data class IsolatedSeriesCase(
        val subjectId: String,
        val episodeId: String,
        val subjectName: String,
        val seriesName: String,
        val seriesProviderId: String?,
        val episodeSort: EpisodeSort,
        val episodeEp: EpisodeSort?,
        val jellyfinEpisodeId: String,
        val jellyfinEpisodeName: String,
    )
}
