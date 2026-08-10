/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameTimeSmootherTest {
    private companion object {
        const val PERIOD_60HZ = 16_666_667L
    }

    @Test
    fun `constant deltas pass through unchanged`() {
        val smoother = FrameTimeSmoother()
        repeat(1000) {
            val out = smoother.smooth(PERIOD_60HZ)
            assertTrue(
                (out - PERIOD_60HZ).absoluteValue <= PERIOD_60HZ / 100,
                "output $out deviates from steady input $PERIOD_60HZ",
            )
        }
    }

    @Test
    fun `non-positive deltas produce zero`() {
        val smoother = FrameTimeSmoother()
        assertEquals(0, smoother.smooth(0))
        assertEquals(0, smoother.smooth(-5_000_000))
    }

    @Test
    fun `jittered deltas are smoothed while preserving total time`() {
        val smoother = FrameTimeSmoother()
        // 模拟桌面端 EDT 调度抖动: 真实上屏是稳定 60Hz, 但回调时间戳在 12ms 和 21.3ms 之间交替
        val inputs = LongArray(2000) { if (it % 2 == 0) 12_000_000L else 21_333_334L }

        var totalIn = 0L
        var totalOut = 0L
        val outputsAfterWarmup = mutableListOf<Long>()
        inputs.forEachIndexed { i, input ->
            val out = smoother.smooth(input)
            totalIn += input
            totalOut += out
            if (i >= 100) outputsAfterWarmup += out
        }

        // 输出的帧间隔应当稳定在平均周期附近, 波动远小于输入的 ±4.6ms
        val mean = outputsAfterWarmup.average()
        val maxDeviation = outputsAfterWarmup.maxOf { (it - mean).absoluteValue }
        assertTrue(
            maxDeviation < 2_000_000f,
            "max deviation ${maxDeviation / 1e6}ms should be far smaller than input jitter (4.6ms)",
        )

        // 长期累计时间不能漂移
        assertTrue(
            (totalOut - totalIn).absoluteValue < 100_000_000L,
            "accumulated drift ${(totalOut - totalIn) / 1e6}ms too large",
        )
    }

    @Test
    fun `dropped frame is amortized instead of lurching`() {
        val smoother = FrameTimeSmoother()
        repeat(100) { smoother.smooth(PERIOD_60HZ) }

        // 丢一帧: 间隔翻倍. 弹幕不能瞬间前跳一整帧, 而应在随后的帧内平滑追上
        val out = smoother.smooth(PERIOD_60HZ * 2)
        assertTrue(
            out < PERIOD_60HZ * 1.3,
            "single-frame advance ${out / 1e6}ms must be amortized, not a lurch",
        )

        // 随后的稳定帧内把亏欠的时间补齐
        var deficit = PERIOD_60HZ * 2 - out
        repeat(100) {
            deficit += PERIOD_60HZ - smoother.smooth(PERIOD_60HZ)
        }
        assertTrue(
            deficit.absoluteValue < 2_000_000L,
            "deficit ${deficit / 1e6}ms should be repaid within following frames",
        )
    }

    @Test
    fun `long gaps snap through unchanged`() {
        val smoother = FrameTimeSmoother()
        repeat(100) { smoother.smooth(PERIOD_60HZ) }

        // 窗口遮挡/暂停恢复等真实长间隔直接透传, 不做平滑
        val gap = 500_000_000L
        assertEquals(gap, smoother.smooth(gap))

        // 长间隔之后恢复正常
        repeat(10) { smoother.smooth(PERIOD_60HZ) }
        val out = smoother.smooth(PERIOD_60HZ)
        assertTrue((out - PERIOD_60HZ).absoluteValue <= PERIOD_60HZ / 10)
    }

    @Test
    fun `adapts to different refresh rates`() {
        val smoother = FrameTimeSmoother()
        val period120 = 8_333_333L
        repeat(1000) { smoother.smooth(period120) }
        val out = smoother.smooth(period120)
        assertTrue(
            (out - period120).absoluteValue <= period120 / 100,
            "should converge to 120Hz period, got ${out / 1e6}ms",
        )
    }
}
