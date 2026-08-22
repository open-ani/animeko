/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import me.him188.ani.utils.selectorworkflow.anim.Easings
import me.him188.ani.utils.selectorworkflow.anim.floatTrack
import me.him188.ani.utils.selectorworkflow.anim.stepTrack
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 分镜脚本 —— 这一层就是题目里说的 DSL.
 *
 * 剧本代码拿着它上面的 **单元句柄** 按拍子下指令, 写完 [build] 出一条可采样的时间线:
 * ```
 * sources[0].beginSearch()
 * linkOf(0).draw(over = latency)
 * advance(latency)
 * sources[0].settle()
 * chipsOf(0).forEach { it.appear() }
 * ```
 * 所有指令都落在当前时刻 [now] 上, 时间只由 [advance] / [at] 推动 ——
 * 于是剧本读起来就是一条时间轴, 不必在每个调用点重复算绝对时刻.
 *
 * 轨道写入是 **截断覆盖** 的 (见 `TrackBuilder`), 所以"先安排好将来的动作, 到时候再中途叫停"
 * 可以直接写出来, 计时器和被取消的 cursor 都靠这一点.
 *
 * 一条规矩: 凡是"用一段时间过渡到某个值"的动作, 一律走 `TrackBuilder.ramp`, 不要直接
 * `key(now + over, x)` —— 后者会让轨道从 **上一帧** 一路插值到这个远期目标, 于是那条线在
 * 整段演出里都在慢慢缩回去, 而取值又始终落在合法区间里, 单看数值发现不了.
 */
