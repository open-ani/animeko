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
}
