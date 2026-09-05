/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalSeasonNumberingTest {
    @Test
    fun `isolated Yuru Camp seasons with specials return only the regular episode`() = runTest {
        for (season in 1..3) {
            assertSeasonSelection(subjectName = "Yuru Camp Season $season")
        }
    }

    @Test
    fun `matching series provider id permits local numbering with specials`() = runTest {
        assertSeasonSelection(seriesName = "Yuru Camp", seriesProviderId = "123")
    }

    @Test
    fun `generic series with specials does not supply a missing later season`() = runTest {
        assertSeasonSelection(seriesName = "Yuru Camp", expectedSeasons = emptyList())
    }

    @Test
    fun `multiple regular seasons still select the requested global season`() = runTest {
        assertSeasonSelection(seasons = listOf(0, 1, 2), expectedSeasons = listOf(2))
    }

    @Test
    fun `multiple regular seasons do not supply a missing later season`() = runTest {
        assertSeasonSelection(
            subjectName = "Yuru Camp Season 3",
            seasons = listOf(0, 1, 2),
            expectedSeasons = emptyList(),
        )
    }

    @Test
    fun `specials alone are not reinterpreted as a regular season`() = runTest {
        assertSeasonSelection(seasons = listOf(0), expectedSeasons = emptyList())
    }

    @Test
    fun `conflicting season provider id rejects local numbering with specials`() = runTest {
        assertSeasonSelection(
            seriesProviderId = "123",
            seasonProviderId = "other-subject",
            expectedSeasons = emptyList(),
        )
    }

    @Test
    fun `conflicting episode provider ids reject locally numbered episodes with specials`() = runTest {
        for (providerIds in listOf(
            """{"BangumiSubject":"other-subject"}""",
            """{"Bangumi":"other-episode"}""",
        )) {
            assertSeasonSelection(
                seriesProviderId = "123",
                episodeProviderIds = providerIds,
                expectedEpisodeSeasons = emptyList(),
            )
        }
    }

    private suspend fun assertSeasonSelection(
        subjectName: String = "Yuru Camp Season 2",
        seriesName: String = subjectName,
        seasons: List<Int> = listOf(0, 1),
        seriesProviderId: String? = null,
        seasonProviderId: String? = null,
        episodeProviderIds: String = "{}",
        expectedSeasons: List<Int> = listOf(1),
        expectedEpisodeSeasons: List<Int> = expectedSeasons,
    ) {
        val seasonItems = seasons.joinToString(",") { season ->
            """
            {"Name":"${seasonName(season)}","Type":"Season","Id":"season-$season",
             "IndexNumber":$season,"ProviderIds":${subjectProviderIds(seasonProviderId)}}
            """.trimIndent()
        }
        val episodes = seasons.associateWith { season ->
            """
            {"Name":"S${season}E01","SeriesName":"$seriesName","SeasonName":"${seasonName(season)}",
             "Type":"Episode","Id":"episode-$season-1","IndexNumber":1,"ParentIndexNumber":$season,
             "ProviderIds":$episodeProviderIds}
            """.trimIndent()
        }

        for (factory in listOf(JellyfinMediaSource.Factory(), EmbyMediaSource.Factory())) {
            val requestedSeasons = mutableListOf<Int>()
            val client = HttpClient(MockEngine { request ->
                val items = when {
                    request.url.parameters["searchTerm"] != null -> """
                        {"Name":"$seriesName","Type":"Series","Id":"series",
                         "ProviderIds":${subjectProviderIds(seriesProviderId)}}
                    """.trimIndent()

                    request.url.encodedPath == "/Shows/series/Seasons" -> seasonItems
                    request.url.encodedPath == "/Shows/series/Episodes" -> {
                        val season = checkNotNull(request.url.parameters["Season"]).toInt()
                        requestedSeasons += season
                        episodes.getValue(season)
                    }

                    request.url.parameters["parentId"] == "series" -> episodes.values.joinToString(",")
                    request.url.parameters["ids"] != null -> {
                        val ids = request.url.parameters["ids"]!!.split(",")
                        episodes.filterKeys { "episode-$it-1" in ids }.values.joinToString(",")
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
                respond(
                    content = """{"Items":[$items]}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
            try {
                val source = factory.create(
                    mediaSourceId = factory.factoryId.value,
                    config = MediaSourceConfig(
                        arguments = mapOf(
                            "baseUrl" to "https://media.example",
                            "userId" to "user-id",
                            "apikey" to "api-key",
                        ),
                    ),
                    client = client.asScopedHttpClient(),
                )
                val result = source.fetch(
                    MediaFetchRequest(
                        subjectId = "123",
                        episodeId = "456",
                        subjectNameCN = subjectName,
                        subjectNames = listOf(subjectName),
                        episodeSort = EpisodeSort(1),
                        episodeEp = EpisodeSort(1),
                        episodeName = "Expected episode",
                    ),
                ).results.toList()

                val context = "${factory.factoryId}: $subjectName / $seriesName / $seasons"
                assertEquals(expectedEpisodeSeasons.map { "episode-$it-1" }, result.map { it.media.mediaId }, context)
                assertEquals(expectedSeasons, requestedSeasons.distinct(), context)
                assertTrue(result.all { it.kind == MatchKind.FUZZY }, context)
            } finally {
                client.close()
            }
        }
    }

    private fun seasonName(season: Int) = if (season == 0) "Specials" else "Season $season"

    private fun subjectProviderIds(id: String?) = id?.let { """{"Bangumi":"$it"}""" } ?: "{}"
}
