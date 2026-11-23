/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalogClockAnglesTest {
    @Test
    fun `second hand advances when it wraps past the top of the dial`() {
        val lastSecond = secondHandTargetAngle(second = 59, nanosecond = 0)
        val firstSecond = secondHandTargetAngle(second = 0, nanosecond = 0)

        val advanced = forwardHandAngle(lastSecond, firstSecond)

        assertTrue(advanced > lastSecond, "second hand must not recede: $lastSecond -> $advanced")
        assertEquals(lastSecond + 6f, advanced, TOLERANCE)
    }

    @Test
    fun `unwrapped second hand angle is strictly increasing for ten ticks`() {
        var angle = secondHandTargetAngle(second = 55, nanosecond = 0)
        val deltas = mutableListOf<Float>()
        for (step in 1..10) {
            val next = forwardHandAngle(angle, secondHandTargetAngle(second = (55 + step) % 60, nanosecond = 0))
            deltas += next - angle
            angle = next
        }

        assertEquals(10, deltas.size)
        for ((index, delta) in deltas.withIndex()) {
            assertTrue(delta > 0f, "tick $index must advance, moved $delta")
            assertEquals(6f, delta, TOLERANCE)
        }
    }

    @Test
    fun `second hand target carries the fraction of the current second`() {
        assertEquals(0f, secondHandTargetAngle(second = 0, nanosecond = 0), TOLERANCE)
        assertEquals(3f, secondHandTargetAngle(second = 0, nanosecond = 500_000_000), TOLERANCE)
        assertEquals(354f, secondHandTargetAngle(second = 59, nanosecond = 0), TOLERANCE)
    }

    @Test
    fun `hour and minute hands move at most half a turn on a time jump`() {
        val hourBefore = hourHandTargetAngle(hour = 11, minute = 0)
        val hourAfter = hourHandTargetAngle(hour = 1, minute = 0)
        assertEquals(60f, nearestHandAngle(hourBefore, hourAfter) - hourBefore, TOLERANCE)

        var hourAngle = hourHandTargetAngle(hour = 0, minute = 0)
        for (hour in 0 until 12) {
            for (minute in 0..59 step 7) {
                val target = hourHandTargetAngle(hour = hour, minute = minute)
                val next = nearestHandAngle(hourAngle, target)
                assertTrue(abs(next - hourAngle) <= 180f, "hour hand moved $hourAngle -> $next")
                assertEquals(target, next.normalized(), TOLERANCE)
                hourAngle = next
            }
        }
    }

    @Test
    fun `minute hand does not spin a full turn when the minute wraps`() {
        val before = minuteHandTargetAngle(minute = 50, second = 0)
        val after = minuteHandTargetAngle(minute = 5, second = 0)

        assertEquals(90f, nearestHandAngle(before, after) - before, TOLERANCE)
    }

    private fun Float.normalized(): Float = ((this % 360f) + 360f) % 360f

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
