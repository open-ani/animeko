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
import kotlin.time.Duration

/**
 * ## 可控制单元清单
 *
 * 整套动画由下面 9 种基础单元拼出来. 每种单元只暴露少量与画法无关的可动属性,
 * 具体画成什么样 (颜色、圆角、坐标) 由 Canvas 层决定.
 *
 * | # | 单元 | 出现在 | 可动属性 |
 * |---|------|--------|----------|
 * | 1 | [SourceNodeState] | 第一步 | `alpha`, `pulsing` |
 * | 2 | [LineState] | 第一步的连线 / 第二步的交棒线 | `progress`, `alpha`, `tone` |
 * | 3 | [ResultChipState] | 第二步 | `alpha`, `tone`, `scale` |
 * | 4 | [RippleState] | 第二步 | `scale`, `alpha` |
 * | 5 | [CursorState] | 第二步 | `cell`(可插值), `alpha` |
 * | 6 | [ClockState] | 第一/二步、第三步各一个 | `sweep`, `alpha`, `tone`, `overlayAlpha` |
 * | 7 | [WindowState] | 第三步 | `tone` |
 * | 8 | [RequestRowState] | 第三步 | `alpha`, `icon`, `tone` |
 * | 9 | [ScrollState] | 第三步 | `rowOffset` |
 *
 * 其余元素 (结果容器边框、mac 三圆点、地址栏) 是静态的, 不属于可控制单元.
 *
 * ## 语义色怎么过渡
 *
 * 语义色 ([ChipTone] / [LineTone] / [RequestTone] / [ClockTone] / [WindowTone]) 本身是离散的, 颜色却该连续地
 * 过渡过去 —— 结果块转绿、表盘转红都不该"啪"地跳一下. 所以带语义色的单元一律给三样东西:
 * `tone` (要去的那个色)、`previousTone` (从哪个色来的)、`toneBlend` (0..1 的过渡进度).
 * Canvas 把前两个各映射成颜色再按进度插值, 状态层因此仍旧只谈语义, 不碰颜色.
 *
 * 过渡之外的时段 `previousTone == tone`, 这时 `toneBlend` 取什么值都是同一个颜色.
 */
@Immutable
data class SelectorWorkflowState(
    /** 当前播放位置. */
    val time: Duration,
    /** 整轮时长. */
    val duration: Duration,
    /** 当前处于哪一拍, 供调试与字幕使用. */
    val phase: String,
    val sourceNodes: List<SourceNodeState>,
    val sourceLinks: List<LineState>,
    val results: List<ResultChipState>,
    val ripples: List<RippleState>,
    val cursors: List<CursorState>,
    val handoff: LineState,
    val window: WindowState,
    val requestRows: List<RequestRowState>,
    val scroll: ScrollState,
    val clocks: Map<ClockId, ClockState>,
) {
    val progress: Float
        get() = if (duration <= Duration.ZERO) 0f
        else (time.inWholeMicroseconds.toFloat() / duration.inWholeMicroseconds).coerceIn(0f, 1f)
}

/** 数据源节点: 一个圆点 + 搜索中的脉冲光环. */
@Immutable
data class SourceNodeState(
    val index: Int,
    val alpha: Float,
    /** 是否正在搜索. Canvas 据此决定要不要画那圈自转的光环. */
    val pulsing: Boolean,
    /** 是否画高优先级标记 (节点里那个菱形). 只有真开了高优先级等待才为 `true`. */
    val priority: Boolean,
)

/** 连线的语义色. */
enum class LineTone {
    /** 用所属数据源的颜色. */
    Source,

    /** 这一条是缓存直出的. */
    Cached,
}

/**
 * 一条被"画出来"的线. 第一步的数据源连线和第二步末尾的交棒线是同一个单元.
 *
 * @param progress 0 = 一点没画, 1 = 画满.
 */
@Immutable
data class LineState(
    val progress: Float,
    val alpha: Float,
    val tone: LineTone = LineTone.Source,
    val previousTone: LineTone = tone,
    val toneBlend: Float = 1f,
) {
    companion object {
        val Hidden = LineState(progress = 0f, alpha = 0f)
    }
}

/** 结果块的语义色. Canvas 把它映射成 M3 token. */
enum class ChipTone {
    /** 用所属数据源的颜色. */
    Source,

    /** 被选中. */
    Selected,

    /** 这一条解析失败了. */
    Failed,
}

/**
 * 一条搜索结果.
 *
 * @param cell 在结果网格里的序号, Canvas 据此算坐标.
 * @param candidate 是否是候选结果 (画中心那个实心圆点).
 * @param priority 是否画高优先级标记 (那个实心菱形). 只有真开了高优先级等待才为 `true` ——
 * 见 [SelectorWorkflowConfig.showPriorityMarks].
 */
