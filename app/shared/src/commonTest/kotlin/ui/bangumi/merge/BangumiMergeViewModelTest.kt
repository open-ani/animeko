/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.data.models.bangumi.BangumiConflictField
import me.him188.ani.app.data.models.bangumi.BangumiConflictFieldType
import me.him188.ani.app.data.models.bangumi.BangumiConflictKey
import me.him188.ani.app.data.models.bangumi.BangumiConflictResolution
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.bangumi.BangumiMergeState
import me.him188.ani.app.data.models.bangumi.BangumiSubjectConflict
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.data.repository.subject.BangumiMergeSyncInProgressException
import me.him188.ani.app.domain.bangumi.BangumiConflictChecker
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * 覆盖 [BangumiMergeViewModel]: 加载 / 选择 / 采用较新的 / 全选 / 应用门控 / 提交与剩余状态 / 失败 /
 * 同步未完成 (进行中或从未同步) 时轮询与上限 (轮询协程跑在 runTest 的调度器上, 用虚拟时间驱动) / 作用域取消时提交仍完成.
 */
@OptIn(TestOnly::class, ExperimentalCoroutinesApi::class)
class BangumiMergeViewModelTest {
    private val now = Instant.fromEpochMilliseconds(1_753_000_000_000)

    private val key1Collection = BangumiConflictKey(1, BangumiConflictFieldType.COLLECTION)
    private val key2Collection = BangumiConflictKey(2, BangumiConflictFieldType.COLLECTION)
    private val key2Rating = BangumiConflictKey(2, BangumiConflictFieldType.RATING)
    private val key3Rating = BangumiConflictKey(3, BangumiConflictFieldType.RATING)
    private val key4Collection = BangumiConflictKey(4, BangumiConflictFieldType.COLLECTION)
    private val key5Rating = BangumiConflictKey(5, BangumiConflictFieldType.RATING)

    private lateinit var checker: BangumiConflictChecker

    private fun startTestKoin(repository: FakeBangumiMergeRepository) {
        checker = createTestConflictChecker(repository)
        startKoin {
            modules(
                module {
                    single<BangumiMergeRepository> { repository }
                    single<BangumiConflictChecker> { checker }
                },
            )
        }
    }

    /**
     * 本测试构造的 ViewModel, 结束时取消其后台作用域, 不让轮询协程泄漏到之后的测试.
     */
    private val viewModels = mutableListOf<BangumiMergeViewModel>()

    private fun newViewModel(
        syncPollInterval: Duration = 5.seconds,
        syncPollTimeout: Duration = 10.minutes,
        pollCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    ): BangumiMergeViewModel = BangumiMergeViewModel(syncPollInterval, syncPollTimeout, pollCoroutineContext)
        .also { viewModels += it }

