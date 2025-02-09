/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.torrent.service.LifecycleAwareTorrentServiceConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifecycleAwareTorrentServiceConnectionTest : AbstractTorrentServiceConnectionTest() {

    @Test
    fun `service starts on resume - success`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val (starter, deferred) = createStarter(expectSuccess = true)
        val connection = LifecycleAwareTorrentServiceConnection(
            parentCoroutineContext = backgroundScope.coroutineContext,
            lifecycle = testLifecycle.lifecycle,
            starter = starter,
        ).also { it.startLifecycleLoop() }

        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")

            // trigger on resumed
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            backgroundScope.launch { deferred.complete(Unit) }
            assertTrue(awaitItem(), "After service is connected, connected should become true.")

            // completed
            expectNoEvents()
        }

        connection.close()
    }

    @Test
    fun `service starts on resume - fails to start service`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val (starter, deferred) = createStarter(expectSuccess = false)
        val connection = LifecycleAwareTorrentServiceConnection(
            parentCoroutineContext = backgroundScope.coroutineContext,
            lifecycle = testLifecycle.lifecycle,
            starter = starter,
        ).also { it.startLifecycleLoop() }

        // The .connected flow should remain false, even after we move to resumed,
        // because startService() always fails, no successful onServiceConnected() is triggered.
        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")

            // Move to RESUMED
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            backgroundScope.launch { deferred.complete(Unit) }
            // Because startService() fails repeatedly in the retry loop, connected never becomes true
            // We'll watch for a short while and confirm it does not become true
            repeat(3) {
                expectNoEvents()
                advanceTimeBy(10000) // Let the internal retry happen
            }
        }

        connection.close()
    }

    @Test
    fun `getBinder suspends until service is connected`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val (starter, deferred) = createStarter(expectSuccess = true)
        val connection = LifecycleAwareTorrentServiceConnection(
            parentCoroutineContext = backgroundScope.coroutineContext,
            lifecycle = testLifecycle.lifecycle,
            starter = starter,
        ).also { it.startLifecycleLoop() }
        
        // Start a coroutine that calls getBinder
        val binderDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            connection.getBinder() // Should suspend
        }

        // The call hasn't returned yet, because we haven't simulated connect
        advanceUntilIdle() // Enough to start the service, but not connect
        assertTrue(!binderDeferred.isCompleted)

        testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
        backgroundScope.launch { deferred.complete(Unit) }

        // Once connected, getBinder should complete with the fake binder
        val binder = binderDeferred.await()
        assertEquals(fakeBinder, binder)

        connection.close()
    }

    @Test
    fun `service disconnect triggers automatic restart if lifecycle is RESUMED`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val (starter, deferred) = createStarter(expectSuccess = true)
        val connection = LifecycleAwareTorrentServiceConnection(
            parentCoroutineContext = backgroundScope.coroutineContext,
            lifecycle = testLifecycle.lifecycle,
            starter = starter,
        ).also { it.startLifecycleLoop() }
        
        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")
            // Wait for the startService invocation
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            backgroundScope.launch { deferred.complete(Unit) }
            advanceUntilIdle()
            // Now it’s connected
            assertTrue(awaitItem(), "Service should be connected.")

            // Disconnect:
            connection.onServiceDisconnected()
            advanceUntilIdle()
            assertFalse(awaitItem(), "Service should be disconnected since we triggered disconnection.")

            backgroundScope.launch { deferred.complete(Unit) }
            // Because the lifecycle is still in RESUMED,
            // it should attempt to startService again automatically
            // We can wait a bit, then connect again:
            advanceUntilIdle() // let the startService happen
            assertTrue(awaitItem(), "Service should be reconnected since lifecycle state is RESUMED.")
            
            expectNoEvents()
        }

        connection.close()
    }

    @Test
    fun `service disconnect does not restart if lifecycle is only CREATED`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val (starter, deferred) = createStarter(expectSuccess = true)
        val connection = LifecycleAwareTorrentServiceConnection(
            parentCoroutineContext = backgroundScope.coroutineContext,
            lifecycle = testLifecycle.lifecycle,
            starter = starter,
        ).also { it.startLifecycleLoop() }
        
        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")

            // Wait for the startService invocation
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            backgroundScope.launch { deferred.complete(Unit) }
            advanceUntilIdle()
            // Now it’s connected
            assertTrue(awaitItem(), "Service should be connected.")

            // Move lifecycle to CREATED
            testLifecycle.setCurrentState(Lifecycle.State.CREATED)
            // Now simulate a service disconnect
            connection.onServiceDisconnected()

            // Should remain disconnected, no auto retry because we are no longer in RESUMED
            advanceUntilIdle()
            assertFalse(awaitItem(), "Service should not be connected because current lifecycle state is not RESUMED.")

            advanceUntilIdle()
            expectNoEvents()
        }

        connection.close()
    }

    @Test
    fun `lifecycle move to STARTED while starting service`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val (starter, deferred) = createStarter(expectSuccess = true)
        val connection = LifecycleAwareTorrentServiceConnection(
            parentCoroutineContext = backgroundScope.coroutineContext,
            lifecycle = testLifecycle.lifecycle,
            starter = starter,
        ).also { it.startLifecycleLoop() }

        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")


            // Wait for the startService invocation
            // connect 成功需要 200ms, 100ms 后就 move to STARTED
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            runCurrent() // ensure flow collector receives the new value
            testLifecycle.setCurrentState(Lifecycle.State.STARTED)
            runCurrent()

            // 启动中途切到后台 (lifecycle state => STARTED) 不会 emit true
            advanceUntilIdle()
            expectNoEvents()
        }

        connection.close()
    }

    @Test
    fun `fast path for service disconnected and also does not affect binder getter`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val (starter, deferred) = createStarter(expectSuccess = true)
        val connection = LifecycleAwareTorrentServiceConnection(
            parentCoroutineContext = backgroundScope.coroutineContext,
            lifecycle = testLifecycle.lifecycle,
            starter = starter,
        ).also { it.startLifecycleLoop() }
        
        advanceUntilIdle()
        assertFalse(connection.connected.value)

        // Attempt to call getBinder() after close => should never succeed
        var cancelled = false

        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")

            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    connection.getBinder()
                } catch (ex: CancellationException) {
                    cancelled = true
                }
            }

            // connect 成功需要 200ms, 100ms 后就 trigger disconnect
            // 此时 connected 不会被设置为 true, 因为通过 fast path 检测到了 disconnect
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)

            advanceUntilIdle()
            connection.onServiceDisconnected()

            advanceUntilIdle()
            testLifecycle.setCurrentState(Lifecycle.State.STARTED)

            // no connected = false should be emitted, and also binder getter is still active.
            expectNoEvents()
            assertFalse(cancelled, "getBinder() should not be cancelled.")

            advanceUntilIdle()
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            backgroundScope.launch { deferred.complete(Unit) }
            advanceUntilIdle()
            // Now it’s connected
            assertTrue(awaitItem(), "Service should be connected.")

            // Disconnect:
            connection.onServiceDisconnected()
            advanceUntilIdle()
            assertFalse(awaitItem(), "Service should be disconnected since we triggered disconnection.")
        }

        connection.close()
    }
}