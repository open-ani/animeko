/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [resolveFocusRepeatedly] 的轮询语义: 到位短路 (不再多发 requestFocus 抢用户焦点)、
 * 用户导航放弃、次数耗尽.
 */
class ResolveFocusRepeatedlyTest {

    /** 立即出帧的时钟 (真实现挂帧节拍, 测试无 UI 帧源). */
    private class ImmediateFrameClock : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
            onFrame(0L)
    }

    private fun runFrameTest(block: suspend () -> Unit) = runTest {
        withContext(ImmediateFrameClock()) { block() }
    }

    @Test
    fun `already arrived short-circuits without any attempt`() = runFrameTest {
        var attempts = 0
        val ok = resolveFocusRepeatedly(arrived = { true }) { attempts++ }
        assertTrue(ok)
        assertEquals(0, attempts)
    }

    @Test
    fun `abandon stops polling before attempting`() = runFrameTest {
        var attempts = 0
        val ok = resolveFocusRepeatedly(arrived = { false }, abandon = { true }) { attempts++ }
        assertFalse(ok)
        assertEquals(0, attempts)
    }

    @Test
    fun `arrival after some attempts succeeds and stops`() = runFrameTest {
        var attempts = 0
        val ok = resolveFocusRepeatedly(arrived = { attempts >= 3 }) { attempts++ }
        assertTrue(ok)
        assertEquals(3, attempts)
    }

    @Test
    fun `abandon mid-flight stops immediately`() = runFrameTest {
        var attempts = 0
        val ok = resolveFocusRepeatedly(
            arrived = { false },
            abandon = { attempts >= 2 },
        ) { attempts++ }
        assertFalse(ok)
        assertEquals(2, attempts)
    }

    @Test
    fun `exhausting attempts fails`() = runFrameTest {
        var attempts = 0
        val ok = resolveFocusRepeatedly(attempts = 5, arrived = { false }) { attempts++ }
        assertFalse(ok)
        assertEquals(5, attempts)
    }
}
