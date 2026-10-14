/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.tracking.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class PendingLoginGateTest {
    private val time = TestTimeSource()
    private val gate = PendingLoginGate(window = 5.minutes, timeSource = time)

    @Test
    fun rejectsRedirectWithoutStartedLogin() {
        assertFalse(gate.consume())
    }

    @Test
    fun acceptsOneRedirectWithinWindow() {
        gate.begin()
        time += 4.minutes
        assertTrue(gate.consume())
        assertFalse(gate.consume())
    }

    @Test
    fun rejectsRedirectAfterWindow() {
        gate.begin()
        time += 5.minutes + 1.seconds
        assertFalse(gate.consume())
    }

    @Test
    fun restartingLoginResetsWindow() {
        gate.begin()
        time += 4.minutes
        gate.begin()
        time += 4.minutes
        assertTrue(gate.consume())
    }
}
