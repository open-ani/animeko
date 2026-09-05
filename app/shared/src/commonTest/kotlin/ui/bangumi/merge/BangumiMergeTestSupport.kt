/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.bangumi.BangumiConflictResolution
import me.him188.ani.app.data.models.bangumi.BangumiMergeState
import me.him188.ani.app.data.models.bangumi.BangumiMergeSummary
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.data.repository.subject.OfflineSubjectDisplayInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.bangumi.BangumiConflictChecker
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.time.Instant

/**
 * resolve 的默认返回: 没有剩余冲突, 且同步已完成 (有同步时间).
 * 真实服务端的 resolve 响应总带 `lastSyncedAt`; [BangumiMergeState.Empty] 的 `lastSyncedAt` 为 `null`, 会被界面视为 "同步中".
 */
internal fun createTestResolvedEmptyState(
    lastSyncedAt: Instant = Instant.fromEpochMilliseconds(1_753_000_000_000),
): BangumiMergeState = BangumiMergeState.Empty.copy(lastSyncedAt = lastSyncedAt, syncInProgress = false)

/**
 * 测试用的合并仓库: [stateProvider] 提供 [getMergeState] 的结果, [resolveHandler] 提供 [resolve] 的结果 (默认已完成同步且无剩余冲突).
 * 记录全部调用.
 */
internal class FakeBangumiMergeRepository(
    var stateProvider: suspend () -> BangumiMergeState,
    var resolveHandler: suspend (List<BangumiConflictResolution>) -> BangumiMergeState = { createTestResolvedEmptyState() },
) : BangumiMergeRepository() {
    var stateCalls = 0
        private set
    var summaryCalls = 0
        private set
    val resolveCalls = mutableListOf<List<BangumiConflictResolution>>()

    /** 非空时 [resolve] 在记录调用后挂起, 直到测试放行. 用于测试并发防重入. */
    var resolveGate: CompletableDeferred<Unit>? = null

    /** [resolve] 记录调用后 (放行前) 完成. 用于跨线程等待请求已发出. */
    val resolveStarted = CompletableDeferred<Unit>()

    /** [resolve] 正常返回前完成. */
    val resolveFinished = CompletableDeferred<Unit>()

    /** [getSummary] 首次被调用时完成. */
    val summaryRequested = CompletableDeferred<Unit>()

    override suspend fun getSummary(): BangumiMergeSummary {
        summaryCalls++
        summaryRequested.complete(Unit)
        val state = stateProvider()
        return BangumiMergeSummary(
            conflictCount = state.conflictCount,
            autoMergedTotal = state.autoMergedTotal,
            lastSyncedAt = state.lastSyncedAt,
            syncInProgress = state.syncInProgress,
        )
    }

    override suspend fun getMergeState(): BangumiMergeState {
        stateCalls++
        return stateProvider()
    }

    override suspend fun resolve(resolutions: List<BangumiConflictResolution>): BangumiMergeState {
        resolveCalls.add(resolutions)
        resolveStarted.complete(Unit)
        resolveGate?.await()
        val result = resolveHandler(resolutions)
        resolveFinished.complete(Unit)
        return result
    }
}

/**
 * 只实现 [BangumiConflictChecker] 需要的缓存失效方法, 其余抛出.
 */
internal class StubSubjectCollectionRepository : SubjectCollectionRepository() {
    var invalidateAllCalls = 0
        private set

    override suspend fun invalidateAllCaches() {
        invalidateAllCalls++
    }

    override suspend fun invalidateCache(subjectIds: List<Int>) = throw UnsupportedOperationException()

    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> =
        throw UnsupportedOperationException()

    override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> =
        throw UnsupportedOperationException()

    override fun subjectCollectionsPager(
        query: CollectionsFilterQuery,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<SubjectCollectionInfo>> = throw UnsupportedOperationException()

    override fun cachedValidSubjectIds(): Flow<List<Int>> = throw UnsupportedOperationException()

    override suspend fun updateRecentlyUpdatedSubjectCollections(
        limit: Int,
        type: UnifiedCollectionType?,
        offset: Int,
    ) = throw UnsupportedOperationException()

    override fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>?,
    ): Flow<List<SubjectCollectionInfo>> = throw UnsupportedOperationException()

    override suspend fun updateRating(
        subjectId: Int,
        score: Int?,
        comment: String?,
        tags: List<String>?,
        isPrivate: Boolean?,
    ) = throw UnsupportedOperationException()

    override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) =
        throw UnsupportedOperationException()

    override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> =
        throw UnsupportedOperationException()

    override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> =
        throw UnsupportedOperationException()

    override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
        throw UnsupportedOperationException()

    override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
        throw UnsupportedOperationException()

    override suspend fun performBangumiFullSync() = throw UnsupportedOperationException()

    override suspend fun getBangumiFullSyncState(): BangumiSyncState? = throw UnsupportedOperationException()
}

/**
 * 用 [repository] 构造一个真实的 [BangumiConflictChecker] (固定时钟, 不轮询).
 */
internal fun createTestConflictChecker(
    repository: BangumiMergeRepository,
    getCurrentTimeMillis: () -> Long = { 1_000_000L },
): BangumiConflictChecker = BangumiConflictChecker(
    mergeRepository = repository,
    subjectCollectionRepository = StubSubjectCollectionRepository(),
    getCurrentTimeMillis = getCurrentTimeMillis,
)
