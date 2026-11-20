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

/** Estimates server time from the fastest of the latest sixteen accepted round trips. */
class ServerClock(
    private val localNowMillis: () -> Long = ::currentTimeMillis,
) {
    private data class Sample(val offset: Double, val rtt: Long, val receivedAt: Long)
    private val samples = atomic<List<Sample>>(emptyList())

    /** Age of the most recently accepted sample, or null when the sample window is empty. */
    fun latestSampleAgeMillis(): Long? = samples.value.maxOfOrNull { it.receivedAt }
        ?.let { (localNowMillis() - it).coerceAtLeast(0L) }

    fun now(): Long = (localNowMillis() + currentOffsetMillis()).toLong()

    fun recordSample(serverTimeMillis: Long, sentAtMillis: Long, receivedAtMillis: Long): Boolean {
        val rtt = receivedAtMillis - sentAtMillis
        if (rtt !in 0..MAX_SAMPLE_RTT_MILLIS) return false
        val sample = Sample(serverTimeMillis - (sentAtMillis + rtt / 2.0), rtt, receivedAtMillis)
        while (true) {
            val current = samples.value
            val updated = (current + sample)
                .sortedBy { it.receivedAt }
                .takeLast(SAMPLE_COUNT)
            if (samples.compareAndSet(current, updated)) return true
        }
    }

    internal fun currentOffsetMillis(): Double = samples.value.minByOrNull { it.rtt }?.offset ?: 0.0

    private companion object {
        const val SAMPLE_COUNT = 16
        const val MAX_SAMPLE_RTT_MILLIS = 2_000L
    }
}
