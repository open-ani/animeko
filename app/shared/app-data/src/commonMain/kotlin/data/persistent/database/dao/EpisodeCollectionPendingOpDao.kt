/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 剧集收藏状态的本地待同步操作 (outbox). 用户改了剧集看过状态先写本地, 再由 syncer 推到服务端.
 *
 * 每集只保留最后一次操作, 旧操作被新操作顶掉.
 */
@Entity(
    tableName = "episode_collection_pending_op",
    indices = [
        Index(value = ["episodeId"], unique = true),
    ],
)
data class EpisodeCollectionPendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Int,
    val episodeId: Int,
    val collectionType: UnifiedCollectionType,
    val updatedAtMillis: Long,
)

@Dao
interface EpisodeCollectionPendingOpDao {
    @Query("SELECT * FROM episode_collection_pending_op ORDER BY id ASC")
    fun pendingOpsFlow(): Flow<List<EpisodeCollectionPendingOpEntity>>

    @Query("SELECT * FROM episode_collection_pending_op ORDER BY id ASC")
    suspend fun getPendingOps(): List<EpisodeCollectionPendingOpEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPendingOp(op: EpisodeCollectionPendingOpEntity): Long

    @Query("DELETE FROM episode_collection_pending_op WHERE episodeId IN (:episodeIds)")
    suspend fun deletePendingOpsByEpisodeIds(episodeIds: Collection<Int>)

    @Query("DELETE FROM episode_collection_pending_op WHERE id IN (:ids)")
    suspend fun deletePendingOpsByIds(ids: Collection<Long>)

    @Transaction
    suspend fun replacePendingOps(ops: List<EpisodeCollectionPendingOpEntity>): List<Long> {
        deletePendingOpsByEpisodeIds(ops.map { it.episodeId })
        return ops.map { insertPendingOp(it) }
    }
}
