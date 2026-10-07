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
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaFetchRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * 自动匹配的关键词链: 主关键词搜不到名字能对上的条目时, 依次尝试回退关键词.
 */
class SelectorMediaSourceFetchFallbackTest {
    /**
     * 站点只认识「出包王女」系列和「铃芽之旅」, 其他关键词搜不到任何条目.
     */
    private class Site {
        val searchKeywords = mutableListOf<String>()
        val engine = MockEngine { request ->
            val url = request.url.toString()
            val html = when {
                url.startsWith("https://example.com/search") -> {
                    val keyword = request.url.parameters["wd"].orEmpty()
                    searchKeywords += keyword
                    when (keyword) {
                        "出包王女" -> SERIES_SEARCH_PAGE
                        "铃芽之旅" -> SUZUME_SEARCH_PAGE
                        else -> EMPTY_SEARCH_PAGE
                    }
                }

                url.startsWith("https://example.com/subject/") -> SUBJECT_PAGE
                else -> return@MockEngine respondError(HttpStatusCode.NotFound)
            }
            respond(html, headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
        }
    }

    private val config = SelectorSearchConfig(
        searchUrl = "https://example.com/search?wd={keyword}",
        requestInterval = Duration.ZERO,
        subjectFormatId = SelectorSubjectFormatA.id,
        selectorSubjectFormatA = SelectorSubjectFormatA.Config(selectLists = "div.result > a"),
        channelFormatId = SelectorChannelFormatNoChannel.id,
        selectorChannelFormatNoChannel = SelectorChannelFormatNoChannel.Config(selectEpisodes = ".list > a"),
    )

    private fun TestScope.createSource(
        searchConfig: SelectorSearchConfig = config,
    ): Pair<SelectorMediaSource, Site> {
        val site = Site()
        return createTestSelectorMediaSource(searchConfig, site.engine) to site
    }

    private fun request(
        subjectNames: List<String>,
        fallbackSearchKeywords: List<String> = emptyList(),
        episode: Int = 1,
    ) = MediaFetchRequest(
        subjectId = "8546",
        episodeId = episode.toString(),
        subjectNames = subjectNames,
        episodeSort = EpisodeSort(episode),
        episodeName = "",
        fallbackSearchKeywords = fallbackSearchKeywords,
    )

    private suspend fun SelectorMediaSource.fetchSubjectNames(request: MediaFetchRequest): List<String> =
        fetch(request).results.toList().map { it.media.properties.subjectName.orEmpty() }.distinct()

    @Test
    fun `falls back to the season alias when the primary keyword finds nothing`() = runTest {
        val (source, site) = createSource()

        val subjects = source.fetchSubjectNames(
            request(
                subjectNames = listOf("更多 出包王女", "もっとTo LOVEる -とらぶる-", "出包王女 第二季"),
                // 「出包王女 第二季」取首词后与「出包王女」相同, 只搜一次
                fallbackSearchKeywords = listOf("出包王女 第二季", "出包王女"),
            ),
        )

        assertEquals(listOf("更多", "出包王女"), site.searchKeywords)
        // 整个系列都返回, 由选择器挑出本季
        assertEquals(listOf("出包王女", "出包王女第二季", "出包王女 Darkness", "出包王女Darkness 第二季"), subjects)
    }

    @Test
    fun `does not fall back when the primary keyword matches`() = runTest {
        val (source, site) = createSource()

        val subjects = source.fetchSubjectNames(
            request(subjectNames = listOf("铃芽之旅"), fallbackSearchKeywords = listOf("出包王女")),
        )

        assertEquals(listOf("铃芽之旅"), site.searchKeywords)
        assertEquals(listOf("铃芽之旅"), subjects)
    }

    @Test
    fun `stops at the first fallback keyword that matches`() = runTest {
        val (source, site) = createSource()

        source.fetchSubjectNames(
            request(
                subjectNames = listOf("更多 出包王女", "出包王女 第二季"),
                fallbackSearchKeywords = listOf("不存在的关键词", "出包王女 第二季", "To LOVEる"),
            ),
        )

        assertEquals(listOf("更多", "不存在的关键词", "出包王女"), site.searchKeywords)
    }

    @Test
    fun `tries every fallback keyword when nothing matches`() = runTest {
        val (source, site) = createSource()

        val subjects = source.fetchSubjectNames(
            request(subjectNames = listOf("不存在的番"), fallbackSearchKeywords = listOf("也不存在", "还是不存在")),
        )

        assertEquals(listOf("不存在的番", "也不存在", "还是不存在"), site.searchKeywords)
        assertEquals(emptyList(), subjects)
    }

