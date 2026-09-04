/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.bangumi

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * 覆盖 [BangumiConflictChecker]: 发布结果 / 节流与 force / 失败不消耗节流 / 同步进行中轮询与超时 /
 * reset / clearConflicts / 缓存失效 (已知同步时间变化, 或进程内观察到同步未完成后才完成; 冷启动不失效; 超时不失效, 观察跨检查保留) /
 * 并发与取消.
 *
 * 全部使用 `runTest` 虚拟时间: 轮询的 `delay` 走 [TestScope] 的调度器, 节流走注入的时钟.
 */
@OptIn(TestOnly::class)
class BangumiConflictCheckerTest {

    private class FakeMergeRepository(
        var summaryProvider: suspend () -> BangumiMergeSummary,
    ) : BangumiMergeRepository() {
        var summaryCalls = 0
            private set

        override suspend fun getSummary(): BangumiMergeSummary {
            summaryCalls++
            return summaryProvider()
        }

        override suspend fun getMergeState(): BangumiMergeState = throw UnsupportedOperationException()

        override suspend fun resolve(resolutions: List<BangumiConflictResolution>): BangumiMergeState =
            throw UnsupportedOperationException()
    }

    /**
     * 只记录 [invalidateAllCaches] 调用, 其他方法不应被调用.
     */
    private class RecordingSubjectCollectionRepository : SubjectCollectionRepository() {
        var invalidateAllCalls = 0
            private set
        var invalidateAllFailure: Throwable? = null

