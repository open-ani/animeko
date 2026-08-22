/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import kotlin.time.Duration

/**
 * 把配置编译成一条可采样的时间线.
 *
 * 这是整个数据层唯一的入口 —— 上层只要改 [SelectorWorkflowConfig], 动画就跟着变,
 * 不需要碰任何时间常量.
 */
fun SelectorWorkflowConfig.buildTimeline(): SelectorWorkflowTimeline {
    require(!showInterceptClock || interceptStopFraction() < 1f) {
        "pacing.clockSweep (${pacing.clockSweep}) is too short: the intercept would time out before " +
                "the request list finishes streaming (needs > ${interceptElapsed()}). " +
                "Use clockSweepForInterceptStop() to size it."
    }
    val storyboard = Storyboard(this)
    val passes = buildPasses()
    passes.forEachIndexed { index, pass ->
        storyboard.playPass(pass, isLast = index == passes.lastIndex)
    }
    return storyboard.build()
}

/**
 * 第三步从"窗口打开"到"拦到播放链接"之间要走多久 (动画时间).
 * 它由请求进场、滚动这些演出参数决定, 不是一个常量.
 */
fun SelectorWorkflowConfig.interceptElapsed(): Duration {
    val p = pacing
    val rowsIn = p.rowStagger * resolve.visibleRows.toDouble()
    return p.windowOpen + rowsIn + p.scroll + p.fade
}

/**
 * 拦截成功时表盘指针会停在哪 (0..1).
 *
 * 由 `拦到用了多久 / 指针转一圈用多久` 算出来, 两者都是动画时间 ——
 * 与 [ResolveSpec.budget] 无关, 改设置项不会挪动指针停的位置.
 *
 * 想让指针停在钟面 7 点半, 把 [Pacing.clockSweep] 设成
 * [clockSweepForInterceptStop] `(7.5f / 12f)` 的返回值即可, 不需要在任何地方写角度.
 */
fun SelectorWorkflowConfig.interceptStopFraction(): Float {
    val full = pacing.clockSweep
    if (full <= Duration.ZERO) return 1f
    return (interceptElapsed().inWholeMicroseconds.toFloat() / full.inWholeMicroseconds).coerceIn(0f, 1f)
}

/**
 * 想让拦截成功时指针停在 [fraction] 处, [Pacing.clockSweep] 该填多少.
 */
fun SelectorWorkflowConfig.clockSweepForInterceptStop(fraction: Float): Duration {
    require(fraction in 0.05f..1f) { "fraction must be in 0.05..1" }
    return interceptElapsed() / fraction.toDouble()
}

/**
 * 一段演示. 高优先级门连演两条路径时会有两段, 其余情况只有一段.
 */
internal data class Pass(
    /** 本段里每个源的搜索耗时 (被演示的时间). */
    val latencies: List<Duration>,
    /** 有没有高优先级门, 以及这一段里它是等到了还是超时了. */
    val gate: Gate?,
) {
    enum class Gate { Hit, Timeout }
}

internal fun SelectorWorkflowConfig.buildPasses(): List<Pass> {
    val base = sources.map { it.latency }
    if (selection.priorityWait == null) return listOf(Pass(base, gate = null))
    val prio = checkNotNull(priorityIndex) { "priorityWait requires a priority source" }
    // 闸开多久 = 指针转一圈的时长 (动画时间), 与 priorityWait 配的秒数无关
    val gateWindow = pacing.clockSweep

    if (!selection.demoBothPriorityPaths) {
        val gate = if (pacing.scaled(base[prio]) <= gateWindow) Pass.Gate.Hit else Pass.Gate.Timeout
        return listOf(Pass(base, gate))
    }
    // 连演两条路径时高优先级源的耗时是编出来的: 一条稳稳赶上, 一条稳稳错过.
    // 两边都不看 SourceSpec.latency, 免得"赶上"那条的指针位置随配置乱跑.
    val fast = base.toMutableList().also { it[prio] = pacing.unscaled(gateWindow * PRIORITY_HIT_FRACTION) }
    val slow = base.toMutableList().also { it[prio] = selection.effectiveLateLatency(pacing) }
    return listOf(Pass(fast, Pass.Gate.Hit), Pass(slow, Pass.Gate.Timeout))
}

