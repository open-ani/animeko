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
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState

class DownloadOperationsTest {
    @Test
    fun `busy download rejects repeated actions until its operation finishes`() = runTest {
        val finishPause = CompletableDeferred<Unit>()
        val underlying = testDownload(1)
        val download = object : MediaCache by underlying {
            override suspend fun pause() {
                underlying.pause()
                finishPause.await()
            }
        }
        val operations = operations(backgroundScope, download)
        val pause = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        runCurrent()
        assertEquals(MediaCacheState.PAUSED, underlying.state.value)
        assertEquals(setOf(download.cacheId), operations.busyIds.value)

        for (action in DownloadOperations.Action.entries) {
            val rejected = operations.submit(setOf(download.cacheId), action).await()
            assertEquals(setOf(download.cacheId), rejected.rejectedIds)
        }
        assertFalse(pause.isCompleted)
        assertEquals(0, underlying.getResumeCalled())

        finishPause.complete(Unit)
        assertTrue(pause.await().failures.isEmpty())
        assertTrue(operations.busyIds.value.isEmpty())
        operations.submit(setOf(download.cacheId), DownloadOperations.Action.Resume).await()
        assertEquals(MediaCacheState.IN_PROGRESS, underlying.state.value)
    }

    @Test
    fun `independent downloads execute concurrently within a batch`() = runTest {
        val finishPause = CompletableDeferred<Unit>()
        val first = testDownload(1)
        val blocked = object : MediaCache by first {
            override suspend fun pause() { finishPause.await(); first.pause() }
        }
        val second = testDownload(2)
        val operations = operations(backgroundScope, blocked, second)
        val batch = operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperations.Action.Pause)
        runCurrent()

