/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.watchtogether

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackAutomationGateTest {
    @Test
    fun `transient suppression does not clear watch together suppression`() = runTest {
        val gate = PlaybackAutomationGate()
        gate.setSuppressed(true)
        assertFalse(gate.transientlySuppressed)

        gate.suppressDuring {
            assertTrue(gate.suppressed.value)
            assertTrue(gate.transientlySuppressed)
        }

        assertTrue(gate.suppressed.value)
        assertFalse(gate.transientlySuppressed)
        gate.setSuppressed(false)
        assertFalse(gate.suppressed.value)
    }

    @Test
    fun `nested transient suppression remains active until the outer operation completes`() = runTest {
        val gate = PlaybackAutomationGate()

        gate.suppressDuring {
            assertTrue(gate.transientlySuppressed)
            gate.suppressDuring {
                assertTrue(gate.suppressed.value)
                assertTrue(gate.transientlySuppressed)
            }
            assertTrue(gate.suppressed.value)
            assertTrue(gate.transientlySuppressed)
        }

        assertFalse(gate.suppressed.value)
        assertFalse(gate.transientlySuppressed)
    }
}
