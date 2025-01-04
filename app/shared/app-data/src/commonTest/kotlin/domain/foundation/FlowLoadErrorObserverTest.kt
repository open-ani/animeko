/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation


import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FlowLoadErrorObserverTest {

    @Test
    fun `success flow does not update loadErrorState`() = runTest {
        val observer = FlowLoadErrorObserver()
        val flow = flowOf("value").catchLoadError(observer)

        // Collect the flow fully
        flow.test {
            assertEquals("value", awaitItem())
            awaitComplete()
        }

        // Check that no error was captured
        assertNull(observer.loadErrorState.value, "Expected no error for a success flow.")
    }

    @Test
    fun `flow emits an exception updates loadErrorState`() = runTest {
        val observer = FlowLoadErrorObserver()
        val flow = flow {
            emit("value1")
            throw IllegalStateException("Test exception")
        }.catchLoadError(observer)

        flow.test {
            // We see the first item...
            assertEquals("value1", awaitItem())
            // Then we see the exception thrown
            awaitError() // This will throw from the test perspective, which is expected
        }

        // The observer should have captured LoadError with the thrown exception
        val error = observer.loadErrorState.value
        assertIs<LoadError.UnknownError>(error, "Expected an UnknownError for a generic exception.")
        assertIs<IllegalStateException>(error.throwable, "Should wrap the original exception.")
        assertEquals("Test exception", error.throwable!!.message)
    }

    @Test
    fun `flow throws CancellationException does NOT update loadErrorState`() = runTest {
        val observer = FlowLoadErrorObserver()
        val flow = flow {
            emit("value1")
            throw CancellationException("Simulated cancellation")
        }.catchLoadError(observer)

        flow.test {
            // We'll receive the item, then the collection is canceled
            assertEquals("value1", awaitItem())
            // The `CancellationException` will surface as a test error from turbine
            // but let's just confirm it. It's typical to see `awaitError()` here:
            val exception = awaitError()
            assertIs<CancellationException>(exception)
        }

        // loadErrorState should remain null, because CancellationExceptions are explicitly ignored
        assertNull(observer.loadErrorState.value, "Should not capture a LoadError for CancellationExceptions.")
    }

    @Test
    fun `flow completes successfully after emission - no error`() = runTest {
        val observer = FlowLoadErrorObserver()
        val flow = flow {
            emit("value1")
            emit("value2")
            // completes normally
        }.catchLoadError(observer)

        flow.test {
            assertEquals("value1", awaitItem())
            assertEquals("value2", awaitItem())
            awaitComplete()
        }

        assertNull(observer.loadErrorState.value, "No error should be set if the flow completes normally.")
    }

    @Test
    fun `flow resets error state on new emission`() = runTest {
        // This test shows that if one collection had an error, 
        // the next successful emission resets the error.

        val observer = FlowLoadErrorObserver()

        // First, let's create a flow that fails after one emission
        val failingFlow = flow {
            emit("value")
            throw IllegalArgumentException("Test fail")
        }.catchLoadError(observer)

        // Then a second flow that emits successfully
        val successFlow = flowOf("new-value").catchLoadError(observer)

        // Collect failing flow
        failingFlow.test {
            assertEquals("value", awaitItem())
            awaitError()
        }
        // Confirm we have an error
        assertIs<LoadError.UnknownError>(observer.loadErrorState.value)

        // Now collect the success flow
        successFlow.test {
            // This should reset the error to null on first item
            assertEquals("new-value", awaitItem())
            awaitComplete()
        }

        // Confirm error state was reset
        assertNull(observer.loadErrorState.value, "Error should be reset after a successful emission.")
    }
}