@Immutable
data class ResultChipState(
    val key: ResultKey,
    val cell: Int,
    val alpha: Float,
    val tone: ChipTone,
    val scale: Float,
    val candidate: Boolean,
    val priority: Boolean,
    val previousTone: ChipTone = tone,
    val toneBlend: Float = 1f,
)

/** 涟漪锚在什么上. */
enum class RippleTarget {
    /** 第二步的结果块, [RippleState.index] 是格号. */
    Result,

    /** 第三步命中的那条请求, [RippleState.index] 是行号. */
    RequestRow,

    /** 第一步走缓存时数据源节点上那一圈, [RippleState.index] 是数据源下标. */
    SourceNode,
}

/**
 * 选中 / 命中时扩散出去的涟漪. 第二步和第三步是同一个单元, 只是锚的东西不同.
 */
@Immutable
data class RippleState(
    val target: RippleTarget,
    val index: Int,
    val scale: Float,
    val alpha: Float,
)

/**
 * 遍历 cursor.
 *
 * @param owner 归属的数据源下标; `null` 表示"等待全部"模式下那个中性色的全局 cursor.
 * @param cell 当前位置. 是浮点数 —— 在两格之间移动时会取到中间值, Canvas 直接按它插值坐标.
 */
@Immutable
data class CursorState(
    val id: String,
    val owner: Int?,
    val cell: Float,
    val alpha: Float,
)

/** 计时器的语义状态. */
enum class ClockTone {
    /** 正在走. */
    Running,

    /** 在预算内停住了. */
    Stopped,

    /** 走满一圈, 超时. */
    Expired,
}

/**
 * 计时器.
 *
 * ## 转多久 与 数到几 是两件事
 *
 * 指针转满一圈花多久由演出参数 [Pacing.clockSweep] 定, 与设置项里配的秒数无关;
 * 设置项配的秒数只决定 **旁边那个读数数到几** ([budgetSeconds]).
 * 于是"最大等待 20 秒"和"最大等待 5 秒"放出来一样长, 差别只在读数从 0 数到 20.0 还是 5.0 ——
 * 读数也就成了辨认"这个表在数哪个设置项"的标识.
 *
 * @param sweep 指针已经扫过的比例, 0..1.
 * @param budgetSeconds 这个表数的是设置里配的多少秒. 0 表示没配, 不显示读数.
 * @param overlayAlpha 超时后罩在表盘上那层底色的不透明度.
 */
@Immutable
data class ClockState(
    val id: ClockId,
    val alpha: Float,
    val sweep: Float,
    val tone: ClockTone,
    val overlayAlpha: Float,
    val budgetSeconds: Float,
    val previousTone: ClockTone = tone,
    val toneBlend: Float = 1f,
) {
    /** 指针角度, 12 点为 0°, 顺时针. */
    val handDegrees: Float get() = sweep * 360f

    /** 旁边显示的读数: 把指针走过的比例线性映射回配置的秒数. */
    val elapsedSeconds: Float get() = sweep * budgetSeconds

    /** 有没有读数可显示. */
    val hasReadout: Boolean get() = budgetSeconds > 0f

    companion object {
        fun hidden(id: ClockId) = ClockState(
            id, alpha = 0f, sweep = 0f, tone = ClockTone.Running, overlayAlpha = 0f, budgetSeconds = 0f,
        )
    }
}

/** WebView 窗口的边框状态. */
enum class WindowTone {
    /** 还没打开. */
    Closed,

    /** 页面已加载. */
    Open,

    /** 本次解析失败. */
    Failed,
}

@Immutable
data class WindowState(
    val tone: WindowTone,
    val previousTone: WindowTone = tone,
    val toneBlend: Float = 1f,
)

/** 请求行左侧的图标. */
enum class RequestIcon {
    /** 普通请求. */
    Request,

    /** 命中的那条 (播放三角). */
    Media,
}

enum class RequestTone { Idle, Hit }

@Immutable
data class RequestRowState(
    val index: Int,
    val alpha: Float,
    val icon: RequestIcon,
    val tone: RequestTone,
    /** 命中那一下图标弹一弹, 与第二步选中结果块时是同一个动作. */
    val iconScale: Float,
    val previousTone: RequestTone = tone,
    val toneBlend: Float = 1f,
)

/**
 * 请求列表的滚动位置.
 *
 * @param rowOffset 往上滚了几行. Canvas 乘以行高就是位移.
 */
@Immutable
data class ScrollState(val rowOffset: Float)
