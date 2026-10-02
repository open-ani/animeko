/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange

class DownloadReusePolicyTest {
    /**
     * sort 为 14 但 ep 为 2 的一集, 例如第二季的第二集.
     */
    private val episode = requestTestEpisode(14).copy(ep = EpisodeSort(2))
    private val season = requestTestMedia(1, EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
    private val single = requestTestMedia(2, EpisodeRange.single(EpisodeSort(2)))
    private val unknown = requestTestMedia(3, null)

    @Test
    fun `season range covering ep is reused`() {
        assertSame(season, BatchDownloadPlanner.findReusableSeasonMedia(episode, listOf(single, unknown, season)))
    }

    @Test
    fun `season range covering sort is reused`() {
        assertSame(season, BatchDownloadPlanner.findReusableSeasonMedia(episode.copy(sort = EpisodeSort(2), ep = null), listOf(season)))
    }

    @Test
    fun `season range covering neither sort nor ep is not reused`() {
        assertNull(BatchDownloadPlanner.findReusableSeasonMedia(episode.copy(ep = null), listOf(season)))
    }

    @Test
    fun `whole season pack covers every episode`() {
        val pack = requestTestMedia(4, EpisodeRange.season(1))
        assertSame(pack, BatchDownloadPlanner.findReusableSeasonMedia(episode.copy(ep = null), listOf(pack)))
    }

    @Test
    fun `single episode media is never reused`() {
        val degenerateRange = requestTestMedia(5, EpisodeRange.range(EpisodeSort(2), EpisodeSort(2)))
        assertNull(BatchDownloadPlanner.findReusableSeasonMedia(episode, listOf(single, degenerateRange, unknown)))
        assertNull(BatchDownloadPlanner.findReusableSeasonMedia(episode, emptyList()))
    }

    @Test
    fun `cached media is unwrapped to its origin and the first match wins`() {
        val cached = requestTestCache(season, subjectId = 1, episodeId = 1).media
        val later = requestTestMedia(6, EpisodeRange.range(EpisodeSort(1), EpisodeSort(24)))
        assertSame(season, BatchDownloadPlanner.findReusableSeasonMedia(episode, listOf(single, cached, later)))
    }
}
