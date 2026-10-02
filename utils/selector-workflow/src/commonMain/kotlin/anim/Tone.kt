/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow.anim

import kotlin.time.Duration

/**
 * 一路"语义色".
 *
 * 语义色本身是离散的 (`Idle` -> `Hit`), 颜色却该连续地过渡过去, 不然会"啪"地跳一下.
 * 这里用三条轨道把两者接上: 目标色、切换之前的那个色, 以及两者之间的过渡进度. 画的时候把前两个
 * 各映射成颜色再按进度插值 —— 状态层依旧只谈语义, 颜色的连续性交给这一层, 调色板不必知道时间.
 *
 * 过渡走完之后 [previous] 会被拨到与 [current] 一致. 有了这个不变式, [blend] 那条 float 轨道就可以
 * 随便从上一帧插值过来 (它躲不开 `Storyboard` 里那条 `ramp` 规矩): 反正过渡之外的时段两端同色,
 * 插值插到哪一档取出来都是同一个颜色.
 */
internal class ToneChannel<T>(initial: T) {
    val current = stepTrack(initial)
    val previous = stepTrack(initial)
    val blend = floatTrack(1f)

    /** 从 [at] 起, 花 [over] 过渡到 [to]. */
    fun shift(to: T, at: Duration, over: Duration) {
        previous.key(at, current.valueAt(at))
        current.key(at, to)
        blend.key(at, 0f)
        blend.key(at + over, 1f)
        previous.key(at + over, to)
    }

    /** 直接切过去, 不做过渡. 留给"这会儿它本来就看不见"的那些复位. */
    fun snap(to: T, at: Duration) {
        previous.key(at, to)
        current.key(at, to)
        blend.key(at, 1f)
    }

    fun build(): ToneTracks<T> = ToneTracks(current.build(), previous.build(), blend.build())
}

/** [ToneChannel] 编译出来的采样端. */
internal class ToneTracks<T>(
    private val current: Track<T>,
    private val previous: Track<T>,
    private val blend: Track<Float>,
) {
    fun current(t: Duration): T = current.valueAt(t)
    fun previous(t: Duration): T = previous.valueAt(t)
    fun blend(t: Duration): Float = blend.valueAt(t)
}
