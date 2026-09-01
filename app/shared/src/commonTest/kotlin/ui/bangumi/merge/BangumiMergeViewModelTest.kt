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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeConflictKey
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeFieldId
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeOpCompiler
import me.him188.ani.app.domain.bangumi.merge.BangumiMergePlan
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeCompileResult
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeResolution
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeSide
import me.him188.ani.app.domain.bangumi.merge.conflictKeys
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(TestOnly::class)
class BangumiMergeViewModelTest {

    private class FakeMergeRepository(
        var planProvider: suspend () -> BangumiMergePlan,
        var applyError: Throwable? = null,
    ) : BangumiMergeRepository() {
        val applyCalls = mutableListOf<Pair<BangumiMergePlan, BangumiMergeResolution>>()

        /** 非空时 applyMerge 在记录调用后挂起, 直到测试放行. 用于测试并发防重入. */
        var applyGate: CompletableDeferred<Unit>? = null

        override suspend fun computeMergePlan(): BangumiMergePlan = planProvider()

        override suspend fun applyMerge(
            plan: BangumiMergePlan,
            resolution: BangumiMergeResolution,
        ): BangumiMergeCompileResult {
            applyCalls.add(plan to resolution)
            applyGate?.await()
            applyError?.let { throw it }
            return BangumiMergeOpCompiler().compile(plan, resolution)
        }
    }

    private val now = Instant.fromEpochMilliseconds(1_753_000_000_000)

    private fun startTestKoin(repository: BangumiMergeRepository) {
        startKoin {
            modules(
                module {
                    single<BangumiMergeRepository> { repository }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    // 不用 withTimeout: runTest 的虚拟时间会在真实 Default 线程完成前触发超时.
    // runTest 自带 60s 真实超时兜底.
    private suspend fun BangumiMergeViewModel.awaitLoaded(): BangumiMergeUiState =
        uiState.first { !it.isLoading }

    @Test
    fun `VM-01 加载成功后展示合并计划`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val state = vm.awaitLoaded()
        assertNull(state.loadError)
        assertNotNull(state.plan)
        assertEquals(5, state.plan!!.conflictGroups.size)
        assertEquals(6, state.totalConflictCount)
        assertEquals(0, state.confirmedCount)
        assertFalse(state.allResolved)
    }

    @Test
    fun `VM-02 加载失败展示错误 重试后成功`() = runTest {
        var fail = true
        val repository = FakeMergeRepository(
            {
                if (fail) throw IllegalStateException("network down") else createTestBangumiMergePlan(now)
            },
        )
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val failed = vm.awaitLoaded()
        assertNotNull(failed.loadError)
        assertNull(failed.plan)

        fail = false
        vm.reload()
        val loaded = vm.uiState.first { !it.isLoading && it.plan != null }
        assertNull(loaded.loadError)
        assertEquals(6, loaded.totalConflictCount)
    }

    @Test
    fun `VM-03 逐项选择更新进度 全部选择后可应用`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val state = vm.awaitLoaded()
        val plan = state.plan!!
        val allKeys = plan.conflictGroups.flatMap { it.conflictKeys }
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
    }

    @Test
    fun `VM-04 采用较新的只选择可判定较新一侧的冲突`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val state = vm.awaitLoaded()
        val plan = state.plan!!

        vm.adoptNewer()
        val after = vm.uiState.first { it.confirmedCount > 0 }

        val expected = buildMap {
            for (group in plan.conflictGroups) {
                for (conflict in group.conflicts) {
                    conflict.newerSide?.let { put(BangumiMergeConflictKey(group.subjectId, conflict.id), it) }
                }
            }
        }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, after.choices)
        // 芙莉莲 (无基线, 时间已知) 的进度冲突: 本地较新.
        assertEquals(
            BangumiMergeSide.ANIMEKO,
            after.choices[BangumiMergeConflictKey(2, BangumiMergeFieldId.Episode(7))],
        )
    }

