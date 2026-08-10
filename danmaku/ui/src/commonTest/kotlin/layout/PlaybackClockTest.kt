/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui.layout

import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackClockTest {
    private companion object {
        const val FRAME = 16_666_667L
        fun ms(millis: Long) = millis * 1_000_000L
    }

    @Test
    fun `uninitialized until first report`() {
        val clock = PlaybackClock()
        assertNull(clock.positionAt(ms(100)))

        clock.onPositionReport(5000, ms(100))
        assertEquals(5000, clock.positionAt(ms(100)))
    }

    @Test
    fun `extrapolates between reports`() {
        val clock = PlaybackClock()
        clock.onPositionReport(10_000, ms(0))

        assertEquals(10_500, clock.positionAt(ms(500)))
        assertEquals(11_000, clock.positionAt(ms(1000)))
    }

    @Test
    fun `extrapolation follows playback speed`() {
        val clock = PlaybackClock()
        clock.onPositionReport(10_000, ms(0))
        clock.setPlaybackSpeed(2f, ms(0))

        assertEquals(12_000, clock.positionAt(ms(1000)))
    }

    @Test
    fun `pause freezes position`() {
        val clock = PlaybackClock()
        clock.onPositionReport(10_000, ms(0))
        clock.setPaused(true, ms(500))

        assertEquals(10_500, clock.positionAt(ms(2000)))

        clock.setPaused(false, ms(3000))
        assertEquals(11_500, clock.positionAt(ms(4000)))
    }

    @Test
    fun `small report deviation is slewed smoothly`() {
        val clock = PlaybackClock()
        clock.onPositionReport(10_000, ms(0))
        // 报告比外推快 300ms (< snap 阈值)
        clock.onPositionReport(10_300, ms(0))

        // 每帧位置仍然单调平滑: 增量在 1x 速度附近, 且不会一次跳 300ms
        var last = clock.positionAt(0L)!!
        var t = 0L
        repeat(390) { // 6.5s; 300ms 偏差按 6%/s 的消化速率需要 5s
            t += FRAME
            val now = clock.positionAt(t)!!
            val delta = now - last
            assertTrue(delta >= 0, "position must be monotonic")
            assertTrue(delta < 40, "single-frame jump of ${delta}ms is visible")
            last = now
        }
        val expected = 10_300 + t / 1_000_000
        assertTrue((last - expected).absoluteValue < 50, "drift ${last - expected}ms not converged")
    }

    @Test
    fun `large deviation snaps immediately`() {
        val clock = PlaybackClock()
        clock.onPositionReport(10_000, ms(0))
        clock.onPositionReport(60_000, ms(0)) // seek

        assertEquals(60_000, clock.positionAt(ms(0)))
        assertEquals(61_000, clock.positionAt(ms(1000)))
    }

    @Test
    fun `backward seek snaps immediately`() {
        val clock = PlaybackClock()
        clock.onPositionReport(60_000, ms(0))
        clock.onPositionReport(10_000, ms(100))

        assertEquals(10_000, clock.positionAt(ms(100)))
    }
}
