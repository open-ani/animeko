/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState

class DownloadOperationsTest {
    private class Harness(
        val storage: DownloadTestStorage,
        val manager: MediaDownloadManager,
        val operations: DownloadOperations,
        /**
         * [DeleteCacheUseCase] 收到的记录, 按调用顺序.
         */
        val deleted: List<MediaCache>,
    ) {
        fun download(cache: MediaCache): MediaDownload = checkNotNull(manager.findDownload(cache.cacheId))
    }

    /**
     * 删除用例把记录交给 [MediaDownloadManager.deleteDownload].
     */
    private fun TestScope.harness(
        vararg caches: MediaCache,
        executionScope: CoroutineScope = backgroundScope,
    ): Harness {
        val storage = DownloadTestStorage().apply { listFlow.value = caches.toList() }
        val manager = MediaDownloadManager(listOf(storage), backgroundScope)
        val deleted = mutableListOf<MediaCache>()
        val deleteCache = object : DeleteCacheUseCase {
            override suspend fun invoke(cache: MediaCache) {
                deleted += cache
                manager.deleteDownload(cache)
            }
        }
        val operations = DownloadOperations(manager, deleteCache, executionScope)
        runCurrent()
        return Harness(storage, manager, operations, deleted)
    }

    private fun TestScope.executionScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    /**
     * 让 [cache] 的暂停挂起, 直到返回的门被完成.
     */
    private fun gated(cache: DownloadTestCache): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        cache.onPause = { gate.await() }
        return gate
    }

    @Test
    fun `downloads in a batch execute one at a time in submission order`() = runTest {
        val first = testDownload(1)
        val second = testDownload(2)
        val firstGate = gated(first)
        val secondGate = gated(second)
        val harness = harness(first, second)

        val batch = harness.operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperation.Pause)
        // 提交时两条都标记为忙碌, 但只有第一条开始执行.
        assertEquals(DownloadOperation.Pause, harness.download(first).operation.value)
        assertEquals(DownloadOperation.Pause, harness.download(second).operation.value)
        runCurrent()
        assertEquals(1, first.pauseCalls)
        assertEquals(0, second.pauseCalls)
        assertFalse(batch.isCompleted)

        firstGate.complete(Unit)
        runCurrent()
        assertEquals(MediaCacheState.PAUSED, first.state.value)
        assertNull(harness.download(first).operation.value)
        assertEquals(1, second.pauseCalls)
        assertEquals(DownloadOperation.Pause, harness.download(second).operation.value)
        assertFalse(batch.isCompleted)

        secondGate.complete(Unit)
        assertEquals(emptyMap(), batch.await())
        assertEquals(MediaCacheState.PAUSED, second.state.value)
        assertNull(harness.download(second).operation.value)
    }

    @Test
    fun `later batches wait for the running batch`() = runTest {
        val first = testDownload(1)
        val gate = gated(first)
        val second = testDownload(2)
        val harness = harness(first, second)

        val firstBatch = harness.operations.submit(setOf(first.cacheId), DownloadOperation.Pause)
        val secondBatch = harness.operations.submit(setOf(second.cacheId), DownloadOperation.Pause)
        runCurrent()
        assertEquals(DownloadOperation.Pause, harness.download(second).operation.value)
        assertEquals(0, second.pauseCalls)
        assertFalse(secondBatch.isCompleted)

        gate.complete(Unit)
        assertEquals(emptyMap(), firstBatch.await())
        assertEquals(emptyMap(), secondBatch.await())
        assertEquals(MediaCacheState.PAUSED, first.state.value)
        assertEquals(MediaCacheState.PAUSED, second.state.value)
    }

    @Test
    fun `queued download is skipped by later submissions until its operation finishes`() = runTest {
        val cache = testDownload(1)
        val gate = gated(cache)
        val harness = harness(cache)
        val pause = harness.operations.submit(setOf(cache.cacheId), DownloadOperation.Pause)
        runCurrent()
        val download = harness.download(cache)
        assertEquals(DownloadOperation.Pause, download.operation.value)

        for (operation in DownloadOperation.entries) {
            assertEquals(emptyMap(), harness.operations.submit(setOf(cache.cacheId), operation).await())
        }
        assertFalse(pause.isCompleted)
        assertEquals(1, cache.pauseCalls)
        assertEquals(0, cache.resumeCalls)
        assertEquals(listOf(cache), harness.storage.listFlow.value)

        gate.complete(Unit)
        assertEquals(emptyMap(), pause.await())
        assertNull(download.operation.value)
        assertEquals(MediaCacheState.PAUSED, cache.state.value)

        assertEquals(emptyMap(), harness.operations.submit(setOf(cache.cacheId), DownloadOperation.Resume).await())
        assertEquals(MediaCacheState.IN_PROGRESS, cache.state.value)
    }

    @Test
    fun `cancelling the caller keeps the operation running`() = runTest {
        val cache = testDownload(1)
        val gate = gated(cache)
        val harness = harness(cache)

        lateinit var result: Deferred<Map<String, Throwable>>
        val caller = launch {
            result = harness.operations.submit(setOf(cache.cacheId), DownloadOperation.Pause)
            result.await()
        }
        runCurrent()
        val download = harness.download(cache)
        assertEquals(DownloadOperation.Pause, download.operation.value)

        caller.cancel()
        runCurrent()
        assertFalse(result.isCompleted)
        assertEquals(DownloadOperation.Pause, download.operation.value)

        gate.complete(Unit)
        assertEquals(emptyMap(), result.await())
        assertNull(download.operation.value)
        assertEquals(MediaCacheState.PAUSED, cache.state.value)
    }

    @Test
    fun `cancelling the returned result does not interrupt the operation`() = runTest {
        val cache = testDownload(1)
        val gate = gated(cache)
        val harness = harness(cache)

        val result = harness.operations.submit(setOf(cache.cacheId), DownloadOperation.Pause)
        runCurrent()
        val download = harness.download(cache)
        assertEquals(DownloadOperation.Pause, download.operation.value)

        result.cancel()
        runCurrent()
        assertTrue(result.isCancelled)
        assertEquals(DownloadOperation.Pause, download.operation.value)
        assertEquals(MediaCacheState.IN_PROGRESS, cache.state.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(MediaCacheState.PAUSED, cache.state.value)
        assertNull(download.operation.value)
    }

    @Test
    fun `submission captures ids before later selection changes`() = runTest {
        val first = testDownload(1)
        val second = testDownload(2)
        val harness = harness(first, second)

        val selected = mutableSetOf(first.cacheId)
        val result = harness.operations.submit(selected, DownloadOperation.Pause)
        selected.clear()
        selected.add(second.cacheId)
        assertEquals(emptyMap(), result.await())
        assertEquals(MediaCacheState.PAUSED, first.state.value)
        assertEquals(MediaCacheState.IN_PROGRESS, second.state.value)
    }

    @Test
    fun `cancelling the execution scope cancels the running operation and releases queued downloads`() = runTest {
        val executionScope = executionScope()
        val first = testDownload(1)
        val second = testDownload(2)
        var cancelled = false
        first.onPause = {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val harness = harness(first, second, executionScope = executionScope)

        val result = harness.operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperation.Pause)
        runCurrent()
        assertEquals(DownloadOperation.Pause, harness.download(first).operation.value)
        assertEquals(DownloadOperation.Pause, harness.download(second).operation.value)

        executionScope.cancel()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(result.isCancelled)
        assertNull(harness.download(first).operation.value)
        assertNull(harness.download(second).operation.value)
        assertEquals(0, second.pauseCalls)
        assertEquals(MediaCacheState.IN_PROGRESS, first.state.value)
        assertTrue(backgroundScope.isActive)

        // 执行器已停止: 之后的提交立即取消并且不留下忙碌标记.
        val subsequent = harness.operations.submit(setOf(first.cacheId), DownloadOperation.Resume)
        runCurrent()
        assertTrue(subsequent.isCancelled)
        assertNull(harness.download(first).operation.value)
    }

    @Test
    fun `cancellation inside one download is reported as its failure without interrupting others`() = runTest {
        val first = testDownload(1)
        first.onPause = { throw CancellationException("download removed") }
        val second = testDownload(2)
        val harness = harness(first, second)

        val result = harness.operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperation.Pause).await()
        assertEquals(setOf(first.cacheId), result.keys)
        assertIs<CancellationException>(result.getValue(first.cacheId))
        assertEquals(MediaCacheState.IN_PROGRESS, first.state.value)
        assertEquals(MediaCacheState.PAUSED, second.state.value)
        assertNull(harness.download(first).operation.value)
        assertNull(harness.download(second).operation.value)
    }

    @Test
    fun `failed deletion releases the download and allows retry`() = runTest {
        val first = testDownload(1)
        val second = testDownload(2)
        val harness = harness(first, second)
        var fail = true
        harness.storage.onDelete = { if (it === first && fail) error("permission denied") }

        val result = harness.operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperation.Delete).await()
        assertEquals(setOf(first.cacheId), result.keys)
        assertEquals("permission denied", assertNotNull(result[first.cacheId]).message)
        assertEquals(listOf(first), harness.storage.listFlow.value)
        runCurrent()
        assertNull(harness.download(first).operation.value)

        fail = false
        assertEquals(emptyMap(), harness.operations.submit(setOf(first.cacheId), DownloadOperation.Delete).await())
        assertEquals(emptyList(), harness.storage.listFlow.value)
        assertEquals(listOf(first, second, first), harness.deleted)
    }

    @Test
    fun `pause and resume follow the current state and leave completed and failed downloads alone`() = runTest {
        val completed = testDownload(1).apply { state.value = MediaCacheState.COMPLETED }
        val failed = testDownload(2).apply { state.value = MediaCacheState.FAILED }
        val running = testDownload(3)
        val harness = harness(completed, failed, running)
        val ids = setOf(completed.cacheId, failed.cacheId, running.cacheId)

        assertEquals(emptyMap(), harness.operations.submit(ids, DownloadOperation.Pause).await())
        assertEquals(MediaCacheState.COMPLETED, completed.state.value)
        assertEquals(MediaCacheState.FAILED, failed.state.value)
        assertEquals(MediaCacheState.PAUSED, running.state.value)
        assertEquals(0, completed.pauseCalls)
        assertEquals(0, failed.pauseCalls)
        assertEquals(1, running.pauseCalls)

        assertEquals(emptyMap(), harness.operations.submit(ids, DownloadOperation.Resume).await())
        assertEquals(MediaCacheState.COMPLETED, completed.state.value)
        assertEquals(MediaCacheState.FAILED, failed.state.value)
        assertEquals(MediaCacheState.IN_PROGRESS, running.state.value)
        assertEquals(0, completed.resumeCalls)
        assertEquals(0, failed.resumeCalls)
        assertEquals(1, running.resumeCalls)

        assertEquals(emptyMap(), harness.operations.submit(ids, DownloadOperation.Delete).await())
        assertEquals(emptyList(), harness.storage.listFlow.value)
        runCurrent()
        assertEquals(emptyMap(), harness.operations.submit(ids, DownloadOperation.Resume).await())
        assertEquals(1, running.resumeCalls)
    }

    @Test
    fun `empty submission completes immediately`() = runTest {
        val harness = harness()
        val result = harness.operations.submit(emptySet(), DownloadOperation.Pause)
        assertTrue(result.isCompleted)
        assertEquals(emptyMap(), result.await())
    }

    @Test
    fun `unknown ids are ignored`() = runTest {
        val running = testDownload(1)
        val harness = harness(running)

        assertEquals(emptyMap(), harness.operations.submit(setOf(running.cacheId, "missing"), DownloadOperation.Pause).await())
        assertEquals(MediaCacheState.PAUSED, running.state.value)

        assertEquals(emptyMap(), harness.operations.submit(setOf("missing"), DownloadOperation.Delete).await())
        assertEquals(emptyList(), harness.deleted)
    }

    @Test
    fun `delete hands the download's cache to the delete use case`() = runTest {
        val cache = testDownload(1)
        val harness = harness(cache)

        assertEquals(emptyMap(), harness.operations.submit(setOf(cache.cacheId), DownloadOperation.Delete).await())
        assertSame(cache, harness.deleted.single())
        assertEquals(emptyList(), harness.storage.listFlow.value)
    }
}
