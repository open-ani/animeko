/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings

import app.cash.turbine.test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServiceConnectionTesterTest {
    /**
     * Helper to produce a [ServiceConnectionTester.Service]. Instead of using [testDelay] and
     * `delay(...)`, we await on [signal] to control exactly when the test finishes.
     *
     * [onTestCalled] gives us access to the dispatcher for testing or capturing additional info.
     *
     * If [signal] is null, the test immediately proceeds. Otherwise, the test suspends until
     * `signal.await()` returns.
     */
    private fun createService(
        id: String,
        shouldThrow: Boolean = false,
        shouldFail: Boolean = false,
        onTestCalled: suspend (ContinuationInterceptor) -> Unit = {},
        signal: CompletableDeferred<Unit>? = null,
    ): ServiceConnectionTester.Service {
        return ServiceConnectionTester.Service(
            id = id,
            test = {
                onTestCalled(currentContinuationInterceptor())
                // If a signal is provided, wait for it. Otherwise proceed immediately.
                signal?.await()

                if (shouldThrow) {
                    throw IllegalStateException("Test error")
                }
                !shouldFail
            }
        )
    }

    private suspend fun currentContinuationInterceptor() =
        currentCoroutineContext()[ContinuationInterceptor]!!

    // region Single service tests

    @Test
    fun `testAll - single service success`() = runTest {
        val service = createService("service-id")
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        // Should complete without throwing:
        tester.testAll()

        val results = tester.results.first()
        val state = results.states[service]
        assertTrue(state is ServiceConnectionTester.TestState.Success)
    }

    @Test
    fun `testAll - single service failure`() = runTest {
        val service = createService("fail-service", shouldFail = true)
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        val results = tester.results.first()
        val state = results.states[service]
        assertTrue(state is ServiceConnectionTester.TestState.Failed)
    }

    @Test
    fun `testAll - single service error`() = runTest {
        val service = createService("error-service", shouldThrow = true)
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        val results = tester.results.first()
        val state = results.states[service]
        assertTrue(state is ServiceConnectionTester.TestState.Error)
        assertTrue(state.e is IllegalStateException)
    }

    // endregion

    // region Multiple services

    @Test
    fun `testAll - multiple services mixed results`() = runTest {
        val okService = createService("ok")
        val failService = createService("fail", shouldFail = true)
        val errorService = createService("error", shouldThrow = true)

        val tester = ServiceConnectionTester(
            services = listOf(okService, failService, errorService),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        val results = tester.results.first()
        assertTrue(results.states[okService] is ServiceConnectionTester.TestState.Success)
        assertTrue(results.states[failService] is ServiceConnectionTester.TestState.Failed)
        assertTrue(results.states[errorService] is ServiceConnectionTester.TestState.Error)
    }

    // endregion

    // region Cancellation tests

    @Test
    fun `testAll - cancel during testing - states revert to Idle`() = runTest {
        // Use a signal so the service doesn't complete until we allow it.
        val serviceSignal = CompletableDeferred<Unit>()
        val longRunningService = createService(
            "long-run",
            signal = serviceSignal,
        ) // never completes unless we .complete() the signal

        val tester = ServiceConnectionTester(
            services = listOf(longRunningService),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        // Collect from the results to see when it enters Testing state
        // Then cancel.
        tester.results.test {
            // Initial = Idle
            val initial = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, initial.states[longRunningService])

            // Start testAll in a separate job
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                tester.testAll()
            }

            // Next emission => Testing
            val testing = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Testing, testing.states[longRunningService])

            // Now cancel
            job.cancelAndJoin()

            // After cancel, we should get a new emission => Idle
            val final = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, final.states[longRunningService])

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stopAll - states are kept intact`() = runTest {
        val service1 = createService("s1")
        val service2 = createService("s2", shouldFail = true)

        val tester = ServiceConnectionTester(
            services = listOf(service1, service2),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()
        val results = tester.results.first()
        assertNotEquals(ServiceConnectionTester.TestState.Idle, results.states[service1])
        assertNotEquals(ServiceConnectionTester.TestState.Idle, results.states[service2])

        // now call stopAll
        tester.stopAll()
        val newResults = tester.results.first()
        assertEquals(results.states, newResults.states)
    }

    // endregion

    // region Flow emission tests

    @Test
    fun `results flow - verifies states update in real-time`() = runTest {
        // We'll hold the service completion until we decide to finish.
        val serviceSignal = CompletableDeferred<Unit>()
        val service = createService(
            "service",
            signal = serviceSignal,
        )
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.results.test {
            // Initially, Idle
            val initial = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, initial.states[service])

            // Launch testAll
            val job = launch(start = CoroutineStart.UNDISPATCHED) { tester.testAll() }

            // Next => Testing
            val testing = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Testing, testing.states[service])

            // Now signal the service to complete
            serviceSignal.complete(Unit)

            // Next => Success
            val final = awaitItem()
            assertTrue(final.states[service] is ServiceConnectionTester.TestState.Success)

            job.cancelAndJoin()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `results flow - verifies multiple states update in real-time`() = runTest {
        // Two signals for two services
        val service1Signal = CompletableDeferred<Unit>()
        val service2Signal = CompletableDeferred<Unit>()

        val service1 = createService("service1", signal = service1Signal)
        val service2 = createService("service2", signal = service2Signal)

        val tester = ServiceConnectionTester(
            services = listOf(service1, service2),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.results.test {
            // Initially, Idle for both
            val initial = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, initial.states[service1])
            assertEquals(ServiceConnectionTester.TestState.Idle, initial.states[service2])

            // Start the testAll
            val job = launch(start = CoroutineStart.UNDISPATCHED) { tester.testAll() }

            // We might receive several emissions as services go from Idle -> Testing
            // Let's wait until we see both in Testing
            var bothAreTesting = false
            while (!bothAreTesting) {
                val next = awaitItem()
                val s1State = next.states[service1]
                val s2State = next.states[service2]
                if (s1State == ServiceConnectionTester.TestState.Testing &&
                    s2State == ServiceConnectionTester.TestState.Testing
                ) {
                    bothAreTesting = true
                }
            }

            // Now complete service2, to verify it finishes first
            service2Signal.complete(Unit)

            // Next emission => service2 => Success, service1 => still Testing
            val afterService2 = awaitItem()
            assertTrue(afterService2.states[service2] is ServiceConnectionTester.TestState.Success)
            assertTrue(afterService2.states[service1] is ServiceConnectionTester.TestState.Testing)

            // Finally complete service1
            service1Signal.complete(Unit)

            // Next emission => service1 => Success, service2 => Success
            val final = awaitItem()
            assertTrue(final.states[service1] is ServiceConnectionTester.TestState.Success)
            assertTrue(final.states[service2] is ServiceConnectionTester.TestState.Success)

            job.cancelAndJoin()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Dispatcher checks

    @Test
    fun `testAll - uses provided defaultDispatcher`() = runTest {
        val interceptorFlow = MutableStateFlow<ContinuationInterceptor?>(null)
        val service = createService(
            id = "capture-dispatcher",
            onTestCalled = { interceptor ->
                interceptorFlow.value = interceptor
            }
        )

        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        // Service test has finished, check which dispatcher captured
        assertEquals(currentContinuationInterceptor(), interceptorFlow.value)
    }

    // endregion
}