        assertFalse(batch.isCompleted)
        assertEquals(MediaCacheState.PAUSED, second.state.value)
        assertEquals(setOf(first.cacheId), operations.busyIds.value)
        operations.submit(setOf(second.cacheId), DownloadOperations.Action.Resume).await()
        assertEquals(MediaCacheState.IN_PROGRESS, second.state.value)
        finishPause.complete(Unit)
        assertTrue(batch.await().failures.isEmpty())
    }

    @Test
    fun `overlapping batches reject busy targets and execute remaining downloads`() = runTest {
        val finishPause = CompletableDeferred<Unit>()
        val first = testDownload(1)
        val blocked = object : MediaCache by first {
            override suspend fun pause() { finishPause.await(); first.pause() }
        }
        val second = testDownload(2)
        val operations = operations(backgroundScope, blocked, second)
        val firstBatch = operations.submit(setOf(first.cacheId), DownloadOperations.Action.Pause)
        val overlap = operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperations.Action.Pause)
        val result = overlap.await()
        assertEquals(setOf(first.cacheId), result.rejectedIds)
        assertEquals(MediaCacheState.PAUSED, second.state.value)
        finishPause.complete(Unit)
        firstBatch.await()
    }

    @Test
    fun `cancelling the caller preserves the operation and shared busy state`() = runTest {
        val finishPause = CompletableDeferred<Unit>()
        val underlying = testDownload(1)
        val download = object : MediaCache by underlying {
            override suspend fun pause() { finishPause.await(); underlying.pause() }
        }
        val operations = operations(backgroundScope, download)
        val result = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        val caller = backgroundScope.launch { result.await() }
        runCurrent()
        caller.cancel()
        runCurrent()
        assertFalse(result.isCompleted)
        assertEquals(setOf(download.cacheId), operations.busyIds.value)
        assertEquals(setOf(download.cacheId), operations.submit(setOf(download.cacheId), DownloadOperations.Action.Delete).await().rejectedIds)
        finishPause.complete(Unit)
        assertTrue(result.await().failures.isEmpty())
        assertTrue(operations.busyIds.value.isEmpty())
    }

    @Test
    fun `cancelling the returned result does not interrupt admitted operations`() = runTest {
        val finishPause = CompletableDeferred<Unit>()
        val underlying = testDownload(1)
        val download = object : MediaCache by underlying {
            override suspend fun pause() { finishPause.await(); underlying.pause() }
        }
        val operations = operations(backgroundScope, download)
        val result = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        runCurrent()
        result.cancel()
        runCurrent()
        assertEquals(setOf(download.cacheId), operations.busyIds.value)
        finishPause.complete(Unit)
        runCurrent()
        assertEquals(MediaCacheState.PAUSED, underlying.state.value)
        assertTrue(operations.busyIds.value.isEmpty())
    }

    @Test
    fun `submission captures ids before selection changes`() = runTest {
        val first = testDownload(1)
        val second = testDownload(2)
        val operations = operations(backgroundScope, first, second)
        val selected = mutableSetOf(first.cacheId)
        val result = operations.submit(selected, DownloadOperations.Action.Pause)
        selected.clear()
        selected.add(second.cacheId)
        result.await()
        assertEquals(MediaCacheState.PAUSED, first.state.value)
        assertEquals(MediaCacheState.IN_PROGRESS, second.state.value)
    }

    @Test
    fun `injected execution scope owns operations independently of the manager scope`() = runTest {
        val executionScope = CoroutineScope(
            backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]) +
                    StandardTestDispatcher(testScheduler),
        )
        val underlying = testDownload(1)
        val download = object : MediaCache by underlying {
            override suspend fun pause() { awaitCancellation() }
        }
        val storage = DownloadTestStorage().apply { listFlow.value = listOf(download) }
        val operations = DownloadOperations(
            MediaDownloadManager(listOf(storage), backgroundScope, cacheDanmaku = {}),
            object : DeleteCacheUseCase { override suspend fun invoke(cache: MediaCache) = Unit },
            executionScope,
            StandardTestDispatcher(testScheduler),
        )
        val result = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        runCurrent()
        assertEquals(setOf(download.cacheId), operations.busyIds.value)
        executionScope.cancel()
        runCurrent()
        assertTrue(result.isCancelled)
        assertTrue(operations.busyIds.value.isEmpty())
        assertTrue(backgroundScope.isActive)
    }

    @Test
    fun `application shutdown cancels work and releases busy ids`() = runTest {
        val applicationScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val underlying = testDownload(1)
        var cancelled = false
        val download = object : MediaCache by underlying {
            override suspend fun pause() {
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        val operations = operations(applicationScope, download)
        val active = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        runCurrent()
        applicationScope.cancel()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(active.isCancelled)
        assertTrue(operations.busyIds.value.isEmpty())
        val subsequent = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Resume)
        runCurrent()
        assertTrue(subsequent.isCancelled)
    }

    @Test
    fun `shutdown before dispatch does not start work or retain busy ids`() = runTest {
        val applicationScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val download = testDownload(1)
        val operations = operations(applicationScope, download)
        val result = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        applicationScope.cancel()
        runCurrent()
        assertTrue(result.isCancelled)
        assertTrue(operations.busyIds.value.isEmpty())
        assertEquals(MediaCacheState.IN_PROGRESS, download.state.value)
    }

    @Test
    fun `per download cancellation is reported without interrupting other downloads`() = runTest {
        val first = testDownload(1)
        val cancelled = object : MediaCache by first {
            override suspend fun pause() { throw CancellationException("download removed") }
        }
        val second = testDownload(2)
        val operations = operations(backgroundScope, cancelled, second)
        val result = operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperations.Action.Pause).await()
        assertEquals(setOf(first.cacheId), result.failures.keys)
        assertEquals(MediaCacheState.PAUSED, second.state.value)
        assertTrue(operations.busyIds.value.isEmpty())
    }

    @Test
    fun `failed deletion releases busy state and allows retry`() = runTest {
        val storage = DownloadTestStorage()
        val first = testDownload(1)
        val second = testDownload(2)
        storage.listFlow.value = listOf(first, second)
        var fail = true
        val operations = DownloadOperations(
            MediaDownloadManager(listOf(storage), backgroundScope, cacheDanmaku = {}),
            object : DeleteCacheUseCase {
                override suspend fun invoke(cache: MediaCache) {
                    if (cache === first && fail) error("permission denied")
                    storage.delete(cache)
                }
            },
            CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)),
            StandardTestDispatcher(testScheduler),
        )
        val result = operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperations.Action.Delete).await()
        assertEquals(setOf(first.cacheId), result.failures.keys)
        assertEquals(listOf(first), storage.listFlow.value)
        assertTrue(operations.busyIds.value.isEmpty())
        fail = false
        assertTrue(operations.submit(setOf(first.cacheId), DownloadOperations.Action.Delete).await().failures.isEmpty())
        assertTrue(storage.listFlow.value.isEmpty())
    }

    @Test
    fun `pause and resume evaluate current state and preserve completed and failed downloads`() = runTest {
        val completed = testDownload(1).apply { state.value = MediaCacheState.COMPLETED }
        val failed = testDownload(2).apply { state.value = MediaCacheState.FAILED }
        val running = testDownload(3)
        val operations = operations(backgroundScope, completed, failed, running)
        val ids = setOf(completed.cacheId, failed.cacheId, running.cacheId)
        operations.submit(ids, DownloadOperations.Action.Pause).await()
        assertEquals(MediaCacheState.COMPLETED, completed.state.value)
        assertEquals(MediaCacheState.FAILED, failed.state.value)
        assertEquals(MediaCacheState.PAUSED, running.state.value)
        operations.submit(ids, DownloadOperations.Action.Resume).await()
        assertEquals(MediaCacheState.COMPLETED, completed.state.value)
        assertEquals(MediaCacheState.FAILED, failed.state.value)
        assertEquals(MediaCacheState.IN_PROGRESS, running.state.value)
        operations.submit(ids, DownloadOperations.Action.Delete).await()
        assertTrue(operations.submit(ids, DownloadOperations.Action.Resume).await().failures.isEmpty())
    }

    private fun TestScope.operations(scope: CoroutineScope, vararg downloads: MediaCache): DownloadOperations {
        val storage = DownloadTestStorage().apply { listFlow.value = downloads.toList() }
        return DownloadOperations(
            MediaDownloadManager(listOf(storage), scope, cacheDanmaku = {}),
            object : DeleteCacheUseCase {
                override suspend fun invoke(cache: MediaCache) { storage.delete(cache) }
            },
            CoroutineScope(scope.coroutineContext + StandardTestDispatcher(testScheduler)),
            StandardTestDispatcher(testScheduler),
        )
    }
}
