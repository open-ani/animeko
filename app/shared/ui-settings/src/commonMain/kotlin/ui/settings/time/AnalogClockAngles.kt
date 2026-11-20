/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

/*
 * 表盘指针角度: 12 点方向为 0 度, 顺时针递增, 一圈 360 度.
 *
 * 目标角度都落在 0 到 360 度之间; 补间动画使用的角度由 forwardHandAngle 与 nearestHandAngle
 * 相对当前角度换算, 因此不会倒退, 也不会绕远路.
 */

private const val FULL_TURN = 360f
private const val DEGREES_PER_SECOND = 6f
private const val DEGREES_PER_MINUTE = 6f
private const val DEGREES_PER_HOUR = 30f
private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * 秒针的目标角度, 含 [nanosecond] 表示的不足一秒的偏移.
 */
internal fun secondHandTargetAngle(second: Int, nanosecond: Int): Float =
    second * DEGREES_PER_SECOND + nanosecond / NANOS_PER_SECOND * DEGREES_PER_SECOND

/**
 * 分针的目标角度, 含 [second] 表示的不足一分钟的偏移.
 */
internal fun minuteHandTargetAngle(minute: Int, second: Int): Float =
    minute * DEGREES_PER_MINUTE + second * (DEGREES_PER_MINUTE / 60f)

/**
 * 时针的目标角度, 含 [minute] 表示的不足一小时的偏移.
 */
internal fun hourHandTargetAngle(hour: Int, minute: Int): Float =
    (hour % 12) * DEGREES_PER_HOUR + minute * (DEGREES_PER_HOUR / 60f)

/**
 * 从 [current] 顺时针转到与 [target] 同角度的最近位置, 结果不小于 [current].
 *
 * 秒针只向前走: 59 秒到 0 秒的跳变只前进一格, 不会倒扫一整圈.
 */
internal fun forwardHandAngle(current: Float, target: Float): Float =
    current + (target - current).floorMod(FULL_TURN)

/**
 * 从 [current] 转到与 [target] 同角度的最近位置, 位移不超过半圈.
 *
 * 时/分针用它: 时间跳变时沿最短弧转向新角度, 不会多转整圈.
 */
internal fun nearestHandAngle(current: Float, target: Float): Float {
    val forward = (target - current).floorMod(FULL_TURN)
    return current + if (forward > FULL_TURN / 2f) forward - FULL_TURN else forward
}

private fun Float.floorMod(divisor: Float): Float = ((this % divisor) + divisor) % divisor