        override suspend fun invalidateAllCaches() {
            invalidateAllCalls++
            invalidateAllFailure?.let { throw it }
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

    private val syncedAt = Instant.fromEpochMilliseconds(1_753_000_000_000)
    private val syncedAtLater = Instant.fromEpochMilliseconds(1_753_000_600_000)

    private fun summary(
        conflictCount: Int = 3,
        lastSyncedAt: Instant? = syncedAt,
        syncInProgress: Boolean = false,
    ) = BangumiMergeSummary(
        conflictCount = conflictCount,
        autoMergedTotal = 0,
        lastSyncedAt = lastSyncedAt,
        syncInProgress = syncInProgress,
    )

    private fun TestScope.createChecker(
        repository: BangumiMergeRepository,
        subjectRepository: SubjectCollectionRepository = RecordingSubjectCollectionRepository(),
        getCurrentTimeMillis: () -> Long = { 1_000_000L },
        checkInterval: Duration = 1.hours,
        syncPollInterval: Duration = 5.seconds,
        syncPollTimeout: Duration = 10.minutes,
    ) = BangumiConflictChecker(
        mergeRepository = repository,
        subjectCollectionRepository = subjectRepository,
        parentCoroutineContext = backgroundScope.coroutineContext,
        getCurrentTimeMillis = getCurrentTimeMillis,
        checkInterval = checkInterval,
        syncPollInterval = syncPollInterval,
        syncPollTimeout = syncPollTimeout,
    )

    // region 发布结果

    @Test
    fun `CHECK-01 startCheck 发布冲突数与上次同步时间`() = runTest {
        val repository = FakeMergeRepository { summary(conflictCount = 6) }
        val checker = createChecker(repository)

        assertEquals(0, checker.conflictCount.value)
        assertNull(checker.lastSyncedAt.value)

        checker.startCheck()
        checker.joinCheck()

        assertEquals(6, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        assertEquals(1, repository.summaryCalls)
    }

    @Test
    fun `CHECK-02 check 返回摘要并发布`() = runTest {
        val repository = FakeMergeRepository { summary(conflictCount = 2) }
        val checker = createChecker(repository)

        val result = checker.check()

        assertEquals(summary(conflictCount = 2), result)
        assertEquals(2, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        assertEquals(1, repository.summaryCalls)
    }

    @Test
    fun `CHECK-03 没有冲突时冲突数为 0 但仍记录同步时间`() = runTest {
        val repository = FakeMergeRepository { summary(conflictCount = 0) }
        val checker = createChecker(repository)

        checker.startCheck()
        checker.joinCheck()

        assertEquals(0, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
    }

    // endregion

    // region 节流

    @Test
    fun `CHECK-04 间隔内重复 startCheck 不重复检查, 超过间隔后重新检查`() = runTest {
        var time = 1_000_000L
        val repository = FakeMergeRepository { summary() }
        val checker = createChecker(repository, getCurrentTimeMillis = { time }, checkInterval = 1.hours)

        checker.startCheck()
        checker.joinCheck()
        assertEquals(1, repository.summaryCalls)

        // 间隔内: 不检查
        time += 59.minutes.inWholeMilliseconds
        checker.startCheck()
        checker.joinCheck()
        assertEquals(1, repository.summaryCalls)

        // 刚好到达间隔: 重新检查
        time += 1.minutes.inWholeMilliseconds
        checker.startCheck()
        checker.joinCheck()
        assertEquals(2, repository.summaryCalls)
    }

    @Test
    fun `CHECK-05 节流中的 check 不请求服务端, 直接返回上次结果`() = runTest {
        val repository = FakeMergeRepository { summary(conflictCount = 5) }
        val checker = createChecker(repository)

        val first = checker.check()
        val second = checker.check()

        assertEquals(summary(conflictCount = 5), first)
        assertEquals(first, second)
        assertEquals(1, repository.summaryCalls)
    }

    @Test
    fun `CHECK-06 force 绕过节流`() = runTest {
        val repository = FakeMergeRepository { summary() }
        val checker = createChecker(repository)

        checker.check()
        assertEquals(1, repository.summaryCalls)

        checker.check(force = true)
        assertEquals(2, repository.summaryCalls)

        checker.startCheck(force = true)
        checker.joinCheck()
        assertEquals(3, repository.summaryCalls)

        // force 检查成功后节流窗口重新开始, 非 force 仍被节流
        checker.startCheck()
        checker.joinCheck()
        assertEquals(3, repository.summaryCalls)
    }

    // endregion

    // region 失败

    @Test
    fun `CHECK-07 检查失败返回 null 且不消耗节流窗口`() = runTest {
        var fail = true
        val repository = FakeMergeRepository {
            if (fail) throw IllegalStateException("network down") else summary(conflictCount = 4)
        }
        val checker = createChecker(repository)

        assertNull(checker.check())
        assertEquals(0, checker.conflictCount.value)
        assertNull(checker.lastSyncedAt.value)
        assertEquals(1, repository.summaryCalls)

        // 失败不消耗节流窗口: 不需要 force 就会重试
        fail = false
        assertEquals(summary(conflictCount = 4), checker.check())
        assertEquals(2, repository.summaryCalls)
        assertEquals(4, checker.conflictCount.value)
    }

    @Test
    fun `CHECK-08 startCheck 失败静默且不上报`() = runTest {
        val repository = FakeMergeRepository { throw IllegalStateException("network down") }
        val checker = createChecker(repository)

        checker.startCheck()
        checker.joinCheck()

        assertEquals(0, checker.conflictCount.value)
        assertNull(checker.lastSyncedAt.value)
        assertEquals(1, repository.summaryCalls)

        // 未消耗节流: 再次触发会再检查
        checker.startCheck()
        checker.joinCheck()
        assertEquals(2, repository.summaryCalls)
    }

    @Test
    fun `CHECK-09 成功后再失败保留上次已知结果`() = runTest {
        var fail = false
        val repository = FakeMergeRepository {
            if (fail) throw IllegalStateException("network down") else summary(conflictCount = 3)
        }
        val checker = createChecker(repository)

        checker.check()
        assertEquals(3, checker.conflictCount.value)

        fail = true
        assertNull(checker.check(force = true))
        assertEquals(3, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
    }

    // endregion

    // region 同步进行中轮询

    @Test
    fun `CHECK-10 syncInProgress 时按间隔轮询直到完成, 只发布最终结果`() = runTest {
        var inProgressRemaining = 2
        val repository = FakeMergeRepository {
            if (inProgressRemaining > 0) {
                inProgressRemaining--
                summary(conflictCount = 1, lastSyncedAt = syncedAt, syncInProgress = true)
            } else {
                summary(conflictCount = 5, lastSyncedAt = syncedAtLater)
            }
        }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository, syncPollInterval = 5.seconds)

        checker.startCheck()
        runCurrent()
        assertEquals(1, repository.summaryCalls)
        // 同步进行中: 不发布中间结果
        assertEquals(0, checker.conflictCount.value)
        assertNull(checker.lastSyncedAt.value)
        assertEquals(0, subjectRepository.invalidateAllCalls)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, repository.summaryCalls)
        assertEquals(0, checker.conflictCount.value)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(3, repository.summaryCalls)
        assertEquals(5, checker.conflictCount.value)
        assertEquals(syncedAtLater, checker.lastSyncedAt.value)
        // 只对最终结果失效一次缓存
        assertEquals(1, subjectRepository.invalidateAllCalls)

        checker.joinCheck()
        assertEquals(3, repository.summaryCalls)
    }

    @Test
    fun `CHECK-11 lastSyncedAt 为 null (从未同步) 时轮询直到有同步时间`() = runTest {
        var synced = false
        val repository = FakeMergeRepository {
            if (synced) summary(conflictCount = 2) else summary(conflictCount = 0, lastSyncedAt = null)
        }
        val checker = createChecker(repository, syncPollInterval = 5.seconds)

        checker.startCheck()
        runCurrent()
        assertEquals(1, repository.summaryCalls)
        assertNull(checker.lastSyncedAt.value)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, repository.summaryCalls)
        assertNull(checker.lastSyncedAt.value)

        synced = true
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(3, repository.summaryCalls)
        assertEquals(2, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)

        checker.joinCheck()
        assertEquals(3, repository.summaryCalls)
    }

    @Test
    fun `CHECK-12 轮询超时后采用最后一次摘要并消耗节流, 但不失效缓存`() = runTest {
        val repository = FakeMergeRepository { summary(conflictCount = 2, syncInProgress = true) }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository, syncPollInterval = 5.seconds, syncPollTimeout = 32.seconds)

        checker.startCheck()
        checker.joinCheck()

        // t = 0, 5, 10, 15, 20, 25, 30; 32s 超时
        assertEquals(7, repository.summaryCalls)
        assertEquals(2, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        // 同步还没完成, 不能让收藏列表拉半成品
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 超时不算失败: 节流已消耗, 不会立刻再轮询
        checker.startCheck()
        checker.joinCheck()
        assertEquals(7, repository.summaryCalls)
    }

    @Test
    fun `CHECK-13 轮询途中失败返回 null 且不发布, 不消耗节流`() = runTest {
        var n = 0
        val repository = FakeMergeRepository {
            when (++n) {
                1 -> summary(conflictCount = 1, syncInProgress = true)
                2 -> throw IllegalStateException("network down")
                else -> summary(conflictCount = 7)
            }
        }
        val checker = createChecker(repository, syncPollInterval = 5.seconds)

        assertNull(checker.check())
        assertEquals(2, repository.summaryCalls)
        assertEquals(0, checker.conflictCount.value)
        assertNull(checker.lastSyncedAt.value)

        // 未消耗节流
        assertEquals(summary(conflictCount = 7), checker.check())
        assertEquals(3, repository.summaryCalls)
        assertEquals(7, checker.conflictCount.value)
    }

    // endregion

    // region reset / clearConflicts

    @Test
    fun `CHECK-14 reset 清空结果与节流`() = runTest {
        val repository = FakeMergeRepository { summary(conflictCount = 3) }
        val checker = createChecker(repository)

        checker.check()
        assertEquals(3, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)

        checker.reset()
        assertEquals(0, checker.conflictCount.value)
        assertNull(checker.lastSyncedAt.value)

        // 节流已清空: 非 force 也会重新检查
        checker.startCheck()
        checker.joinCheck()
        assertEquals(2, repository.summaryCalls)
        assertEquals(3, checker.conflictCount.value)
    }

    @Test
    fun `CHECK-15 reset 取消进行中的后台检查`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val repository = FakeMergeRepository {
            try {
                gate.await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
            summary(conflictCount = 3)
        }
        val checker = createChecker(repository)

        checker.startCheck()
        runCurrent()
        assertEquals(1, repository.summaryCalls)

        checker.reset()
        checker.joinCheck()
        assertTrue(cancelled)
        assertEquals(0, checker.conflictCount.value)

        // 被取消的检查结束后放行也不会发布
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, checker.conflictCount.value)

        // 之后可以正常检查
        checker.startCheck()
        checker.joinCheck()
        assertEquals(2, repository.summaryCalls)
        assertEquals(3, checker.conflictCount.value)
    }

    @Test
    fun `CHECK-16 clearConflicts 只清空计数, 保留同步时间与节流`() = runTest {
        val repository = FakeMergeRepository { summary(conflictCount = 3) }
        val checker = createChecker(repository)

        checker.check()
        checker.clearConflicts()

        assertEquals(0, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)

        checker.startCheck()
        checker.joinCheck()
        assertEquals(1, repository.summaryCalls)
        assertEquals(0, checker.conflictCount.value)
    }

    // endregion

    // region 缓存失效

    @Test
    fun `CHECK-17 已知同步时间变化时使收藏缓存失效, 不变时不失效`() = runTest {
        var current: Instant? = syncedAt
        val repository = FakeMergeRepository { summary(conflictCount = 1, lastSyncedAt = current) }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository)

        // 冷启动: 进程内还不知道同步时间, 直接拿到已完成的摘要不失效 (否则每次启动都会让全部缓存过期)
        checker.check(force = true)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 未变化: 不失效
        checker.check(force = true)
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 变化: 失效
        current = syncedAtLater
        checker.check(force = true)
        assertEquals(1, subjectRepository.invalidateAllCalls)
        assertEquals(syncedAtLater, checker.lastSyncedAt.value)

        // 再次未变化: 不失效
        checker.check(force = true)
        assertEquals(1, subjectRepository.invalidateAllCalls)
    }

