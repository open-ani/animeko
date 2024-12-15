/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE")

package me.him188.ani.utils.platform.collections

class IntPriorityQueue {
    private val heap = mutableListOf<Int>()

    val size: Int
        get() = heap.size

    fun isEmpty(): Boolean = heap.isEmpty()

    fun peek(): Int? = heap.firstOrNull()

    fun add(value: Int) {
        heap.add(value)
        heapifyUp(heap.size - 1)
    }

    fun poll(): Int? {
        if (heap.isEmpty()) return null
        return if (heap.size == 1) {
            heap.removeAt(0)
        } else {
            val top = heap[0]
            heap[0] = heap.removeAt(heap.size - 1)
            heapifyDown(0)
            top
        }
    }

    fun removeFirst(): Int {
        if (heap.isEmpty()) throw NoSuchElementException("Priority queue is empty")
        return if (heap.size == 1) {
            heap.removeAt(0)
        } else {
            val top = heap[0]
            heap[0] = heap.removeAt(heap.size - 1)
            heapifyDown(0)
            top
        }
    }

    private fun heapifyUp(index: Int) {
        var i = index
        while (i > 0) {
            val parentIndex = (i - 1) / 2
            if (heap[i] < heap[parentIndex]) {
                swap(i, parentIndex)
                i = parentIndex
            } else {
                break
            }
        }
    }

    private fun heapifyDown(index: Int) {
        var i = index
        val size = heap.size
        while (true) {
            val left = 2 * i + 1
            val right = 2 * i + 2
            var smallest = i

            if (left < size && heap[left] < heap[smallest]) smallest = left
            if (right < size && heap[right] < heap[smallest]) smallest = right

            if (smallest != i) {
                swap(i, smallest)
                i = smallest
            } else {
                break
            }
        }
    }

    private fun swap(i: Int, j: Int) {
        val temp = heap[i]
        heap[i] = heap[j]
        heap[j] = temp
    }
}

inline fun IntPriorityQueue.isNotEmpty() = !isEmpty()
