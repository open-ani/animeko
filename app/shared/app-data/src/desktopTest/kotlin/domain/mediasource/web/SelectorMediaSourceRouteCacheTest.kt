/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.media.selector.PlaybackSourceMemory
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaFetchRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** 使用真实 Room 缓存验证线路分组身份，覆盖唯一索引覆盖同名同集记录的情况。 */
class SelectorMediaSourceRouteCacheTest {
    private val subject = BrowseSubject("Example", "https://example.com/subject/1")
    private val request = MediaFetchRequest(
        subjectId = "1", episodeId = "1", subjectNames = listOf(subject.name),
        episodeSort = EpisodeSort(1), episodeName = "",
    )
    private val config = SelectorSearchConfig(
        searchUrl = "https://example.com/search?wd={keyword}",
        requestInterval = Duration.ZERO,
        subjectFormatId = SelectorSubjectFormatA.id,
        selectorSubjectFormatA = SelectorSubjectFormatA.Config(selectLists = ".result > a"),
        channelFormatId = SelectorChannelFormatIndexGrouped.id,
        selectorChannelFormatFlattened = SelectorChannelFormatIndexGrouped.Config(
            selectChannelNames = ".tabs > a", selectEpisodeLists = ".list", selectEpisodesFromList = "a",
        ),
        autoMatch = SelectorAutoMatchConfig(filterByEpisodeSort = false),
    )

    @Test
    fun `cached duplicate channel names keep uncertain identities after conflicting episodes are replaced`() = runTest {
        val database = createTestAniDatabase()
        try {
            var subjectRequests = 0
            val engine = MockEngine { call ->
                val html = if (call.url.encodedPath == "/search") {
                    """<div class="result"><a href="/subject/1">Example</a></div>"""
                } else {
                    subjectRequests++
                    """
                    <div class="tabs"><a>线路1</a><a>线路2</a><a>备用</a></div>
                    <div class="list"><a href="/play/a1">第1集</a><a href="/play/a2">第2集</a></div>
                    <div class="list"><a href="/play/b1">第1集</a></div>
                    <div class="list"><a href="/play/c1">第1集</a><a href="/play/c2">第2集</a></div>
                    """.trimIndent()
                }
                respond(html, headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
            }
            val dao = database.webSearchSessionCacheDao()
            val source = createTestSelectorMediaSource(config, engine, cacheDao = dao, cacheTtl = 1.hours)
            val live = source.fetch(request).results.toList().map { it.media }
            val liveAmbiguous = live.filter { it.properties.alliance == "线路" }
            assertEquals(3, liveAmbiguous.size)
            assertTrue(liveAmbiguous.all { it.properties.webRoute == null })

            // 重新创建源实例，只从数据库恢复；重名同集的一条地址已被唯一索引覆盖。
            val reopened = createTestSelectorMediaSource(config, engine, cacheDao = dao, cacheTtl = 1.hours)
            val cached = reopened.fetch(request).results.toList().map { it.media }
            assertEquals(1, subjectRequests)
            val ambiguous = cached.filter { it.properties.alliance == "线路" }
            assertEquals(2, ambiguous.size)
            assertTrue(ambiguous.all { it.properties.webRoute == null })
            val backup = cached.first { it.properties.alliance == "备用" }
            assertNotNull(backup.properties.webRoute)
            assertEquals(live.first { it.mediaId == backup.mediaId }.properties.webRoute, backup.properties.webRoute)
            val manual = reopened.createMedia(
                subject, "线路", BrowseEpisode("第2集", "https://example.com/play/a2", EpisodeSort(2)), EpisodeSort(2),
            )
            assertNull(manual.properties.webRoute)

            val memory = PlaybackSourceMemory()
            memory.recordFailure(memory.beginAttempt(1, ambiguous.first()), PlaybackSourceMemory.FailureReason.PLAYBACK)
            assertTrue(memory.snapshot().failures.isEmpty())
            assertTrue(memory.snapshot().allows(ambiguous.last()))
        } finally {
            database.close()
        }
    }

    @Test
    fun `legacy cached episodes remain playable and gain route identity only after a fresh page query`() = runTest {
        val database = createTestAniDatabase()
        try {
            val dao = database.webSearchSessionCacheDao()
            val repository = SelectorMediaSourceEpisodeCacheRepository(dao, flowOf(1.hours))
            // 缺少页面分组判定的旧缓存，不能单凭展开后的剧集恢复唯一线路身份。
            repository.addCache(
                1, "test-source", subject.name,
                WebSearchSubjectInfo("1", subject.name, subject.url, "/subject/1", null),
                listOf(WebSearchEpisodeInfo("线路", "第1集", EpisodeSort(1), "https://example.com/play/a1")),
                sourceCacheTtl = 1.hours,
            )
            var subjectRequests = 0
            val engine = MockEngine { call ->
                val html = if (call.url.encodedPath == "/search") {
                    """<div class="result"><a href="/subject/1">Example</a></div>"""
                } else {
                    subjectRequests++
                    """<div class="tabs"><a>线路</a></div><div class="list"><a href="/play/a1">第1集</a></div>"""
                }
                respond(html, headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
            }
            val source = createTestSelectorMediaSource(config, engine, cacheDao = dao, cacheTtl = 1.hours)
            val legacy = source.fetch(request).results.toList().single().media
            assertEquals("https://example.com/play/a1", legacy.originalUrl)
            assertNull(legacy.properties.webRoute)
            assertEquals(0, subjectRequests)

            repository.clearByRequestedSubject(1)
            val fresh = source.fetch(request).results.toList().single().media
            assertNotNull(fresh.properties.webRoute)
            val reopened = createTestSelectorMediaSource(config, engine, cacheDao = dao, cacheTtl = 1.hours)
            val cached = reopened.fetch(request).results.toList().single().media
            assertEquals(fresh.properties.webRoute, cached.properties.webRoute)
            assertEquals(1, subjectRequests)
        } finally {
            database.close()
        }
    }
}