    @Test
    fun `VM-05 列头全选设置所有冲突`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        vm.awaitLoaded()
        vm.selectAll(BangumiMergeSide.BANGUMI)
        val state = vm.uiState.first { it.allResolved }
        assertTrue(state.choices.values.all { it == BangumiMergeSide.BANGUMI })
        assertEquals(6, state.confirmedCount)
    }

    @Test
    fun `VM-06 未全部确认时 apply 不执行`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        vm.awaitLoaded()
        assertNull(vm.apply())
        assertTrue(repository.applyCalls.isEmpty())
    }

    @Test
    fun `VM-07 全部确认后 apply 提交选择并标记完成`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val state = vm.awaitLoaded()
        val allKeys = state.plan!!.conflictGroups.flatMap { it.conflictKeys }
        allKeys.forEach { vm.select(it, BangumiMergeSide.ANIMEKO) }
        vm.uiState.first { it.allResolved }

        assertNull(vm.apply())

        val (plan, resolution) = repository.applyCalls.single()
        assertEquals(state.plan, plan)
        assertEquals(allKeys.toSet(), resolution.choices.keys)
        val applied = vm.uiState.first { it.applied }
        assertTrue(applied.applied)

        // 已应用后再次 apply 不重复提交.
        assertNull(vm.apply())
        assertEquals(1, repository.applyCalls.size)
    }

    @Test
    fun `VM-08 apply 失败返回错误且不标记完成`() = runTest {
        val repository = FakeMergeRepository(
            { createTestBangumiMergePlan(now) },
            applyError = IllegalStateException("server error"),
        )
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val state = vm.awaitLoaded()
        state.plan!!.conflictGroups.flatMap { it.conflictKeys }
            .forEach { vm.select(it, BangumiMergeSide.BANGUMI) }
        vm.uiState.first { it.allResolved }

        val error = vm.apply()
        assertNotNull(error)
        val after = vm.uiState.first { !it.isApplying }
        assertFalse(after.applied)
    }

    @Test
    fun `VM-10 startApply 失败通过 applyError 上报并可清除`() = runTest {
        val repository = FakeMergeRepository(
            { createTestBangumiMergePlan(now) },
            applyError = IllegalStateException("server error"),
        )
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val state = vm.awaitLoaded()
        state.plan!!.conflictGroups.flatMap { it.conflictKeys }
            .forEach { vm.select(it, BangumiMergeSide.BANGUMI) }
        vm.uiState.first { it.allResolved }

        vm.startApply()
        val withError = vm.uiState.first { it.applyError != null }
        assertFalse(withError.applied)

        vm.clearApplyError()
        vm.uiState.first { it.applyError == null }
    }

    @Test
    fun `VM-11 应用中并发 apply 只提交一次`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        repository.applyGate = CompletableDeferred()
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val state = vm.awaitLoaded()
        state.plan!!.conflictGroups.flatMap { it.conflictKeys }
            .forEach { vm.select(it, BangumiMergeSide.ANIMEKO) }
        vm.uiState.first { it.allResolved }

        val first = launch { vm.apply() }
        vm.uiState.first { it.isApplying }

        // 第一次提交挂起期间连点: CAS 防重入, 不产生第二次提交.
        assertNull(vm.apply())
        assertEquals(1, repository.applyCalls.size)

        repository.applyGate!!.complete(Unit)
        first.join()
        vm.uiState.first { it.applied }
        assertEquals(1, repository.applyCalls.size)
    }

    @Test
    fun `VM-09 空计划时 isFullySynced`() = runTest {
        val repository = FakeMergeRepository({ BangumiMergePlan.Empty })
        startTestKoin(repository)
        val vm = BangumiMergeViewModel()

        val state = vm.awaitLoaded()
        assertTrue(state.isFullySynced)
        assertTrue(state.allResolved)
    }
}
