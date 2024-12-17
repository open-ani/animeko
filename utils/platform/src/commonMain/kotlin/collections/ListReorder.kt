/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.platform.collections

import androidx.collection.mutableIntListOf
import androidx.collection.mutableObjectIntMapOf

/**
 * Reorders the elements of this list according to the given [newPartialOrder], ensuring a stable integrated topological order.
 *
 * This function:
 * - Respects the partial order specified by [newPartialOrder]. If `a` precedes `b` in [newPartialOrder],
 *   then in the resulting list `a` appears before `b`.
 * - Maintains the relative order of elements not mentioned in [newPartialOrder] ("leftover" elements)
 *   as much as possible. They appear in the result in the same relative order they appeared originally,
 *   except where changes are needed to respect the partial order.
 * - If there is a conflict between the original ordering and the partial order, the partial order takes precedence.
 *   Edges from the original order that would create contradictions (cycles) with the partial order are skipped.
 *
 * It achieves this by constructing a directed acyclic graph (DAG) from both the partial order edges and
 * the original order edges, skipping any edges that introduce cycles. Finally, it performs a stable topological sort:
 * when multiple vertices have no incoming edges, it picks the one with the smallest original index first.
 *
 * Example:
 * Original: [x, y, a, b, c, z]
 * newPartialOrder: [c, a] means c must appear before a.
 *
 * Without constraints: original order is x<y<a<b<c<z.
 * Partial order says c<a. We must place 'c' before 'a'.
 * A stable topological sort that respects this gives: [x, y, c, a, b, z]
 *
 * @param getKey A function to extract a key from each element. The partial order is defined on these keys.
 * @param newPartialOrder A list of keys describing the desired partial order.
 */
fun <T, Key : Comparable<Key>> List<T>.partiallyReorderBy(
    getKey: (T) -> Key,
    newPartialOrder: List<Key>
): List<T> {
    if (newPartialOrder.isEmpty() || size <= 1) return this.toList()

    val elements = this
    val keys = elements.map(getKey)

    val keyToPartialIndex = mutableObjectIntMapOf<Key>().apply {
        keys.forEachIndexed { index, key -> put(key, index) }
    }

    // Identify indices of partial elements in their order
    // Assuming keys in newPartialOrder are unique and exist at most once in the list
    val partialOrderIndices = mutableIntListOf().apply {
        newPartialOrder.forEach { pKey ->
            val index = elements.indexOfFirst { getKey(it) == pKey }
            if (index != -1) add(index)
        }
    }

    val adjacency = MutableList(size) { mutableIntListOf() }
    val inDegree = IntArray(size)

    // Add edges from partial order
    // For consecutive keys in newPartialOrder, add an edge from the earlier to the later
    for (i in 0 until partialOrderIndices.size - 1) {
        val from = partialOrderIndices[i]
        val to = partialOrderIndices[i + 1]
        adjacency[from].add(to)
    }

    // Function to check if adding an edge creates a cycle using DFS
    fun createsCycle(u: Int, v: Int): Boolean {
        // Temporarily add edge u->v and check for cycle
        adjacency[u].add(v)
        val visited = IntArray(size) { 0 } // 0=unvisited,1=visiting,2=visited

        fun dfs(n: Int): Boolean {
            if (visited[n] == 1) return true // cycle detected
            if (visited[n] == 2) return false
            visited[n] = 1
            adjacency[n].forEach { w ->
                if (dfs(w)) return true
            }
            visited[n] = 2
            return false
        }

        val hasCycle = (0 until size).any { visited[it] == 0 && dfs(it) }
        if (hasCycle) {
            // Remove the edge we just added to revert state
            adjacency[u].remove(v)
        }
        return hasCycle
    }

    // Before adding all leftover edges, let's define a helper to check partial contradictions:
    // A direct contradiction occurs if partial order says x<y but we try to add y->x.
    fun partialContradicts(from: Int, to: Int): Boolean {
        val fromPartialIndex = keyToPartialIndex.getOrElse(keys[from]) { -1 }
        val toPartialIndex = keyToPartialIndex.getOrElse(keys[to]) { -1 }
        // If both elements are mentioned in partial order:
        if (fromPartialIndex != -1 && toPartialIndex != -1) {
            // If partial order says from<to (fromPartialIndex<toPartialIndex)
            // Adding from->to is correct. But if partial order says to<from, that contradicts.
            if (fromPartialIndex > toPartialIndex) {
                // partial says 'to' should come before 'from', but we are adding 'from'->'to'
                return true
            }
        }
        // If one or neither is partial, no direct contradiction from partial order perspective
        return false
    }

    // Add leftover edges (from original order) if no contradiction or cycle is formed
    // The original ordering is represented by edges i->i+1 for i in [0..size-2]
    // We try to preserve them unless they cause a cycle or contradict partial order.
    for (i in 0 until size - 1) {
        val u = i
        val v = i + 1
        // Check if this edge contradicts partial order:
        // If partial order says v< u, then adding u->v would contradict it.
        if (!partialContradicts(u, v)) {
            // Check if this edge creates a cycle:
            @Suppress("ControlFlowWithEmptyBody")
            if (createsCycle(u, v)) {
                // skip this edge if it creates a cycle
            } else {
                // edge added successfully
            }
        }
    }

    // Compute in-degree
    inDegree.fill(0)
    for (u in adjacency.indices) {
        adjacency[u].forEach { v ->
            inDegree[v]++
        }
    }

    // Stable topological sort
    val zeroInDegree = IntPriorityQueue()
    for (i in 0 until size) {
        if (inDegree[i] == 0) zeroInDegree.add(i)
    }

    val resultIndices = mutableListOf<Int>()
    while (zeroInDegree.isNotEmpty()) {
        // pick the vertex with the smallest original index for stability
        val u = zeroInDegree.removeFirst()
        resultIndices.add(u)
        adjacency[u].forEach { w ->
            inDegree[w]--
            if (inDegree[w] == 0) {
                zeroInDegree.add(w)
            }
        }
    }

    // In case of cycle (which should not occur now), revert or do nothing
    if (resultIndices.size < size) return this.toList()

    // Rebuild list
    return resultIndices.map { elements[it] }
}

/**
 * Convenience method when [T] is comparable and the key is the element itself.
 */
fun <T : Comparable<T>> MutableList<T>.partiallyReorderBy(
    newPartialOrder: List<T>
) = partiallyReorderBy({ it }, newPartialOrder)
