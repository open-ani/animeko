/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.PlaybackHistoryAniApi
import me.him188.ani.client.models.AniDELETE
import me.him188.ani.client.models.AniPlaybackHistoryRoutingPlaybackHistoryDeleteRecord
import me.him188.ani.client.models.AniPlaybackHistoryRoutingPlaybackHistoryOp
import me.him188.ani.client.models.AniPlaybackHistoryRoutingPlaybackHistoryUpsertRecord
import me.him188.ani.client.models.AniSyncRequest
import me.him188.ani.client.models.AniUPSERT
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.info
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

class PlaybackHistorySyncer(
    private val repository: EpisodePlayHistoryRepository,
    private val api: ApiInvoker<PlaybackHistoryAniApi>,
    private val sessionStateProvider: SessionStateProvider,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : Repository() {
    private val syncMutex = Mutex()

    fun start() {
        scope.launch(CoroutineName("PlaybackHistorySyncer")) {
            sessionStateProvider.stateFlow
                .filterIsInstance<SessionState.Valid>()
                .first()
            syncOnceCatching()

            sessionStateProvider.eventFlow.collect { event ->
                if (event is SessionEvent.NewLogin) {
                    syncOnceCatching()
                }
            }
        }
    }

    fun requestSync() {
        scope.launch(CoroutineName("PlaybackHistorySyncer.requestSync")) {
            syncOnceCatching()
        }
    }

    suspend fun syncOnce() = syncMutex.withLock {
        if (sessionStateProvider.stateFlow.first() !is SessionState.Valid) return@withLock

        val pendingOps = repository.pendingOpsFlow.first()
        val ops = pendingOps.map { it.toApiOp() }
        val cursor = repository.lastSyncAtMillisFlow.first()

        val response = withContext(ioDispatcher) {
            api {
                sync(
                    AniSyncRequest(
                        ops = ops,
                        lastSyncAt = cursor.toApiInstantString(),
                    ),
                ).body()
            }
        }

        repository.applySyncResult(
            sentPendingOpIds = pendingOps.map { it.id },
            records = response.upserts.map { it.toEpisodeHistory() } +
                    response.deletes.map { it.toEpisodeHistory() },
            nextSyncAtMillis = response.nextSyncAt.toEpochMillis(),
        )
    }

    private suspend fun syncOnceCatching() {
        try {
            syncOnce()
        } catch (e: Exception) {
            RepositoryException.wrapOrThrowCancellation(e)
            logger.info { "Failed to sync playback histories: ${e.message}" }
        }
    }

    private fun PlaybackHistoryPendingOp.toApiOp(): AniPlaybackHistoryRoutingPlaybackHistoryOp {
        return when (this) {
            is PlaybackHistoryPendingOp.Delete -> toApiDelete()
            is PlaybackHistoryPendingOp.Upsert -> toApiUpsert()
        }
    }

    private fun PlaybackHistoryPendingOp.Upsert.toApiUpsert(): AniUPSERT {
        return AniUPSERT(
            episodeId = episodeId.toLong(),
            subjectId = subjectId.toLong(),
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            updatedAt = updatedAtMillis.toApiInstantString(),
            episodeSort = episodeSort,
            subjectName = subjectName,
            subjectImageUrl = subjectImageUrl,
            episodeName = episodeName,
        )
    }

    private fun PlaybackHistoryPendingOp.Delete.toApiDelete(): AniDELETE {
        return AniDELETE(
            episodeId = episodeId.toLong(),
            deletedAt = deletedAtMillis.toApiInstantString(),
        )
    }

    private fun AniPlaybackHistoryRoutingPlaybackHistoryUpsertRecord.toEpisodeHistory(): EpisodeHistory {
        return EpisodeHistory(
            episodeId = episodeId.toInt(),
            positionMillis = positionMillis,
            subjectId = subjectId.toInt(),
            episodeSort = episodeSort,
            subjectName = subjectName,
            subjectImageUrl = subjectImageUrl,
            episodeName = episodeName,
            durationMillis = durationMillis,
            updatedAtMillis = updatedAt.toEpochMillis(),
            deletedAtMillis = null,
            isDirty = false,
        )
    }

    private fun AniPlaybackHistoryRoutingPlaybackHistoryDeleteRecord.toEpisodeHistory(): EpisodeHistory {
        return EpisodeHistory(
            episodeId = episodeId.toInt(),
            positionMillis = 0,
            updatedAtMillis = 0,
            deletedAtMillis = deletedAt.toEpochMillis(),
            isDirty = false,
        )
    }

    private fun Long.toApiInstantString(): String {
        return Instant.fromEpochMilliseconds(this).toString()
    }

    private fun String.toEpochMillis(): Long {
        return Instant.parse(this).toEpochMilliseconds()
    }
}
