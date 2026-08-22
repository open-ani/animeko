/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SelectorWorkflowTimelineTest {

    private fun config(
        mode: SelectMode = SelectMode.WaitAll,
        priorityWait: Duration? = null,
        demoBothPaths: Boolean = false,
        outcomes: List<ResolveOutcome> = listOf(ResolveOutcome.Hit),
    ) = SelectorWorkflowPresets.threeSources(
        mode = mode,
        priorityWait = priorityWait,
        resolveOutcomes = outcomes,
    ).let { base ->
        base.copy(selection = base.selection.copy(demoBothPriorityPaths = demoBothPaths))
    }

    // ------------------------------------------------------------------ 选源规则

    @Test
    fun wait_all_picks_the_first_candidate_in_grid_order() {
        val c = config(mode = SelectMode.WaitAll)
        val plan = SelectionEngine.plan(
            config = c,
            mode = SelectMode.WaitAll,
            ready = c.readyTimes(),
            step = c.pacing.cursorStep,
            stagger = c.pacing.cursorStep,
        )
        // 源 B 的第一条 = 整体第四条候选
        assertEquals(ResultKey(1, 0), plan.winner)
        assertEquals(1, plan.cursors.size, "等待全部只有一个全局 cursor")
        assertNull(plan.cursors.single().owner)
    }

    @Test
    fun eager_picks_the_same_candidate_but_earlier() {
        val c = config(mode = SelectMode.Eager)
        val ready = c.readyTimes()
        val waitAll = SelectionEngine.plan(c, SelectMode.WaitAll, ready, c.pacing.cursorStep, c.pacing.cursorStep)
        val eager = SelectionEngine.plan(c, SelectMode.Eager, ready, c.pacing.cursorStep, c.pacing.cursorStep)
        assertEquals(waitAll.winner, eager.winner, "两种模式必须选中同一条结果")
        assertTrue(eager.winnerAt < waitAll.winnerAt, "抢先必须更早选定: ${eager.winnerAt} vs ${waitAll.winnerAt}")
    }

    @Test
    fun eager_cancels_the_cursor_that_has_not_reached_a_candidate() {
        val c = config(mode = SelectMode.Eager)
        val plan = SelectionEngine.plan(
            c, SelectMode.Eager, c.readyTimes(),
            c.pacing.cursorStep, c.pacing.cursorStep,
        )
        val sourceA = plan.cursors.single { it.owner == 0 }
        assertTrue(sourceA.cancelled, "源 A 没有候选, 应该在别人命中时就地作废")
        assertTrue(sourceA.stops.size < c.sources[0].resultCount, "被截断的 cursor 不该走完自己所有格子")
    }

    @Test
    fun a_source_without_candidates_never_wins() {
        val c = SelectorWorkflowConfig(
            sources = listOf(
                SourceSpec("A", 1.seconds, resultCount = 3),
                SourceSpec("B", 2.seconds, resultCount = 2, candidates = setOf(1)),
            ),
        )
        val plan = SelectionEngine.plan(
            c, SelectMode.WaitAll, c.readyTimes(),
            c.pacing.cursorStep, c.pacing.cursorStep,
        )
        assertEquals(ResultKey(1, 1), plan.winner)
    }

    @Test
    fun no_candidate_at_all_yields_no_winner() {
        val c = SelectorWorkflowConfig(sources = listOf(SourceSpec("A", 1.seconds, resultCount = 2)))
        val plan = SelectionEngine.plan(
            c, SelectMode.WaitAll, c.readyTimes(),
            c.pacing.cursorStep, c.pacing.cursorStep,
        )
        assertNull(plan.winner)
    }

    @Test
    fun fallback_goes_to_the_next_candidate_and_wraps_around() {
        val c = config()
        val first = c.candidates[0]
        val second = c.candidates[1]
        assertEquals(second, SelectionEngine.nextCandidateAfter(c, first))
        assertEquals(first, SelectionEngine.nextCandidateAfter(c, second), "到尾了要绕回开头")
    }

    // ------------------------------------------------------------------ 时间线

    @Test
    fun timeline_is_sampleable_and_deterministic() {
        val timeline = config().buildTimeline()
        assertTrue(timeline.duration > Duration.ZERO)
        val a = timeline.sampleAt(timeline.duration / 2.0)
        val b = timeline.sampleAt(timeline.duration / 2.0)
        assertEquals(a, b, "同一时刻必须采出同一份状态")
    }

    @Test
    fun every_unit_stays_within_its_declared_range_across_the_whole_timeline() {
        val timeline = config(outcomes = ALL_OUTCOMES).buildTimeline()
        var t = Duration.ZERO
        val step = timeline.duration / 400.0
        while (t <= timeline.duration) {
            val s = timeline.sampleAt(t)
            s.sourceNodes.forEach { assertInUnitRange(it.alpha, "node ${it.index} alpha", t) }
            s.sourceLinks.forEach {
                assertInUnitRange(it.alpha, "link alpha", t)
                assertInUnitRange(it.progress, "link progress", t)
            }
            s.results.forEach {
                assertInUnitRange(it.alpha, "chip ${it.key} alpha", t)
                assertTrue(it.scale in 0.5f..2f, "chip scale out of range at $t: ${it.scale}")
            }
            s.cursors.forEach {
                assertInUnitRange(it.alpha, "cursor ${it.id} alpha", t)
                assertTrue(
                    it.cell in -0.01f..(timeline.config.results.size).toFloat(),
                    "cursor ${it.id} cell out of grid at $t: ${it.cell}",
                )
            }
            s.clocks.values.forEach {
                assertInUnitRange(it.alpha, "clock ${it.id} alpha", t)
                assertInUnitRange(it.sweep, "clock ${it.id} sweep", t)
            }
            assertInUnitRange(it = s.handoff.progress, name = "handoff progress", t = t)
            assertTrue(s.scroll.rowOffset >= -0.01f, "scroll must not go negative at $t")
            t += step
        }
    }

    @Test
    fun lines_that_have_been_drawn_stay_drawn_until_the_pass_resets() {
        // 回归: 收线/重画的关键帧如果直接写在远期时刻, 轨道会从"画满那一帧"一路插值下来,
        // 于是整段演出里那条线都在慢慢缩回去 —— 取值始终在 0..1 之内, 范围检查发现不了.
        val timeline = config(outcomes = ALL_OUTCOMES).buildTimeline()
        var t = Duration.ZERO
        val step = timeline.duration / 600.0
        var checked = 0
        while (t <= timeline.duration) {
            val s = timeline.sampleAt(t)
            if (s.phase.startsWith("resolve")) {
                checked++
                s.sourceLinks.forEachIndexed { i, line ->
                    assertTrue(line.progress > 0.999f, "第 $i 条连线在 $t (${s.phase}) 缩回去了: ${line.progress}")
                }
                assertTrue(
                    s.handoff.progress > 0.999f,
                    "交棒线在 $t (${s.phase}) 缩回去了: ${s.handoff.progress}",
                )
            }
            t += step
        }
        assertTrue(checked > 20, "没采到几帧解析阶段, 这个用例白跑了")
    }

    @Test
    fun the_request_list_scrolls_all_the_way_to_the_target_row() {
        val c = config(outcomes = ALL_OUTCOMES)
        val timeline = c.buildTimeline()
        val expected = (c.resolve.hitRow - c.resolve.visibleRows / 2)
            .coerceIn(0, c.resolve.requestCount - c.resolve.visibleRows)
        var maxOffset = 0f
        var t = Duration.ZERO
        val step = timeline.duration / 800.0
        while (t <= timeline.duration) {
            maxOffset = maxOf(maxOffset, timeline.sampleAt(t).scroll.rowOffset)
            t += step
        }
        assertTrue(
            abs(maxOffset - expected) < 0.05f,
            "列表该滚到第 $expected 行, 实际最多滚到 $maxOffset",
        )
    }

    @Test
    fun results_that_arrive_after_the_selection_keep_their_own_pace() {
        // 回归: 选定时 mute 所有落选结果, 而 mute 走 ramp、ramp 会截断后面的关键帧,
        // 于是"还没返回的源"的结果被连累着在选定那一刻一股脑冒出来.
        val c = config(mode = SelectMode.Eager)
        val timeline = c.buildTimeline()
        val ready = c.readyTimes()
        val selectAt = SelectionEngine.plan(
            c, SelectMode.Eager, ready, c.pacing.cursorStep, c.pacing.cursorStep,
        ).winnerAt

        // 选定之后才返回的源
        val lateSource = c.sources.indices.first { ready[it] > selectAt }
        val lateKeys = c.results.filter { it.source == lateSource }.toSet()
        assertTrue(lateKeys.isNotEmpty())

        // 选定的那一刻 (以及之后的一小会儿) 它们必须还没出现
        val justAfter = timeline.sampleAt(selectAt + c.pacing.fade * 0.5)
        justAfter.results.filter { it.key in lateKeys }.forEach {
            assertTrue(it.alpha < 0.05f, "${it.key} 在选定后立刻就冒出来了: alpha=${it.alpha}")
        }

        // 到了它自己该出现的时候才出现, 而且一出场就是落选的暗色
        val ownTime = ready[lateSource] + c.pacing.fade
        val atOwnTime = timeline.sampleAt(ownTime)
        atOwnTime.results.filter { it.key in lateKeys }.forEach {
            assertTrue(it.alpha > 0.3f, "${it.key} 到点了却没出现: alpha=${it.alpha}")
            assertTrue(it.alpha < 0.6f, "${it.key} 是选定之后才到的, 该以暗色出场: alpha=${it.alpha}")
        }
    }

    @Test
    fun the_selected_chip_turns_green_and_the_others_dim() {
        val c = config()
        val timeline = c.buildTimeline()
        val winner = SelectionEngine.plan(
            c, c.selection.mode, c.readyTimes(), c.pacing.cursorStep, c.pacing.cursorStep,
        ).winner
        assertNotNull(winner)

        val selectedMoment = firstTimeWhen(timeline) { s ->
            s.results.any { it.key == winner && it.tone == ChipTone.Selected }
        }
        assertNotNull(selectedMoment, "整条时间线上应该出现一次选中")
        val state = timeline.sampleAt(selectedMoment + c.pacing.pop)
        val others = state.results.filter { it.key != winner }
        assertTrue(others.all { it.alpha < 0.99f }, "落选的结果应该暗下去")
    }

    // ------------------------------------------------------------------ 高优先级标记

    @Test
    fun priority_marks_are_hidden_until_the_gate_is_switched_on() {
        val off = config()                                   // 没开高优先级等待
        val on = config(priorityWait = 5.seconds, demoBothPaths = true)
        val prio = assertNotNull(off.priorityIndex, "预设里本来就有一个高优先级源")

        assertTrue(off.sources[prio].priority, "源身上的配置还在")
        assertTrue(!off.showPriorityMarks, "但没开闸就不该画标记")
        assertTrue(on.showPriorityMarks)

        val offState = off.buildTimeline().let { it.sampleAt(it.duration / 2.0) }
        assertTrue(
            offState.sourceNodes.none { it.priority },
            "没开闸时数据源节点上不该有高优先级菱形",
        )
        assertTrue(
            offState.results.none { it.priority },
            "没开闸时结果上也不该有",
        )

        val onState = on.buildTimeline().let { it.sampleAt(it.duration / 2.0) }
        assertEquals(
            listOf(prio), onState.sourceNodes.filter { it.priority }.map { it.index },
            "开闸后只有那一个源带标记",
        )
        assertEquals(
            off.sources[prio].resultCount,
            onState.results.count { it.priority },
            "它的每一条结果都带标记",
        )
    }

    @Test
    fun candidate_marks_do_not_depend_on_the_gate() {
        // 候选圆点是选源规则的一部分, 跟高优先级开关无关
        val off = config()
        val on = config(priorityWait = 5.seconds, demoBothPaths = true)
        assertEquals(
            off.candidates.size,
            off.buildTimeline().let { it.sampleAt(it.duration / 2.0) }.results.count { it.candidate },
        )
        assertEquals(
            on.candidates.size,
            on.buildTimeline().let { it.sampleAt(it.duration / 2.0) }.results.count { it.candidate },
        )
    }

    // ------------------------------------------------------------------ 计时器

    @Test
    fun intercept_clock_stop_position_is_derived_from_the_sweep_duration() {
        val base = config()
        val slow = base.copy(pacing = base.pacing.copy(clockSweep = base.pacing.clockSweep * 2))
        assertTrue(
            slow.interceptStopFraction() < base.interceptStopFraction(),
            "指针转一圈越久, 拦到时停得越靠前",
        )
        // 一圈的时长翻倍, 指针刚好停在一半的位置
        assertTrue(abs(slow.interceptStopFraction() * 2 - base.interceptStopFraction()) < 1e-3f)
    }

    @Test
    fun clockSweepForInterceptStop_round_trips() {
        val base = config()
        val target = 7.5f / 12f // 钟面 7 点半
        val sized = base.copy(
            pacing = base.pacing.copy(clockSweep = base.clockSweepForInterceptStop(target)),
        )
        assertTrue(
            abs(sized.interceptStopFraction() - target) < 1e-3f,
            "算出来的一圈时长应该让指针恰好停在 7 点半, 实际 ${sized.interceptStopFraction()}",
        )
    }

    // ------------------------------------------------------------------ 配置的秒数只影响读数

    @Test
    fun configured_seconds_do_not_change_the_animation_at_all() {
        val base = config(priorityWait = 5.seconds, demoBothPaths = true, outcomes = ALL_OUTCOMES)
        val other = base.copy(
            selection = base.selection.copy(priorityWait = 30.seconds),
            resolve = base.resolve.copy(budget = 45.seconds),
        )
        val a = base.buildTimeline()
        val b = other.buildTimeline()
        assertEquals(a.duration, b.duration, "改设置项不该让动画变长变短")

        // 逐帧比对: 除了读数, 每个单元的取值必须一模一样
        var t = Duration.ZERO
        val step = a.duration / 300.0
        while (t <= a.duration) {
            val sa = a.sampleAt(t)
            val sb = b.sampleAt(t)
            assertEquals(sa.copy(clocks = emptyMap()), sb.copy(clocks = emptyMap()), "第 $t 帧不一致")
            sa.clocks.forEach { (id, clock) ->
                assertEquals(clock.sweep, sb.clocks.getValue(id).sweep, 1e-4f, "$id 的指针位置不该受设置项影响")
            }
            t += step
        }
    }

    @Test
    fun the_readout_counts_up_to_the_configured_seconds() {
        val c = config(priorityWait = 20.seconds, demoBothPaths = true, outcomes = ALL_OUTCOMES)
            .let { it.copy(resolve = it.resolve.copy(budget = 12.seconds)) }
        val timeline = c.buildTimeline()

        // 高优先级那条超时的路径: 指针走满一圈, 读数必须正好数到配置的秒数
        val expired = firstStateWhen(timeline) { it.clocks.getValue(ClockId.PriorityWait).tone == ClockTone.Expired }
        assertNotNull(expired)
        val prio = expired.clocks.getValue(ClockId.PriorityWait)
        assertEquals(20f, prio.budgetSeconds, 1e-3f)
        assertTrue(abs(prio.elapsedSeconds - 20f) < 0.2f, "超时时读数该数到 20.0, 实际 ${prio.elapsedSeconds}")

        // 拦截成功那次: 读数 = 配置秒数 × 指针走过的比例
        val stopped = firstStateWhen(timeline) {
            it.clocks.getValue(ClockId.InterceptBudget).tone == ClockTone.Stopped
        }
        assertNotNull(stopped)
        val intercept = stopped.clocks.getValue(ClockId.InterceptBudget)
        assertEquals(12f, intercept.budgetSeconds, 1e-3f)
        assertEquals(intercept.sweep * 12f, intercept.elapsedSeconds, 1e-3f)
        assertTrue(intercept.elapsedSeconds in 0.1f..11.9f)
    }

    @Test
    fun the_two_clocks_read_out_the_two_different_settings() {
        val c = config(priorityWait = 7.seconds, demoBothPaths = true)
            .let { it.copy(resolve = it.resolve.copy(budget = 19.seconds)) }
        val timeline = c.buildTimeline()
        val s = timeline.sampleAt(timeline.duration / 2.0)
        assertEquals(7f, s.clocks.getValue(ClockId.PriorityWait).budgetSeconds, 1e-3f)
        assertEquals(19f, s.clocks.getValue(ClockId.InterceptBudget).budgetSeconds, 1e-3f)
    }

    @Test
    fun the_readout_starts_at_zero_and_never_exceeds_the_budget() {
        val c = config(priorityWait = 9.seconds, demoBothPaths = true, outcomes = ALL_OUTCOMES)
        val timeline = c.buildTimeline()
        var t = Duration.ZERO
        val step = timeline.duration / 500.0
        while (t <= timeline.duration) {
            timeline.sampleAt(t).clocks.values.forEach {
                assertTrue(
                    it.elapsedSeconds >= -1e-3f && it.elapsedSeconds <= it.budgetSeconds + 1e-3f,
                    "$t 的 ${it.id} 读数越界: ${it.elapsedSeconds} / ${it.budgetSeconds}",
                )
            }
            t += step
        }
    }

    @Test
    fun hit_stops_the_clock_while_timeout_completes_the_sweep() {
        val c = config(outcomes = ALL_OUTCOMES)
            .let { it.copy(pacing = it.pacing.copy(clockSweep = it.clockSweepForInterceptStop(0.625f))) }
        val timeline = c.buildTimeline()
        val stopped = firstStateWhen(timeline) { s ->
            s.clocks.getValue(ClockId.InterceptBudget).tone == ClockTone.Stopped
        }
        assertNotNull(stopped, "应该出现拦截成功")
        assertTrue(abs(stopped.clocks.getValue(ClockId.InterceptBudget).sweep - 0.625f) < 0.02f)

        val expired = firstStateWhen(timeline) { s ->
            s.clocks.getValue(ClockId.InterceptBudget).tone == ClockTone.Expired
        }
        assertNotNull(expired, "应该出现拦截超时")
        assertTrue(expired.clocks.getValue(ClockId.InterceptBudget).sweep > 0.99f, "超时时指针必须走满一圈")
        assertTrue(expired.clocks.getValue(ClockId.InterceptBudget).overlayAlpha > 0f)
    }

    @Test
    fun too_short_a_sweep_is_rejected_with_an_actionable_message() {
        // 只在真有计时器时才管 —— 不演超时的话一圈多长根本无所谓
        val base = config(outcomes = ALL_OUTCOMES)
        val broken = base.copy(pacing = base.pacing.copy(clockSweep = 100.milliseconds))
        val error = runCatching { broken.buildTimeline() }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message.orEmpty().contains("clockSweepForInterceptStop"))

        val withoutClock = config().copy(pacing = base.pacing.copy(clockSweep = 100.milliseconds))
        assertTrue(!withoutClock.showInterceptClock)
        withoutClock.buildTimeline()   // 没有表就不该拦着
    }

    // ------------------------------------------------------------------ 高优先级门

    @Test
    fun priority_gate_demo_plays_both_paths_and_they_select_different_candidates() {
        val c = config(priorityWait = 5.seconds, demoBothPaths = true)
        val passes = c.buildPasses()
        assertEquals(2, passes.size)
        assertEquals(Pass.Gate.Hit, passes[0].gate)
        assertEquals(Pass.Gate.Timeout, passes[1].gate)

        val timeline = c.buildTimeline()
        val prioritySource = c.priorityIndex!!
        val priorityCandidate = c.candidates.first { it.source == prioritySource }
        val otherCandidate = c.candidates.first { it.source != prioritySource }

        assertNotNull(
            firstTimeWhen(timeline) { s -> s.results.any { it.key == priorityCandidate && it.tone == ChipTone.Selected } },
            "路径一应该选中高优先级源的候选",
        )
        assertNotNull(
            firstTimeWhen(timeline) { s -> s.results.any { it.key == otherCandidate && it.tone == ChipTone.Selected } },
            "路径二应该选中另一个候选",
        )
    }

    @Test
    fun nothing_is_selected_while_the_priority_gate_is_still_open() {
        val c = config(priorityWait = 5.seconds, demoBothPaths = true)
        val timeline = c.buildTimeline()
        val gateEnd = c.pacing.scaled(c.selection.priorityWait!!)
        var t = Duration.ZERO
        while (t < gateEnd) {
            val s = timeline.sampleAt(t)
            val prioritySource = c.priorityIndex!!
            // 等待期内: 只有高优先级源自己的 cursor 允许出现
            s.cursors.filter { it.alpha > 0.01f }.forEach {
                assertEquals(prioritySource, it.owner, "等待期内非高优先级源不该有 cursor, t=$t")
            }
            t += gateEnd / 60.0
        }
    }

    @Test
    fun priority_clock_stop_position_follows_the_priority_source_latency() {
        val wait = 5.seconds
        val c = config(priorityWait = wait, demoBothPaths = true)
        val timeline = c.buildTimeline()
        val stopped = firstStateWhen(timeline) { s ->
            s.clocks.getValue(ClockId.PriorityWait).tone == ClockTone.Stopped
        }
        assertNotNull(stopped)
        // 路径一里高优先级源用了 wait * 0.7
        assertTrue(
            abs(stopped.clocks.getValue(ClockId.PriorityWait).sweep - 0.7f) < 0.03f,
            "指针该停在 70% 处, 实际 ${stopped.clocks.getValue(ClockId.PriorityWait).sweep}",
        )
    }

    // ------------------------------------------------------------------ 第三步三种结局

    @Test
    fun resolve_demo_plays_hit_then_timeout_then_a_fallback_hit() {
        val c = config(outcomes = ALL_OUTCOMES)
        val timeline = c.buildTimeline()

        val hit = firstTimeWhen(timeline) { it.requestRows.any { r -> r.tone == RequestTone.Hit } }
        val failed = firstTimeWhen(timeline) { it.window.tone == WindowTone.Failed }
        assertNotNull(hit)
        assertNotNull(failed)
        assertTrue(hit < failed, "先演成功, 再演超时")

        val fallbackHit = firstTimeWhen(timeline, from = failed) {
            it.requestRows.any { r -> r.tone == RequestTone.Hit }
        }
        assertNotNull(fallbackHit, "超时之后还要再演一次成功")

        val failedChip = firstTimeWhen(timeline) { s -> s.results.any { it.tone == ChipTone.Failed } }
        assertNotNull(failedChip, "失败的那条结果应该转红")
        assertTrue(failedChip > failed)
    }

    @Test
    fun timeout_run_has_no_media_row_at_all() {
        val c = config(outcomes = ALL_OUTCOMES)
        val timeline = c.buildTimeline()
        val expiredAt = firstTimeWhen(timeline) {
            it.clocks.getValue(ClockId.InterceptBudget).tone == ClockTone.Expired
        }
        assertNotNull(expiredAt)
        val s = timeline.sampleAt(expiredAt)
        assertTrue(
            s.requestRows.none { it.icon == RequestIcon.Media },
            "超时那一遍列表里不该出现可以匹配的那一条",
        )
    }

    @Test
    fun single_outcome_keeps_the_intercept_clock_hidden() {
        val c = config(outcomes = listOf(ResolveOutcome.Hit))
        assertTrue(!c.showInterceptClock, "只演成功那一种时不该有计时器")
        val timeline = c.buildTimeline()
        var t = Duration.ZERO
        val step = timeline.duration / 400.0
        while (t <= timeline.duration) {
            val clock = timeline.sampleAt(t).clocks.getValue(ClockId.InterceptBudget)
            assertTrue(clock.alpha <= 0.001f, "$t 处第三步计时器不该露面: alpha=${clock.alpha}")
            t += step
        }
    }

    @Test
    fun the_intercept_clock_shows_up_once_the_demo_is_switched_on() {
        val c = config(outcomes = ALL_OUTCOMES)
        assertTrue(c.showInterceptClock)
        val timeline = c.buildTimeline()
        assertNotNull(
            firstTimeWhen(timeline) { it.clocks.getValue(ClockId.InterceptBudget).alpha > 0.9f },
            "连演三种结局时计时器该出现",
        )
        assertNotNull(
            firstTimeWhen(timeline) { it.clocks.getValue(ClockId.InterceptBudget).tone == ClockTone.Expired },
            "而且该走到超时",
        )
    }

    @Test
    fun a_hit_only_run_still_plays_the_whole_third_step() {
        // 没有表不代表第三步缩水: 请求照样进场、照样滚动、照样命中
        val timeline = config(outcomes = listOf(ResolveOutcome.Hit)).buildTimeline()
        assertNotNull(
            firstTimeWhen(timeline) { it.requestRows.any { r -> r.tone == RequestTone.Hit } },
            "该演到拦截成功",
        )
        assertNotNull(
            firstTimeWhen(timeline) { it.requestRows.count { r -> r.alpha > 0.9f } == it.requestRows.size },
            "所有请求都该进场",
        )
        assertNull(
            firstTimeWhen(timeline) { it.window.tone == WindowTone.Failed },
            "只演成功时窗口不该变红",
        )
    }

    // ------------------------------------------------------------------ 可配置性

    @Test
    fun changing_the_number_of_sources_changes_the_grid_without_touching_the_script() {
        val five = SelectorWorkflowConfig(
            sources = List(5) { i ->
                SourceSpec("源 $i", (i + 1).seconds, resultCount = 2, candidates = if (i == 3) setOf(0) else emptySet())
            },
            resolve = ResolveSpec(budget = 30.seconds),
        )
        assertEquals(10, five.results.size)
        assertEquals(1, five.candidates.size)
        val timeline = five.buildTimeline()
        assertEquals(10, timeline.sampleAt(timeline.duration).results.size)
        assertEquals(ResultKey(3, 0), SelectionEngine.plan(
            five, SelectMode.WaitAll, five.readyTimes(),
            five.pacing.cursorStep, five.pacing.cursorStep,
        ).winner)
    }

    @Test
    fun slower_sources_push_the_selection_later() {
        val fast = config()
        val slow = fast.copy(sources = fast.sources.map { it.copy(latency = it.latency * 2) })
        assertTrue(slow.buildTimeline().duration > fast.buildTimeline().duration)
    }

    /** 剧本里 cursor 用的是折算过的动画时间, 测试也得按同一把尺子. */
    private fun SelectorWorkflowConfig.readyTimes(): List<Duration> =
        sources.map { pacing.scaled(it.latency) }

    private companion object {
        val ALL_OUTCOMES = listOf(ResolveOutcome.Hit, ResolveOutcome.Timeout, ResolveOutcome.HitAfterFallback)

        fun assertInUnitRange(it: Float, name: String, t: Duration) {
            assertTrue(it in -0.001f..1.001f, "$name out of 0..1 at $t: $it")
        }

        fun firstTimeWhen(
            timeline: SelectorWorkflowTimeline,
            from: Duration = Duration.ZERO,
            steps: Int = 2000,
            predicate: (SelectorWorkflowState) -> Boolean,
        ): Duration? {
            val step = timeline.duration / steps.toDouble()
            var t = from
            while (t <= timeline.duration) {
                if (predicate(timeline.sampleAt(t))) return t
                t += step
            }
            return null
        }

        fun firstStateWhen(
            timeline: SelectorWorkflowTimeline,
            predicate: (SelectorWorkflowState) -> Boolean,
        ): SelectorWorkflowState? = firstTimeWhen(timeline, predicate = predicate)?.let { timeline.sampleAt(it) }
    }
}
