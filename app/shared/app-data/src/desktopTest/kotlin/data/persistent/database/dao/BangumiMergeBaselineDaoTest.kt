/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BangumiMergeBaselineDaoTest {
    private fun runDatabaseTest(block: suspend (AniDatabase) -> Unit) = runBlocking {
        val database = createTestAniDatabase()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun entity(
        subjectId: Int,
        type: UnifiedCollectionType = UnifiedCollectionType.DOING,
        score: Int = 8,
        comment: String? = "好看",
        watched: List<Int> = listOf(1, 2, 3),
        updatedAtMillis: Long = 1000,
    ) = BangumiMergeBaselineEntity(
        subjectId = subjectId,
        collectionType = type,
        score = score,
        comment = comment,
        watchedEpisodeIds = watched,
        updatedAtMillis = updatedAtMillis,
    )

    @Test
    fun `DAO-01 upsert 后读回完整字段 含剧集列表`() = runDatabaseTest { database ->
        val dao = database.bangumiMergeBaselineDao()
        dao.upsertAll(listOf(entity(1, watched = listOf(101, 102))))

        val loaded = dao.getBySubjectId(1)!!
        assertEquals(UnifiedCollectionType.DOING, loaded.collectionType)
        assertEquals(8, loaded.score)
        assertEquals("好看", loaded.comment)
        assertEquals(listOf(101, 102), loaded.watchedEpisodeIds)
        assertEquals(1000, loaded.updatedAtMillis)
    }

    @Test
    fun `DAO-02 upsert 覆盖同一条目`() = runDatabaseTest { database ->
        val dao = database.bangumiMergeBaselineDao()
        dao.upsertAll(listOf(entity(1, score = 5)))
        dao.upsertAll(listOf(entity(1, score = 9, comment = null)))

        val loaded = dao.getBySubjectId(1)!!
        assertEquals(9, loaded.score)
        assertNull(loaded.comment)
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `DAO-03 replaceAll 整体替换`() = runDatabaseTest { database ->
        val dao = database.bangumiMergeBaselineDao()
        dao.upsertAll(listOf(entity(1), entity(2)))

        dao.replaceAll(listOf(entity(3)))

        assertEquals(listOf(3), dao.getAll().map { it.subjectId })
    }

    @Test
    fun `DAO-04 deleteBySubjectIds 只删除指定条目`() = runDatabaseTest { database ->
        val dao = database.bangumiMergeBaselineDao()
        dao.upsertAll(listOf(entity(1), entity(2), entity(3)))

        dao.deleteBySubjectIds(listOf(1, 3))

        assertEquals(listOf(2), dao.getAll().map { it.subjectId })
    }

    @Test
    fun `DAO-05 基线不依赖收藏表 收藏删除后保留`() = runDatabaseTest { database ->
        // 故意没有外键: 收藏行在同步时可能被整表删除重建, 基线必须保留.
        val dao = database.bangumiMergeBaselineDao()
        dao.upsertAll(listOf(entity(1)))

        database.subjectCollection().deleteAll()

        assertEquals(1, dao.getAll().size)
    }
}
