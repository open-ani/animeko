/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.datasources.api.EpisodeSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 测试 [findMatchingEpisodeOrNull] 在剧集列表中定位正在播放剧集的行为.
 * 搜索缓存以它判断缓存的条目页面是否包含当前请求的剧集.
 */
class FindMatchingEpisodeOrNullTest {
    private fun episode(channel: String?, sort: Int, name: String = "第0${sort}集") = WebSearchEpisodeInfo(
        channel = channel,
        name = name,
        episodeSortOrEp = EpisodeSort(sort),
        playUrl = "https://example.com/${channel ?: "nochannel"}/$sort",
    )

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

    /**
     * 剧场版的条目页把同一部作品的不同配音列成多条, 名称只有画质与语言, 集号解析不出来.
     * 实测 https://www.yinghua2.com 的「铃芽之旅」: 9 条线路里 5 条是这种命名.
     */
    @Test
    fun `findMatchingEpisodeOrNull matches a whole work label as first episode`() {
        val episodes = listOf(
            WebSearchEpisodeInfo("线路1", "HD高清国语版", EpisodeSort("HD高清国语版"), MOVIE_URL),
            WebSearchEpisodeInfo("线路1", "HD高清原声版", EpisodeSort("HD高清原声版"), MOVIE_URL),
        )
        assertEquals(episodes[0], episodes.findMatchingEpisodeOrNull(EpisodeSort(1), EpisodeSort(1), null))
        assertNull(episodes.findMatchingEpisodeOrNull(EpisodeSort(5), EpisodeSort(5), null))
    }

    @Test
    fun `findMatchingEpisodeOrNull does not treat a title or a full season entry as first episode`() {
        val episodes = listOf(
            WebSearchEpisodeInfo("线路1", "剧场版01", EpisodeSort("剧场版01"), MOVIE_URL),
            WebSearchEpisodeInfo("线路1", "全集", EpisodeSort("全集"), MOVIE_URL),
        )
        assertNull(episodes.findMatchingEpisodeOrNull(EpisodeSort(1), EpisodeSort(1), null))
    }

    @Test
    fun `findMatchingEpisodeOrNull keeps a parsed sort`() {
        // 站点给了集号就以它为准
        val episodes = listOf(WebSearchEpisodeInfo("线路1", "HD中字 第03集", EpisodeSort(3), MOVIE_URL))
        assertNull(episodes.findMatchingEpisodeOrNull(EpisodeSort(1), EpisodeSort(1), null))
        assertEquals(episodes[0], episodes.findMatchingEpisodeOrNull(EpisodeSort(3), EpisodeSort(3), null))
    }

    @Test
    fun `findMatchingEpisodeOrNull keeps a match that already worked`() {
        // 站点把集号写成了与请求完全相同的怪字符串: 本来就能按相等匹配上, 替换判据不能把它弄丢
        val weird = EpisodeSort("HD中字")
        val episodes = listOf(WebSearchEpisodeInfo("线路1", "HD中字", weird, MOVIE_URL))
        assertEquals(episodes[0], episodes.findMatchingEpisodeOrNull(weird, null, null))
    }

    private companion object {
        private const val MOVIE_URL = "https://example.com/movie"
    }
}
