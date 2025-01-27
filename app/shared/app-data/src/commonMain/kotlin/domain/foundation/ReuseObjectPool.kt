/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.foundation

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.concurrent.Volatile

/**
 * An object pool that allows concurrent reuse of the same objects.
 *
 * It uses a reference counter to track the number of references to the object.
 * When a instance is borrowed, the ref counter is increased. When it's returned, the ref counter is decreased.
 * If the ref counter decreases to 0, the object is removed from the pool.
 * More specifically, [onRelease] will be called on the [V] from the thread who decreases the ref counter to 0.
 * Conversely, [newInstance] will be invoked from the thread who is the first to request a new instance.
 *
 * It's optimized for borrows only when ref counter is at least 1, and for returns when ref counter is at least 2 (i.e. when there is at least another borrower).
 * Creating new instances and destroying them requires locks and hence is slower.
 *
 * @param K A stable type that can be used as a key to identify the object. It must implement `equals` and `hashCode`.
 * @param V The type of the object to be pooled.
 */
internal class ReuseObjectPool<K : Any, V>(
    private val newInstance: (K) -> V,
    private val onRelease: (V) -> Unit = {},
) {
    private data class Store<V>(
        val value: V,
    ) {
        val refCounter = atomic(0)
    }

    @Volatile
    private var map = mapOf<K, Store<V>>()

    private val mapLock = ReentrantLock()


    private fun borrowExisting(matrix: K): V? {
        val existingClient = map[matrix] ?: return null
        // We have a possibly live client, atomically increase the ref counter
        while (true) {
            val curr = existingClient.refCounter.value
            if (curr == 0) {
                // Already freed, restart.
                // No need to free the client since the thread who decreases the ref counter from 1 to 0 will do it.
                return null
            }
            if (existingClient.refCounter.compareAndSet(curr, curr + 1)) {
                return existingClient.value
            } else {
                // CAS failed, retry
            }
        }
    }

    /**
     * Borrows an object matching the key [matrix]. If no object is available, a new one is created.
     *
     * Is mut be released by calling [release] when no longer needed.
     */
    fun borrow(matrix: K): V {
        borrowExisting(matrix)?.let { return it }
        mapLock.withLock {
            borrowExisting(matrix)?.let { return it }

            // No existing client, create one

            val newClient = newInstance(matrix)
            val store = Store(newClient)
            store.refCounter.incrementAndGet()
            map =
                map + (matrix to store) // Note: this may replace a existing store (which has refCount == 0).
            return newClient
        }
    }

    /**
     * Decrease the reference counter of the client and release it if no one is using it.
     *
     * This method also checks if the client is still in the map. In all cases it should be, otherwise it's a bug.
     */
    fun release(matrix: K, value: V) {
        val existing = map[matrix]
        checkNotNull(existing) { "Value $value (for matrix $matrix) not found in the map" }
        check(existing.value === value) { "Matrix $matrix has a corresponding value ${existing.value}, but does not equal to releasing value $value" }
        releaseOneReference(existing, matrix)
        return
    }

    private fun releaseOneReference(
        store: Store<V>,
        matrix: K,
    ) {
        while (true) {
            val curr = store.refCounter.value
            if (store.refCounter.compareAndSet(curr, curr - 1)) {
                if (curr == 1) {
                    // Last one, remove the client. We've already set refCounter to 0 so no one else will use the HttpClient.
                    // However, note that we were not in lock. So some one may alter the map in the meantime.
                    mapLock.withLock {
                        // We must check the map again because it may be a new HttpClient with refCount being 1.
                        if (map[matrix] === store) {
                            // No one else has replaced the client, remove it
                            val newMap = map.toMutableMap()
                            newMap.remove(matrix)
                            map = newMap
                        }
                        onRelease(store.value)
                    }
                } else {
                    // Others are still using the client, no need to remove it.
                }

                return
            } else {
                // Failed race, retry CAS
            }
        }
    }

    /**
     * Forcibly releases all clients in the pool, even if someone is still using them.
     * This breaks algorithm invariants and should only be used for testing.
     * This is useful for cleanup in unit testing.
     */
    @TestOnly
    fun forceReleaseAll() {
        mapLock.withLock {
            map.forEach { (_, store) ->
                if (store.refCounter.value != 0) {
                    store.refCounter.value = 0
                    onRelease(store.value)
                }
            }
            map = emptyMap()
        }
    }
}