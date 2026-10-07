/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.data.persistent.database.dao

import androidx.paging.PagingSource
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.preference.CollectionSortOrder
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SubjectCollectionSortTest {
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


    @Test
    fun sortingAndFilteringApplyBeforePaging() = runBlocking {
        val database = createTestAniDatabase()
        try {
            val dao = database.subjectCollection()
            dao.upsert(listOf(
                subjectCollection(1).copy(nameCn = "Zulu", lastUpdated = 30, airDate = PackedDate.parseFromDate("2024-01-01")),
                subjectCollection(2).copy(nameCn = "", name = "alpha", lastUpdated = 10, airDate = PackedDate.Invalid),
                subjectCollection(3).copy(nameCn = "Beta", lastUpdated = 20, airDate = PackedDate.parseFromDate("2025-01-01")),
                subjectCollection(4).copy(nameCn = "Beta", lastUpdated = 20, airDate = PackedDate.parseFromDate("2025-01-01")),
                subjectCollection(5).copy(nameCn = "AAA", nsfw = true),
                subjectCollection(6).copy(collectionType = UnifiedCollectionType.DONE),
            ))
            for ((order, expected) in listOf(
                CollectionSortOrder.LAST_UPDATED to listOf(1, 4, 3, 2),
                CollectionSortOrder.NAME to listOf(2, 4, 3, 1),
                CollectionSortOrder.AIR_DATE to listOf(4, 3, 1, 2),
            )) {
                val source = dao.filterByCollectionTypePaging(UnifiedCollectionType.DOING, false, order.name)
                val first = assertIs<PagingSource.LoadResult.Page<Int, SubjectCollectionAndEpisodes>>(
                    source.load(PagingSource.LoadParams.Refresh(null, 2, false)),
                )
                val second = assertIs<PagingSource.LoadResult.Page<Int, SubjectCollectionAndEpisodes>>(
                    source.load(PagingSource.LoadParams.Append(requireNotNull(first.nextKey), 2, false)),
                )
                assertEquals(expected, (first.data + second.data).map { it.collection.subjectId }, order.name)
            }
        } finally {
            database.close()
        }
    }
}
