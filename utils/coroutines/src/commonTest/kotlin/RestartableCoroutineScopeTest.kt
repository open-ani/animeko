/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.coroutines

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RestartableCoroutineScopeTest {

    private lateinit var scope: RestartableCoroutineScope

    @BeforeTest
    fun setUp() {
        scope = RestartableCoroutineScope()
    }

    @Test
    fun `launch starts coroutine`() = runTest {
        val result = CompletableDeferred<Int>()

        scope.launch {
            result.complete(42)
        }

        assertEquals(42, result.await())
    }

    @Test
    fun `cancel stops running coroutines`() = runTest {
        val isRunning = CompletableDeferred<Boolean>()

        val job = scope.launch {
            try {
                delay(1000)
                isRunning.complete(true)
            } catch (e: Exception) {
                isRunning.complete(false)
            }
        }

        // Ensure the job has started
        delay(10)
        assertTrue(job.isActive)

        scope.cancel()
        assertFalse(job.isActive)
        assertFalse(isRunning.await())
    }

    @Test
    fun `can launch new coroutines after cancel`() = runTest {
        // Start and cancel initial coroutines
        val job1 = scope.launch { delay(1000) }
        scope.cancel()
        assertFalse(job1.isActive)

        // Launch a new coroutine after cancellation
        val result = CompletableDeferred<Int>()
        val job2 = scope.launch {
            result.complete(42)
        }

        // The new coroutine should run successfully
        assertEquals(42, result.await())
        assertNotEquals(job1, job2)
    }

    @Test
    fun `close cancels all coroutines and allows new launches`() = runTest {
        val job1 = scope.launch { delay(1000) }

        scope.close()
        assertFalse(job1.isActive)

        // Attempt to launch after close should return a completed job
        val job2 = scope.launch { delay(1000) }
        assertTrue(job2.isActive)
    }

    @Test
    fun `cancel doesn't affect parent scope`() = runTest {
        val parentJob = coroutineContext.job
        val initialActiveState = parentJob.isActive

        scope.launch { delay(1000) }
        scope.cancel()

        assertEquals(initialActiveState, parentJob.isActive)
    }

    @Test
    fun `multiple launches work independently`() = runTest {
        val results = List(5) { CompletableDeferred<Int>() }

        results.forEachIndexed { index, deferred ->
            scope.launch {
                delay(index * 100L)
                deferred.complete(index)
            }
        }

        results.forEachIndexed { index, deferred ->
            assertEquals(index, deferred.await())
        }
    }

    @Test
    fun `cancel affects only current child jobs`() = runTest {
        val beforeCancelResult = CompletableDeferred<Int>()
        val afterCancelResult = CompletableDeferred<Int>()

        scope.launch {
            delay(100)
            beforeCancelResult.complete(1)
        }

        // Cancel before the first job completes
        scope.cancel()

        // Launch a new job after cancellation
        scope.launch {
            afterCancelResult.complete(2)
        }

        // First job should be cancelled
        advanceTimeBy(200)
        assertFalse(beforeCancelResult.isCompleted)

        // Second job should complete
        assertEquals(2, afterCancelResult.await())
    }

    @Test
    fun `thread-safety - concurrent launches`() = runTest {
        val completedJobs = CompletableDeferred<Int>()
        var completed = 0

        val jobs = List(100) {
            async {
                val job = scope.launch {
                    delay(10)
                    synchronized(this@RestartableCoroutineScopeTest) {
                        completed++
                        if (completed == 100) {
                            completedJobs.complete(completed)
                        }
                    }
                }
                job
            }
        }

        jobs.forEach { it.await() }
        assertEquals(100, completedJobs.await())
    }

    @Test
    fun `thread-safety - concurrent cancellations`() = runTest {
        // Create tasks that will run if not cancelled
        val results = List(50) { CompletableDeferred<Boolean>() }

        results.forEachIndexed { index, deferred ->
            scope.launch {
                delay(100.milliseconds)
                deferred.complete(true)
            }
        }

        // Launch multiple cancellations and new jobs concurrently
        val cancellationJobs = List(10) {
            async {
                scope.cancel()
                scope.launch {
                    // These should run
                    delay(10.milliseconds)
                }
            }
        }

        cancellationJobs.forEach { it.await() }

        // Original jobs should be cancelled
        delay(200.milliseconds)
        results.forEach { deferred ->
            assertFalse(deferred.isCompleted)
        }
    }

    @Test
    fun `child job cancellation doesn't affect other children`() = runTest {
        val result1 = CompletableDeferred<Int>()
        val result2 = CompletableDeferred<Int>()

        val job1 = scope.launch {
            delay(100)
            result1.complete(1)
        }

        val job2 = scope.launch {
            delay(100)
            result2.complete(2)
        }

        // Cancel just one job
        job1.cancel()

        // Advance time to let job2 complete
        advanceTimeBy(200)

        assertFalse(result1.isCompleted)
        assertEquals(2, result2.await())
    }

    @Test
    fun `cancelled scope doesn't leak memory`() = runTest {
        val scope = RestartableCoroutineScope()
        val deferreds = mutableListOf<CompletableDeferred<Unit>>()

        // Launch coroutines that would run forever
        repeat(100) {
            val deferred = CompletableDeferred<Unit>()
            deferreds.add(deferred)

            scope.launch {
                try {
                    delay(Long.MAX_VALUE)
                } finally {
                    deferred.complete(Unit)
                }
            }
        }

        scope.close()

        // All coroutines should have their finally blocks executed
        deferreds.forEach { deferred ->
            assertTrue(deferred.isCompleted)
        }
    }
}