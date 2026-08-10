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

/**
 * 把播放器粗粒度、跳跃式的进度报告变成每帧连续平滑的视频时间估计.
 *
 * 播放器的 `currentPositionMillis` 通常以数百毫秒的间隔跳变式更新, 直接用它驱动弹幕会一顿一顿.
 * 本类在两次报告之间用帧时钟外推 (`基准位置 + 经过的墙钟时间 × 倍速`), 并把报告与外推的偏差
 * 以有界速率缓慢消化 ([maxSlewRate]), 超过 [snapThresholdMillis] 的偏差视为 seek, 立即重定基准.
 *
 * 所有 `frameTimeNanos` 参数应来自同一个单调帧时钟 (例如 PLL 平滑后的帧时间).
 * 此类不是线程安全的, 应仅在主线程使用.
 */
internal class PlaybackClock(
    /**
     * 偏差超过此值视为 seek, 立即跳变到报告位置.
     */
    private val snapThresholdMillis: Long = 1000,
    /**
     * 消化小偏差的最大速率, 相对于经过的墙钟时间的比例.
     * 0.06 表示每经过 1s 墙钟时间最多校正 60ms.
     */
    private val maxSlewRate: Float = 0.06f,
) {
    private var initialized = false

    /** 基准视频位置, 单位 ms */
    private var basePositionMillis: Double = 0.0

    /** 基准对应的帧时刻, 单位 ns */
    private var baseFrameTimeNanos: Long = 0

    /** 尚未消化的报告偏差, 单位 ms */
    private var pendingErrorMillis: Double = 0.0

    var playbackSpeed: Float = 1f
        private set

    var paused: Boolean = false
        private set

    /**
     * 当前估计的视频位置, 单位 ms. 未收到任何进度报告时返回 `null`.
     *
     * 调用后基准会推进到 [frameTimeNanos], 因此对同一帧应只调用一次并复用返回值.
     */
    fun positionAt(frameTimeNanos: Long): Long? {
        if (!initialized) return null
        advanceTo(frameTimeNanos)
        return basePositionMillis.toLong()
    }

    /**
     * 播放器报告当前进度.
     */
    fun onPositionReport(positionMillis: Long, frameTimeNanos: Long) {
        if (!initialized) {
            initialized = true
            basePositionMillis = positionMillis.toDouble()
            baseFrameTimeNanos = frameTimeNanos
            pendingErrorMillis = 0.0
            return
        }
        advanceTo(frameTimeNanos)
        val errorMillis = positionMillis - basePositionMillis
        if (errorMillis.absoluteValue >= snapThresholdMillis) {
            // seek: 立即跳变
            basePositionMillis = positionMillis.toDouble()
            pendingErrorMillis = 0.0
        } else {
            pendingErrorMillis = errorMillis
        }
    }

    fun setPlaybackSpeed(speed: Float, frameTimeNanos: Long) {
        require(speed > 0f) { "playbackSpeed must be positive, got $speed" }
        advanceTo(frameTimeNanos)
        playbackSpeed = speed
    }

    fun setPaused(paused: Boolean, frameTimeNanos: Long) {
        advanceTo(frameTimeNanos)
        this.paused = paused
    }

    private fun advanceTo(frameTimeNanos: Long) {
        val elapsedNanos = frameTimeNanos - baseFrameTimeNanos
        baseFrameTimeNanos = frameTimeNanos
        if (elapsedNanos <= 0) return

        val elapsedMillis = elapsedNanos / 1_000_000.0
        if (!paused) {
            basePositionMillis += elapsedMillis * playbackSpeed
        }

        // 以有界速率消化报告偏差, 避免可见的跳变
        if (pendingErrorMillis != 0.0) {
            val maxStep = elapsedMillis * maxSlewRate
            val step = pendingErrorMillis.coerceIn(-maxStep, maxStep)
            basePositionMillis += step
            pendingErrorMillis -= step
        }
    }
}
