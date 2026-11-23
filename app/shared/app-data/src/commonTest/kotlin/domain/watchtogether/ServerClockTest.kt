package me.him188.ani.app.domain.watchtogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerClockTest {
    @Test
    fun `uses fastest midpoint despite jitter and asymmetric queueing`() {
        val clock = ServerClock { 10_000L }
        assertNull(clock.latestSampleAgeMillis())
        assertTrue(clock.recordSample(10_050, 9_900, 10_000))
        assertEquals(100.0, clock.currentOffsetMillis())
        // Most of this request's RTT is queued in one direction.
        clock.recordSample(10_090, 9_200, 10_000)
        assertEquals(100.0, clock.currentOffsetMillis())
        clock.recordSample(10_090, 9_980, 10_000)
        assertEquals(100.0, clock.currentOffsetMillis())
        assertEquals(10_100L, clock.now())
        assertEquals(0L, clock.latestSampleAgeMillis())
    }

    @Test
    fun `evicts fastest sample after sixteen newer samples`() {
        var now = 10_000L
        val clock = ServerClock { now }
        clock.recordSample(10_100, now, now)
        repeat(16) {
            now += 100
            clock.recordSample(now + 150, now - 100, now)
        }
        assertEquals(200.0, clock.currentOffsetMillis())
    }

    @Test
    fun `retains the offset after all samples are older than ten minutes`() {
        var now = 10_000L
        val clock = ServerClock { now }
        clock.recordSample(17_000, now, now)
        now += 60_001
        assertEquals(60_001L, clock.latestSampleAgeMillis())
        now += 540_000
        assertEquals(600_001L, clock.latestSampleAgeMillis())
        assertEquals(now + 7_000L, clock.now())
        assertEquals(7_000.0, clock.currentOffsetMillis())
    }

    @Test
    fun `rejects slow and negative RTT without refreshing age`() {
        val clock = ServerClock { 1_000L }
        assertFalse(clock.recordSample(10_000, 0, 2_001))
        assertFalse(clock.recordSample(10_000, 10, 9))
        assertNull(clock.latestSampleAgeMillis())
        assertEquals(0.0, clock.currentOffsetMillis())
    }
}
