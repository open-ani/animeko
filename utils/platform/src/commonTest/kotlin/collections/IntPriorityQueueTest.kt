/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.platform.collections

import kotlin.test.*

class IntPriorityQueueTest {
    @Test
    fun testEmptyQueue() {
        val pq = IntPriorityQueue()
        assertTrue(pq.isEmpty(), "Newly created queue should be empty")
        assertNull(pq.peek(), "Peek on empty queue should return null")
        assertNull(pq.poll(), "Poll on empty queue should return null")
    }

    @Test
    fun testAddAndPeek() {
        val pq = IntPriorityQueue()
        pq.add(5)
        assertFalse(pq.isEmpty())
        assertEquals(5, pq.peek())
        assertEquals(1, pq.size)
    }

    @Test
    fun testAddMultipleAndPoll() {
        val pq = IntPriorityQueue()
        val values = listOf(5, 3, 6, 2, 9, 1)
        values.forEach { pq.add(it) }

        assertEquals(6, pq.size)
        // Since it's a min-heap, polling should give us elements in ascending order
        val sortedValues = values.sorted()
        for (expected in sortedValues) {
            assertEquals(expected, pq.poll())
        }
        assertTrue(pq.isEmpty())
    }

    @Test
    fun testPeekDoesNotRemoveElement() {
        val pq = IntPriorityQueue()
        pq.add(10)
        assertEquals(10, pq.peek())
        assertEquals(10, pq.peek()) // Peek again, still 10
        assertEquals(1, pq.size, "Size should not change after peek")
    }

    @Test
    fun testLargeInputStaysCorrect() {
        val pq = IntPriorityQueue()
        val largeRange = (1..1000).shuffled()
        largeRange.forEach { pq.add(it) }

        for (i in 1..1000) {
            assertEquals(i, pq.poll(), "Polling should retrieve elements in sorted order")
        }
        assertTrue(pq.isEmpty())
    }

    @Test
    fun testRandomOperations() {
        val pq = IntPriorityQueue()
        pq.add(10)
        pq.add(5)
        pq.add(7)
        assertEquals(5, pq.poll())
        pq.add(3)
        pq.add(15)
        pq.add(4)

        // Now we have: 3, 4, 7, 10, 15 in the heap
        val sorted = listOf(3, 4, 7, 10, 15)
        for (x in sorted) {
            assertEquals(x, pq.poll())
        }
        assertTrue(pq.isEmpty())
    }
}
