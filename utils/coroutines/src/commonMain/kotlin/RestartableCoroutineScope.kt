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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Thread-safe coroutine management tool that allows launching coroutines,
 * cancelling all existing ones without closing the scope, and launching new ones later.
 */
class RestartableCoroutineScope(
    parentContext: CoroutineContext = EmptyCoroutineContext
) {
    private val parentScope = CoroutineScope(parentContext + SupervisorJob())
    private val childJobRef = atomic(SupervisorJob())

    /**
     * Thread-safely gets the current child scope for launching coroutines
     */
    private val childScope: CoroutineScope
        get() {
            val job = childJobRef.value
            return CoroutineScope(parentScope.coroutineContext + job)
        }

    /**
     * Launches a new coroutine in the current child scope in a thread-safe manner
     * @param block The coroutine code to execute
     * @return Job representing the launched coroutine
     */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return childScope.launch(block = block)
    }

    /**
     * Cancels all currently active coroutines without closing the parent scope,
     * allowing new coroutines to be launched later.
     * Thread-safe.
     */
    fun cancel() {
        val oldJob = childJobRef.getAndSet(SupervisorJob())
        oldJob.cancel()
    }

    /**
     * Completely closes this scope and all its coroutines.
     * Thread-safe.
     */
    fun close() {
        cancel() // Cancel any active child jobs
        parentScope.cancel() // Cancel the parent scope
    }
}