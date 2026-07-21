/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.watchtogether

import kotlinx.atomicfu.atomic
import me.him188.ani.utils.platform.currentTimeMillis

class ServerClock(
    private val localNowMillis: () -> Long = ::currentTimeMillis,
) {
    private val offsetMillis = atomic<Double?>(null)

    fun now(): Long = (localNowMillis() + (offsetMillis.value ?: 0.0)).toLong()

    fun recordSample(serverTimeMillis: Long, sentAtMillis: Long, receivedAtMillis: Long): Boolean {
        val roundTripMillis = receivedAtMillis - sentAtMillis
        if (roundTripMillis !in 0..MAX_SAMPLE_RTT_MILLIS) return false
        val midpoint = sentAtMillis + roundTripMillis / 2.0
        val sample = serverTimeMillis - midpoint
        while (true) {
            val current = offsetMillis.value
            val updated = current?.let { it * (1.0 - EWMA_ALPHA) + sample * EWMA_ALPHA } ?: sample
            if (offsetMillis.compareAndSet(current, updated)) break
        }
        return true
    }

    internal fun currentOffsetMillis(): Double = offsetMillis.value ?: 0.0

    private companion object {
        const val EWMA_ALPHA = 0.2
        const val MAX_SAMPLE_RTT_MILLIS = 2_000L
    }
}
