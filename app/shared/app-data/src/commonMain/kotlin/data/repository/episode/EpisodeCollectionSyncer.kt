/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.network.toAniEpisodeCollectionTypeUpdate
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.player.LeadingTrailingSyncGate
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniBatchUpdateEpisodeCollectionsRequest
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 把 [EpisodeCollectionRepository] 里的待同步操作推到服务端.
 *
 * 同条目同状态的剧集合并成一次批量请求. 网络或服务端故障时保留操作等下次; 服务端明确拒绝 (4xx, 例如条目没收藏或剧集不存在)
 * 时丢弃, 因为重试也不会成功.
 */
class EpisodeCollectionSyncer(
    private val repository: EpisodeCollectionPendingOpSource,
    private val api: ApiInvoker<SubjectsAniApi>,
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
        name = "EpisodeCollectionSyncer.requestGate",
    )

    fun start() {
        scope.launch(CoroutineName("EpisodeCollectionSyncer")) {
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

    /**
     * 推送一轮. 遇到网络或服务端故障时抛出 [RepositoryException] 并保留剩余操作.
     */
    suspend fun syncOnce() = syncMutex.withLock {
        if (sessionStateProvider.stateFlow.first() !is SessionState.Valid) return@withLock

        val pendingOps = repository.pendingOpsFlow.first()
        if (pendingOps.isEmpty()) return@withLock

        for (batch in pendingOps.batchBySubjectAndType()) {
            val dropped = try {
                withContext(ioDispatcher) {
                    api {
                        batchUpdateEpisodeCollections(
                            batch.subjectId.toLong(),
                            AniBatchUpdateEpisodeCollectionsRequest(
                                episodeIds = batch.ops.map { it.episodeId.toLong() },
                                episodeCollectionType = batch.collectionType.toAniEpisodeCollectionTypeUpdate(),
                            ),
                        )
                    }
                }
                false
            } catch (e: ClientRequestException) {
                if (e.response.status.isRetryable()) throw RepositoryException.wrapOrThrowCancellation(e)
                logger.warn { "Server rejected episode collection ops for subject ${batch.subjectId}, dropping: ${e.message}" }
                true
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
            if (!dropped) {
                logger.info { "Synced ${batch.ops.size} episode collection ops for subject ${batch.subjectId}" }
            }
            repository.deletePendingOps(batch.ops.map { it.id })
        }
    }

    private suspend fun syncOnceCatching() {
        try {
            syncOnce()
        } catch (e: Exception) {
            RepositoryException.wrapOrThrowCancellation(e)
            logger.info { "Failed to sync episode collections: ${e.message}" }
        }
    }

    private fun HttpStatusCode.isRetryable(): Boolean {
        return this == HttpStatusCode.Unauthorized ||
                this == HttpStatusCode.Forbidden ||
                this == HttpStatusCode.TooManyRequests ||
                this == HttpStatusCode.RequestTimeout
    }
}

internal data class EpisodeCollectionOpBatch(
    val subjectId: Int,
    val collectionType: UnifiedCollectionType,
    val ops: List<EpisodeCollectionPendingOp>,
)

/**
 * 按 (条目, 状态) 分组, 组的顺序按组内最早的操作排; 服务端只区分看过和未看过, 其他状态按 [toAniEpisodeCollectionTypeUpdate] 的映射归到未看过.
 */
internal fun List<EpisodeCollectionPendingOp>.batchBySubjectAndType(): List<EpisodeCollectionOpBatch> {
    return groupBy { it.subjectId to it.collectionType.toAniEpisodeCollectionTypeUpdate() }
        .values
        .map { ops -> EpisodeCollectionOpBatch(ops.first().subjectId, ops.first().collectionType, ops) }
        .sortedBy { batch -> batch.ops.minOf { it.id } }
}