/** 演"等到了"那条路径时, 高优先级源在闸的百分之多少处回来. */
private const val PRIORITY_HIT_FRACTION = 0.7

/**
 * 演一段: 第一步搜源 → 第二步选源 → 第三步解析 (可能连演多种结局).
 */
private fun Storyboard.playPass(pass: Pass, isLast: Boolean) {
    val p = config.pacing
    val passStart = now

    // ---------------- 第一步: 搜源 ----------------
    phase("search")
    val finish = pass.latencies.map { passStart + p.scaled(it) }
    config.sources.indices.forEach { i ->
        sources[i].beginSearch()
        linkOf(i).draw(over = p.scaled(pass.latencies[i]))
        at(finish[i]) {
            sources[i].settle()
            linkOf(i).mute()
            chipsOf(i).forEach { it.appear() }
        }
    }

    // ---------------- 第二步: 选源 ----------------
    val prio = config.priorityIndex
    val gateStart = passStart
    val gateStop: Duration?
    val plan: SelectionPlan

    when (pass.gate) {
        null -> {
            gateStop = null
            plan = SelectionEngine.plan(
                config = config,
                mode = config.selection.mode,
                ready = finish,
                step = p.cursorStep,
                stagger = p.cursorStep * 1.5,
            )
        }

        Pass.Gate.Hit -> {
            // 等待期内高优先级源就回来了: 其余源一个 cursor 都不起
            val index = checkNotNull(prio)
            gateStop = finish[index]
            plan = SelectionEngine.planPriorityHit(config, index, finish[index], p.cursorStep, idPrefix = "")
        }

        Pass.Gate.Timeout -> {
            // 等超时才放闸, 只遍历此刻已经回来的源
            val open = passStart + p.clockSweep
            gateStop = open
            plan = SelectionEngine.plan(
                config = config,
                mode = config.selection.mode,
                ready = finish.map { if (it <= open) open else null },
                step = p.cursorStep,
                stagger = p.cursorStep * 1.5,
            )
        }
    }

    if (pass.gate != null) {
        val clock = clocks.getValue(ClockId.PriorityWait)
        at(gateStart) { clock.start() }
        at(checkNotNull(gateStop)) {
            phase(if (pass.gate == Pass.Gate.Hit) "priority-hit" else "priority-timeout")
            if (pass.gate == Pass.Gate.Hit) clock.stop() else clock.expire()
        }
    }

    plan.cursors.forEach { planned ->
        val handle = cursor(planned.id, planned.owner)
        at(planned.stops.first().time) { handle.enter(planned.stops.first().cell, planned.peakAlpha) }
        planned.stops.drop(1).forEach { stop ->
            at(stop.time - p.cursorStep) { handle.step(stop.cell, p.cursorStep) }
        }
        at(planned.exitAt) { handle.leave() }
    }

    val winner = plan.winner
    if (winner == null) {
        // 没有任何候选可选 —— 这段到此为止
        seekAfter(finish.max() + p.hold)
        if (!isLast) resetPass(p.reset) else finalHold(p)
        return
    }

    var selected: ResultKey = winner
    at(plan.winnerAt) {
        phase("select")
        val chosen = selected
        chipOf(chosen).select()
        rippleAt(config.cellOf(chosen)).pulse()
        chips.filter { it.key != chosen }.forEach { it.mute() }
    }
    seekAfter(plan.winnerAt + config.pacing.pop)

    // ---------------- 第三步: 解析 ----------------
    config.resolve.outcomes.forEachIndexed { index, outcome ->
        if (outcome == ResolveOutcome.HitAfterFallback) {
            selected = fallbackToNextCandidate(selected) ?: selected
        }
        playResolve(
            outcome = outcome,
            selected = selected,
            handoffNeeded = index == 0 || outcome == ResolveOutcome.HitAfterFallback,
            isLastOfPass = index == config.resolve.outcomes.lastIndex,
        )
    }

    if (!isLast) resetPass(p.reset) else finalHold(p)
}

