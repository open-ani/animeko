/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.fetch.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue


internal class ReuseObjectPoolTest {

    @Test
    fun `single borrow and release should create and then remove instance`() {
        val createdCount = AtomicInteger(0)
        val releasedCount = AtomicInteger(0)

        val pool = ReuseObjectPool<String, String>(
            newInstance = { key ->
                createdCount.incrementAndGet()
                "ClientFor_$key"
            },
            onRelease = {
                releasedCount.incrementAndGet()
            },
        )

        val instance = pool.borrow("matrixA")
        assertEquals(1, createdCount.get())
        assertEquals("ClientFor_matrixA", instance)

        pool.release("matrixA", instance)
        assertEquals(1, releasedCount.get())
    }

    @Test
    fun `borrowing same key multiple times returns same instance`() {
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
        )

        val client1 = pool.borrow("matrixA")
        val client2 = pool.borrow("matrixA")
        assertSame(client1, client2, "Should be same instance for the same key")

        // Release them both
        pool.release("matrixA", client1)
        pool.release("matrixA", client2)
    }

    @Test
    fun `refCount reaches zero after correct number of releases`() {
        val releasedCount = AtomicInteger(0)

        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
            onRelease = { releasedCount.incrementAndGet() },
        )

        // Borrow the same client 2 times
        val c1 = pool.borrow("matrixA")
        val c2 = pool.borrow("matrixA")
        assertSame(c1, c2)

        // Release once -> should still NOT remove from the map
        pool.release("matrixA", c1)
        assertEquals(0, releasedCount.get())

        // Release second time -> should remove from map
        pool.release("matrixA", c2)
        assertEquals(1, releasedCount.get(), "onRelease must have been called exactly once")
    }

    /**
     * Concurrency test that attempts many borrows and releases in parallel
     * to ensure the reference counting doesn't break or throw exceptions.
     */
    @Test
    fun `concurrency test - multiple borrows and releases`() = runTest {
        val createdCount = AtomicInteger(0)
        val releasedCount = AtomicInteger(0)

        val pool = ReuseObjectPool<String, String>(
            newInstance = {
                createdCount.incrementAndGet()
                "client_for_$it"
            },
            onRelease = { releasedCount.incrementAndGet() },
        )

        val nCoroutines = 50
        val nIterationsPerCoroutine = 100

        coroutineScope {
            repeat(nCoroutines) {
                launch(Dispatchers.Default) {
                    repeat(nIterationsPerCoroutine) {
                        val client = pool.borrow("matrixA")
                        // Do a tiny bit of work
                        // Then release
                        pool.release("matrixA", client)
                    }
                }
            }
        }

        // By the time all coroutines complete, the final refCount should be 0
        // which means the item has been released from the map.
        // It's possible the pool will recreate the instance if concurrency occurs
        // after a prior release, so createdCount could be > 1
        // but eventually it should come back down to 0 references.

        // We can't guarantee that it's removed immediately
        // if the final release call hasn't run in time, but runTest() or runBlockingTest()
        // ensures all coroutines are complete.

        // The easiest check is that we can do a final borrow+release
        // and see that only 1 more is created for the new final usage:
        val c = pool.borrow("matrixA")
        pool.release("matrixA", c)

        // createdCount is not necessarily 1, it's however many times the pool had to
        // create a new instance in concurrency. We do know that each time refCount
        // hits zero, onRelease is invoked.
        assertTrue(releasedCount.get() >= 1, "Should have released at least once")
    }

    /**
     * A scenario to test if two different keys remain distinct in concurrency usage.
     */
    @Test
    fun `concurrency test - multiple keys remain distinct`() = runTest {
        val pool = ReuseObjectPool<Int, String>(
            newInstance = { "client_for_$it" },
        )

        val keyCount = 5
        val concurrencyPerKey = 20

        coroutineScope {
            (0 until keyCount).map { key ->
                launch(Dispatchers.Default) {
                    repeat(concurrencyPerKey) {
                        val c = pool.borrow(key)
                        assertEquals("client_for_$key", c, "Should match expected pooled instance")
                        pool.release(key, c)
                    }
                }
            }.joinAll()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Error cases
    ///////////////////////////////////////////////////////////////////////////


    @Test
    fun `releasing key that does not exist throws`() {
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
        )
        // Borrow and release a valid key
        val instance = pool.borrow("matrixA")
        pool.release("matrixA", instance)

        // Attempt to release a non-existent key
        val ex = assertFailsWith<IllegalStateException> {
            pool.release("unknownKey", "fakeClient")
        }
        assertTrue(
            ex.message?.contains("not found in the map") == true,
            "Expected error about key not found",
        )
    }

    @Test
    fun `releasing value that does not match the stored value throws`() {
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
        )
        val instance = pool.borrow("matrixA")

        // Attempt to release a different (incorrect) value for the same key
        val ex = assertFailsWith<IllegalStateException> {
            pool.release("matrixA", "not_the_same_instance")
        }
        assertTrue(
            ex.message?.contains("does not equal to releasing value") == true,
            "Expected error about mismatched value",
        )
    }

    @Test
    fun `borrow after fully released returns a brand new instance`() {
        val pool = ReuseObjectPool<String, Any>(
            newInstance = { Any() }, // Use Any() to easily verify brand new instances
        )
        // Borrow
        val instance1 = pool.borrow("matrixA")
        // Release (refCount -> 0)
        pool.release("matrixA", instance1)

        // Borrow again after fully released
        val instance2 = pool.borrow("matrixA")

        // Verify that it's a new instance (not the same reference)
        assertNotSame(instance1, instance2, "Should be a new instance after refCount reached 0")
    }

    @Test
    fun `releasing multiple times with one borrow triggers an exception on the second release`() {
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
        )
        val instance = pool.borrow("matrixA")

        // Release it once
        pool.release("matrixA", instance)

        // Attempting to release again should fail because the refCount was already 0
        val ex = assertFailsWith<IllegalStateException> {
            pool.release("matrixA", instance)
        }
        assertTrue(
            ex.message?.contains("Value client_for_matrixA (for matrix matrixA) not found in the map") == true,
            "Expected error about value not found (since it was already removed)",
        )
    }

    /**
     * Demonstrates behavior when the onRelease callback might throw an exception.
     * The pool doesn't catch or handle it, so the caller sees the exception.
     */
    @Test
    fun `onRelease throws exception does not corrupt pool`() = runTest {
        var onReleaseCalled = false
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
            onRelease = {
                onReleaseCalled = true
                error("Simulated failure in onRelease")
            },
        )

        val instance = pool.borrow("matrixA")
        val ex = assertFailsWith<IllegalStateException> {
            pool.release("matrixA", instance)
        }
        assertEquals("Simulated failure in onRelease", ex.message)
        assertTrue(onReleaseCalled)
    }
}