/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 一个数据源在这段演示里的行为.
 *
 * @param name 展示用的短名, 例如 "源 A".
 * @param latency 从开始搜索到给出结果需要多久. 演示的节奏几乎完全由这个值决定.
 * @param resultCount 这个源会给出几条结果.
 * @param candidates 这些结果里哪几条是 **候选结果** (源内下标). 见 [SelectorWorkflowConfig] 的选源规则.
 * @param priority 是否是高优先级源. 只在 [SelectionSpec.priorityWait] 非空时有意义.
 */
@Immutable
data class SourceSpec(
    val name: String,
    val latency: Duration,
    val resultCount: Int,
    val candidates: Set<Int> = emptySet(),
    val priority: Boolean = false,
) {
    init {
        require(resultCount >= 0) { "resultCount must not be negative" }
        require(candidates.all { it in 0 until resultCount }) {
            "candidate index out of range for $name: $candidates, resultCount=$resultCount"
        }
        require(latency >= Duration.ZERO) { "latency must not be negative" }
    }
}

/**
 * 遍历候选的方式.
 */
enum class SelectMode {
    /**
     * 等待全部: 所有源都返回之后, **一个** cursor 从第一条候选开始往后走.
     */
    WaitAll,

    /**
     * 抢先: 每个源一返回就为 **它自己的结果** 起一个 cursor, 各走各的.
     */
    Eager,
}

/**
 * 第二步(选源)的规则.
 *
 * @param mode 遍历方式.
 * @param priorityWait 最大等待高优先级源的时长. 非空时启用高优先级门:
 * 这段时间内无论其他源有没有候选都不选, 只等高优先级源;
 * 它带着候选回来就直接用它的候选, 超时才放闸回到 [mode] 的普通规则.
 *
 * 这个值 **只决定计时器旁边那个读数数到几**, 不决定这道闸在动画里开多久 ——
 * 闸的时长就是指针转一圈的时长 [Pacing.clockSweep].
 */
@Immutable
data class SelectionSpec(
    val mode: SelectMode = SelectMode.WaitAll,
    val priorityWait: Duration? = null,
    /**
     * 把"等到了"和"等超时"两条路径连着演一遍.
     *
     * 打开时高优先级源两条路径里的耗时都是编出来的 (一条稳稳赶上闸, 一条稳稳错过),
     * 它自己的 [SourceSpec.latency] 不参与; 关闭时才按 [SourceSpec.latency] 演一条,
     * 赶没赶上由它自己的耗时决定.
     */
    val demoBothPriorityPaths: Boolean = false,
    /**
     * 演"等超时"那条路径时, 高优先级源假装用多久才回来. 为 `null` 时取 [priorityWait] 的 1.5 倍.
     */
    val lateLatency: Duration? = null,
) {
    init {
        require(priorityWait == null || priorityWait > Duration.ZERO) { "priorityWait must be positive" }
        require(lateLatency == null || lateLatency > Duration.ZERO) { "lateLatency must be positive" }
        if (demoBothPriorityPaths) {
            require(priorityWait != null) { "demoBothPriorityPaths requires priorityWait" }
        }
    }

    /** 演"等超时"那条路径时高优先级源的耗时 (被演示的时间). */
    fun effectiveLateLatency(pacing: Pacing): Duration =
        lateLatency ?: pacing.unscaled(pacing.clockSweep * 1.5)
}

/**
 * 第三步(解析)一次播放的结局.
 */
enum class ResolveOutcome {
    /** 预算内拦到播放链接. */
    Hit,

    /** 到点仍没拦到, 判定超时失败. */
    Timeout,

    /** 上一次失败之后换下一个候选重新交棒, 这次拦到. */
    HitAfterFallback,
}

/**
 * 第三步(解析)的参数.
 *
 * @param requestCount WebView 里一共会看到几条请求.
 * @param visibleRows 请求列表的可视行数, 超出的靠滚动露出.
 * @param hitRow 命中的那条请求在列表里的下标.
 * @param budget 拦截播放链接的最大等待时长. **只决定计时器旁边那个读数数到几**,
 * 不决定动画放多久 —— 指针转一圈的时长由 [Pacing.clockSweep] 定.
 * @param outcomes 这一轮要依次演哪几种结局.
 */
@Immutable
data class ResolveSpec(
    val requestCount: Int = 8,
    val visibleRows: Int = 4,
    val hitRow: Int = 5,
    val budget: Duration = 8.seconds,
    val outcomes: List<ResolveOutcome> = listOf(ResolveOutcome.Hit),
) {
    init {
        require(requestCount > 0) { "requestCount must be positive" }
        require(visibleRows in 1..requestCount) { "visibleRows must be within 1..requestCount" }
        require(hitRow in 0 until requestCount) { "hitRow out of range" }
        require(budget > Duration.ZERO) { "budget must be positive" }
        require(outcomes.isNotEmpty()) { "outcomes must not be empty" }
        require(outcomes.first() != ResolveOutcome.HitAfterFallback) {
            "HitAfterFallback must follow a Timeout"
        }
    }
}