    @Test
    fun `primary results are kept even when none of them matches`() = runTest {
        val (source, site) = createSource()

        val subjects = source.fetchSubjectNames(
            // 搜到的是「出包王女」系列, 但没有一个名字能对上「出包王女 OVA」
            request(subjectNames = listOf("出包王女 OVA"), fallbackSearchKeywords = listOf("铃芽之旅")),
        )

        assertEquals(listOf("出包王女", "铃芽之旅"), site.searchKeywords)
        assertEquals(
            listOf("出包王女", "出包王女第二季", "出包王女 Darkness", "出包王女Darkness 第二季", "铃芽之旅"),
            subjects,
        )
    }

    @Test
    fun `every primary name is searched before falling back`() = runTest {
        val (source, site) = createSource(
            config.copy(autoMatch = SelectorAutoMatchConfig(searchUseSubjectNamesCount = 2)),
        )

        source.fetchSubjectNames(
            request(
                subjectNames = listOf("更多 出包王女", "もっとTo LOVEる -とらぶる-", "出包王女 第二季"),
                fallbackSearchKeywords = listOf("出包王女"),
            ),
        )

        assertEquals(listOf("更多", "もっとTo", "出包王女"), site.searchKeywords)
    }

    @Test
    fun `remembered keyword is used first and the primary is not searched again`() = runTest {
        val site = Site()
        val source = createTestSelectorMediaSource(
            config, site.engine, cacheDao = InMemoryWebSearchSessionCacheDao(), cacheTtl = 1.hours,
        )
        val names = listOf("更多 出包王女", "出包王女 第二季")
        val fallback = listOf("出包王女 第二季", "出包王女")

        source.fetchSubjectNames(request(names, fallback, episode = 1))
        assertEquals(listOf("更多", "出包王女"), site.searchKeywords)

        // 切集: 缓存的条目页含第 2 集, 不发任何请求; 「更多」没有缓存行, 不再搜
        val subjects = source.fetchSubjectNames(request(names, fallback, episode = 2))
        assertEquals(listOf("更多", "出包王女"), site.searchKeywords)
        assertEquals(listOf("出包王女", "出包王女第二季", "出包王女 Darkness", "出包王女Darkness 第二季"), subjects)
    }

    @Test
    fun `stale cache searches only the remembered keyword`() = runTest {
        val site = Site()
        val source = createTestSelectorMediaSource(
            config, site.engine, cacheDao = InMemoryWebSearchSessionCacheDao(), cacheTtl = 1.hours,
        )
        val names = listOf("更多 出包王女", "出包王女 第二季")
        val fallback = listOf("出包王女 第二季", "出包王女")

        source.fetchSubjectNames(request(names, fallback, episode = 1))

        // 新一集不在缓存的条目页里: 只对记住的「出包王女」发请求
        val subjects = source.fetchSubjectNames(request(names, fallback, episode = 3))
        assertEquals(listOf("更多", "出包王女", "出包王女"), site.searchKeywords)
        assertEquals(listOf("出包王女", "出包王女第二季", "出包王女 Darkness", "出包王女Darkness 第二季"), subjects)
    }

    @Test
    fun `fallback keywords are deduplicated against the primary keywords`() {
        val fallback = listOf("出包王女 第二季", "出包王女", "更多 出包王女", "", "出包王女第2季")
        // 取首词: 「出包王女 第二季」与「出包王女」同为「出包王女」; 「更多 出包王女」与主关键词同为「更多」; 「出包王女第2季」没有空格, 整个是关键词
        assertEquals(
            listOf("出包王女 第二季", "出包王女第2季"),
            config.distinctFallbackKeywords(primary = listOf("更多 出包王女"), fallback = fallback),
        )
        assertEquals(
            listOf("出包王女 第二季", "出包王女", "出包王女第2季"),
            config.copy(autoMatch = SelectorAutoMatchConfig(searchUseOnlyFirstWord = false))
                .distinctFallbackKeywords(primary = listOf("更多 出包王女"), fallback = fallback),
        )
    }

    private companion object {
        val SERIES_SEARCH_PAGE = """
            <html><body>
            <div class="result"><a href="/subject/1.html" title="出包王女">出包王女</a></div>
            <div class="result"><a href="/subject/2.html" title="出包王女第二季">出包王女第二季</a></div>
            <div class="result"><a href="/subject/3.html" title="出包王女 Darkness">出包王女 Darkness</a></div>
            <div class="result"><a href="/subject/4.html" title="出包王女Darkness 第二季">出包王女Darkness 第二季</a></div>
            </body></html>
        """.trimIndent()

        val SUZUME_SEARCH_PAGE = """
            <html><body>
            <div class="result"><a href="/subject/5.html" title="铃芽之旅">铃芽之旅</a></div>
            </body></html>
        """.trimIndent()

        val EMPTY_SEARCH_PAGE = """
            <html><body><p>没有找到相关内容</p></body></html>
        """.trimIndent()

        val SUBJECT_PAGE = """
            <html><body>
            <div class="list"><a href="/play/1.html">第1集</a><a href="/play/2.html">第2集</a></div>
            </body></html>
        """.trimIndent()
    }
}
