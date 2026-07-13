/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.persistent.database.dao.PlaybackHistoryDao
import me.him188.ani.app.data.persistent.database.dao.toEntity
import me.him188.ani.app.data.persistent.database.dao.toEpisodeHistory
import me.him188.ani.app.data.persistent.database.dao.toPendingOp
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.platform.currentTimeMillis

@Serializable
data class EpisodeHistories(
    // Playback histories are stored in SQLite. This field is only kept for one-time migration.
    val histories: List<EpisodeHistory> = emptyList(),
    val lastSyncAtMillis: Long = 0,
) {
    companion object {
        val Empty = EpisodeHistories()
    }
}

sealed class PlaybackHistoryPendingOp {
    abstract val id: Long
    abstract val episodeId: Int
    abstract val versionMillis: Long

    data class Upsert(
        override val id: Long,
        override val episodeId: Int,
        val subjectId: Int,
        val episodeSort: Float? = null,
        val subjectName: String? = null,
        val subjectImageUrl: String? = null,
        val episodeName: String? = null,
        val positionMillis: Long,
        val durationMillis: Long,
        val updatedAtMillis: Long,
    ) : PlaybackHistoryPendingOp() {
        override val versionMillis: Long get() = updatedAtMillis
    }

    data class Delete(
        override val id: Long,
        override val episodeId: Int,
        val deletedAtMillis: Long,
    ) : PlaybackHistoryPendingOp() {
        override val versionMillis: Long get() = deletedAtMillis
    }
}

interface EpisodePlayHistoryRepository {
    val flow: Flow<List<EpisodeHistory>>
    val allHistoriesFlow: Flow<List<EpisodeHistory>>
    val pendingOpsFlow: Flow<List<PlaybackHistoryPendingOp>>
    val lastSyncAtMillisFlow: Flow<Long>

    suspend fun clear()

    suspend fun remove(episodeId: Int)

    suspend fun removeAll(episodeIds: Collection<Int>)

    suspend fun saveOrUpdate(
        episodeId: Int,
        positionMillis: Long,
        subjectId: Int? = null,
        episodeSort: Float? = null,
        subjectName: String? = null,
        subjectImageUrl: String? = null,
        episodeName: String? = null,
        durationMillis: Long? = null,
    )

    suspend fun applySyncResult(
        sentPendingOpIds: Collection<Long>,
        records: List<EpisodeHistory>,
        nextSyncAtMillis: Long,
    )

    suspend fun deletePendingOps(ids: Collection<Long>)

    suspend fun getPositionMillisByEpisodeId(episodeId: Int): Long?
}