/**
 * 演出节奏. 这些是"动画自己的时钟", 与 [SourceSpec.latency]、[ResolveSpec.budget] 这些"被演示的时间"是两回事:
 * 被演示的时间会按 [Pacing.timeScale] 压缩到动画时长上, 这里的值则是原样使用的演出参数.
 */
@Immutable
data class Pacing(
    /**
     * 把数据源的 [SourceSpec.latency] 这类"被演示的秒"换算成"动画的秒"的比例.
     * 0.3 表示演示里的 1 秒在动画里只占 0.3 秒.
     */
    val timeScale: Float = 0.3f,
    /**
     * **指针转满一圈用多久 (动画时间).** 两个计时器共用.
     *
     * 这是纯粹的演出参数, 与设置项里配的秒数无关 —— 配 20 秒还是 5 秒, 指针都是花这么久转一圈,
     * 变的只是旁边那个读数最后数到几. 换句话说, 改设置项不会让动画变长变短.
     */
    val clockSweep: Duration = 3.seconds + 500.milliseconds,
    /** 结果淡入 / 淡出的时长. */
    val fade: Duration = 240.milliseconds,
    /** 遍历 cursor 从一格走到下一格的时长. */
    val cursorStep: Duration = 240.milliseconds,
    /** 命中之后 cursor 还停留多久才退场. */
    val cursorExit: Duration = 160.milliseconds,
    /** 选中时那一下放大回落的时长. */
    val pop: Duration = 400.milliseconds,
    /** 选中涟漪扩散的时长. */
    val ripple: Duration = 560.milliseconds,
    /**
     * 走缓存时数据源连线画满的时长.
     *
     * 这是演出参数, 不是"被演示的查询耗时" —— 缓存直出本来就不花时间, 这一小段只是让人看见
     * 线是被画出来的, 而不是凭空出现的.
     */
    val cacheDraw: Duration = 140.milliseconds,
    /** 交棒连线画完的时长. */
    val handoff: Duration = 480.milliseconds,
    /** 交棒结束到窗口打开之间的空档. */
    val windowOpenDelay: Duration = 190.milliseconds,
    /** 窗口描边亮起的时长. */
    val windowOpen: Duration = 240.milliseconds,
    /** 相邻两条请求进入列表的间隔. */
    val rowStagger: Duration = 240.milliseconds,
    /** 列表滚动的时长. */
    val scroll: Duration = 800.milliseconds,
    /** 一段演示结束后的停留. */
    val hold: Duration = 800.milliseconds,
    /** 整轮结束后的停留. */
    val finalHold: Duration = 1040.milliseconds,
    /** 收尾复位的时长. */
    val reset: Duration = 320.milliseconds,
    /** 复位结束到下一轮开始之间的空档. */
    val loopGap: Duration = 80.milliseconds,
) {
    init {
        require(timeScale > 0f) { "timeScale must be positive" }
        require(clockSweep > Duration.ZERO) { "clockSweep must be positive" }
    }

    /**
     * 把"被演示的时间"换算成动画时长.
     */
    fun scaled(demoTime: Duration): Duration = demoTime * timeScale.toDouble()

    /**
     * [scaled] 的反函数: 想在动画里占 [animationTime] 这么久, 对应"被演示的时间"是多少.
     */
    fun unscaled(animationTime: Duration): Duration = animationTime / timeScale.toDouble()
}

/**
 * 整套演示动画的输入. 改这里就能改出另一套动画, 不需要动任何时间线代码.
 *
 * ## 选源规则
 *
 * - 结果分 **普通结果** 与 **候选结果** ([SourceSpec.candidates]);
 * - cursor 遍历到候选结果 **就选它**; 如果此刻已经选过一个, **就不再选**;
 * - 启用 [SelectionSpec.priorityWait] 时, 在这段时间内谁都不选, 只等高优先级源.
 */
