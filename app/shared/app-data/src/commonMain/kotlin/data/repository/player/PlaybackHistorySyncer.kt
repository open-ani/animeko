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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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
import me.him188.ani.client.models.AniPlaybackHistoryDeleteRecord
import me.him188.ani.client.models.AniPlaybackHistoryOp
import me.him188.ani.client.models.AniPlaybackHistoryUpsertRecord
import me.him188.ani.client.models.AniSyncRequest
import me.him188.ani.client.models.AniUPSERT
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.info
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlaybackHistorySyncer(
    private val repository: EpisodePlayHistoryRepository,
    private val api: ApiInvoker<PlaybackHistoryAniApi>,
    private val sessionStateProvider: SessionStateProvider,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    requestCooldown: Duration = 5.seconds,
) : Repository() {
    private val syncMutex = Mutex()
    private val requestGate = LeadingTrailingSyncGate(
        scope = scope,
        cooldown = requestCooldown,
        task = ::syncOnceCatching,
    )

    fun start() {
        scope.launch(CoroutineName("PlaybackHistorySyncer")) {
            sessionStateProvider.stateFlow
                .filterIsInstance<SessionState.Valid>()
                .first()
            requestSync()

            sessionStateProvider.eventFlow.collect { event ->
                if (event is SessionEvent.NewLogin) {
                    requestSync()
                }
            }
        }
    }

    fun requestSync() {
        requestGate.request()
    }

    suspend fun syncOnce() = syncMutex.withLock {
        if (sessionStateProvider.stateFlow.first() !is SessionState.Valid) return@withLock

        val pendingOps = repository.pendingOpsFlow.first()
        val ops = pendingOps.latestPerEpisode().map { it.toApiOp() }
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

    private fun PlaybackHistoryPendingOp.toApiOp(): AniPlaybackHistoryOp {
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

    private fun AniPlaybackHistoryUpsertRecord.toEpisodeHistory(): EpisodeHistory {
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

    private fun AniPlaybackHistoryDeleteRecord.toEpisodeHistory(): EpisodeHistory {
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

/**
 * Runs the first request immediately, then coalesces requests received during [cooldown] into one trailing run.
 * Every trailing run starts a new cooldown of its own.
 */
internal class LeadingTrailingSyncGate(
    scope: CoroutineScope,
    private val cooldown: Duration,
    private val task: suspend () -> Unit,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        require(cooldown.isPositive()) { "cooldown must be positive" }
        scope.launch(CoroutineName("PlaybackHistorySyncer.requestGate")) {
            while (currentCoroutineContext().isActive) {
                requests.receive()
                do {
                    task()
                    delay(cooldown)
                } while (requests.tryReceive().isSuccess)
            }
        }
    }

    fun request() {
        requests.trySend(Unit)
    }
}

internal fun List<PlaybackHistoryPendingOp>.latestPerEpisode(): List<PlaybackHistoryPendingOp> {
    return groupBy(PlaybackHistoryPendingOp::episodeId)
        .values
        .map { episodeOps ->
            episodeOps.maxWith(
                compareBy(PlaybackHistoryPendingOp::versionMillis, PlaybackHistoryPendingOp::id),
            )
        }
        .sortedBy(PlaybackHistoryPendingOp::id)
}