class EpisodePlayHistoryRepositoryImpl(
    private val dataStore: DataStore<EpisodeHistories>,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val nowMillis: () -> Long = { currentTimeMillis() },
    private val onDirtyChanged: () -> Unit = {},
) : Repository(), EpisodePlayHistoryRepository {
    private val legacyMigrationMutex = Mutex()
    private var legacyMigrationChecked = false

    override val allHistoriesFlow: Flow<List<EpisodeHistory>> = playbackHistoryDao.allRecordsFlow().map { records ->
        records.map { it.toEpisodeHistory() }
    }
    override val flow: Flow<List<EpisodeHistory>> = playbackHistoryDao.activeRecordsFlow().map { records ->
        records.map { it.toEpisodeHistory() }
    }
    override val pendingOpsFlow: Flow<List<PlaybackHistoryPendingOp>> = playbackHistoryDao.pendingOpsFlow().map { ops ->
        ops.map { it.toPendingOp() }
    }
    override val lastSyncAtMillisFlow: Flow<Long> = dataStore.data.map { it.lastSyncAtMillis }

    override suspend fun clear() {
        ensureLegacyDataStoreMigrated()
        val activeHistories = playbackHistoryDao.getActiveRecords().map { it.toEpisodeHistory() }
        if (activeHistories.isEmpty()) return

        val now = nowMillis()
        playbackHistoryDao.upsertRecords(
            activeHistories.map { history ->
                history.copy(deletedAtMillis = now, isDirty = false).toEntity()
            },
        )
        playbackHistoryDao.replacePendingOps(
            activeHistories.map { history ->
                PlaybackHistoryPendingOp.Delete(
                    id = 0,
                    episodeId = history.episodeId,
                    deletedAtMillis = now,
                ).toEntity()
            },
        )
        onDirtyChanged()
    }

    override suspend fun remove(episodeId: Int) {
        removeAll(listOf(episodeId))
    }

    override suspend fun removeAll(episodeIds: Collection<Int>) {
        ensureLegacyDataStoreMigrated()
        if (episodeIds.isEmpty()) return

        val now = nowMillis()
        val idSet = episodeIds.toSet()
        val histories = idSet.mapNotNull { playbackHistoryDao.getRecordByEpisodeId(it) }
            .map { it.toEpisodeHistory() }
            .filterNot(EpisodeHistory::isDeleted)
        if (histories.isEmpty()) return

        logger.info { "remove play progress for episodes $idSet" }
        playbackHistoryDao.upsertRecords(
            histories.map { history ->
                history.copy(deletedAtMillis = now, isDirty = false).toEntity()
            },
        )
        playbackHistoryDao.replacePendingOps(
            histories.map { history ->
                PlaybackHistoryPendingOp.Delete(
                    id = 0,
                    episodeId = history.episodeId,
                    deletedAtMillis = now,
                ).toEntity()
            },
        )
        onDirtyChanged()
    }

    override suspend fun saveOrUpdate(
        episodeId: Int,
        positionMillis: Long,
        subjectId: Int?,
        episodeSort: Float?,
        subjectName: String?,
        subjectImageUrl: String?,
        episodeName: String?,
        durationMillis: Long?,
    ) {
        ensureLegacyDataStoreMigrated()
        val now = nowMillis()
        val existing = playbackHistoryDao.getRecordByEpisodeId(episodeId)?.toEpisodeHistory()
        val episodeHistory = EpisodeHistory(
            episodeId = episodeId,
            positionMillis = positionMillis,
            subjectId = subjectId ?: existing?.subjectId,
            episodeSort = episodeSort ?: existing?.episodeSort,
            subjectName = subjectName ?: existing?.subjectName,
            subjectImageUrl = subjectImageUrl ?: existing?.subjectImageUrl,
            episodeName = episodeName ?: existing?.episodeName,
            durationMillis = durationMillis ?: existing?.durationMillis,
            updatedAtMillis = now,
            deletedAtMillis = null,
            isDirty = false,
        )
        logger.info { "save or update play progress $episodeHistory" }
        playbackHistoryDao.upsertRecord(episodeHistory.toEntity())

        val pendingOp = episodeHistory.toPendingUpsertOrNull()
        if (pendingOp != null) {
            playbackHistoryDao.replacePendingOp(pendingOp.toEntity())
            onDirtyChanged()
        }
    }

    override suspend fun applySyncResult(
        sentPendingOpIds: Collection<Long>,
        records: List<EpisodeHistory>,
        nextSyncAtMillis: Long,
    ) {
        ensureLegacyDataStoreMigrated()
        if (sentPendingOpIds.isNotEmpty()) {
            playbackHistoryDao.deletePendingOpsByIds(sentPendingOpIds)
        }

        val pendingEpisodeIds = playbackHistoryDao.getPendingOps().mapTo(mutableSetOf()) { it.episodeId }
        playbackHistoryDao.upsertRecords(
            records
                .filter { it.episodeId !in pendingEpisodeIds }
                .map { it.copy(isDirty = false).toEntity() },
        )
        dataStore.updateData { current ->
            current.copy(lastSyncAtMillis = nextSyncAtMillis)
        }
    }

    override suspend fun deletePendingOps(ids: Collection<Long>) {
        ensureLegacyDataStoreMigrated()
        if (ids.isEmpty()) return
        playbackHistoryDao.deletePendingOpsByIds(ids)
    }

    override suspend fun getPositionMillisByEpisodeId(episodeId: Int): Long? {
        ensureLegacyDataStoreMigrated()
        return playbackHistoryDao.getRecordByEpisodeId(episodeId)
            ?.toEpisodeHistory()
            ?.takeUnless(EpisodeHistory::isDeleted)
            ?.positionMillis
            ?.also {
                logger.info { "load play progress for episode $episodeId: positionMillis=$it" }
            }
    }

    private fun EpisodeHistory.toPendingUpsertOrNull(): PlaybackHistoryPendingOp.Upsert? {
        val subjectId = subjectId ?: return null
        val durationMillis = durationMillis ?: return null
        return PlaybackHistoryPendingOp.Upsert(
            id = 0,
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

    private suspend fun ensureLegacyDataStoreMigrated() {
        if (legacyMigrationChecked) return
        legacyMigrationMutex.withLock {
            if (legacyMigrationChecked) return@withLock

            val legacyHistories = dataStore.data.first().histories
            if (legacyHistories.isNotEmpty() && playbackHistoryDao.allRecordsFlow().first().isEmpty()) {
                val migratedHistories = legacyHistories.map { it.copy(isDirty = false) }
                playbackHistoryDao.upsertRecords(migratedHistories.map { it.toEntity() })

                val pendingOps = migratedHistories
                    .filterNot(EpisodeHistory::isDeleted)
                    .mapNotNull { it.toPendingUpsertOrNull() }
                if (pendingOps.isNotEmpty()) {
                    playbackHistoryDao.replacePendingOps(pendingOps.map { it.toEntity() })
                    onDirtyChanged()
                }
            }
            if (legacyHistories.isNotEmpty()) {
                dataStore.updateData { current -> current.copy(histories = emptyList()) }
            }
            legacyMigrationChecked = true
        }
    }
}
