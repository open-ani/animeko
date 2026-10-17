/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow.anim

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import kotlin.time.Duration

/**
 * 插值器. 决定两个关键帧之间怎么过渡.
 */
fun interface Interpolator<T> {
    fun interpolate(from: T, to: T, fraction: Float): T
}

object Interpolators {
    val Float: Interpolator<kotlin.Float> = Interpolator { a, b, f -> a + (b - a) * f }

    private val StepAny: Interpolator<Any?> = Interpolator { a, _, _ -> a }

    /**
     * 阶跃: 不做过渡, 到下一个关键帧的时刻才切换. 用于枚举、布尔这类离散量.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> step(): Interpolator<T> = StepAny as Interpolator<T>
}

/**
 * 一个关键帧.
 *
 * [easing] 作用于 **本帧到下一帧** 的那一段, 与 CSS `animation-timing-function` 写在关键帧上的语义一致.
 */
@Immutable
data class Keyframe<T>(
    val time: Duration,
    val value: T,
    val easing: Easing = Easings.Linear,
)

internal fun <T> sampleKeyframes(
    keys: List<Keyframe<T>>,
    interpolator: Interpolator<T>,
    time: Duration,
): T {
    if (time <= keys.first().time) return keys.first().value
    if (time >= keys.last().time) return keys.last().value

    var lo = 0
    var hi = keys.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (keys[mid].time <= time) lo = mid else hi = mid - 1
    }
    val from = keys[lo]
    val to = keys.getOrNull(lo + 1) ?: return from.value
    val span = (to.time - from.time).inWholeMicroseconds
    if (span <= 0L) return to.value
    val raw = (time - from.time).inWholeMicroseconds.toFloat() / span
    return interpolator.interpolate(from.value, to.value, from.easing.transform(raw))
}

/**
 * 一条属性轨道: 按绝对时间排好的关键帧序列, 可以在任意时刻采样.
 *
 * 轨道两端做钳制 —— 第一帧之前恒为第一帧的值, 最后一帧之后恒为最后一帧的值.
 * 因此"这个单元在某段时间里什么都不做"不需要显式写帧.
 */
@Immutable
class Track<T> internal constructor(
    private val keys: List<Keyframe<T>>,
    private val interpolator: Interpolator<T>,
) {
    init {
        require(keys.isNotEmpty()) { "Track must have at least one keyframe" }
    }

    val start: Duration get() = keys.first().time
    val end: Duration get() = keys.last().time
    val keyframes: List<Keyframe<T>> get() = keys

    fun valueAt(time: Duration): T = sampleKeyframes(keys, interpolator, time)

    companion object {
        fun <T> constant(value: T): Track<T> =
            Track(listOf(Keyframe(Duration.ZERO, value)), Interpolators.step())
    }
}

/**
 * 轨道构造器.
 *
 * 写入语义是 **截断覆盖**: 在 [time] 打帧时, 时间在它之后的帧会被丢掉.
 * 这样"先排好一段将来的动作, 到时候再中途叫停"能自然表达 ——
 * 计时器就是这么做的: 起转时先按走满一圈排好, 真拦到了再在当时的位置把它钉住.
 */
class TrackBuilder<T> internal constructor(
    initial: T,
    private val interpolator: Interpolator<T>,
) {
    private val keys = mutableListOf(Keyframe(Duration.ZERO, initial))

    /** 采样构造中的轨道. 截断覆盖之后仍然准确. */
    fun valueAt(time: Duration): T = sampleKeyframes(keys, interpolator, time)

    /** 最后一帧的时刻. */
    val lastTime: Duration get() = keys.last().time

    /**
     * 在 [time] 打一个关键帧, 并丢掉它之后的所有帧.
     */
    fun key(time: Duration, value: T, easing: Easing = Easings.Linear): TrackBuilder<T> {
        require(time >= Duration.ZERO) { "keyframe time must not be negative: $time" }
        while (keys.size > 1 && keys.last().time > time) {
            keys.removeAt(keys.lastIndex)
        }
        val last = keys.last()
        if (last.time == time) {
            keys[keys.lastIndex] = Keyframe(time, value, easing)
        } else {
            keys += Keyframe(time, value, easing)
        }
        return this
    }

    /**
     * 从 [from] 起用 [duration] 过渡到 [value]. 起点取 [from] 时刻的真实取值, 所以不会把之前的动作提前掐掉.
     */
    fun ramp(
        from: Duration,
        duration: Duration,
        value: T,
        easing: Easing = Easings.Standard,
    ): TrackBuilder<T> {
        val at = valueAt(from)
        key(from, at, easing)
        key(from + duration, value)
        return this
    }

    internal fun build(): Track<T> = Track(keys.toList(), interpolator)
}

internal fun floatTrack(initial: Float) = TrackBuilder(initial, Interpolators.Float)

internal fun <T> stepTrack(initial: T) = TrackBuilder(initial, Interpolators.step())
