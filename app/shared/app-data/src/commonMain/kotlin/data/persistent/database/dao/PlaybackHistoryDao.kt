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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.repository.player.PlaybackHistoryPendingOp
import me.him188.ani.utils.platform.annotations.TestOnly

@Entity(
    tableName = "playback_history_record",
    indices = [
        Index(value = ["episodeId"], unique = true),
        Index(value = ["updatedAtMillis"]),
        Index(value = ["deletedAtMillis"]),
    ],
)
data class PlaybackHistoryRecordEntity(
    @PrimaryKey val episodeId: Int,
    val positionMillis: Long,
    val subjectId: Int? = null,
    val episodeSort: Float? = null,
    val subjectName: String? = null,
    val subjectImageUrl: String? = null,
    val episodeName: String? = null,
    val durationMillis: Long? = null,
    val updatedAtMillis: Long = 0,
    val deletedAtMillis: Long? = null,
)

@Entity(
    tableName = "playback_history_pending_op",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["episodeId"]),
    ],
)
data class PlaybackHistoryPendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opType: String,
    val episodeId: Int,
    val subjectId: Int? = null,
    val episodeSort: Float? = null,
    val subjectName: String? = null,
    val subjectImageUrl: String? = null,
    val episodeName: String? = null,
    val positionMillis: Long? = null,
    val durationMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val deletedAtMillis: Long? = null,
)

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history_record WHERE deletedAtMillis IS NULL ORDER BY updatedAtMillis DESC")
    fun activeRecordsFlow(): Flow<List<PlaybackHistoryRecordEntity>>

    @Query("SELECT * FROM playback_history_record ORDER BY max(updatedAtMillis, coalesce(deletedAtMillis, 0)) DESC")
    fun allRecordsFlow(): Flow<List<PlaybackHistoryRecordEntity>>

    @Query("SELECT * FROM playback_history_record WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getRecordByEpisodeId(episodeId: Int): PlaybackHistoryRecordEntity?

    @Query("SELECT * FROM playback_history_record WHERE deletedAtMillis IS NULL")
    suspend fun getActiveRecords(): List<PlaybackHistoryRecordEntity>

    @Upsert
    suspend fun upsertRecord(record: PlaybackHistoryRecordEntity)

    @Upsert
    suspend fun upsertRecords(records: List<PlaybackHistoryRecordEntity>)

    @Query("SELECT * FROM playback_history_pending_op ORDER BY id ASC")
    fun pendingOpsFlow(): Flow<List<PlaybackHistoryPendingOpEntity>>

    @Query("SELECT * FROM playback_history_pending_op ORDER BY id ASC")
    suspend fun getPendingOps(): List<PlaybackHistoryPendingOpEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPendingOp(op: PlaybackHistoryPendingOpEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPendingOps(ops: List<PlaybackHistoryPendingOpEntity>): List<Long>

    @Query("DELETE FROM playback_history_pending_op WHERE episodeId = :episodeId")
    suspend fun deletePendingOpsByEpisodeId(episodeId: Int)

    @Query("DELETE FROM playback_history_pending_op WHERE id IN (:ids)")
    suspend fun deletePendingOpsByIds(ids: Collection<Long>)

    @Transaction
    suspend fun replacePendingOp(op: PlaybackHistoryPendingOpEntity): Long {
        deletePendingOpsByEpisodeId(op.episodeId)
        return insertPendingOp(op)
    }

    @Transaction
    suspend fun replacePendingOps(ops: List<PlaybackHistoryPendingOpEntity>): List<Long> {
        return ops.map { op ->
            deletePendingOpsByEpisodeId(op.episodeId)
            insertPendingOp(op)
        }
    }
}

fun PlaybackHistoryRecordEntity.toEpisodeHistory(): EpisodeHistory {
    return EpisodeHistory(
        episodeId = episodeId,
        positionMillis = positionMillis,
        subjectId = subjectId,
        episodeSort = episodeSort,
        subjectName = subjectName,
        subjectImageUrl = subjectImageUrl,
        episodeName = episodeName,
        durationMillis = durationMillis,
        updatedAtMillis = updatedAtMillis,
        deletedAtMillis = deletedAtMillis,
        isDirty = false,
    )
}

fun EpisodeHistory.toEntity(): PlaybackHistoryRecordEntity {
    return PlaybackHistoryRecordEntity(
        episodeId = episodeId,
        positionMillis = positionMillis,
        subjectId = subjectId,
        episodeSort = episodeSort,
        subjectName = subjectName,
        subjectImageUrl = subjectImageUrl,
        episodeName = episodeName,
        durationMillis = durationMillis,
        updatedAtMillis = updatedAtMillis,
        deletedAtMillis = deletedAtMillis,
    )
}

fun PlaybackHistoryPendingOpEntity.toPendingOp(): PlaybackHistoryPendingOp {
    return when (opType) {
        PlaybackHistoryPendingOpEntityType.Upsert -> PlaybackHistoryPendingOp.Upsert(
            id = id,
            episodeId = episodeId,
            subjectId = requireNotNull(subjectId) { "subjectId is required for upsert playback history op" },
            episodeSort = episodeSort,
            subjectName = subjectName,
            subjectImageUrl = subjectImageUrl,
            episodeName = episodeName,
            positionMillis = requireNotNull(positionMillis) { "positionMillis is required for upsert playback history op" },
            durationMillis = requireNotNull(durationMillis) { "durationMillis is required for upsert playback history op" },
            updatedAtMillis = requireNotNull(updatedAtMillis) { "updatedAtMillis is required for upsert playback history op" },
        )

        PlaybackHistoryPendingOpEntityType.Delete -> PlaybackHistoryPendingOp.Delete(
            id = id,
            episodeId = episodeId,
            deletedAtMillis = requireNotNull(deletedAtMillis) {
                "deletedAtMillis is required for delete playback history op"
            },
        )

        else -> error("Unknown playback history pending op type: $opType")
    }
}

fun PlaybackHistoryPendingOp.Upsert.toEntity(): PlaybackHistoryPendingOpEntity {
    return PlaybackHistoryPendingOpEntity(
        opType = PlaybackHistoryPendingOpEntityType.Upsert,
        episodeId = episodeId,
        subjectId = subjectId,
        episodeSort = episodeSort,
        subjectName = subjectName,
        subjectImageUrl = subjectImageUrl,
        episodeName = episodeName,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

fun PlaybackHistoryPendingOp.Delete.toEntity(): PlaybackHistoryPendingOpEntity {
    return PlaybackHistoryPendingOpEntity(
        opType = PlaybackHistoryPendingOpEntityType.Delete,
        episodeId = episodeId,
        deletedAtMillis = deletedAtMillis,
    )
}

private object PlaybackHistoryPendingOpEntityType {
    const val Upsert = "UPSERT"
    const val Delete = "DELETE"
}

@TestOnly
fun createMemoryPlaybackHistoryDao(): PlaybackHistoryDao {
    return object : PlaybackHistoryDao {
        private val recordsStore = MutableStateFlow(emptyList<PlaybackHistoryRecordEntity>())
        private val pendingOpsStore = MutableStateFlow(emptyList<PlaybackHistoryPendingOpEntity>())
        private var nextPendingOpId = 1L

        override fun activeRecordsFlow(): Flow<List<PlaybackHistoryRecordEntity>> {
            return recordsStore.map { records ->
                records.filter { it.deletedAtMillis == null }.sortedByDescending { it.updatedAtMillis }
            }
        }

        override fun allRecordsFlow(): Flow<List<PlaybackHistoryRecordEntity>> {
            return recordsStore.map { records ->
                records.sortedByDescending { maxOf(it.updatedAtMillis, it.deletedAtMillis ?: 0) }
            }
        }

        override suspend fun getRecordByEpisodeId(episodeId: Int): PlaybackHistoryRecordEntity? {
            return recordsStore.value.find { it.episodeId == episodeId }
        }

        override suspend fun getActiveRecords(): List<PlaybackHistoryRecordEntity> {
            return recordsStore.value.filter { it.deletedAtMillis == null }
        }

        override suspend fun upsertRecord(record: PlaybackHistoryRecordEntity) {
            recordsStore.value = recordsStore.value.upsertByEpisodeId(record)
        }

        override suspend fun upsertRecords(records: List<PlaybackHistoryRecordEntity>) {
            recordsStore.value = records.fold(recordsStore.value) { current, record ->
                current.upsertByEpisodeId(record)
            }
        }

        override fun pendingOpsFlow(): Flow<List<PlaybackHistoryPendingOpEntity>> {
            return pendingOpsStore
        }

        override suspend fun getPendingOps(): List<PlaybackHistoryPendingOpEntity> {
            return pendingOpsStore.value
        }

        override suspend fun insertPendingOp(op: PlaybackHistoryPendingOpEntity): Long {
            val id = nextPendingOpId++
            pendingOpsStore.value += op.copy(id = id)
            return id
        }

        override suspend fun insertPendingOps(ops: List<PlaybackHistoryPendingOpEntity>): List<Long> {
            return ops.map { insertPendingOp(it) }
        }

        override suspend fun deletePendingOpsByEpisodeId(episodeId: Int) {
            pendingOpsStore.value = pendingOpsStore.value.filterNot { it.episodeId == episodeId }
        }

        override suspend fun deletePendingOpsByIds(ids: Collection<Long>) {
            val idSet = ids.toSet()
            pendingOpsStore.value = pendingOpsStore.value.filterNot { it.id in idSet }
        }

        private fun List<PlaybackHistoryRecordEntity>.upsertByEpisodeId(
            record: PlaybackHistoryRecordEntity,
        ): List<PlaybackHistoryRecordEntity> {
            val index = indexOfFirst { it.episodeId == record.episodeId }
            return if (index == -1) {
                this + record
            } else {
                toMutableList().apply { this[index] = record }
            }
        }
    }
}
