/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class SearchDanmakuRequestTest {
    @Test
    fun `has stable equals`() {
        assertEquals(
            createRequest(),
            createRequest(),
        )
    }

    private fun createRequest() = SearchDanmakuRequest(
        subjectInfo = createSubjectInfo(),
        episodeInfo = createEpisodeInfo(),
        episodeId = 1,
        filename = "filename",
        fileLength = 2,
        fileHash = "hash",
        videoDuration = 24.minutes,
    )

    private fun createEpisodeInfo() = EpisodeInfo(
        episodeId = 9307,
        type = EpisodeType.OVA,
        name = "Flora Gibbs",
        nameCn = "Kristina Witt",
        airDate = PackedDate(2021, 1, 2),
        comment = 2092,
        desc = "in",
        sort = EpisodeSort(1),
        ep = EpisodeSort(2),
    )

    private fun createSubjectInfo() = SubjectInfo(
        subjectId = 5177,
        subjectType = SubjectType.ANIME,
        name = "Junior Riggs",
        nameCn = "Simone Murphy",
        summary = "viderer",
        nsfw = false,
        imageLarge = "appetere",
        totalEpisodes = 6398,
        airDate = PackedDate(2021, 1, 2),
        tags = listOf(Tag("a", 2)),
        aliases = listOf("aaaa"),
        ratingInfo = RatingInfo(
            rank = 4164, total = 1125,
            count = RatingCounts(
                s1 = 7639,
                s2 = 8911,
                s3 = 6703,
                s4 = 3866,
                s5 = 2973,
                s6 = 3899,
                s7 = 4849,
                s8 = 6338,
                s9 = 7915,
                s10 = 4746,
            ),
            score = "lectus",
        ),
        collectionStats = SubjectCollectionStats(
            wish = 1188,
            doing = 3042,
            done = 8465,
            onHold = 8480,
            dropped = 2138,
        ),
        completeDate = PackedDate(2021, 1, 2),
    )
}