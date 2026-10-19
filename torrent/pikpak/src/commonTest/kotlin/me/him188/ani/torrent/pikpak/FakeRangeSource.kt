/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.RangeSource
import io.ktor.utils.io.ByteReadChannel
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal class FakeRangeSource(
    private val content: ByteArray,
    failFirstReads: Int = 0,
) : RangeSource {
    data class Request(val start: Long, val length: Long)

    private val lock = SynchronizedObject()
    private val _requests = mutableListOf<Request>()
    private var remainingFailures = failFirstReads

    val requests: List<Request> get() = synchronized(lock) { _requests.toList() }

    override suspend fun <T> read(
        start: Long,
        length: Long,
        priority: Int,
        block: suspend (ByteReadChannel) -> T,
    ): T = block(ByteReadChannel(readBytes(start, length, priority)))

    override suspend fun readBytes(start: Long, length: Long, priority: Int): ByteArray {
        val down = synchronized(lock) {
            _requests += Request(start, length)
            if (remainingFailures <= 0) {
                false
            } else {
                remainingFailures--
                true
            }
        }
        if (down) throw IllegalStateException("simulated outage at $start")
        val end = minOf(content.size.toLong(), start + length).toInt()
        return content.copyOfRange(start.toInt(), end)
    }
}
