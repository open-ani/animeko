/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow.anim

import androidx.compose.runtime.Immutable

/**
 * 缓动曲线. 输入输出都是 0..1.
 *
 * 这里自带一份实现而不是直接用 `androidx.compose.animation.core.Easing`, 是为了让数据层可以脱离 Compose 单测.
 */
@Immutable
fun interface Easing {
    fun transform(fraction: Float): Float
}

/**
 * Material 3 的标准缓动曲线, 数值取自 M3 motion spec.
 */
object Easings {
    val Linear = Easing { it }

    /** M3 standard. 一般的位置/尺寸变化. */
    val Standard = cubicBezier(0.2f, 0f, 0f, 1f)

    /** M3 emphasized. 大幅度、需要被注意到的移动, 例如列表滚动. */
    val Emphasized = cubicBezier(0.2f, 0f, 0f, 1f)

    /** M3 emphasized decelerate. 进场、弹一下再落定. */
    val EmphasizedDecelerate = cubicBezier(0.05f, 0.7f, 0.1f, 1f)

    /** 遍历 cursor 在格子之间移动用的曲线: 起步快, 收尾稳. */
    val CursorHop = cubicBezier(0.25f, 0f, 0.15f, 1f)

    /**
     * 三次贝塞尔缓动. 与 CSS `cubic-bezier(x1, y1, x2, y2)` 同义.
     */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): Easing = CubicBezierEasing(x1, y1, x2, y2)
}

private class CubicBezierEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float,
) : Easing {
    override fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        // 牛顿迭代求出使 x(t) == fraction 的参数 t, 再取 y(t)
        var t = fraction
        repeat(NEWTON_ITERATIONS) {
            val dx = curve(t, x1, x2) - fraction
            if (dx > -EPS && dx < EPS) return curve(t, y1, y2)
            val slope = derivative(t, x1, x2)
            if (slope > -EPS && slope < EPS) return curve(t, y1, y2)
            t -= dx / slope
            if (t < 0f) t = 0f
            if (t > 1f) t = 1f
        }
        return curve(t, y1, y2)
    }

    private fun curve(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
    }

    private fun derivative(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * a + 6f * u * t * (b - a) + 3f * t * t * (1f - b)
    }

    private companion object {
        const val NEWTON_ITERATIONS = 8
        const val EPS = 1e-5f
    }
}
