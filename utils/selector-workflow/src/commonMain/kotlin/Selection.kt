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
 * 一次遍历 cursor 的排期.
 *
 * @param stops 依次停在哪一格、什么时候到. 已经按"命中就没后面了"截断过.
 * @param exitAt 什么时候退场. 走完自己那几格没找到, 或者被别人抢先命中, 都在这里退场.
 * @param cancelled 是否是被抢先命中截断的 (而不是自己正常走完).
 */
internal data class PlannedCursor(
    val id: String,
    val owner: Int?,
    val stops: List<CursorStop>,
    val exitAt: Duration,
    val cancelled: Boolean,
    val peakAlpha: Float = 1f,
)

internal data class CursorStop(val time: Duration, val cell: Int)

/**
 * 一段演示里"谁在什么时候选中了什么"的完整结论.
 *
 * @param winner 选中的结果; 没有任何候选被走到时为 `null`.
 * @param winnerAt 选定时刻.
 */
internal data class SelectionPlan(
    val cursors: List<PlannedCursor>,
    val winner: ResultKey?,
    val winnerAt: Duration,
)

/**
 * 选源规则引擎.
 *
 * 规则只有两条, 全部在这里实现, 剧本不再重复判断:
 * 1. cursor 遍历到 **候选结果** 就选它;
 * 2. 已经选过一个就不再选 —— 于是"谁先走到候选"就是唯一的胜负判据.
 *
 * 高优先级门是这两条之上的一层闸: 门开之前所有 cursor 都不许起步.
 */
internal object SelectionEngine {

    /**
     * 排普通规则 (没有高优先级门, 或者门已经放开) 下的遍历.
     *
     * @param ready 每个源可以开始被遍历的时刻; 值为 `null` 表示这个源这一段里不参与.
     * @param cellsOf 每个源要走的格子, 按遍历顺序.
     */
    fun plan(
        config: SelectorWorkflowConfig,
        mode: SelectMode,
        ready: List<Duration?>,
        step: Duration,
        stagger: Duration,
        idPrefix: String = "",
    ): SelectionPlan {
        val participating = config.sources.indices.filter { ready[it] != null }
        if (participating.isEmpty()) return SelectionPlan(emptyList(), null, Duration.ZERO)

        val raw: List<RawCursor> = when (mode) {
            SelectMode.WaitAll -> {
                // 一个中性色的全局 cursor, 等参与的源全部就绪后, 按网格顺序从头走
                val start = participating.maxOf { ready[it]!! }
                val cells = config.results
                    .withIndex()
                    .filter { (_, key) -> key.source in participating }
                    .map { (cell, _) -> cell }
                listOf(RawCursor("${idPrefix}global", owner = null, start = start, cells = cells))
            }

            SelectMode.Eager -> {
                // 每个源一就绪就起一个自己的 cursor; 同一时刻就绪的按完成顺序错开, 免得挤在同一帧
                val order = participating.sortedBy { ready[it]!! }
                var previous: Duration? = null
                var sameInstantRank = 0
                order.map { source ->
                    val at = ready[source]!!
                    if (at == previous) sameInstantRank++ else sameInstantRank = 0
                    previous = at
                    RawCursor(
                        id = "${idPrefix}src$source",
                        owner = source,
                        start = at + stagger * sameInstantRank.toDouble(),
                        cells = config.results.withIndex()
                            .filter { (_, key) -> key.source == source }
                            .map { (cell, _) -> cell },
                    )
                }
            }
        }

        // 每一格的到达时刻
        val arrivals = raw.map { cursor ->
            cursor.cells.mapIndexed { i, cell -> CursorStop(cursor.start + step * i.toDouble(), cell) }
        }

        // 第一个被走到的候选就是赢家; 同一时刻并列时, 网格顺序靠前的赢
        var winner: ResultKey? = null
        var winnerAt = Duration.ZERO
        arrivals.flatten()
            .filter { config.results[it.cell].isCandidate(config) }
            .minWithOrNull(compareBy({ it.time }, { it.cell }))
            ?.let {
                winner = config.results[it.cell]
                winnerAt = it.time
            }

        val decidedAt = winner?.let { winnerAt }
        val planned = raw.mapIndexed { index, cursor ->
            val stops = arrivals[index]
            val kept = if (decidedAt == null) stops else stops.filter { it.time <= decidedAt }
            val effective = kept.ifEmpty { stops.take(1) }
            val naturalEnd = effective.last().time + step
            val cancelled = decidedAt != null && naturalEnd > decidedAt && effective.size < stops.size
            PlannedCursor(
                id = cursor.id,
                owner = cursor.owner,
                stops = effective,
                exitAt = if (decidedAt != null) minOf(naturalEnd, maxOf(decidedAt, effective.last().time))
                else naturalEnd,
                cancelled = cancelled,
            )
        }.filter { it.stops.isNotEmpty() && (decidedAt == null || it.stops.first().time <= decidedAt) }

        return SelectionPlan(planned, winner, winnerAt)
    }

    /**
     * 高优先级源及时回来的那条路径: 只有它自己起一趟 cursor, 直奔它的候选.
     */
    fun planPriorityHit(
        config: SelectorWorkflowConfig,
        priorityIndex: Int,
        ready: Duration,
        step: Duration,
        idPrefix: String,
    ): SelectionPlan {
        val cells = config.results.withIndex()
            .filter { (_, key) -> key.source == priorityIndex }
            .map { (cell, _) -> cell }
        val stops = cells.mapIndexed { i, cell -> CursorStop(ready + step * i.toDouble(), cell) }
        val hit = stops.firstOrNull { config.results[it.cell].isCandidate(config) }
        val kept = if (hit == null) stops else stops.filter { it.time <= hit.time }
        return SelectionPlan(
            cursors = listOf(
                PlannedCursor(
                    id = "${idPrefix}prio",
                    owner = priorityIndex,
                    stops = kept,
                    exitAt = kept.last().time,
                    cancelled = false,
                ),
            ),
            winner = hit?.let { config.results[it.cell] },
            winnerAt = hit?.time ?: (stops.lastOrNull()?.time ?: ready),
        )
    }

    /**
     * 当前候选失败之后应该换到哪个候选. 按网格顺序取下一个, 到尾了绕回开头.
     */
    fun nextCandidateAfter(config: SelectorWorkflowConfig, current: ResultKey): ResultKey? {
        val all = config.candidates
        if (all.size <= 1) return null
        val i = all.indexOf(current)
        if (i < 0) return all.firstOrNull()
        return all[(i + 1) % all.size]
    }

    private data class RawCursor(
        val id: String,
        val owner: Int?,
        val start: Duration,
        val cells: List<Int>,
    )
}
