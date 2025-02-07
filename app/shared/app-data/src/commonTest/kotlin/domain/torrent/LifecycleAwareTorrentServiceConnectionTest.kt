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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifecycleAwareTorrentServiceConnectionTest : AbstractTorrentServiceConnectionTest() {
    @Test
    fun `service starts on resume - success`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val connection = TestTorrentServiceConnection(
            shouldStartServiceSucceed = true,
            coroutineContext = backgroundScope.coroutineContext,
        )

        testLifecycle.lifecycle.addObserver(connection)

        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")

            // trigger on resumed
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            assertTrue(awaitItem(), "After service is connected, connected should become true.")

            // completed
            expectNoEvents()
        }
    }

    @Test
    fun `service starts on resume - fails to start service`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val connection = TestTorrentServiceConnection(
            shouldStartServiceSucceed = false, // Force a failure
            coroutineContext = backgroundScope.coroutineContext,
        )

        testLifecycle.lifecycle.addObserver(connection)

        // The .connected flow should remain false, even after we move to resumed,
        // because startService() always fails, no successful onServiceConnected() is triggered.
        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")

            // Move to RESUMED
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)

            // Because startService() fails repeatedly in the retry loop, connected never becomes true
            // We'll watch for a short while and confirm it does not become true
            repeat(3) {
                expectNoEvents()
                advanceTimeBy(8000) // Let the internal retry happen
            }
        }
    }

    @Test
    fun `getBinder suspends until service is connected`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val connection = TestTorrentServiceConnection(
            shouldStartServiceSucceed = true,
            coroutineContext = backgroundScope.coroutineContext,
        )
        
        testLifecycle.lifecycle.addObserver(connection)
        
        // Start a coroutine that calls getBinder
        val binderDeferred = async {
            connection.getBinder() // Should suspend
        }

        // The call hasn't returned yet, because we haven't simulated connect
        advanceTimeBy(200) // Enough to start the service, but not connect
        assertTrue(!binderDeferred.isCompleted)

        testLifecycle.setCurrentState(Lifecycle.State.RESUMED)

        // Once connected, getBinder should complete with the fake binder
        val binder = binderDeferred.await()
        assertEquals("FAKE_BINDER_OBJECT", binder)
    }

    @Test
    fun `service disconnect triggers automatic restart if lifecycle is RESUMED`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val connection = TestTorrentServiceConnection(
            shouldStartServiceSucceed = true,
            coroutineContext = backgroundScope.coroutineContext,
        )

        testLifecycle.lifecycle.addObserver(connection)
        
        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")
            // Wait for the startService invocation
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            advanceTimeBy(200)
            // Now it’s connected
            assertTrue(awaitItem(), "Service should be connected.")

            // Disconnect:
            connection.triggerServiceDisconnected()
            assertFalse(awaitItem(), "Service should be disconnected since we triggered disconnection.")

            // Because the lifecycle is still in RESUMED,
            // it should attempt to startService again automatically
            // We can wait a bit, then connect again:
            advanceTimeBy(200) // let the startService happen
            assertTrue(awaitItem(), "Service should be reconnected since lifecycle state is RESUMED.")
            
            expectNoEvents()
        }
    }

    @Test
    fun `service disconnect does not restart if lifecycle is only CREATED`() = runTest {
        val testLifecycle = TestLifecycleOwner()
        val connection = TestTorrentServiceConnection(
            shouldStartServiceSucceed = true,
            coroutineContext = backgroundScope.coroutineContext,
        )
        testLifecycle.lifecycle.addObserver(connection)
        
        connection.connected.test {
            assertFalse(awaitItem(), "Initially, connected should be false.")

            // Wait for the startService invocation
            testLifecycle.setCurrentState(Lifecycle.State.RESUMED)
            advanceTimeBy(200)
            // Now it’s connected
            assertTrue(awaitItem(), "Service should be connected.")

            // Move lifecycle to CREATED
            testLifecycle.setCurrentState(Lifecycle.State.CREATED)
            // Now simulate a service disconnect
            connection.triggerServiceDisconnected()

            // Should remain disconnected, no auto retry because we are no longer in RESUMED
            advanceTimeBy(2000)
            assertFalse(awaitItem(), "Service should not be connected because current lifecycle state is not RESUMED.")
        }
    }

    @Test
    fun `close cancels all coroutines and flows`() = runTest {
        val connection = TestTorrentServiceConnection(
            shouldStartServiceSucceed = true,
            coroutineContext = backgroundScope.coroutineContext,
        )
        advanceUntilIdle()
        assertFalse(connection.connected.value)

        // Attempt to call getBinder() after close => should never succeed
        val cancelled = CompletableDeferred<Boolean>()
        launch {
            try {
                connection.getBinder()
            } catch (ex: CancellationException) {
                cancelled.complete(true)
            }
        }
        
        advanceUntilIdle()
        connection.close()

        advanceUntilIdle()
        assertTrue(cancelled.await(), "getBinder() should be cancelled because connection is closed.")
    }
}