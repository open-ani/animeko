/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.http.Url
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.utils.xml.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 测试 [SelectorMediaSourceEngine.selectMedia] 将线路上的所有剧集聚合为单个 media 的行为.
 */
class SelectorMediaSourceEngineSelectMediaTest {
    private object TestEngine : SelectorMediaSourceEngine() {
        override suspend fun searchImpl(finalUrl: Url): SearchSubjectResult =
            throw UnsupportedOperationException()

        override suspend fun doHttpGet(uri: String): Document =
            throw UnsupportedOperationException()
    }

    private val config = SelectorSearchConfig()

    private fun episode(channel: String?, sort: Int, name: String = "第0${sort}集") = WebSearchEpisodeInfo(
        channel = channel,
        name = name,
        episodeSortOrEp = EpisodeSort(sort),
        playUrl = "https://example.com/${channel ?: "nochannel"}/$sort",
    )

    private fun query(sort: EpisodeSort, ep: EpisodeSort? = sort, episodeName: String? = null) = SelectorSearchQuery(
        subjectName = "孤独摇滚",
        allSubjectNames = setOf("孤独摇滚"),
        episodeSort = sort,
        episodeEp = ep,
        episodeName = episodeName,
    )

    @Test
    fun `aggregates episodes of each channel into one media`() {
        val episodes = sequenceOf(
            episode("线路1", 1), episode("线路1", 2), episode("线路1", 3),
            episode("线路2", 1), episode("线路2", 2),
        )
        val result = TestEngine.selectMedia(episodes, config, query(EpisodeSort(2)), "test-source", "孤独摇滚")

        assertEquals(2, result.filteredList.size)
        val channel1 = result.filteredList[0]
        assertEquals("线路1", channel1.properties.alliance)
        assertEquals("孤独摇滚", channel1.originalTitle)
        assertEquals("test-source.孤独摇滚-线路1", channel1.mediaId)
        assertEquals(EpisodeRange.range(EpisodeSort(1), EpisodeSort(3)), channel1.episodeRange)

        val channel2 = result.filteredList[1]
        assertEquals(EpisodeRange.range(EpisodeSort(1), EpisodeSort(2)), channel2.episodeRange)
    }

    @Test
    fun `download url points to current episode`() {
        val episodes = sequenceOf(episode("线路1", 1), episode("线路1", 2), episode("线路1", 3))
        val result = TestEngine.selectMedia(episodes, config, query(EpisodeSort(2)), "test-source", "孤独摇滚")

        val media = result.filteredList.single()
        assertEquals("https://example.com/线路1/2", media.originalUrl)
        assertEquals(ResourceLocation.WebVideo("https://example.com/线路1/2"), media.download)
        assertEquals("第02集", media.properties.episodeName)
    }

    @Test
    fun `channel without current episode is kept with fallback url`() {
        val episodes = sequenceOf(episode("线路1", 1), episode("线路1", 2))
        val result = TestEngine.selectMedia(episodes, config, query(EpisodeSort(99), ep = null), "test-source", "孤独摇滚")

        // 缺当前集的线路也会保留, 由 MediaSelector 层标记并禁止自动选择
        val media = result.filteredList.single()
        assertEquals("https://example.com/线路1/2", media.originalUrl) // 回退到最后一集
        assertEquals(EpisodeRange.range(EpisodeSort(1), EpisodeSort(2)), media.episodeRange)
        // 不能把当前集补进 range, 否则下游会误判为"含当前集", 缺本集标记与自动选择保护都会失效
        assertFalse(EpisodeSort(99) in assertNotNull(media.episodeRange))
    }

    @Test
    fun `special episode matched by name is contained in episode range`() {
        // 页面把 "OVA上" 解析成了第 13 集, 与条目的 sort ("OVA上") 不一致, 只能靠名称匹配
        val episodes = sequenceOf(
            episode("线路1", 1),
            WebSearchEpisodeInfo("线路1", "OVA上", EpisodeSort(13), "https://example.com/线路1/ova"),
        )
        val result = TestEngine.selectMedia(
            episodes, config,
            query(EpisodeSort("OVA上"), ep = null, episodeName = "OVA上"),
            "test-source", "孤独摇滚",
        )

        val media = result.filteredList.single()
        assertEquals("https://example.com/线路1/ova", media.originalUrl)
        val range = assertNotNull(media.episodeRange)
        // MediaSelector 只按 sort/ep 是否落在 episodeRange 内计算 MatchMetadata,
        // 名称匹配到的当前集必须补进 range, 否则会与 download 指向当前集的事实矛盾
        assertTrue(EpisodeSort("OVA上") in range)
        // 页面上原有的集数不受影响
        assertTrue(EpisodeSort(1) in range)
        assertTrue(EpisodeSort(13) in range)
    }

