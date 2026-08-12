/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

/**
 * 把 `withFrameNanos` 回调时间戳之间的间隔平滑成接近真实显示节拍的间隔.
 *
 * ## 为什么需要
 *
 * 在桌面端 (Skiko), 帧回调的时间戳是渲染 tick 在 EDT 上开始执行时采样的 `System.nanoTime()`,
 * 而不是这一帧实际上屏 (vsync) 的时间. 回调时刻受线程调度影响抖动很大 (例如 12ms, 21ms, 14ms, ...),
 * 但画面上屏间隔是均匀的. 如果直接用原始间隔积分弹幕位置, 弹幕的速度会随测量噪声振荡,
 * 在匀速滚动中表现为肉眼可见的左右顿挫. (Android 的 Choreographer 提供 vsync 对齐时间戳, 无此问题.)
 *
 * ## 算法
 *
 * 一个简化的锁相环 (PLL):
 * 1. 用受钳制的 EMA 估计显示刷新周期 [periodNanos];
 * 2. 每帧默认推进一个周期, 并维护相位误差 [errorNanos] (真实累计时间与平滑后累计时间之差);
 * 3. 误差以小增益 [correctionGain] 回授, 且单帧修正量不超过周期的 [maxCorrectionRatio],
 *    因此掉帧造成的时间亏欠会在随后若干帧内平滑补齐, 而不是瞬间前跳;
 * 4. 超过 [snapThresholdNanos] 的间隔视为真实长间隔 (暂停恢复/窗口遮挡/严重卡顿), 不平滑, 直接跟上真实时间.
 *
 * 因此输出的间隔几乎恒定, 同时长期累计与真实时间保持同步 (误差有界).
 *
 * 此类不是线程安全的, 应仅在 UI 帧循环中使用.
 */
internal class FrameTimeSmoother(
    /**
     * 刷新周期 EMA 的平滑系数.
     */
    private val periodSmoothingFactor: Float = 1f / 32f,
    /**
     * 相位误差的回授增益.
     */
    private val correctionGain: Float = 0.1f,
    /**
     * 单帧最大修正量, 相对于刷新周期的比例.
     */
    private val maxCorrectionRatio: Float = 0.2f,
    /**
     * 超过此间隔的帧视为真实长间隔, 直接透传.
     */
    private val snapThresholdNanos: Long = 250_000_000L,
) {
    /**
     * 显示刷新周期的估计值. `0` 表示还未初始化.
     */
    private var periodNanos: Float = 0f

    /**
     * 相位误差: 真实累计时间 - 平滑后累计时间. 有界.
     */
    private var errorNanos: Float = 0f

    /**
     * 输入原始帧间隔, 返回应当用于推进动画时钟的平滑间隔. 单位均为纳秒.
     */
    fun smooth(rawDeltaNanos: Long): Long {
        if (rawDeltaNanos <= 0) return 0

        if (rawDeltaNanos >= snapThresholdNanos) {
            errorNanos = 0f
            return rawDeltaNanos
        }

        if (periodNanos == 0f) {
            periodNanos = rawDeltaNanos.toFloat().coerceIn(MIN_PERIOD_NANOS, MAX_PERIOD_NANOS)
            return rawDeltaNanos
        }

        // 钳制到当前估计值附近再做 EMA, 防止个别掉帧的极端值把周期估计带偏.
        val clampedDelta = rawDeltaNanos.toFloat()
            .coerceIn(periodNanos / 2f, periodNanos * 2f)
            .coerceIn(MIN_PERIOD_NANOS, MAX_PERIOD_NANOS)
        periodNanos += (clampedDelta - periodNanos) * periodSmoothingFactor

        errorNanos += rawDeltaNanos - periodNanos
        val maxCorrection = periodNanos * maxCorrectionRatio
        val correction = (errorNanos * correctionGain).coerceIn(-maxCorrection, maxCorrection)
        errorNanos -= correction

        return (periodNanos + correction).toLong().coerceAtLeast(0L)
    }

    private companion object {
        // 认为合理的刷新周期范围: 20Hz..500Hz
        const val MIN_PERIOD_NANOS = 2_000_000f
        const val MAX_PERIOD_NANOS = 50_000_000f
    }
}
