/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.EpisodeType.ED
import me.him188.ani.datasources.api.EpisodeType.MainStory
import me.him188.ani.datasources.api.EpisodeType.OP
import me.him188.ani.datasources.api.EpisodeType.SP
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.serialization.BigNum
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeCollectionDaoTest {
    private fun runDatabaseTest(block: suspend (AniDatabase) -> Unit) = runBlocking {
        val database = createTestAniDatabase()
        try {
            database.subjectCollection().upsert(subjectCollection(SUBJECT_ID))
            block(database)
        } finally {
            database.close()
        }
    }

    private fun subjectCollection(subjectId: Int) = SubjectCollectionEntity(
        subjectId = subjectId,
        name = "test",
        nameCn = "测试",
        summary = "",
        nsfw = false,
        imageLarge = "",
        totalEpisodes = 12,
        airDate = PackedDate.Invalid,
        aliases = emptyList(),
        tags = emptyList(),
        collectionStats = SubjectCollectionStats.Zero,
        ratingInfo = RatingInfo.Empty,
        completeDate = PackedDate.Invalid,
        selfRatingInfo = SelfRatingInfo.Empty,
        collectionType = UnifiedCollectionType.DOING,
        recurrence = null,
        lastUpdated = 0,
        lastFetched = 0,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )

    @Suppress("DEPRECATION")
    private fun episode(episodeId: Int, type: EpisodeType?, sort: String) = EpisodeCollectionEntity(
        subjectId = SUBJECT_ID,
        episodeId = episodeId,
        episodeType = type,
        name = "ep",
        nameCn = "",
        airDate = PackedDate.Invalid,
        comment = 0,
        desc = "",
        sort = EpisodeSort(BigNum(sort), type),
        sortNumber = sort.toFloat(),
        selfCollectionType = UnifiedCollectionType.NOT_COLLECTED,
        lastFetched = 0,
    )

    // 按 id 打乱插入顺序, 结果只取决于 ORDER BY
    private val episodes = listOf(
        episode(1, MainStory, "1"),
        episode(2, SP, "1"),
        episode(3, MainStory, "2"),
        episode(4, ED, "1"),
        episode(5, SP, "0"),
        episode(6, MainStory, "1.5"),
        episode(7, OP, "1"),
        episode(8, null, "1"),
        episode(9, SP, "2"),
    )

    @Test
    fun `main episodes come first then other types in EpisodeType order`() = runDatabaseTest { database ->
        val dao = database.episodeCollection()
        dao.upsert(episodes.shuffled())

        val expected = listOf(1, 6, 3, 5, 2, 9, 7, 4, 8)
        assertEquals(expected, dao.filterBySubjectId(SUBJECT_ID).first().map { it.episodeId })
        assertEquals(expected, dao.listIdBySubjectId(SUBJECT_ID).first())
    }

    @Test
    fun `filtering by types keeps the grouped order`() = runDatabaseTest { database ->
        val dao = database.episodeCollection()
        dao.upsert(episodes.shuffled())

        assertEquals(
            listOf(1, 6, 3, 5, 2, 9),
            dao.filterBySubjectId(SUBJECT_ID, listOf(SP, MainStory)).first().map { it.episodeId },
        )
    }

    @Test
    fun `order matches EpisodeSort comparison`() = runDatabaseTest { database ->
        val dao = database.episodeCollection()
        dao.upsert(episodes.shuffled())

        assertEquals(
            episodes.sortedBy { it.sort }.map { it.episodeId },
            dao.filterBySubjectId(SUBJECT_ID).first().map { it.episodeId },
        )
    }

    private companion object {
        const val SUBJECT_ID = 1
    }
}
