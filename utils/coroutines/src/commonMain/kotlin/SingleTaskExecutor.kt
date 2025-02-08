/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.coroutines

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A task executor that ensures there will be only one 'instance' of [invoke] running at a time.
 * Previous tasks will be canceled if a new task is submitted.
 *
 * This class is thread-safe.
 */
interface SingleTaskExecutor {
    /**
     * Invokes [block] in a coroutine. Suspends until [block] completes, and returns its result.
     * Previous [invoke] will be canceled when [invoke] is called again.
     *
     * This function is similar to calling `backgroundScope.launch { }.join()` using the scope provided in the constructor [SingleTaskExecutor],
     * but this function ensures only one [invoke] is running at a time.
     */
    suspend operator fun <R> invoke(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> R,
    ): R

    fun cancelCurrent()
}

/**
 * Creates a [SingleTaskExecutor] that uses the provided [backgroundScope].
 *
 * @param backgroundScope The scope to run tasks in. If the scope is canceled, [SingleTaskExecutor.invoke] will be cancelled,
 * and further tasks will not be started (also throws [CancellationException]).
 * BackgroundScope could either have a [Job] or a `SupervisorJob`, or no job, and the implementation will always throw [CancellationException] if the scope is canceled.
 */
fun SingleTaskExecutor(backgroundScope: CoroutineScope): SingleTaskExecutor = AtomicSingleTaskExecutor(backgroundScope)

/**
 * A task executor that ensures only one task is running at a time.
 * Previous tasks will be canceled if a new task is submitted.
 *
 * This class is thread-safe and optimized for low contention environments.
 */
class AtomicSingleTaskExecutor(
    private val scope: CoroutineScope,
) : SingleTaskExecutor {
    private val _job = atomic<Job?>(null)

    @TestOnly
    internal fun getJob(): Job? = _job.value

    override suspend fun <R> invoke(
        coroutineContext: CoroutineContext,
        block: suspend CoroutineScope.() -> R,
    ): R {
        scope.coroutineContext[Job]?.let { scopeJob ->
            if (scopeJob.isCancelled) {
                // Fast path: the scope is already cancelled, no need to start a new job.
                scopeJob.checkCancelledAndThrowCancellation()
            }
        }

        // Atomically, create a new job and start it.

        // 1. Cancel previous job
        val previousJob = _job.value
        previousJob?.cancel()

        // 2. Create a new job but does not start it
        val newJob = scope.async(
            coroutineContext + currentCoroutineContext()[Job]!!,
            start = CoroutineStart.LAZY,
            block = block,
        )

        // 3. Atomically set the new job
        if (_job.compareAndSet(previousJob, newJob) // This CAS may fail, because of other thread executing step 6.
        ) {
            // 4. Successful, _job is made visible to all threads.
            // Now we are safe to start the job.
            if (newJob.start()) {
                // Job started, all done.
            } else {
                // Some other thread executed step 1, and it have already canceled our newJob.
                // Then that thread will start the new job.
                newJob.checkCancelledAndThrowCancellation()
            }
            // Post condition: newJob is started.

            // 5. Wait for the job to complete
            try {
                return newJob.await()
            } finally {
                // 6. If no other thread has set a new job, we can free our job. (help GC)
                _job.compareAndSet(newJob, null)
            }
        } else {
            // 7. Failed race, a newer invocation overshadowed us; cancel our job and exit.
            newJob.cancel()
            newJob.checkCancelledAndThrowCancellation()
        }
    }

    override fun cancelCurrent() {
        val previousJob = _job.value
        previousJob?.cancel()
        _job.compareAndSet(previousJob, null)
    }

    private suspend fun Job.checkCancelledAndThrowCancellation(): Nothing {
        check(isCancelled) { "newJob is not cancelled" }
        throwCancellation()
    }

    private suspend fun Job.throwCancellation(): Nothing {
        // Should throw CancellationException if the scope has a normal Job.
        // If the scope has a SupervisorJob, this will not throw.
        join()

        // If the scope has a SupervisorJob, we should throw CancellationException manually.
        throw CancellationException("AtomicSingleTaskExecutor.invoke is superseded by a new invocation")
    }
}