@Immutable
data class SelectorWorkflowConfig(
    val sources: List<SourceSpec>,
    val selection: SelectionSpec = SelectionSpec(),
    val resolve: ResolveSpec = ResolveSpec(),
    val pacing: Pacing = Pacing(),
    /**
     * 结果容器的列数. 结果按 **源的顺序** 依次填进网格.
     */
    val gridColumns: Int = 2,
    /**
     * 第一步走缓存: 数据源的查询结果早就缓存下来了, 不必再等 [SourceSpec.latency].
     *
     * 打开后全部源同时"瞬间"返回, 连线转成 success 色并泛起一圈涟漪 (与选中候选、拦到播放链接
     * 是同一个动作), 表示这一步是白拿的. 线仍旧是画出来的, 只是画满只花 [Pacing.cacheDraw] ——
     * 凭空出现的线看不出是从哪连到哪的.
     *
     * 与 [SelectionSpec.demoBothPriorityPaths] 同时打开没有意义: 后者会为高优先级源编一个耗时,
     * 那条线于是不再是"瞬间"的. 上层保证一次只演一件事.
     */
    val cachedQuery: Boolean = false,
) {
    init {
        require(sources.isNotEmpty()) { "at least one source is required" }
        require(gridColumns > 0) { "gridColumns must be positive" }
        require(sources.count { it.priority } <= 1) { "at most one priority source is supported" }
        if (selection.priorityWait != null) {
            require(sources.any { it.priority }) {
                "priorityWait is set but no source is marked as priority"
            }
        }
    }

    /** 全部结果按网格顺序展开. */
    val results: List<ResultKey> = buildList {
        sources.forEachIndexed { s, spec ->
            repeat(spec.resultCount) { i -> add(ResultKey(s, i)) }
        }
    }

    /** 全部候选结果, 按网格顺序. */
    val candidates: List<ResultKey> = results.filter { it.isCandidate(this) }

    val priorityIndex: Int? = sources.indexOfFirst { it.priority }.takeIf { it >= 0 }

    /**
     * 要不要显示第三步的计时器.
     *
     * 只演"拦到了"那一种结局时不显示 —— 没有超时这回事, 摆个表在那里只会让人以为有个时限在跑.
     * 一旦要演超时, 表才有意义: 它是"预算用完了"的唯一表达.
     */
    val showInterceptClock: Boolean = resolve.outcomes.any { it == ResolveOutcome.Timeout }

    /**
     * 要不要画高优先级标记 (源节点上和它结果上的那个菱形).
     *
     * 没开 [SelectionSpec.priorityWait] 时, 源身上的 [SourceSpec.priority] 只是一个没生效的配置 ——
     * 这一轮里它和别的源没有任何区别, 标出来只会让人以为它有特殊待遇.
     */
    val showPriorityMarks: Boolean = selection.priorityWait != null && priorityIndex != null

    /**
     * 每个源这一轮实际要花多久返回 (被演示的时间).
     *
     * 走缓存时全都换成"画满一条线"那么久 —— 从这里统一换掉, 下游 (选源计划、结果何时淡入、
     * 连线画多久) 才会一致地看到"大家同时就位".
     */
    val effectiveLatencies: List<Duration> =
        if (cachedQuery) sources.map { pacing.unscaled(pacing.cacheDraw) } else sources.map { it.latency }

    /** [key] 在网格里的位置, 见 [results]. */
    fun cellOf(key: ResultKey): Int = results.indexOf(key)

    /** 网格总行数. */
    val gridRows: Int = (results.size + gridColumns - 1) / gridColumns

    companion object
}

/**
 * 定位一条搜索结果: 第 [source] 个源给出的第 [indexInSource] 条.
 */
@Immutable
data class ResultKey(val source: Int, val indexInSource: Int) {
    fun isCandidate(config: SelectorWorkflowConfig): Boolean =
        indexInSource in config.sources[source].candidates
}

/**
 * 两个计时器. 画法完全一样, 只是数的东西不同.
 */
enum class ClockId {
    /** 第一/二步左上角: 数"最大等待高优先级源的时长". */
    PriorityWait,

    /** 第三步右上角: 数"拦截播放链接的最大等待时长". */
    InterceptBudget,
}

/**
 * 一些常用的预设.
 */
@Stable
object SelectorWorkflowPresets {
    /**
     * 与设计稿一致的三源示例: A / B / C, C 慢且是高优先级源, B 的第一条与 C 的第一条是候选.
     */
    fun threeSources(
        mode: SelectMode = SelectMode.WaitAll,
        priorityWait: Duration? = null,
        resolveOutcomes: List<ResolveOutcome> = listOf(ResolveOutcome.Hit),
        interceptBudget: Duration = 8.seconds,
        prioritySourceLatency: Duration = 7.seconds,
    ): SelectorWorkflowConfig = SelectorWorkflowConfig(
        sources = listOf(
            SourceSpec("源 A", latency = 2.seconds + 700.milliseconds, resultCount = 3),
            SourceSpec("源 B", latency = 3.seconds + 700.milliseconds, resultCount = 3, candidates = setOf(0)),
            SourceSpec(
                "源 C", latency = prioritySourceLatency, resultCount = 2,
                candidates = setOf(0), priority = true,
            ),
        ),
        selection = SelectionSpec(mode = mode, priorityWait = priorityWait),
        resolve = ResolveSpec(budget = interceptBudget, outcomes = resolveOutcomes),
    )
}
