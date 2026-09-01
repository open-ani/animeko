/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeCompileResult
import me.him188.ani.app.domain.bangumi.merge.BangumiMergePlan
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeResolution
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * 覆盖 [BangumiConflictCheckViewModel] 的后台冲突检查: 上报 / 静默失败 / 节流.
 */
@OptIn(TestOnly::class)
class BangumiConflictCheckViewModelTest {

    private class FakeMergeRepository(
        var planProvider: suspend () -> BangumiMergePlan,
    ) : BangumiMergeRepository() {
        var computeCalls = 0

        override suspend fun computeMergePlan(): BangumiMergePlan {
            computeCalls++
            return planProvider()
        }

        override suspend fun applyMerge(
            plan: BangumiMergePlan,
            resolution: BangumiMergeResolution,
        ): BangumiMergeCompileResult = throw UnsupportedOperationException()
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

    @Test
    fun `NOTIFY-01 有冲突时上报冲突数`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiConflictCheckViewModel(getCurrentTimeMillis = { 1_000_000L })

        vm.startAutomaticCheck()
        assertEquals(6, vm.conflictCount.first { it > 0 })
    }

    @Test
    fun `NOTIFY-02 仅自动合并的计划不算冲突`() = runTest {
        val repository = FakeMergeRepository(
            { createTestBangumiMergePlan(now).copy(conflictGroups = emptyList()) },
        )
        startTestKoin(repository)
        val vm = BangumiConflictCheckViewModel(getCurrentTimeMillis = { 1_000_000L })

        vm.startAutomaticCheck()
        vm.joinCheck()
        assertEquals(0, vm.conflictCount.value)
    }

    @Test
    fun `NOTIFY-03 检查失败时静默且不上报`() = runTest {
        val repository = FakeMergeRepository({ throw IllegalStateException("network down") })
        startTestKoin(repository)
        val vm = BangumiConflictCheckViewModel(getCurrentTimeMillis = { 1_000_000L })

        vm.startAutomaticCheck()
        vm.joinCheck()
        assertEquals(0, vm.conflictCount.value)
        assertEquals(1, repository.computeCalls)
    }

    @Test
    fun `NOTIFY-04 间隔内重复触发不重复检查`() = runTest {
        var time = 1_000_000L
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiConflictCheckViewModel(
            getCurrentTimeMillis = { time },
            checkIntervalMillis = 60_000L,
        )

        vm.startAutomaticCheck()
        vm.joinCheck()
        assertEquals(1, repository.computeCalls)

        // 间隔内再次触发: 不检查.
        time += 30_000L
        vm.startAutomaticCheck()
        vm.joinCheck()
        assertEquals(1, repository.computeCalls)
    }

    @Test
    fun `NOTIFY-05 超过间隔后重新检查`() = runTest {
        var time = 1_000_000L
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiConflictCheckViewModel(
            getCurrentTimeMillis = { time },
            checkIntervalMillis = 60_000L,
        )

        vm.startAutomaticCheck()
        vm.joinCheck()
        assertEquals(1, repository.computeCalls)

        time += 60_001L
        vm.startAutomaticCheck()
        vm.joinCheck()
        assertEquals(2, repository.computeCalls)
    }

    @Test
    fun `NOTIFY-06 检查失败不消耗节流窗口`() = runTest {
        var fail = true
        val repository = FakeMergeRepository(
            {
                if (fail) throw IllegalStateException("network down") else createTestBangumiMergePlan(now)
            },
        )
        startTestKoin(repository)
        val vm = BangumiConflictCheckViewModel(
            getCurrentTimeMillis = { 1_000_000L },
            checkIntervalMillis = 60_000L,
        )

        vm.startAutomaticCheck()
        vm.joinCheck()
        assertEquals(1, repository.computeCalls)
        assertEquals(0, vm.conflictCount.value)

        // 失败后无需等待间隔即可重试.
        fail = false
        vm.startAutomaticCheck()
        assertEquals(6, vm.conflictCount.first { it > 0 })
        assertEquals(2, repository.computeCalls)
    }

    @Test
    fun `NOTIFY-07 reset 清空结果并允许立即重新检查`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiConflictCheckViewModel(
            getCurrentTimeMillis = { 1_000_000L },
            checkIntervalMillis = 60_000L,
        )

        vm.startAutomaticCheck()
        assertEquals(6, vm.conflictCount.first { it > 0 })

        // 登出/换绑: 清空过期结果, 且新会话不受上次检查时间的节流压制.
        vm.reset()
        assertEquals(0, vm.conflictCount.value)

        vm.startAutomaticCheck()
        assertEquals(6, vm.conflictCount.first { it > 0 })
        assertEquals(2, repository.computeCalls)
    }

    @Test
    fun `NOTIFY-08 clearConflicts 清零但保留节流`() = runTest {
        val repository = FakeMergeRepository({ createTestBangumiMergePlan(now) })
        startTestKoin(repository)
        val vm = BangumiConflictCheckViewModel(
            getCurrentTimeMillis = { 1_000_000L },
            checkIntervalMillis = 60_000L,
        )

        vm.startAutomaticCheck()
        assertEquals(6, vm.conflictCount.first { it > 0 })

        // 用户点击"处理"跳转后清零; 间隔内不重新检查 (用户正在处理, 无需再提示).
        vm.clearConflicts()
        assertEquals(0, vm.conflictCount.value)

        vm.startAutomaticCheck()
        vm.joinCheck()
        assertEquals(1, repository.computeCalls)
        assertEquals(0, vm.conflictCount.value)
    }
}
