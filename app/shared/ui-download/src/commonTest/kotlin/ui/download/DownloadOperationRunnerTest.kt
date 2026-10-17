/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.download.DownloadOperation
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.utils.coroutines.childScope

class DownloadOperationRunnerTest {
    private class Fixture(testScope: TestScope, caches: List<MediaCache>) {
        val storage = FakeDownloadStorage().apply { listFlow.value = caches }
        val downloadManager = MediaDownloadManager(listOf(storage), testScope.backgroundScope)
        val deleteCache = FakeDeleteCacheUseCase(downloadManager)

        /**
         * 操作的执行作用域. 提交操作会立即在其中创建协程, 因此检查 [pendingOperationCount] 即可判断是否提交了操作.
         */
        private val operationJob = SupervisorJob(testScope.backgroundScope.coroutineContext.job)
        val operations = DownloadOperations(
            downloadManager, deleteCache,
            CoroutineScope(testScope.backgroundScope.coroutineContext + operationJob),
        )
        val pendingOperationCount: Int get() = operationJob.children.count()

        val runnerScope = testScope.backgroundScope.childScope()
        val runner = DownloadOperationRunner(operations, runnerScope)
    }

    private fun TestScope.fixture(vararg caches: MediaCache): Fixture = Fixture(this, caches.toList()).also { runCurrent() }

    @Test
    fun `failures accumulate across batches and reset on dismiss`() = runTest {
        val failing = object : MediaCache by testDownloadCache(1) {
            override suspend fun pause() = throw IllegalStateException("pause failed")
        }
        val normal = testDownloadCache(2)
        val fixture = fixture(failing, normal)

        fixture.runner.run(setOf(failing.cacheId, normal.cacheId), DownloadOperation.Pause)
        fixture.runner.failedCount.first { it == 1 }
        assertEquals(MediaCacheState.PAUSED, normal.state.value)

        fixture.runner.run(setOf(failing.cacheId), DownloadOperation.Pause)
        fixture.runner.failedCount.first { it == 2 }

        fixture.runner.dismissFailures()
        assertEquals(0, fixture.runner.failedCount.value)

        fixture.deleteCache.failure = IllegalStateException("delete failed")
        fixture.runner.run(setOf(normal.cacheId), DownloadOperation.Delete)
        fixture.runner.failedCount.first { it == 1 }
        assertEquals(2, fixture.storage.listFlow.value.size)
    }

    @Test
    fun `empty ids submit nothing`() = runTest {
        val fixture = fixture(testDownloadCache(1))
        fixture.runner.run(emptySet(), DownloadOperation.Pause)
        assertEquals(0, fixture.pendingOperationCount)
        runCurrent()
        assertEquals(0, fixture.runner.failedCount.value)
    }

    @Test
    fun `closing the runner scope stops counting while the operation still completes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var pauseCalls = 0
        val failing = object : MediaCache by testDownloadCache(1) {
            override suspend fun pause() {
                pauseCalls++
                gate.await()
                throw IllegalStateException("pause failed")
            }
        }
        val fixture = fixture(failing)

        fixture.runner.run(setOf(failing.cacheId), DownloadOperation.Pause)
        runCurrent()
        assertEquals(1, pauseCalls)

        fixture.runnerScope.cancel()
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, fixture.pendingOperationCount)
        assertEquals(0, fixture.runner.failedCount.value)
    }
}
