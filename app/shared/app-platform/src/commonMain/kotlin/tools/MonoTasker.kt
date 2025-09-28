/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Note: This function is not thread safe. For thread-safe variant, you may consider `SingleTaskExecutor`.
 */
@Stable
interface MonoTasker {
    val isRunning: StateFlow<Boolean>

    /**
     * Note: This function is not thread safe.
     */
    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job

    /**
     * Note: This function is not thread safe.
     */
    fun <R> async(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> R,
    ): Deferred<R>

    /**
     * 等待上一个任务完成后再执行
     *
     * Note: This function is not thread safe.
     */
    fun launchNext(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    )

    /**
     * Note: This function is not thread safe.
     */
    fun cancel(cause: CancellationException? = null)

    /**
     * Note: This function is not thread safe.
     */
    suspend fun cancelAndJoin()

    /**
     * Note: This function is not thread safe.
     */
    suspend fun join()
}

fun MonoTasker(
    scope: CoroutineScope
): MonoTasker = object : MonoTasker {
    var job: Job? = null

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override fun launch(
        context: CoroutineContext,
        start: CoroutineStart,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        job?.cancel()
        val newJob = scope.launch(context, start, block).apply {
            invokeOnCompletion {
                if (job === this) {
                    _isRunning.value = false
                }
            }
        }.also { job = it }
        _isRunning.value = true

        return newJob
    }

    override fun <R> async(
        context: CoroutineContext,
        start: CoroutineStart,
        block: suspend CoroutineScope.() -> R
    ): Deferred<R> {
        job?.cancel()
        val deferred = scope.async(context, start, block).apply {
            invokeOnCompletion {
                if (job === this) {
                    _isRunning.value = false
                }
            }
        }
        job = deferred
        _isRunning.value = true
        return deferred
    }

    override fun launchNext(
        context: CoroutineContext,
        start: CoroutineStart,
        block: suspend CoroutineScope.() -> Unit
    ) {
        val existingJob = job
        job = scope.launch(context, start) {
            try {
                existingJob?.join()
                block()
            } catch (e: CancellationException) {
                existingJob?.cancel()
                throw e
            }
        }.apply {
            invokeOnCompletion {
                if (job === this) {
                    _isRunning.value = false
                }
            }
        }
        _isRunning.value = true
    }

    override fun cancel(cause: CancellationException?) {
        job?.cancel(cause) // use completion handler to set _isRunning to false
    }

    override suspend fun cancelAndJoin() {
        job?.run {
            cancel()
            join()
        }
    }

    override suspend fun join() {
        job?.join()
    }
}

// ui (composition) scope
@Composable
inline fun rememberUiMonoTasker(
    crossinline getContext: @DisallowComposableCalls () -> CoroutineContext = { EmptyCoroutineContext }
): MonoTasker {
    val uiScope = rememberCoroutineScope(getContext)
    val tasker = remember(uiScope) { MonoTasker(uiScope) }
    return tasker
}

private fun <T> Flow<T>.produceState(
    initialValue: T,
    scope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    val state = mutableStateOf(initialValue)
    scope.launch(coroutineContext + Dispatchers.Main) {
        flowOn(Dispatchers.Default) // compute in background
            .collect {
                // update state in main
                state.value = it
            }
    }
    return state
}