    @Test
    fun `CHECK-18 reset 后再次检查视同冷启动 - 已完成的摘要不失效, 观察到同步后完成才失效`() = runTest {
        var inProgress = false
        val repository = FakeMergeRepository { summary(syncInProgress = inProgress) }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository, syncPollInterval = 5.seconds)

        checker.check()
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 解绑再绑定同一账号 (服务端稳态): 不失效
        checker.reset()
        checker.check()
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 换绑后的首次同步: 检查时看到同步进行中, 完成后失效
        checker.reset()
        inProgress = true
        val job = launch { checker.check() }
        runCurrent()
        assertEquals(0, subjectRepository.invalidateAllCalls)
        inProgress = false
        advanceTimeBy(5.seconds)
        runCurrent()
        job.join()
        assertEquals(1, subjectRepository.invalidateAllCalls)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
    }

    @Test
    fun `CHECK-19 缓存失效失败不影响发布结果`() = runTest {
        var current: Instant? = syncedAt
        val repository = FakeMergeRepository { summary(conflictCount = 3, lastSyncedAt = current) }
        val subjectRepository = RecordingSubjectCollectionRepository().apply {
            invalidateAllFailure = IllegalStateException("database closed")
        }
        val checker = createChecker(repository, subjectRepository)

        checker.check()
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 同步时间变化 → 失效 (失败), 结果照常发布
        current = syncedAtLater
        assertEquals(summary(conflictCount = 3, lastSyncedAt = syncedAtLater), checker.check(force = true))
        assertEquals(3, checker.conflictCount.value)
        assertEquals(syncedAtLater, checker.lastSyncedAt.value)
        assertEquals(1, subjectRepository.invalidateAllCalls)

        // 检查本身成功: 节流已消耗
        checker.startCheck()
        checker.joinCheck()
        assertEquals(2, repository.summaryCalls)
    }

