/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package me.him188.ani.app.domain.settings

import app.cash.turbine.test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ServiceConnectionTesterTest {

    /**
     * Helper to produce a [ServiceConnectionTester.Service] that can succeed,
     * fail, throw, or be long-running based on configuration.
     * [onTestCalled] gives us access to the dispatcher for testing.
     */
    private fun createService(
        id: String,
        testDelay: Duration = Duration.ZERO,
        shouldThrow: Boolean = false,
        shouldFail: Boolean = false,
        onTestCalled: suspend (ContinuationInterceptor) -> Unit = {},
    ): ServiceConnectionTester.Service {
        return ServiceConnectionTester.Service(
            id = id,
            test = {
                onTestCalled(currentContinuationInterceptor())
                if (testDelay > Duration.ZERO) {
                    delay(testDelay)
                }
                if (shouldThrow) {
                    throw IllegalStateException("Test error")
                }
                !shouldFail
            },
        )
    }

    // region Single service tests

    @Test
    fun `testAll - single service success`() = runTest {
        val service = createService("service-id")
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(), // or Dispatchers.Default
        )

        tester.testAll() // should complete without throwing

        // After testAll completes, the state of the service must be either Success or Failed/Error.
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
        // We’ll make a service that takes 300ms to finish,
        // and we’ll cancel the job after 100ms.
        val interceptorFlow = MutableStateFlow<ContinuationInterceptor?>(null)
        val longRunningService = createService(
            "long-run",
            testDelay = 300.milliseconds,
            onTestCalled = {
                interceptorFlow.value = it
            },
        )

        val tester = ServiceConnectionTester(
            services = listOf(longRunningService),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            tester.testAll()
        }

        advanceTimeBy(100) // time passes, still not complete
        assertEquals(
            ServiceConnectionTester.TestState.Testing,
            tester.results.first().states[longRunningService],
        )

        // Now cancel
        job.cancel()
        job.join()

        assertEquals(currentContinuationInterceptor(), interceptorFlow.value)
        // The service was in progress, but got cancelled => revert to Idle
        val results = tester.results.first()
        val state = results.states[longRunningService]
        assertEquals(ServiceConnectionTester.TestState.Idle, state)
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
        // confirm they have final states
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
    fun `results flow - verifies states update in real-time`() = runTest(StandardTestDispatcher()) {
        val service = createService("service", testDelay = 100.milliseconds)
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        // We'll collect from the shared flow using Turbine.
        // Then we call testAll and observe the progression of states.
        tester.results.test {
            // Initially, we should see Idle.
            val initialEmission = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, initialEmission.states[service])

            // Start the testAll
            val job = launch(start = CoroutineStart.UNDISPATCHED) { tester.testAll() }

            // Next emission: Testing
            val next = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Testing, next.states[service])

            // Then eventually we get either Success or Failed
            // Because testDelay=100ms, we need to advance virtual time
            advanceTimeBy(200)
            runCurrent()

            val final = awaitItem()
            assertTrue(final.states[service] is ServiceConnectionTester.TestState.Success)

            job.join()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `results flow - verifies multiple states update in real-time`() = runTest {
        val service1 = createService("service", testDelay = 100.milliseconds)
        val service2 = createService("service2", testDelay = 300.milliseconds)
        val tester = ServiceConnectionTester(
            services = listOf(service1, service2),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        // We'll collect from the shared flow using Turbine.
        // Then we call testAll and observe the progression of states.
        tester.results.test {
            // Initially, we should see Idle.
            val initialEmission = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, initialEmission.states[service1])
            assertEquals(ServiceConnectionTester.TestState.Idle, initialEmission.states[service2])

            // Start the testAll
            val job = launch(start = CoroutineStart.UNDISPATCHED) { tester.testAll() }

            // Next emission: Testing
            val next = awaitItem()
            assertEquals(0, testScope.currentTime)
            assertTrue(next.states[service1] is ServiceConnectionTester.TestState.Testing)
            assertTrue(next.states[service2] is ServiceConnectionTester.TestState.Testing)

            advanceTimeBy(200) // service1 should complete, while service2 is still testing
            runCurrent()

            val next2 = awaitItem()
            assertTrue(next2.states[service1] is ServiceConnectionTester.TestState.Success)
            assertTrue(next2.states[service2] is ServiceConnectionTester.TestState.Testing)

            advanceTimeBy(200) // service2 should complete
            runCurrent()

            val final = awaitItem()
            assertTrue(final.states[service1] is ServiceConnectionTester.TestState.Success)
            assertTrue(final.states[service2] is ServiceConnectionTester.TestState.Success)

            job.join()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun currentContinuationInterceptor() = currentCoroutineContext()[ContinuationInterceptor]!!

    // endregion

    // region Dispatcher checks

    @Test
    fun `testAll - uses provided defaultDispatcher`() = runTest {
        val interceptorFlow = MutableStateFlow<ContinuationInterceptor?>(null)
        val customDispatcher = StandardTestDispatcher(testScheduler, name = "CustomDispatcher")
        backgroundScope.coroutineContext.job.invokeOnCompletion {
            customDispatcher.cancel()
        }

        val service = createService(
            id = "capture-dispatcher",
            onTestCalled = { interceptor ->
                interceptorFlow.value = interceptor
            },
        )

        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = customDispatcher,
        )

        tester.testAll()
        // Because everything is using the test scheduler, we may need to advance until the service is done:
        advanceUntilIdle()

        // Confirm the service ran on 'customDispatcher'
        assertEquals(customDispatcher, interceptorFlow.value)
    }

    private val TestScope.testScope get() = this

    // endregion
}
