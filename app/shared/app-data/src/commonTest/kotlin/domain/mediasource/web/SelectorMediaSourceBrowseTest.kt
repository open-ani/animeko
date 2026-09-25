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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.EpisodeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration

/**
 * [SelectorMediaSource] 的浏览 (列表) 模式: 只把站点上的东西列出来, 不做任何自动匹配;
 * 自动模式 [SelectorMediaSource.fetch] 与浏览手动选集对同一集产生相同的 [me.him188.ani.datasources.api.Media.mediaId].
 */
class SelectorMediaSourceBrowseTest {
    private class Site {
        val requestedUrls = mutableListOf<String>()
        val searchKeywords = mutableListOf<String>()

        val engine = MockEngine { request ->
            val url = request.url.toString()
            requestedUrls += url
            request.url.parameters["wd"]?.let { searchKeywords += it }
            val html = when {
                url.startsWith("https://example.com/search") -> SEARCH_PAGE
                url == "https://example.com/subject/1.html" -> SUBJECT_PAGE
                url == "https://example.com/subject/2.html" -> NO_CHANNEL_PAGE
                else -> return@MockEngine respondError(HttpStatusCode.NotFound)
            }
            respond(html, headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
        }
    }

    private fun TestScope.createSource(
        searchConfig: SelectorSearchConfig,
        site: Site = Site(),
    ): Pair<SelectorMediaSource, Site> = createTestSelectorMediaSource(searchConfig, site.engine, MEDIA_SOURCE_ID) to site

    private val indexGroupedConfig = SelectorSearchConfig(
        searchUrl = "https://example.com/search?wd={keyword}",
        requestInterval = Duration.ZERO,
        subjectFormatId = SelectorSubjectFormatA.id,
        selectorSubjectFormatA = SelectorSubjectFormatA.Config(selectLists = "div.result > a"),
        channelFormatId = SelectorChannelFormatIndexGrouped.id,
        selectorChannelFormatFlattened = SelectorChannelFormatIndexGrouped.Config(
            selectChannelNames = ".tabs > a",
            selectEpisodeLists = ".list",
            selectEpisodesFromList = "a",
        ),
    )

    @Test
    fun `searchSubjects lists every result without filtering`() = runTest {
        val (source, site) = createSource(indexGroupedConfig)

        val subjects = source.searchSubjects("铃芽之旅 第二季")

        assertEquals(
            listOf(
                BrowseSubject("铃芽之旅", "https://example.com/subject/1.html"),
                BrowseSubject("完全无关的条目", "https://example.com/subject/2.html"),
            ),
            subjects,
        )
        // 关键字原样代入, 不做取首词等自动匹配处理
        assertEquals(listOf("铃芽之旅 第二季"), site.searchKeywords)
    }

    @Test
    fun `browseSubject keeps page order and does not merge channels sharing a normalized name`() = runTest {
        val (source, _) = createSource(indexGroupedConfig)

        val channels = source.browseSubject(BrowseSubject("铃芽之旅", "https://example.com/subject/1.html"))

        assertEquals(
            listOf(
                BrowseChannel(
                    name = "线路", label = "线路1",
                    episodes = listOf(
                        BrowseEpisode("第1集", "https://example.com/play/1-1.html", EpisodeSort(1)),
                        BrowseEpisode("第2集", "https://example.com/play/1-2.html", EpisodeSort(2)),
                        BrowseEpisode("OVA", "https://example.com/play/1-ova.html", EpisodeSort("OVA")),
                    ),
                ),
                BrowseChannel(
                    name = "线路", label = "线路2",
                    episodes = listOf(
                        BrowseEpisode("第1集", "https://example.com/play/2-1.html", EpisodeSort(1)),
                    ),
                ),
            ),
            channels,
        )
    }