    @Test
    fun `CHECK-23 冷启动时服务端已完成同步 - 发布结果但不失效缓存`() = runTest {
        val repository = FakeMergeRepository { summary(conflictCount = 2) }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository)

        checker.startCheck()
        checker.joinCheck()

        assertEquals(2, checker.conflictCount.value)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        assertEquals(0, subjectRepository.invalidateAllCalls)
    }

    @Test
    fun `CHECK-24 首次绑定 - 本次检查观察到从未同步 (lastSyncedAt 为 null) 后完成, 失效缓存一次`() = runTest {
        var synced = false
        val repository = FakeMergeRepository {
            if (synced) summary(conflictCount = 2) else summary(conflictCount = 0, lastSyncedAt = null)
        }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository, syncPollInterval = 5.seconds)

        checker.startCheck()
        runCurrent()
        assertEquals(0, subjectRepository.invalidateAllCalls)

        synced = true
        advanceTimeBy(5.seconds)
        runCurrent()
        checker.joinCheck()
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        assertEquals(2, checker.conflictCount.value)
        assertEquals(1, subjectRepository.invalidateAllCalls)

        // 之后同步时间未变: 不再失效
        checker.check(force = true)
        assertEquals(1, subjectRepository.invalidateAllCalls)
    }

    @Test
    fun `CHECK-25 首次绑定轮询超时不失效, 之后的检查看到同步完成才失效一次`() = runTest {
        var synced = false
        val repository = FakeMergeRepository {
            if (synced) summary(conflictCount = 2) else summary(conflictCount = 0, lastSyncedAt = null, syncInProgress = true)
        }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository, syncPollInterval = 5.seconds, syncPollTimeout = 32.seconds)

        // 大收藏的首次同步超过轮询上限: 发布未完成的摘要, 不失效
        checker.check()
        assertEquals(7, repository.summaryCalls)
        assertNull(checker.lastSyncedAt.value)
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 之后 (1h 后或 force) 直接拿到已完成的摘要: 之前的观察仍然有效, 失效一次
        synced = true
        checker.check(force = true)
        assertEquals(8, repository.summaryCalls)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        assertEquals(2, checker.conflictCount.value)
        assertEquals(1, subjectRepository.invalidateAllCalls)

        // 观察已消费: 同步时间未变不再失效
        checker.check(force = true)
        assertEquals(1, subjectRepository.invalidateAllCalls)
    }

    @Test
    fun `CHECK-26 轮询中被 force 重启, 新检查看到同步完成时失效一次`() = runTest {
        var inProgress = true
        val repository = FakeMergeRepository {
            if (inProgress) summary(conflictCount = 0, lastSyncedAt = null, syncInProgress = true) else summary(conflictCount = 3)
        }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository, syncPollInterval = 5.seconds)

        checker.startCheck()
        runCurrent()
        assertEquals(1, repository.summaryCalls)
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 合并页 / 手动全量同步结束后 force 重启检查, 旧检查被取消; 新检查第一次请求就拿到已完成的摘要
        inProgress = false
        checker.startCheck(force = true)
        checker.joinCheck()
        assertEquals(2, repository.summaryCalls)
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        assertEquals(3, checker.conflictCount.value)
        assertEquals(1, subjectRepository.invalidateAllCalls)

        checker.check(force = true)
        assertEquals(1, subjectRepository.invalidateAllCalls)
    }

    @Test
    fun `CHECK-27 reset 清除未消费的观察 - 之后视同冷启动不失效`() = runTest {
        var synced = false
        val repository = FakeMergeRepository {
            if (synced) summary(conflictCount = 2) else summary(conflictCount = 0, lastSyncedAt = null, syncInProgress = true)
        }
        val subjectRepository = RecordingSubjectCollectionRepository()
        val checker = createChecker(repository, subjectRepository, syncPollInterval = 5.seconds, syncPollTimeout = 32.seconds)

        checker.check()
        assertEquals(0, subjectRepository.invalidateAllCalls)

        // 解绑 / 会话失效
        checker.reset()
        synced = true
        checker.check()
        assertEquals(syncedAt, checker.lastSyncedAt.value)
        assertEquals(0, subjectRepository.invalidateAllCalls)
    }

    // endregion

    // region 并发与取消

    @Test
    fun `CHECK-20 后台检查进行中时非 force startCheck 不重复启动, force 则取消重来`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var cancelled = 0
        val repository = FakeMergeRepository {
            try {
                gate.await()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
            summary(conflictCount = 3)
        }
        val checker = createChecker(repository)

        checker.startCheck()
        runCurrent()
        assertEquals(1, repository.summaryCalls)

        checker.startCheck()
        runCurrent()
        assertEquals(1, repository.summaryCalls)
        assertEquals(0, cancelled)

        checker.startCheck(force = true)
        runCurrent()
        assertEquals(1, cancelled)
        assertEquals(2, repository.summaryCalls)

        gate.complete(Unit)
        checker.joinCheck()
        assertEquals(3, checker.conflictCount.value)
        assertEquals(2, repository.summaryCalls)
    }

    @Test
    fun `CHECK-21 并发 check 串行执行, 后者被节流`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeMergeRepository {
            gate.await()
            summary(conflictCount = 3)
        }
        val checker = createChecker(repository)

        val results = mutableListOf<BangumiMergeSummary?>()
        val first = launch { results += checker.check() }
        val second = launch { results += checker.check() }
        runCurrent()
        assertEquals(1, repository.summaryCalls)

        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals(1, repository.summaryCalls)
        assertEquals<List<BangumiMergeSummary?>>(listOf(summary(conflictCount = 3), summary(conflictCount = 3)), results)
        assertEquals(3, checker.conflictCount.value)
    }

    @Test
    fun `CHECK-22 调用方取消 check 时传播取消, 不算失败也不消耗节流`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeMergeRepository {
            gate.await()
            summary(conflictCount = 3)
        }
        val checker = createChecker(repository)

        var thrown: Throwable? = null
        val job = launch {
            try {
                checker.check()
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        runCurrent()
        assertEquals(1, repository.summaryCalls)

        job.cancel()
        job.join()
        assertIs<CancellationException>(thrown)
        assertEquals(0, checker.conflictCount.value)

        gate.complete(Unit)
        assertEquals(summary(conflictCount = 3), checker.check())
        assertEquals(2, repository.summaryCalls)
    }

    // endregion
}
