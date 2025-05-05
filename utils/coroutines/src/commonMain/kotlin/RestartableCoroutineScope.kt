/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.coroutines

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Thread-safe coroutine management tool that allows launching coroutines,
 * cancelling all existing ones without closing the scope, and launching new ones later.
 */
class RestartableCoroutineScope(
    private val parentContext: CoroutineContext = EmptyCoroutineContext
) {
    @Volatile
    private var scope: CoroutineScope = newScope()
    private val lock = ReentrantLock()

    val currentCoroutineContext: CoroutineContext
        get() = lock.withLock {
            scope.coroutineContext
        }

    /**
     * Launches a new coroutine in the current child scope in a thread-safe manner
     * @param block The coroutine code to execute
     * @return Job representing the launched coroutine
     */
    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        lock.withLock {
            return scope.launch(context, start, block)
        }
    }

    /**
     * Cancels all currently active coroutines without closing the parent scope,
     * allowing new coroutines to be launched later.
     * Thread-safe.
     */
    fun restart() {
        lock.withLock {
            scope.cancel() // Cancel all active coroutines
            scope = newScope() // Create a new child scope
        }
    }

    private fun newScope(): CoroutineScope = parentContext.childScope()

    /**
     * Completely closes this scope and all its coroutines.
     * Thread-safe.
     */
    fun close() {
        lock.withLock {
            scope.cancel()
        }
    }

    suspend fun closeAndJoin() {
        val scope = lock.withLock {
            scope.cancel()
            scope
        }
        scope.coroutineContext[Job]!!.join()
    }
}