/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState

class DownloadOperationsTest {
    @Test
    fun `pause then resume follows submission order when background jobs run in reverse`() = runTest {
        val dispatcher = ReverseDispatcher()
        val applicationScope = CoroutineScope(
            backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher,
        )
        val download = testDownload(1)
        val storage = DownloadTestStorage().apply { listFlow.value = listOf(download) }
        val operations = DownloadOperations(
            MediaDownloadManager(listOf(storage), applicationScope),
            object : DeleteCacheUseCase { override suspend fun invoke(cache: MediaCache) = Unit },
        )

        try {
            val pause = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
            val resume = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Resume)
            dispatcher.runAll()
            pause.await()
            resume.await()

            assertEquals(MediaCacheState.IN_PROGRESS, download.state.value)
        } finally {
            applicationScope.cancel()
            dispatcher.runAll()
        }
    }

    @Test
    fun `resume waits for a suspended pause even after paused state is visible`() = runTest {
        val finishPause = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val underlying = testDownload(1)
        val download = object : MediaCache by underlying {
            override suspend fun pause() {
                calls += "pause started"
                underlying.pause()
                finishPause.await()
                calls += "pause finished"
            }

            override suspend fun resume() {
                calls += "resume"
                underlying.resume()
            }
        }
        val operations = operations(backgroundScope, download)
        val firstPause = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        runCurrent()
        val resume = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Resume)
        val lastPause = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        runCurrent()

        assertEquals(MediaCacheState.PAUSED, underlying.state.value)
        assertEquals(listOf("pause started"), calls)
        assertFalse(firstPause.isCompleted)
        assertFalse(resume.isCompleted)
        assertFalse(lastPause.isCompleted)

        finishPause.complete(Unit)
        lastPause.await()
        assertEquals(listOf("pause started", "pause finished", "resume", "pause started", "pause finished"), calls)
        assertEquals(MediaCacheState.PAUSED, underlying.state.value)
    }

    @Test
    fun `cancelling the caller waiting for a result does not cancel the command`() = runTest {
        val finishPause = CompletableDeferred<Unit>()
        val underlying = testDownload(1)
        val download = object : MediaCache by underlying {
            override suspend fun pause() {
                finishPause.await()
                underlying.pause()
            }
        }
        val operations = operations(backgroundScope, download)
        val result = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        val caller = backgroundScope.launch { result.await() }
        runCurrent()
        caller.cancel()
        runCurrent()

        assertFalse(result.isCompleted)
        finishPause.complete(Unit)
        assertTrue(result.await().failures.isEmpty())
        assertEquals(MediaCacheState.PAUSED, underlying.state.value)
    }

    @Test
    fun `resume after queued deletion does not resume the removed download`() = runTest {
        val download = testDownload(1).apply { state.value = MediaCacheState.PAUSED }
        val operations = operations(backgroundScope, download)
        val deletion = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Delete)
        val resume = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Resume)

        assertTrue(deletion.await().failures.isEmpty())
        assertTrue(resume.await().failures.isEmpty())
        assertEquals(0, download.getResumeCalled())
    }

    @Test
    fun `queued command captures ids before selection changes`() = runTest {
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
    fun `application shutdown cancels active queued and subsequent commands`() = runTest {
        val applicationScope = CoroutineScope(
            backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]),
        )
        val underlying = testDownload(1)
        var pauseStarted = false
        var pauseCancelled = false
        val download = object : MediaCache by underlying {
            override suspend fun pause() {
                pauseStarted = true
                try {
                    awaitCancellation()
                } finally {
                    pauseCancelled = true
                }
            }
        }
        val operations = operations(applicationScope, download)
        val active = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        val queued = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Resume)
        runCurrent()
        assertTrue(pauseStarted)

        applicationScope.cancel()
        runCurrent()
        assertTrue(pauseCancelled)
        assertTrue(active.isCancelled)
        assertTrue(queued.isCancelled)
        assertTrue(operations.submit(setOf(download.cacheId), DownloadOperations.Action.Resume).isCancelled)
        assertEquals(0, underlying.getResumeCalled())
    }

    @Test
    fun `shutdown before worker starts settles accepted commands`() = runTest {
        val applicationScope = CoroutineScope(
            backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]),
        )
        val download = testDownload(1)
        val operations = operations(applicationScope, download)
        val result = operations.submit(setOf(download.cacheId), DownloadOperations.Action.Pause)
        applicationScope.cancel()
        runCurrent()

        assertTrue(result.isCancelled)
        assertEquals(MediaCacheState.IN_PROGRESS, download.state.value)
    }

    @Test
    fun `cancellation inside one operation does not stop the application queue`() = runTest {
        val first = testDownload(1)
        val cancelled = object : MediaCache by first {
            override suspend fun pause() { throw CancellationException("download removed") }
        }
        val second = testDownload(2)
        val operations = operations(backgroundScope, cancelled, second)
        val cancelledResult = operations.submit(setOf(cancelled.cacheId), DownloadOperations.Action.Pause)
        val nextResult = operations.submit(setOf(second.cacheId), DownloadOperations.Action.Pause)

        assertTrue(nextResult.await().failures.isEmpty())
        assertTrue(cancelledResult.isCancelled)
        assertEquals(MediaCacheState.PAUSED, second.state.value)
    }

    @Test
    fun `batch failure does not stop remaining items or subsequent commands`() = runTest {
        val storage = DownloadTestStorage()
        val first = testDownload(1)
        val second = testDownload(2)
        storage.listFlow.value = listOf(first, second)
        val operations = DownloadOperations(
            MediaDownloadManager(listOf(storage), backgroundScope),
            object : DeleteCacheUseCase {
                override suspend fun invoke(cache: MediaCache) {
                    if (cache === first) error("permission denied")
                    storage.delete(cache)
                }
            },
        )
        val deletion = operations.submit(setOf(first.cacheId, second.cacheId), DownloadOperations.Action.Delete)
        val pause = operations.submit(setOf(first.cacheId), DownloadOperations.Action.Pause)

        assertEquals(setOf(first.cacheId), deletion.await().failures.keys)
        assertTrue(pause.await().failures.isEmpty())
        assertEquals(listOf(first), storage.listFlow.value)
        assertEquals(MediaCacheState.PAUSED, first.state.value)
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
    }

    private fun operations(scope: CoroutineScope, vararg downloads: MediaCache): DownloadOperations {
        val storage = DownloadTestStorage().apply { listFlow.value = downloads.toList() }
        return DownloadOperations(
            MediaDownloadManager(listOf(storage), scope),
            object : DeleteCacheUseCase {
                override suspend fun invoke(cache: MediaCache) { storage.delete(cache) }
            },
        )
    }

    private class ReverseDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeLast().run() }
    }
}