/**
 * 上一次解析失败之后换到下一个候选: 失败的那条转红, cursor 落回它身上再走过去.
 *
 * @return 新选中的结果; 没有别的候选时返回 `null`.
 */
private fun Storyboard.fallbackToNextCandidate(current: ResultKey): ResultKey? {
    val p = config.pacing
    val next = SelectionEngine.nextCandidateAfter(config, current) ?: return null
    phase("fallback")
    chipOf(current).fail()
    handoff.retract(p.fade)     // 这条不行了, 交棒线先收回去, 待会儿重新画
    advance(p.fade * 1.6)

    val handle = cursor("fallback", next.source)
    handle.enter(config.cellOf(current))
    advance(p.cursorStep * 0.7)
    handle.step(config.cellOf(next), p.cursorStep)
    advance(p.cursorStep)

    chipOf(current).mute()
    chipOf(next).select()
    rippleAt(config.cellOf(next)).pulse()
    handle.leave()
    advance(p.pop)
    return next
}

/**
 * 演一次第三步.
 *
 * @param handoffNeeded 是否需要先把交棒线重新画一遍 (第一次, 以及换候选之后).
 */
private fun Storyboard.playResolve(
    outcome: ResolveOutcome,
    selected: ResultKey,
    handoffNeeded: Boolean,
    isLastOfPass: Boolean,
) {
    val p = config.pacing
    val clock = clocks.getValue(ClockId.InterceptBudget)
    val showClock = config.showInterceptClock

    if (handoffNeeded) {
        phase("handoff")
        handoff.draw(over = p.handoff)
        advance(p.handoff + p.windowOpenDelay)
        window.open()
    }
    val clockStart = now
    if (showClock) clock.start()
    advance(p.windowOpen)

    phase(if (outcome == ResolveOutcome.Timeout) "resolve-timeout" else "resolve-hit")
    val timeoutAt = if (showClock) clockStart + clock.fullSweepDuration else now
    requestList.stream(
        mediaRowIcon = if (outcome == ResolveOutcome.Timeout) RequestIcon.Request else RequestIcon.Media,
    )
    advance(p.rowStagger * config.resolve.visibleRows.toDouble())
    requestList.scrollTo(config.resolve.hitRow)
    advance(p.scroll)

    when (outcome) {
        ResolveOutcome.Timeout -> {
            // 请求都刷完了还没有匹配, 指针继续走到满圈
            seekAfter(timeoutAt)
            clock.expire()
            window.markFailed()
            advance(p.fade)
        }

        ResolveOutcome.Hit, ResolveOutcome.HitAfterFallback -> {
            advance(p.fade)
            requestList.hit()
            if (showClock) {
                val stopped = clock.stop()
                check(stopped < 1f) {
                    "pacing.clockSweep ${p.clockSweep} is too short for the choreography; " +
                            "use clockSweepForInterceptStop() to size it"
                }
            }
            advance(p.fade)
        }
    }

    advance(p.hold)
    if (!isLastOfPass) {
        // 清场, 准备下一次解析
        requestList.clear()
        if (showClock) clock.hide()
        if (outcome == ResolveOutcome.Timeout) window.close()
        advance(p.fade + p.reset * 0.5)
    }
}

/** 整段收尾: 所有单元退回初始态. */
private fun Storyboard.resetPass(over: Duration) {
    phase("reset")
    sources.forEach { it.reset(over) }
    config.sources.indices.forEach { linkOf(it).retract(over) }
    chips.forEach { it.reset(over) }
    handoff.retract(over)
    requestList.clear(over)
    window.close()
    clocks.values.forEach { it.hide(over) }   // hide 对没起过的表是空操作
    advance(over + config.pacing.loopGap)
}

private fun Storyboard.finalHold(p: Pacing) {
    advance(p.finalHold)
    resetPass(p.reset)
}

/** 把游标推到 [time] (只能往后). */
private fun Storyboard.seekAfter(time: Duration) {
    if (time > now) advance(time - now)
}
