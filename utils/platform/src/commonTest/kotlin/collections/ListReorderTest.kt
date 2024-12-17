/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.platform.collections

import kotlin.test.Test
import kotlin.test.assertEquals

class PartiallyReorderByTest {

    @Test
    fun `empty newPartialOrder should leave list unchanged`() {
        val list = mutableListOf("a", "b", "c")
        val newPartialOrder = emptyList<String>()

        // Expect the original order since no constraints are given
        assertEquals(listOf("a", "b", "c"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `newPartialOrder reorders subset of elements`() {
        val list = mutableListOf("a", "b", "c", "d", "e")
        // Suppose we want 'd' to appear before 'c', and 'a' before 'd'
        // So, newPartialOrder = [a, d, c]
        val newPartialOrder = listOf("a", "d", "c")

        // Explanation:
        // The desired partial order is a <= d <= c.
        // Original: a, b, c, d, e
        // After reordering according to partial order: 
        // A consistent result is: a, d, b, c, e
        // 'b' and 'e' are not mentioned, so they maintain their relative order.
        // The partial-ordered subset (a, d, c) must appear in that order.
        assertEquals(listOf("a", "b", "d", "c", "e"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `newPartialOrder includes all elements`() {
        val list = mutableListOf("a", "b", "c")
        // Fully specify new order
        val newPartialOrder = listOf("c", "a", "b")

        // All elements are constrained, so final order should exactly match newPartialOrder
        assertEquals(listOf("c", "a", "b"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `newPartialOrder only includes some elements at the end`() {
        val list = mutableListOf("x", "y", "a", "b", "c", "z")
        // We only specify order for 'c' and 'a'
        val newPartialOrder = listOf("c", "a")

        // Explanation:
        // Desired partial order: c <= a
        // Original: x, y, a, b, c, z
        // After reordering: x, y, c, a, b, z is a valid sequence.
        // 'c' must come before 'a'. Other elements remain in their relative order.
        assertEquals(listOf("x", "y", "c", "a", "b", "z"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `no overlapping keys with newPartialOrder`() {
        val list = mutableListOf("m", "n", "o")
        val newPartialOrder = listOf("x", "y", "z") // None of these are in the list

        // Since none of the keys are in the list, the list should remain unchanged
        assertEquals(listOf("m", "n", "o"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `complex partial order with some keys missing`() {
        val list = mutableListOf("1", "2", "3", "4", "5", "6", "7")
        // Suppose we want: 2 <= 6 <= 3
        // The rest (1,4,5,7) are not mentioned.
        val newPartialOrder = listOf("2", "6", "3")

        // Original: 1, 2, 3, 4, 5, 6, 7
        // Result should have 2 before 6 and 6 before 3.
        // One valid final order: 1, 2, 6, 3, 4, 5, 7
        assertEquals(listOf("1", "2", "6", "3", "4", "5", "7"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `partial order has redundant element`() {
        val list = mutableListOf("1", "2", "3", "4", "5", "6", "7")
        // Suppose we want: 2 <= 6 <= 3
        // The rest (1,4,5,7) are not mentioned.
        val newPartialOrder = listOf("2", "6", "100", "3", "10") // 100 and 10 are redundant

        // Original: 1, 2, 3, 4, 5, 6, 7
        // Result should have 2 before 6 and 6 before 3.
        // One valid final order: 1, 2, 6, 3, 4, 5, 7
        assertEquals(listOf("1", "2", "6", "3", "4", "5", "7"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `custom key extraction`() {
        data class Item(val key: Int, val value: String) : Comparable<Item> {
            override fun compareTo(other: Item): Int = key.compareTo(other.key)
        }

        val list = mutableListOf(
            Item(1, "a"),
            Item(2, "b"),
            Item(3, "c"),
            Item(4, "d"),
        )

        val newPartialOrder = listOf(3, 1) // We want item with key=3 before item with key=1

        // Original: (1,a), (2,b), (3,c), (4,d)
        // After reordering for 3 <= 1:
        // A valid result: (2,b), (3,c), (1,a), (4,d)
        // '2' and '4' are not mentioned, so keep their relative order, but ensure 3 comes before 1.
        assertEquals(listOf(3, 1, 2, 4), list.partiallyReorderBy({ it.key }, newPartialOrder).map { it.key })
    }
}
