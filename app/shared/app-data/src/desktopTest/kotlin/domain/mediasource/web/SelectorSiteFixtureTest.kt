/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.contains
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 用脱敏后的真实站点页面 (`resources/selector-site-fixtures/site-a`) 回归 Selector 数据源:
 * 浏览把页面上的东西原样列出来, 自动匹配在其上解析集号, 两条路径对同一集给出相同的 mediaId.
 */
class SelectorSiteFixtureTest {
    private val config = Json { ignoreUnknownKeys = true }
        .decodeFromString(SelectorSearchConfig.serializer(), resource("config.json"))

    private val site = MockEngine { request ->
        val page = when (request.url.encodedPath) {
            "/search.html" -> when (request.url.parameters["wd"]) {
                "葬送的芙莉莲" -> "search-frieren.html"
                "进击的巨人" -> "search-aot.html"
                else -> null
            }

            "/bangumi/44.html" -> "subject-44.html"
            "/bangumi/3390.html" -> "subject-3390.html"
            "/bangumi/1617.html" -> "subject-1617.html"
            else -> null
        } ?: return@MockEngine respondError(HttpStatusCode.NotFound)
        respond(resource(page), headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
    }

    @Test
    fun `searchSubjects lists every result of a real search page`() = runTest {
        val source = createTestSelectorMediaSource(config, site)

        val subjects = source.searchSubjects("进击的巨人")

        // 页面顺序, 不做 preferShorterName 排序
        assertEquals(
            listOf(
                "进击的巨人 OAD",
                "进击的巨人 最终季 完结篇 后篇",
                "进击的巨人 最终季 完结篇 前篇",
                "进击的巨人 最终季 Part.2",
                "进击的巨人 最终季",
                "进击的巨人 编年史",
                "剧场版 进击的巨人 前篇～红莲的弓矢～",
                "剧场版 进击的巨人 后篇～自由之翼～",
                "剧场版 进击的巨人 Season2～觉醒的咆哮～",
                "进击的巨人 第三季",
            ),
            subjects.map { it.name },
        )
        assertEquals("https://www.fixture.invalid/bangumi/1617.html", subjects.first().url)
    }

    @Test
    fun `browseSubject lists channels and episodes of a real subject page`() = runTest {
        val source = createTestSelectorMediaSource(config, site)

        val channels = source.browseSubject(FRIEREN)

        assertEquals(listOf("旧番主线①", "备用①"), channels.map { it.name })
        // 页面原文带集数角标
        assertEquals(listOf("旧番主线①28", "备用①28"), channels.map { it.label })
        assertEquals(listOf(28, 28), channels.map { it.episodes.size })
        assertEquals((1..28).map { EpisodeSort(it) }, channels[0].episodes.map { it.episodeSort })
        assertEquals("第01集", channels[0].episodes.first().name)
        assertEquals("https://www.fixture.invalid/watch/44/1/1.html", channels[0].episodes.first().url)
        assertEquals("https://www.fixture.invalid/watch/44/2/28.html", channels[1].episodes.last().url)
    }

    @Test
    fun `auto fetch returns the whole subject and agrees with manual createMedia`() = runTest {
        val source = createTestSelectorMediaSource(config, site)

        val medias = source.fetch(
            MediaFetchRequest(
                subjectId = "1",
                episodeId = "14",
                subjectNames = listOf("葬送的芙莉莲"),
                episodeSort = EpisodeSort(14),
                episodeName = "",
            ),
        ).results.toList().map { it.media }

        // 两条搜索结果都被打开: 第一季 2 线路 × 28 集, 第二季 3 线路 × 10 集; 不按当前剧集裁剪
        assertEquals(2 * 28 + 3 * 10, medias.size)
        // preferShorterName: 名字更短的 "葬送的芙莉莲" 排在 "葬送的芙莉莲 第二季" 之前
        assertEquals("葬送的芙莉莲", medias.first().properties.subjectName)
        assertEquals("葬送的芙莉莲 第二季", medias.last().properties.subjectName)
        val ep14 = medias.filter { it.episodeRange?.contains(EpisodeSort(14)) == true }
        assertEquals(listOf("旧番主线①", "备用①"), ep14.map { it.properties.alliance })

        val manual = source.createMedia(
            FRIEREN,
            channelName = "备用①",
            episode = BrowseEpisode("第14集", "https://www.fixture.invalid/watch/44/2/14.html", EpisodeSort(14)),
            episodeSort = EpisodeSort(14),
        )
        val auto = ep14.single { it.properties.alliance == "备用①" }
        assertEquals(auto.mediaId, manual.mediaId)
        assertEquals(auto.originalUrl, manual.originalUrl)
        assertEquals(auto.properties, manual.properties)
        assertEquals(EpisodeRange.single(EpisodeSort(14)), manual.episodeRange)
    }

    @Test
    fun `a special subject only reachable by browsing can be mapped to an episode by hand`() = runTest {
        val source = createTestSelectorMediaSource(config, site)

        val oad = source.searchSubjects("进击的巨人").single { it.name == "进击的巨人 OAD" }
        val channels = source.browseSubject(oad)
        assertEquals(1, channels.size)
        assertEquals(8, channels.single().episodes.size)

        val episode = channels.single().episodes[2]
        val unmapped = source.createMedia(oad, channels.single().name, episode, episodeSort = null)
        assertNull(unmapped.episodeRange)

        val mapped = source.createMedia(oad, channels.single().name, episode, episodeSort = EpisodeSort("OAD3"))
        assertEquals(EpisodeRange.single(EpisodeSort("OAD3")), mapped.episodeRange)
        assertEquals("进击的巨人 OAD", mapped.properties.subjectName)
        assertEquals("第03集", mapped.properties.episodeName)
    }

    private companion object {
        val FRIEREN = BrowseSubject("葬送的芙莉莲", "https://www.fixture.invalid/bangumi/44.html")

        fun resource(name: String): String =
            checkNotNull(SelectorSiteFixtureTest::class.java.getResourceAsStream("/selector-site-fixtures/site-a/$name")) {
                "missing fixture $name"
            }.use { it.readBytes().decodeToString() }
    }
}