    @Test
    fun `episode range is untouched when current episode matches by sort`() {
        val episodes = sequenceOf(episode("线路1", 1), episode("线路1", 2))
        val result = TestEngine.selectMedia(
            episodes, config,
            query(EpisodeSort(2), ep = EpisodeSort(2), episodeName = "第02集"),
            "test-source", "孤独摇滚",
        )

        assertEquals(
            EpisodeRange.range(EpisodeSort(1), EpisodeSort(2)),
            result.filteredList.single().episodeRange,
        )
    }

    @Test
    fun `non-contiguous sorts are represented exactly`() {
        val episodes = sequenceOf(episode("线路1", 1), episode("线路1", 2), episode("线路1", 5))
        val result = TestEngine.selectMedia(episodes, config, query(EpisodeSort(1)), "test-source", "孤独摇滚")

        val range = assertNotNull(result.filteredList.single().episodeRange)
        assertEquals(listOf(EpisodeSort(1), EpisodeSort(2), EpisodeSort(5)), range.knownSorts.toList())
        assertEquals(true, EpisodeSort(5) in range.knownSorts)
        assertEquals(false, range.knownSorts.any { it == EpisodeSort(3) })
    }

    @Test
    fun `episodes without parsed sort are dropped`() {
        val episodes = sequenceOf(
            episode("线路1", 1),
            WebSearchEpisodeInfo("线路1", "花絮", episodeSortOrEp = null, playUrl = "https://example.com/extra"),
            WebSearchEpisodeInfo("线路2", "预告", episodeSortOrEp = null, playUrl = "https://example.com/pv"),
        )
        val result = TestEngine.selectMedia(episodes, config, query(EpisodeSort(1)), "test-source", "孤独摇滚")

        // 线路2 只有无法解析的剧集, 整个线路不产生 media
        val media = result.filteredList.single()
        assertEquals("线路1", media.properties.alliance)
        assertEquals(EpisodeRange.single(EpisodeSort(1)), media.episodeRange)
    }

    @Test
    fun `null channel uses subject name only`() {
        val episodes = sequenceOf(episode(null, 1), episode(null, 2))
        val result = TestEngine.selectMedia(episodes, config, query(EpisodeSort(1)), "test-source", "孤独摇滚")

        val media = result.filteredList.single()
        assertEquals("", media.properties.alliance)
        assertEquals("test-source.孤独摇滚", media.mediaId)
    }

    @Test
    fun `findMatchingEpisodeOrNull prefers sort match`() {
        val episodes = listOf(episode("线路1", 1), episode("线路1", 2))
        assertEquals(episodes[1], episodes.findMatchingEpisodeOrNull(EpisodeSort(2), EpisodeSort(1), null))
    }

    @Test
    fun `findMatchingEpisodeOrNull falls back to ep match`() {
        // 第二季: 系列内 sort 为 14, 季度内 ep 为 2, 页面上解析到的是 2
        val episodes = listOf(episode("线路1", 1), episode("线路1", 2))
        assertEquals(episodes[1], episodes.findMatchingEpisodeOrNull(EpisodeSort(14), EpisodeSort(2), null))
    }

    @Test
    fun `findMatchingEpisodeOrNull matches special episode by name`() {
        val episodes = listOf(
            episode("线路1", 1),
            // 特殊剧集: 页面解析出的 sort (13) 与其系列内 sort ("OVA上") 不一致, 需要按名称匹配
            WebSearchEpisodeInfo("线路1", "OVA上", EpisodeSort(13), "https://example.com/ova"),
        )
        assertEquals(
            episodes[1],
            episodes.findMatchingEpisodeOrNull(EpisodeSort("OVA上"), null, "OVA上"),
        )
    }

    @Test
    fun `findMatchingEpisodeOrNull returns null when nothing matches`() {
        val episodes = listOf(episode("线路1", 1), episode("线路1", 2))
        assertNull(episodes.findMatchingEpisodeOrNull(EpisodeSort(99), null, null))
    }
}
