/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

/**
 * Observes loading errors in a flow.
 *
 * When a flow completes with an exception, it updates [loadErrorState]
 * accordingly—unless the exception is a [CancellationException],
 * which is explicitly ignored and does not set an error.
 *
 * Typical usage:
 * ```
 * val observer = FlowLoadErrorObserver()
 * someFlow
 *   .observeLoadError(observer)
 *   .collect { value ->
 *       // Normal data handling
 *   }
 *
 * // If an error occurs (except CancellationException),
 * // observer.loadErrorState will hold the relevant [LoadError].
 * ```
 */
sealed interface FlowLoadErrorObserver {
    /**
     * Holds any observed [LoadError], or `null` if none has been captured.
     */
    val loadErrorState: StateFlow<LoadError?>
}

/**
 * Creates a new, default [FlowLoadErrorObserver] instance.
 *
 * Example usage:
 * ```
 * val observer = FlowLoadErrorObserver()
 * dataFlow.observeLoadError(observer)
 *    .onEach { /* handle emissions */ }
 *    .launchIn(scope)
 * ```
 */
fun FlowLoadErrorObserver(): FlowLoadErrorObserver = FlowLoadErrorObserverImpl()

/**
 * Extension for binding a flow to a [FlowLoadErrorObserver].
 *
 * - If the flow completes with an exception (not a [CancellationException]),
 *   [FlowLoadErrorObserver.loadErrorState] is updated with a matching [LoadError].
 * - If the flow cancels (throws [CancellationException]), no error is set.
 * - On each successful emission, any prior error state is reset to `null`.
 *
 * **Example:**
 * ```
 * val observer = FlowLoadErrorObserver()
 *
 * someFlow.observeLoadError(observer)
 *   .collect { result ->
 *       // Use result
 *   }
 *
 * // Check for errors
 * if (observer.loadErrorState.value != null) {
 *    // Handle or display error
 * }
 * ```
 */
fun <T> Flow<T>.catchLoadError(observer: FlowLoadErrorObserver): Flow<T> {
    // Smart case for the internal implementation
    when (observer) {
        is FlowLoadErrorObserverImpl -> {}
    }

    return this
        .onEach {
            // Reset the error for any successful emission
            observer.loadErrorStateMutable.value = null
        }
        .onCompletion { exception ->
            // Set the error state if there's a non-cancellation exception
            if (exception != null && exception !is CancellationException) {
                observer.loadErrorStateMutable.value = LoadError.fromException(exception)
            }
        }
}

/**
 * Private implementation that holds mutable error state.
 * This class should not be used directly—use [FlowLoadErrorObserver()]
 * to get an instance of this interface.
 */
private class FlowLoadErrorObserverImpl : FlowLoadErrorObserver {
    val loadErrorStateMutable: MutableStateFlow<LoadError?> = MutableStateFlow(null)
    override val loadErrorState: StateFlow<LoadError?> = loadErrorStateMutable.asStateFlow()
}
