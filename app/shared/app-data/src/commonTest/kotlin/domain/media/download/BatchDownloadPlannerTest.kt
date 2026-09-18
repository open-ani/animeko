/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertEquals
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.platform.annotations.TestOnly

class BatchDownloadPlannerTest {
    private val episodes = (1..6).map { requestTestEpisode(it) }
    private val single1 = requestTestMedia(1)
    private val single2 = requestTestMedia(2)
    private val pack1to3 = requestTestMedia(103, EpisodeRange.range(1, 3))
    private val pack4to6 = requestTestMedia(106, EpisodeRange.range(4, 6))
    private val pack1to6 = requestTestMedia(100, EpisodeRange.range(1, 6))
    private val season = requestTestMedia(200, EpisodeRange.season(1))

    @Test
    fun `result keeps the input episode order`() {
        val plan = planBatchDownload(episodes.reversed(), listOf(pack1to6), emptyList())
        assertEquals((6 downTo 1).toList(), plan.keys.toList())
    }

    @Test
    fun `already downloaded episodes are not planned again`() {
        val plan = planBatchDownload(
            episodes.take(2), listOf(single1, single2),
            existing = listOf(ExistingDownload(single1, episodeId = 1)),
        )
        assertEquals(EpisodeDownloadPlan.AlreadyDownloaded, plan.getValue(1))
        assertEquals(EpisodeDownloadPlan.Create(single2), plan.getValue(2))
    }

    @Test
    fun `existing pack covering an episode is reused before any candidate`() {
        val plan = planBatchDownload(
            episodes.take(3), listOf(single1, single2, pack1to3),
            existing = listOf(ExistingDownload(pack1to6, episodeId = 4)),
        )
        assertEquals(EpisodeDownloadPlan.Reuse(pack1to6), plan.getValue(1))
        assertEquals(EpisodeDownloadPlan.Reuse(pack1to6), plan.getValue(2))
        assertEquals(EpisodeDownloadPlan.Reuse(pack1to6), plan.getValue(3))
    }

    @Test
    fun `pinned single is used for its episode only`() {
        val plan = planBatchDownload(
            episodes.take(2), listOf(single2, pack1to6), emptyList(),
            pinned = single1, pinnedEpisodeId = 1,
        )
        assertEquals(EpisodeDownloadPlan.Create(single1), plan.getValue(1))
        assertEquals(EpisodeDownloadPlan.Create(single2), plan.getValue(2))
    }

    @Test
    fun `pinned pack also covers the other episodes it contains`() {
        val plan = planBatchDownload(
            episodes, listOf(single1, single2, pack4to6), emptyList(),
            pinned = pack1to3, pinnedEpisodeId = 2,
        )
        for (id in 1..3) assertEquals(EpisodeDownloadPlan.Create(pack1to3), plan.getValue(id), "episode $id")
        for (id in 4..6) assertEquals(EpisodeDownloadPlan.Create(pack4to6), plan.getValue(id), "episode $id")
    }

    @Test
    fun `packs are combined greedily and beat singles when covering two or more episodes`() {
        val plan = planBatchDownload(episodes, listOf(single1, single2, pack1to3, pack4to6), emptyList())
        for (id in 1..3) assertEquals(EpisodeDownloadPlan.Create(pack1to3), plan.getValue(id), "episode $id")
        for (id in 4..6) assertEquals(EpisodeDownloadPlan.Create(pack4to6), plan.getValue(id), "episode $id")
    }

    @Test
    fun `larger pack wins over smaller packs`() {
        val plan = planBatchDownload(episodes, listOf(pack1to3, pack1to6, pack4to6), emptyList())
        for (id in 1..6) assertEquals(EpisodeDownloadPlan.Create(pack1to6), plan.getValue(id), "episode $id")
    }

    @Test
    fun `pack with known episodes beats a season pack even when covering fewer`() {
        val plan = planBatchDownload(episodes, listOf(season, pack1to3), emptyList())
        for (id in 1..3) assertEquals(EpisodeDownloadPlan.Create(pack1to3), plan.getValue(id), "episode $id")
        for (id in 4..6) assertEquals(EpisodeDownloadPlan.Create(season), plan.getValue(id), "episode $id")
    }

    @Test
    fun `single is preferred over a pack that would cover only one remaining episode`() {
        val plan = planBatchDownload(episodes.take(1), listOf(pack1to3, single1), emptyList())
        assertEquals(EpisodeDownloadPlan.Create(single1), plan.getValue(1))
    }

    @Test
    fun `pack is still used for a lone episode without a single`() {
        val plan = planBatchDownload(episodes.take(1), listOf(season, pack1to3), emptyList())
        assertEquals(EpisodeDownloadPlan.Create(pack1to3), plan.getValue(1))
    }

    @Test
    fun `episodes matched by ep instead of sort are covered`() {
        val episode = requestTestEpisode(13).copy(sort = EpisodeSort(13), ep = EpisodeSort(1))
        val plan = planBatchDownload(listOf(episode), listOf(single1), emptyList())
        assertEquals(EpisodeDownloadPlan.Create(single1), plan.getValue(13))
    }

    @Test
    fun `episodes nobody covers are uncovered`() {
        val unparsed = requestTestMedia(300, range = null)
        val plan = planBatchDownload(episodes.take(3), listOf(single1, unparsed), emptyList())
        assertEquals(EpisodeDownloadPlan.Create(single1), plan.getValue(1))
        assertEquals(EpisodeDownloadPlan.Uncovered, plan.getValue(2))
        assertEquals(EpisodeDownloadPlan.Uncovered, plan.getValue(3))
    }

    @Test
    fun `availability is stable between previewing all episodes and confirming a subset`() {
        val candidates = listOf(single1, pack1to6)
        val preview = planBatchDownload(episodes, candidates, emptyList(), pinned = single1, pinnedEpisodeId = 1)
        val confirmed = planBatchDownload(
            listOf(episodes[0], episodes[3]), candidates, emptyList(), pinned = single1, pinnedEpisodeId = 1,
        )
        assertEquals(EpisodeDownloadPlan.Create(pack1to6), preview.getValue(4))
        assertEquals(EpisodeDownloadPlan.Create(pack1to6), confirmed.getValue(4))
    }
}
