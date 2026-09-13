/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

// The SDK budgets eight connections per file and sixteen per account. Playback and caching
// the same file share one slot; admitting another file must account for both consumers.
internal class DownloadScheduler(
    private val limit: Int = DEFAULT_LIMIT,
) {
    private val logger = logger<DownloadScheduler>()
    private val lock = SynchronizedObject()

    private val contenders = mutableListOf<Slot>()

    private val playing = mutableSetOf<Slot>()

    private val version = MutableStateFlow(0L)

    private val _streaming = MutableStateFlow(false)

    /** Whether any file has a playback stream open. Fetchers throttle themselves on it. */
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    fun newSlot(name: String): Slot = Slot(name)

    inner class Slot internal constructor(private val name: String) {
        private var streams = 0

        val streaming: StateFlow<Boolean> get() = this@DownloadScheduler.streaming

        suspend fun awaitTurn() {
            var deferred = false
            while (true) {
                val seen = version.value
                val allowed = synchronized(lock) {
                    if (contenders.none { it === this }) contenders += this
                    isAllowed(this)
                }
                if (allowed) {
                    if (deferred) logger.info { "[$name] download resumed" }
                    return
                }
                if (!deferred) {
                    deferred = true
                    logger.info { "[$name] download deferred: another download or a playing stream has the line" }
                }
                version.first { it != seen }
            }
        }

        fun leave() {
            synchronized(lock) { contenders.removeAll { it === this } }
            bump()
        }

        fun openStream() {
            synchronized(lock) {
                streams++
                if (streams == 1) playing += this
            }
            bump()
        }

        fun closeStream() {
            synchronized(lock) {
                if (streams == 0) return@synchronized
                streams--
                if (streams > 0) return@synchronized
                playing -= this

                // Keep finishing the watched episode ahead of older queued downloads.
                val at = contenders.indexOfFirst { it === this }
                if (at > 0) {
                    contenders.removeAt(at)
                    contenders.add(0, this)
                }
            }
            bump()
        }

        fun release() {
            synchronized(lock) {
                streams = 0
                playing -= this
                contenders.removeAll { it === this }
            }
            bump()
        }

        override fun toString(): String = name
    }

    private fun isAllowed(slot: Slot): Boolean {
        if (slot in playing) return true

        val free = limit - playing.size
        val rank = contenders.filter { it !in playing }.indexOfFirst { it === slot }
        return rank in 0 until free
    }

    private fun bump() {
        _streaming.value = synchronized(lock) { playing.isNotEmpty() }
        version.update { it + 1 }
    }

    internal companion object {
        const val DEFAULT_LIMIT = 2
    }
}