    @Test
    fun `no-channel format yields a single unnamed channel`() = runTest {
        val (source, _) = createSource(
            indexGroupedConfig.copy(
                channelFormatId = SelectorChannelFormatNoChannel.id,
                selectorChannelFormatNoChannel = SelectorChannelFormatNoChannel.Config(selectEpisodes = ".list > a"),
            ),
        )

        val channels = source.browseSubject(BrowseSubject("完全无关的条目", "https://example.com/subject/2.html"))

        assertEquals(1, channels.size)
        assertNull(channels.single().name)
        assertNull(channels.single().label)
        assertEquals(listOf("第1集", "第2集"), channels.single().episodes.map { it.name })
    }

    @Test
    fun `missing subject page yields no channels`() = runTest {
        val (source, _) = createSource(indexGroupedConfig)
        assertEquals(emptyList(), source.browseSubject(BrowseSubject("x", "https://example.com/subject/404.html")))
    }

    @Test
    fun `createMedia matches auto fetch for the same episode`() = runTest {
        val (source, _) = createSource(indexGroupedConfig)
        val subject = BrowseSubject("铃芽之旅", "https://example.com/subject/1.html")

        val auto = source.fetch(
            MediaFetchRequest(
                subjectId = "1",
                episodeId = "2",
                subjectNames = listOf("铃芽之旅"),
                episodeSort = EpisodeSort(2),
                episodeName = "",
            ),
        ).results.toList().map { it.media }
        val manual = source.createMedia(
            subject,
            channelName = "线路",
            episode = BrowseEpisode("第2集", "https://example.com/play/1-2.html", EpisodeSort(2)),
            episodeSort = EpisodeSort(2),
        )

        val autoEp2 = auto.single { it.originalUrl == "https://example.com/play/1-2.html" }
        assertEquals(autoEp2.mediaId, manual.mediaId)
        assertEquals(autoEp2.properties, manual.properties)
        assertEquals(EpisodeRange.single(EpisodeSort(2)), manual.episodeRange)
    }

    @Test
    fun `createMedia without an episode sort has no episode range`() = runTest {
        val (source, _) = createSource(indexGroupedConfig)

        val media = source.createMedia(
            BrowseSubject("铃芽之旅", "https://example.com/subject/1.html"),
            channelName = "线路",
            episode = BrowseEpisode("OVA", "https://example.com/play/1-ova.html", EpisodeSort("OVA")),
            episodeSort = null,
        )

        assertNull(media.episodeRange)
        assertEquals("线路", media.properties.alliance)
        assertEquals("OVA", media.properties.episodeName)
    }

    @Test
    fun `fetch sends no request when auto match is disabled`() = runTest {
        val (source, site) = createSource(
            indexGroupedConfig.copy(autoMatch = SelectorAutoMatchConfig(enabled = false)),
        )

        val results = source.fetch(
            MediaFetchRequest(
                subjectId = "1",
                episodeId = "1",
                subjectNames = listOf("铃芽之旅"),
                episodeSort = EpisodeSort(1),
                episodeName = "",
            ),
        ).results.toList()

        assertEquals(emptyList(), results)
        assertEquals(emptyList(), site.requestedUrls)

        // 浏览不受影响
        assertEquals(2, source.searchSubjects("铃芽之旅").size)
    }

    private companion object {
        const val MEDIA_SOURCE_ID = "test-source"

        val SEARCH_PAGE = """
            <html><body>
            <div class="result"><a href="/subject/1.html" title="铃芽之旅">铃芽之旅</a></div>
            <div class="result"><a href="/subject/2.html" title="完全无关的条目">完全无关的条目</a></div>
            </body></html>
        """.trimIndent()

        // 两条线路的原文 "线路1" "线路2" 经默认 matchChannelName 归一化后同为 "线路"
        val SUBJECT_PAGE = """
            <html><body>
            <div class="tabs"><a>线路1</a><a>线路2</a></div>
            <div class="list"><a href="/play/1-1.html">第1集</a><a href="/play/1-2.html">第2集</a><a href="/play/1-ova.html">OVA</a></div>
            <div class="list"><a href="/play/2-1.html">第1集</a></div>
            </body></html>
        """.trimIndent()

        val NO_CHANNEL_PAGE = """
            <html><body>
            <div class="list"><a href="/play/1.html">第1集</a><a href="/play/2.html">第2集</a></div>
            </body></html>
        """.trimIndent()
    }
}