    /**
     * 轮询协程跑在 runTest 的调度器上: `delay` / `withTimeoutOrNull` 用虚拟时间, 测试体挂起等待时 runTest 会自动推进.
     */
    private fun TestScope.newPollingViewModel(
        syncPollInterval: Duration = 5.seconds,
        syncPollTimeout: Duration = 10.minutes,
    ): BangumiMergeViewModel = newViewModel(
        syncPollInterval,
        syncPollTimeout,
        pollCoroutineContext = StandardTestDispatcher(testScheduler),
    )

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.backgroundScope.cancel() }
        viewModels.clear()
        stopKoin()
    }

    // 不用 withTimeout: runTest 的虚拟时间会在真实 Default 线程完成前触发超时.
    // runTest 自带 60s 真实超时兜底.
    private suspend fun BangumiMergeViewModel.awaitLoaded(): BangumiMergeUiState =
        uiState.first { !it.isLoading }

    // 在真实线程上等待 (带真实超时): runTest 的虚拟时间会让 withTimeout 立刻触发, 所以切到 Default.
    private suspend fun <T> awaitReal(deferred: Deferred<T>): T = withContext(Dispatchers.Default) {
        withTimeout(10.seconds) { deferred.await() }
    }

    private fun testRepository(
        resolveHandler: suspend (List<BangumiConflictResolution>) -> BangumiMergeState = {
            createTestResolvedEmptyState(lastSyncedAt = now)
        },
    ) = FakeBangumiMergeRepository({ createTestBangumiMergeState(now) }, resolveHandler)

    // region 加载

    @Test
    fun `VM-01 加载成功后展示冲突分组与自动合并`() = runTest {
        val repository = testRepository()
        startTestKoin(repository)
        val vm = newViewModel()

        val state = vm.awaitLoaded()
        assertNull(state.loadError)
        assertNotNull(state.mergeState)
        assertTrue(state.hasConflicts)
        assertEquals(5, state.groups.size)
        assertEquals(6, state.totalConflictCount)
        assertEquals(0, state.confirmedCount)
        assertFalse(state.allResolved)
        assertFalse(state.canApply)
        assertFalse(state.syncInProgress)
        assertEquals(12, state.autoMergedTotal)
        assertEquals(5, state.autoMerged.size)
        assertEquals(now - 10.minutes, state.lastSyncedAt)
        assertEquals(1, repository.stateCalls)
    }

    @Test
    fun `VM-02 加载失败展示错误 重试后成功`() = runTest {
        var fail = true
        val repository = FakeBangumiMergeRepository(
            {
                if (fail) throw IllegalStateException("network down") else createTestBangumiMergeState(now)
            },
        )
        startTestKoin(repository)
        val vm = newViewModel()

        val failed = vm.awaitLoaded()
        assertNotNull(failed.loadError)
        assertNull(failed.mergeState)
        assertFalse(failed.hasConflicts)

        fail = false
        vm.reload()
        val loaded = vm.uiState.first { !it.isLoading && it.mergeState != null }
        assertNull(loaded.loadError)
        assertEquals(6, loaded.totalConflictCount)
    }

    @Test
    fun `VM-03 没有冲突时 hasConflicts 为 false 且 allResolved`() = runTest {
        val repository = FakeBangumiMergeRepository({ createTestBangumiMergeSyncedState(now) })
        startTestKoin(repository)
        val vm = newViewModel()

        val state = vm.awaitLoaded()
        assertFalse(state.hasConflicts)
        assertTrue(state.allResolved)
        assertFalse(state.canApply)
        assertEquals(12, state.autoMergedTotal)
    }

    // endregion

    // region 选择

    @Test
    fun `VM-04 逐项选择更新进度 全部选择后可应用`() = runTest {
        val repository = testRepository()
        startTestKoin(repository)
        val vm = newViewModel()

        val state = vm.awaitLoaded()
        val allKeys = state.groups.flatMap { it.conflictKeys }
        assertEquals(6, allKeys.size)

        vm.select(allKeys[0], BangumiMergeSide.ANIMEKO)
        val afterOne = vm.uiState.first { it.confirmedCount == 1 }
        assertFalse(afterOne.allResolved)

        // 重复选择同一冲突不会重复计数.
        vm.select(allKeys[0], BangumiMergeSide.BANGUMI)
        val stillOne = vm.uiState.first { it.choices[allKeys[0]] == BangumiMergeSide.BANGUMI }
        assertEquals(1, stillOne.confirmedCount)

        allKeys.forEach { vm.select(it, BangumiMergeSide.ANIMEKO) }
        val resolved = vm.uiState.first { it.allResolved }
        assertEquals(6, resolved.confirmedCount)
        assertTrue(resolved.canApply)
    }

    @Test
    fun `VM-05 采用较新的只选择有两侧时间的收藏状态行 评分行与已删除行跳过`() = runTest {
        val repository = testRepository()
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        // 先手动选一个没有时间的行, 采用较新的不应覆盖它.
        vm.select(key3Rating, BangumiMergeSide.BANGUMI)
        vm.uiState.first { it.confirmedCount == 1 }

        vm.adoptNewer()
        val after = vm.uiState.first { it.confirmedCount == 3 }
        assertEquals(
            mapOf(
                key1Collection to BangumiMergeSide.ANIMEKO,
                key2Collection to BangumiMergeSide.BANGUMI,
                key3Rating to BangumiMergeSide.BANGUMI,
            ),
            after.choices,
        )
        assertNull(after.choices[key2Rating])
        assertNull(after.choices[key4Collection])
        assertNull(after.choices[key5Rating])
    }

    @Test
    fun `VM-06 采用较新的覆盖已有的相反选择`() = runTest {
        val repository = testRepository()
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.select(key1Collection, BangumiMergeSide.BANGUMI)
        vm.uiState.first { it.confirmedCount == 1 }

        vm.adoptNewer()
        val after = vm.uiState.first { it.choices[key1Collection] == BangumiMergeSide.ANIMEKO }
        assertEquals(2, after.confirmedCount)
    }

    @Test
    fun `VM-07 列头全选设置所有冲突`() = runTest {
        val repository = testRepository()
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.BANGUMI)
        val state = vm.uiState.first { it.allResolved }
        assertEquals(6, state.confirmedCount)
        assertTrue(state.choices.values.all { it == BangumiMergeSide.BANGUMI })

        vm.selectAll(BangumiMergeSide.ANIMEKO)
        val switched = vm.uiState.first { it.choices.values.all { side -> side == BangumiMergeSide.ANIMEKO } }
        assertEquals(6, switched.confirmedCount)
    }

    @Test
    fun `VM-08 reload 清空选择`() = runTest {
        val repository = testRepository()
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.BANGUMI)
        vm.uiState.first { it.allResolved }

        vm.reload()
        val reloaded = vm.uiState.first { !it.isLoading && it.choices.isEmpty() && it.mergeState != null }
        assertEquals(0, reloaded.confirmedCount)
        assertEquals(2, repository.stateCalls)
    }

    // endregion

    // region 应用

    @Test
    fun `VM-09 未全部确认时 apply 不执行`() = runTest {
        val repository = testRepository()
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.select(key1Collection, BangumiMergeSide.ANIMEKO)
        vm.uiState.first { it.confirmedCount == 1 }
        assertNull(vm.apply())
        assertTrue(repository.resolveCalls.isEmpty())
    }

    @Test
    fun `VM-10 没有冲突时 apply 直接返回 不请求`() = runTest {
        val repository = FakeBangumiMergeRepository({ createTestBangumiMergeSyncedState(now) })
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        assertNull(vm.apply())
        assertTrue(repository.resolveCalls.isEmpty())
    }

    @Test
    fun `VM-11 全部确认后 apply 提交全部选择 剩余为空则清空并触发强制检查`() = runTest {
        val repository = testRepository()
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.BANGUMI)
        vm.select(key2Rating, BangumiMergeSide.ANIMEKO)
        vm.uiState.first { it.allResolved && it.choices[key2Rating] == BangumiMergeSide.ANIMEKO }

        val outcome = vm.apply()
        assertEquals(BangumiMergeApplyOutcome.Applied(mergedCount = 6, remainingCount = 0), outcome)

        val resolutions = repository.resolveCalls.single()
        assertEquals(6, resolutions.size)
        assertEquals(
            setOf(key1Collection, key2Collection, key2Rating, key3Rating, key4Collection, key5Rating),
            resolutions.map { BangumiConflictKey(it.subjectId, it.fieldType) }.toSet(),
        )
        assertEquals(
            BangumiMergeSide.ANIMEKO,
            resolutions.single { it.subjectId == 2 && it.fieldType == BangumiConflictFieldType.RATING }.side,
        )
        assertTrue(resolutions.filterNot { it.subjectId == 2 && it.fieldType == BangumiConflictFieldType.RATING }
            .all { it.side == BangumiMergeSide.BANGUMI })

        // 状态替换为剩余 (空), 选择清空; 服务端返回的剩余状态带同步时间, 不会被当成 "同步中".
        val after = vm.uiState.first { !it.hasConflicts }
        assertTrue(after.choices.isEmpty())
        assertFalse(after.isApplying)
        assertFalse(after.syncInProgress)
        assertEquals(now, after.lastSyncedAt)
        assertTrue(after.allResolved)

        // 合并完成后强制检查冲突数.
        checker.joinCheck()
        assertEquals(1, repository.summaryCalls)

        // 已无冲突, 再次 apply 不重复提交.
        assertNull(vm.apply())
        assertEquals(1, repository.resolveCalls.size)
    }

    @Test
    fun `VM-12 剩余非空时替换状态 去掉消失的 key 并报告数量`() = runTest {
        val remainingState = BangumiMergeState(
            conflicts = listOf(
                // 原有的条目 3 仍在.
                BangumiSubjectConflict(
                    subjectId = 3,
                    title = "我的青春恋爱物语果然有问题。完",
                    animekoUpdatedAt = now,
                    bangumiUpdatedAt = now,
                    detectedAt = now,
                    fields = listOf(
                        BangumiConflictField.Rating(
                            SelfRatingInfo(9, "a", emptyList(), false),
                            SelfRatingInfo(9, "b", emptyList(), false),
                        ),
                    ),
                ),
                // 同步期间新发现的条目.
                BangumiSubjectConflict(
                    subjectId = 9,
                    title = "新条目",
                    animekoUpdatedAt = now,
                    bangumiUpdatedAt = now,
                    detectedAt = now,
                    fields = listOf(
                        BangumiConflictField.Collection(UnifiedCollectionType.WISH, UnifiedCollectionType.DOING),
                    ),
                ),
            ),
            autoMerged = emptyList(),
            autoMergedTotal = 3,
            lastSyncedAt = now,
            syncInProgress = false,
        )
        val repository = testRepository { remainingState }
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.ANIMEKO)
        vm.uiState.first { it.allResolved }

        val outcome = vm.apply()
        assertEquals(BangumiMergeApplyOutcome.Applied(mergedCount = 5, remainingCount = 2), outcome)

        val after = vm.uiState.first { it.groups.size == 2 }
        assertEquals(listOf(3, 9), after.groups.map { it.subjectId })
        assertEquals(2, after.totalConflictCount)
        // 仍存在的 key 保留选择, 消失的 key 去掉, 新的 key 未选.
        assertEquals(mapOf(key3Rating to BangumiMergeSide.ANIMEKO), after.choices)
        assertEquals(1, after.confirmedCount)
        assertFalse(after.allResolved)
        assertEquals(3, after.autoMergedTotal)
    }

    @Test
    fun `VM-13 服务端同步进行中 (409) 返回 SyncInProgress 且保留选择与状态`() = runTest {
        val repository = testRepository { throw BangumiMergeSyncInProgressException() }
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.BANGUMI)
        vm.uiState.first { it.allResolved }

        assertEquals(BangumiMergeApplyOutcome.SyncInProgress, vm.apply())
        val after = vm.uiState.first { !it.isApplying }
        assertEquals(6, after.confirmedCount)
        assertTrue(after.hasConflicts)
        assertEquals(1, repository.resolveCalls.size)
        // 没有成功, 不触发强制检查.
        checker.joinCheck()
        assertEquals(0, repository.summaryCalls)
    }

    @Test
    fun `VM-14 apply 失败返回 Failed 且保留选择`() = runTest {
        val repository = testRepository { throw IllegalStateException("server error") }
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.BANGUMI)
        vm.uiState.first { it.allResolved }

        assertIs<BangumiMergeApplyOutcome.Failed>(vm.apply())
        val after = vm.uiState.first { !it.isApplying }
        assertEquals(6, after.confirmedCount)
        assertTrue(after.canApply)
    }

    @Test
    fun `VM-15 startApply 通过 applyOutcome 上报并可清除`() = runTest {
        val repository = testRepository { throw IllegalStateException("server error") }
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.BANGUMI)
        vm.uiState.first { it.allResolved }

        vm.startApply()
        val withOutcome = vm.uiState.first { it.applyOutcome != null }
        assertIs<BangumiMergeApplyOutcome.Failed>(withOutcome.applyOutcome)

        vm.clearApplyOutcome()
        vm.uiState.first { it.applyOutcome == null }
    }

    @Test
    fun `VM-16 应用中并发 apply 只提交一次`() = runTest {
        val repository = testRepository()
        repository.resolveGate = CompletableDeferred()
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.ANIMEKO)
        vm.uiState.first { it.allResolved }

        val first = launch { vm.apply() }
        vm.uiState.first { it.isApplying }
        assertFalse(vm.uiState.value.canApply)

        // 第一次提交挂起期间连点: CAS 防重入, 不产生第二次提交.
        assertNull(vm.apply())
        assertEquals(1, repository.resolveCalls.size)

        repository.resolveGate!!.complete(Unit)
        first.join()
        val after = vm.uiState.first { !it.hasConflicts }
        assertFalse(after.syncInProgress)
        assertEquals(1, repository.resolveCalls.size)
    }

    // endregion

    // region 同步进行中

    @Test
    fun `VM-17 同步进行中时不可应用 且静默轮询直到结束 保留仍存在的选择`() = runTest {
        var inProgress = true
        val repository = FakeBangumiMergeRepository(
            {
                val full = createTestBangumiMergeState(now)
                if (inProgress) {
                    full.copy(syncInProgress = true)
                } else {
                    // 同步结束后条目 1 的冲突消失了.
                    full.copy(conflicts = full.conflicts.filter { it.subjectId != 1 }, syncInProgress = false)
                }
            },
        )
        startTestKoin(repository)
        val vm = newPollingViewModel(syncPollInterval = 20.milliseconds)

        val loading = vm.awaitLoaded()
        assertTrue(loading.syncInProgress)
        vm.selectAll(BangumiMergeSide.BANGUMI)
        val chosen = vm.uiState.first { it.allResolved }
        // 全部确认了也不能应用.
        assertFalse(chosen.canApply)

        // 同步结束; 等待下一次 (虚拟时间 20ms 后的) 轮询刷新
        inProgress = false
        val settled = vm.uiState.first { !it.syncInProgress }
        assertEquals(listOf(2, 3, 4, 5), settled.groups.map { it.subjectId })
        assertNull(settled.choices[key1Collection])
        assertEquals(5, settled.confirmedCount)
        assertTrue(settled.allResolved)
        assertTrue(settled.canApply)
        assertTrue(repository.stateCalls >= 2)
    }

    @Test
    fun `VM-18 服务端从未同步过 (lastSyncedAt 为 null) 视同同步中 - 不宣称已同步 不可应用 并轮询直到有同步时间`() = runTest {
        var synced = false
        val repository = FakeBangumiMergeRepository(
            {
                // 首次绑定: 服务端尚未开始 / 尚未完成首次全量同步, 既没有冲突也没有同步时间.
                if (synced) createTestBangumiMergeState(now) else BangumiMergeState.Empty
            },
        )
        startTestKoin(repository)
        val vm = newPollingViewModel(syncPollInterval = 20.milliseconds)

        val unsettled = vm.awaitLoaded()
        assertNull(unsettled.lastSyncedAt)
        assertFalse(unsettled.hasConflicts)
        assertTrue(unsettled.syncInProgress)
        assertFalse(unsettled.canApply)

        synced = true
        val settled = vm.uiState.first { !it.syncInProgress }
        assertEquals(now - 10.minutes, settled.lastSyncedAt)
        assertEquals(6, settled.totalConflictCount)
        assertTrue(repository.stateCalls >= 2)
    }

    @Test
    fun `VM-19 轮询有时间上限 超时后停止刷新`() = runTest {
        val repository = FakeBangumiMergeRepository({ BangumiMergeState.Empty })
        startTestKoin(repository)
        // 上限不取间隔的整数倍, 避免最后一次轮询与超时同时到期
        val vm = newPollingViewModel(syncPollInterval = 10.milliseconds, syncPollTimeout = 75.milliseconds)

        assertTrue(vm.awaitLoaded().syncInProgress)
        assertEquals(1, repository.stateCalls)

        // 虚拟时间推进到超时: t = 10, 20, ..., 70 共 7 次轮询, 75ms 时停止
        advanceUntilIdle()
        assertEquals(8, repository.stateCalls)

        // 超时后不再刷新
        advanceTimeBy(10.minutes)
        advanceUntilIdle()
        assertEquals(8, repository.stateCalls)
        assertTrue(vm.uiState.value.syncInProgress)
    }

    // endregion

    // region 作用域取消

    @Test
    fun `VM-20 提交进行中离开界面 (作用域取消) 时提交与收尾仍完成`() = runTest {
        val repository = testRepository()
        repository.resolveGate = CompletableDeferred()
        startTestKoin(repository)
        val vm = newViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.ANIMEKO)
        vm.uiState.first { it.allResolved }

        vm.startApply()
        awaitReal(repository.resolveStarted)

        // 离开界面: ViewModel 清除时取消后台作用域, 而请求已经发出.
        vm.backgroundScope.cancel()
        repository.resolveGate!!.complete(Unit)

        // 提交仍然完成, 并触发了冲突数的强制检查 (仓库内的缓存失效也在同一不可取消块内).
        awaitReal(repository.resolveFinished)
        awaitReal(repository.summaryRequested)
        checker.joinCheck()
        assertEquals(1, repository.resolveCalls.size)
        assertEquals(1, repository.summaryCalls)
    }

    // endregion
}
