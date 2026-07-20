package me.him188.ani.app.domain.watchtogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerClockTest {
    @Test
    fun `uses midpoint and smooths accepted samples`() {
        var localNow = 1_000L
        val clock = ServerClock { localNow }

        assertTrue(clock.recordSample(serverTimeMillis = 2_050L, sentAtMillis = 900L, receivedAtMillis = 1_000L))
        assertEquals(1_100.0, clock.currentOffsetMillis())
        assertEquals(2_100L, clock.now())

        assertTrue(clock.recordSample(serverTimeMillis = 2_950L, sentAtMillis = 1_900L, receivedAtMillis = 2_000L))
        assertEquals(1_080.0, clock.currentOffsetMillis())
    }

    @Test
    fun `rejects slow samples`() {
        val clock = ServerClock { 1_000L }
        assertFalse(clock.recordSample(10_000L, 0L, 2_001L))
        assertEquals(0.0, clock.currentOffsetMillis())
    }
}