class Storyboard internal constructor(
    val config: SelectorWorkflowConfig,
) {
    private val pacing get() = config.pacing

    /** 当前时刻 (动画时间). */
    var now: Duration = Duration.ZERO
        private set

    /** 脚本里出现过的最晚时刻, 用来定循环长度. */
    private var high: Duration = Duration.ZERO

    private val phaseTrack = stepTrack("idle")

    val sources: List<SourceHandle> = config.sources.indices.map { SourceHandle(it) }
    private val sourceLinks: List<LineHandle> = config.sources.indices.map { LineHandle() }
    val chips: List<ChipHandle> = config.results.mapIndexed { cell, key -> ChipHandle(key, cell) }
    val ripples: List<RippleHandle> = config.candidates.map {
        RippleHandle(RippleTarget.Result, config.cellOf(it))
    }

    /** 第三步命中那一行的涟漪. 与第二步选中时是同一个单元. */
    val requestRipple = RippleHandle(RippleTarget.RequestRow, config.resolve.hitRow)
    val handoff = LineHandle()
    val window = WindowHandle()
    val requestList = RequestListHandle()
    val clocks: Map<ClockId, ClockHandle> = ClockId.entries.associateWith { ClockHandle(it) }

    private val cursorHandles = LinkedHashMap<String, CursorHandle>()

    /**
     * 取一个 cursor 句柄. [owner] 为 `null` 表示"等待全部"模式下那个中性色的全局 cursor.
     * 同一个 id 在一轮里可以被反复启用 —— 每次 [CursorHandle.enter] 都是一次新的活动区间.
     */
    fun cursor(id: String, owner: Int? = null): CursorHandle =
        cursorHandles.getOrPut(id) { CursorHandle(id, owner) }

    /** 第 [index] 个源的连线. */
    fun linkOf(index: Int): LineHandle = sourceLinks[index]

    /** 该源给出的全部结果. */
    fun chipsOf(source: Int): List<ChipHandle> = chips.filter { it.key.source == source }

    fun chipOf(key: ResultKey): ChipHandle = chips.first { it.key == key }

    fun rippleAt(cell: Int): RippleHandle = ripples.first { it.index == cell }

    // ------------------------------------------------------------------ 时间控制

    fun advance(by: Duration) {
        require(by >= Duration.ZERO) { "cannot advance backwards: $by" }
        now += by
        touch(now)
    }

    /**
     * 在 [time] 时刻执行 [block], 结束后把游标放回原处. 用来写"与主线并行发生"的动作.
     */
    fun at(time: Duration, block: Storyboard.() -> Unit) {
        val saved = now
        now = time
        touch(time)
        block()
        now = saved
    }

    /** 给当前这一拍起个名字, 便于调试与字幕. */
    fun phase(label: String) {
        phaseTrack.key(now, label)
    }

    private fun touch(time: Duration) {
        if (time > high) high = time
    }

    // ------------------------------------------------------------------ 单元 1: 数据源节点

    /** 数据源节点: 圆点 + 搜索中的脉冲光环. */
    inner class SourceHandle internal constructor(val index: Int) {
        internal val alpha = floatTrack(1f)
        internal val pulsing = stepTrack(false)

        val spec: SourceSpec get() = config.sources[index]

        /** 开始搜索: 光环转起来. */
        fun beginSearch() {
            alpha.key(now, 1f)
            pulsing.key(now, true)
            touch(now)
        }

        /** 搜完了: 光环停下, 圆点暗一档. */
        fun settle() {
            pulsing.key(now, false)
            alpha.ramp(now, pacing.fade, DIM_ALPHA)
            touch(now + pacing.fade)
        }

        /** 复位到"还没开始搜"的样子. */
        fun reset(over: Duration = pacing.reset) {
            pulsing.key(now, false)
            alpha.ramp(now, over, 1f)
            touch(now + over)
        }
    }

    // ------------------------------------------------------------------ 单元 2: 线

    /** 一条被画出来的线. 数据源连线与交棒线共用这一个单元. */
    inner class LineHandle internal constructor() {
        internal val progress = floatTrack(0f)
        internal val alpha = floatTrack(0f)

        /** 用 [over] 把线画满. 上一次画的还留着的话先瞬间抹掉, 不会从上一帧一路插值回 0. */
        fun draw(over: Duration) {
            progress.key(now, progress.valueAt(now))
            progress.key(now + SNAP, 0f, Easings.Linear)
            alpha.key(now, 0f)
            alpha.ramp(now, SNAP, 1f)
            progress.key(now + SNAP + over, 1f)
            touch(now + SNAP + over)
        }

        /** 画完之后退成背景线. */
        fun mute(over: Duration = pacing.fade) {
            alpha.ramp(now, over, MUTED_ALPHA)
            touch(now + over)
        }

        /** 收回去, 回到没画的状态. */
        fun retract(over: Duration = pacing.reset) {
            alpha.ramp(now, over, 0f)
            progress.ramp(now, over, 0f)
            touch(now + over)
        }
    }

    // ------------------------------------------------------------------ 单元 3: 结果块

    /** 一条搜索结果. */
    inner class ChipHandle internal constructor(val key: ResultKey, val cell: Int) {
        internal val alpha = floatTrack(0f)
        internal val tone = stepTrack(ChipTone.Source)
        internal val scale = floatTrack(1f)

        val candidate: Boolean get() = key.isCandidate(config)
        val priority: Boolean get() = config.showPriorityMarks && config.sources[key.source].priority

        /**
         * 淡入. [target] 小于 1 时直接淡入到那个亮度 —— 用于"选定之后才到的结果", 它们一出场就是落选的.
         */
        fun appear(over: Duration = pacing.fade, target: Float = 1f) {
            alpha.ramp(now, over, target)
            touch(now + over)
        }

        /** 被选中: 转绿 + 弹一下. */
        fun select() {
            tone.key(now, ChipTone.Selected)
            alpha.ramp(now, pacing.fade, 1f)
            scale.key(now, 1f, Easings.EmphasizedDecelerate)
            scale.key(now + pacing.pop * 0.4, SELECT_POP, Easings.EmphasizedDecelerate)
            scale.key(now + pacing.pop, 1f)
            touch(now + pacing.pop)
        }

        /** 这一条解析失败了: 转红. */
        fun fail(over: Duration = pacing.fade * 0.8) {
            tone.key(now + over, ChipTone.Failed)
            touch(now + over)
        }

        /** 落选, 暗下去. */
        fun mute(over: Duration = pacing.fade) {
            alpha.ramp(now, over, MUTED_ALPHA)
            touch(now + over)
        }

        /** 复位. */
        fun reset(over: Duration = pacing.reset) {
            alpha.ramp(now, over, 0f)
            tone.key(now + over, ChipTone.Source)
            scale.ramp(now, over, 1f)
            touch(now + over)
        }
    }

    // ------------------------------------------------------------------ 单元 4: 涟漪

    /** 选中 / 命中涟漪. */
    inner class RippleHandle internal constructor(val target: RippleTarget, val index: Int) {
        internal val scale = floatTrack(RIPPLE_FROM)
        internal val alpha = floatTrack(0f)

        fun pulse(over: Duration = pacing.ripple) {
            scale.key(now, RIPPLE_FROM)
            alpha.key(now, 0f)
            alpha.key(now + SNAP, RIPPLE_ALPHA)
            scale.key(now + over, RIPPLE_TO)
            alpha.key(now + over, 0f)
            touch(now + over)
        }
    }

    // ------------------------------------------------------------------ 单元 5: 遍历 cursor

    /** 遍历 cursor. */
    inner class CursorHandle internal constructor(val id: String, val owner: Int?) {
        internal val cell = floatTrack(0f)
        internal val alpha = floatTrack(0f)

        /** 落到某一格上. [peakAlpha] 小于 1 表示这是一趟"启动即作废"的虚示意. */
        fun enter(atCell: Int, peakAlpha: Float = 1f) {
            cell.key(now, atCell.toFloat())
            alpha.key(now, 0f)
            alpha.key(now + SNAP, peakAlpha)
            touch(now + SNAP)
        }

        /** 走到下一格. */
        fun step(toCell: Int, over: Duration = pacing.cursorStep) {
            cell.key(now, cell.valueAt(now), Easings.CursorHop)
            cell.key(now + over, toCell.toFloat())
            touch(now + over)
        }

        /** 退场. 命中之后、或者这一趟被取消时调用. */
        fun leave(over: Duration = pacing.cursorExit) {
            alpha.ramp(now, over, 0f)
            touch(now + over)
        }
    }

    // ------------------------------------------------------------------ 单元 6: 计时器

    /**
     * 计时器.
     *
     * 表盘一整圈就是 [start] 传进来的预算, 所以 [stop] 停在哪完全由 `已用 / 预算` 算出来,
     * 剧本里不出现任何角度常量.
     */
    inner class ClockHandle internal constructor(val id: ClockId) {
        internal val alpha = floatTrack(0f)
        internal val sweep = floatTrack(0f)
        internal val tone = stepTrack(ClockTone.Running)
        internal val overlay = floatTrack(0f)

        private var startedAt: Duration = Duration.ZERO
        private var window: Duration = Duration.ZERO

        /**
         * 起转. 转满一圈固定用 [Pacing.clockSweep] —— 与设置项配的秒数无关.
         *
         * 起转时先按 **走满一圈** 排好后续帧; 真提前停下时由 [stop] 截断覆写.
         */
        fun start() {
            startedAt = now
            window = pacing.clockSweep
            tone.key(now, ClockTone.Running)
            overlay.key(now, 0f)
            alpha.key(now, 0f)
            alpha.ramp(now, SNAP, 1f)
            sweep.key(now, 0f, Easings.Linear)
            sweep.key(now + window, 1f)
            touch(now + window)
        }

        /** 表盘走满一圈需要多久 (动画时间). */
        val fullSweepDuration: Duration get() = window

        /** 距离起转已经过了多久 (动画时间). */
        val elapsed: Duration get() = now - startedAt

        /**
         * 在当前时刻停住, 指针钉在 `已用 / 预算` 处.
         *
         * @return 指针停下的比例, 0..1.
         */
        fun stop(): Float {
            check(window > Duration.ZERO) { "clock ${id.name} was not started" }
            val used = (now - startedAt).coerceAtLeast(Duration.ZERO)
            val fraction = (used.inWholeMicroseconds.toFloat() / window.inWholeMicroseconds).coerceIn(0f, 1f)
            sweep.key(now, fraction)
            tone.key(now + pacing.fade * 0.5, ClockTone.Stopped)
            touch(now + pacing.fade)
            return fraction
        }

        /** 走满一圈, 判定超时. */
        fun expire() {
            sweep.key(now, 1f)
            tone.key(now + pacing.fade * 0.5, ClockTone.Expired)
            overlay.ramp(now, pacing.fade, TIMEOUT_OVERLAY)
            touch(now + pacing.fade)
        }

        /** 收起来并复位. 指针在收起来之前保持停下的位置, 不会一边淡出一边往回走. */
        fun hide(over: Duration = pacing.fade) {
            alpha.ramp(now, over, 0f)
            tone.key(now + over, ClockTone.Running)
            overlay.ramp(now, over, 0f)
            sweep.ramp(now, over, 0f)
            window = Duration.ZERO
            touch(now + over)
        }
    }

    // ------------------------------------------------------------------ 单元 7: 窗口

    /** WebView 窗口边框. */
    inner class WindowHandle internal constructor() {
        internal val tone = stepTrack(WindowTone.Closed)

        fun open(after: Duration = pacing.windowOpen) {
            tone.key(now + after, WindowTone.Open)
            touch(now + after)
        }

        fun markFailed(after: Duration = pacing.fade * 0.7) {
            tone.key(now + after, WindowTone.Failed)
            touch(now + after)
        }

        fun close() {
            tone.key(now, WindowTone.Closed)
            touch(now)
        }
    }

    // ------------------------------------------------------------------ 单元 8 + 9: 请求列表与滚动

    /** 请求列表: 一批行 + 滚动位置. */
    inner class RequestListHandle internal constructor() {
        internal val rowAlpha = List(config.resolve.requestCount) { floatTrack(0f) }
        internal val rowIcon = List(config.resolve.requestCount) { index ->
            stepTrack(if (index == config.resolve.hitRow) RequestIcon.Media else RequestIcon.Request)
        }
        internal val rowTone = List(config.resolve.requestCount) { stepTrack(RequestTone.Idle) }
        internal val rowIconScale = List(config.resolve.requestCount) { floatTrack(1f) }
        internal val scroll = floatTrack(0f)

        /**
         * 请求一条条进来.
         *
         * @param mediaRowIcon 命中行画成什么图标. 演超时那一遍时传 [RequestIcon.Request] ——
         * 列表里根本没有可以匹配的那一条.
         * @return 最后一条进完的时刻.
         */
        fun stream(
            stagger: Duration = pacing.rowStagger,
            fade: Duration = pacing.fade,
            mediaRowIcon: RequestIcon = RequestIcon.Media,
        ): Duration {
            rowIcon[config.resolve.hitRow].key(now, mediaRowIcon)
            var last = now
            rowAlpha.forEachIndexed { index, track ->
                val at = now + stagger * index.toDouble()
                track.ramp(at, fade, 1f)
                last = at + fade
            }
            touch(last)
            return last
        }

        /** 滚到能看见第 [row] 行为止. */
        fun scrollTo(row: Int, over: Duration = pacing.scroll) {
            val maxOffset = (config.resolve.requestCount - config.resolve.visibleRows).coerceAtLeast(0)
            val target = (row - config.resolve.visibleRows / 2).coerceIn(0, maxOffset)
            scroll.key(now, scroll.valueAt(now), Easings.Standard)
            scroll.key(now + over, target.toFloat())
            touch(now + over)
        }

        /** 命中: 那一行转绿, 图标弹一下, 再扩一圈涟漪 —— 与第二步选中结果块是同一套动作. */
        fun hit(over: Duration = pacing.fade * 0.7) {
            val row = config.resolve.hitRow
            rowTone[row].key(now + over, RequestTone.Hit)
            val scale = rowIconScale[row]
            scale.key(now, 1f, Easings.EmphasizedDecelerate)
            scale.key(now + pacing.pop * 0.4, HIT_POP, Easings.EmphasizedDecelerate)
            scale.key(now + pacing.pop, 1f)
            requestRipple.pulse()
            touch(now + pacing.pop)
        }

        /** 清空列表, 滚动位置归零. */
        fun clear(over: Duration = pacing.fade) {
            rowAlpha.forEach { it.ramp(now, over, 0f) }
            rowTone.forEach { it.key(now + over, RequestTone.Idle) }
            rowIconScale.forEach { it.key(now + over, 1f) }
            scroll.ramp(now, over, 0f)
            touch(now + over)
        }
    }

    // ------------------------------------------------------------------ 产出

    internal fun build(): SelectorWorkflowTimeline = SelectorWorkflowTimeline(
        config = config,
        duration = high + pacing.loopGap,
        phase = phaseTrack.build(),
        nodes = sources.map {
            NodeTracks(
                it.index, it.alpha.build(), it.pulsing.build(),
                priority = config.showPriorityMarks && config.sources[it.index].priority,
            )
        },
        links = sourceLinks.map { LineTracks(it.progress.build(), it.alpha.build()) },
        chips = chips.map {
            ChipTracks(
                it.key, it.cell, it.candidate, it.priority,
                it.alpha.build(), it.tone.build(), it.scale.build(),
            )
        },
        ripples = (ripples + requestRipple).map {
            RippleTracks(it.target, it.index, it.scale.build(), it.alpha.build())
        },
        cursors = cursorHandles.values.map {
            CursorTracks(it.id, it.owner, it.cell.build(), it.alpha.build())
        },
        handoff = LineTracks(handoff.progress.build(), handoff.alpha.build()),
        window = window.tone.build(),
        rows = List(config.resolve.requestCount) { i ->
            RowTracks(
                i,
                requestList.rowAlpha[i].build(),
                requestList.rowIcon[i].build(),
                requestList.rowTone[i].build(),
                requestList.rowIconScale[i].build(),
            )
        },
        scroll = requestList.scroll.build(),
        clocks = clocks.mapValues { (id, h) ->
            ClockTracks(
                id, h.alpha.build(), h.sweep.build(), h.tone.build(), h.overlay.build(),
                budgetSeconds = when (id) {
                    ClockId.PriorityWait -> config.selection.priorityWait
                    ClockId.InterceptBudget -> config.resolve.budget
                }?.let { (it.inWholeMilliseconds / 1000.0).toFloat() } ?: 0f,
            )
        },
    )

    companion object {
        /** 一个"立刻"的极短时长, 用来做硬切而不产生同时刻重复帧. */
        val SNAP: Duration = 20.milliseconds
        const val DIM_ALPHA = 0.35f
        const val MUTED_ALPHA = 0.4f
        const val SELECT_POP = 1.16f
        const val HIT_POP = 1.3f
        const val RIPPLE_FROM = 0.9f
        const val RIPPLE_TO = 1.9f
        const val RIPPLE_ALPHA = 0.9f
        const val TIMEOUT_OVERLAY = 0.22f
    }
}
