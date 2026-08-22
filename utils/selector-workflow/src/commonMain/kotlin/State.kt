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
 * | 2 | [LineState] | 第一步的连线 / 第二步的交棒线 | `progress`, `alpha` |
 * | 3 | [ResultChipState] | 第二步 | `alpha`, `tone`, `scale` |
 * | 4 | [RippleState] | 第二步 | `scale`, `alpha` |
 * | 5 | [CursorState] | 第二步 | `cell`(可插值), `alpha` |
 * | 6 | [ClockState] | 第一/二步、第三步各一个 | `sweep`, `alpha`, `tone`, `overlayAlpha` |
 * | 7 | [WindowState] | 第三步 | `tone` |
 * | 8 | [RequestRowState] | 第三步 | `alpha`, `icon`, `tone` |
 * | 9 | [ScrollState] | 第三步 | `rowOffset` |
 *
 * 其余元素 (结果容器边框、mac 三圆点、地址栏) 是静态的, 不属于可控制单元.
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
)

/**
 * 一条被"画出来"的线. 第一步的数据源连线和第二步末尾的交棒线是同一个单元.
 *
 * @param progress 0 = 一点没画, 1 = 画满.
 */
@Immutable
data class LineState(
    val progress: Float,
    val alpha: Float,
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
 * @param priority 是否属于高优先级源 (画左端那个实心菱形).
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
)

/** 选中时从结果块上扩散出去的涟漪. */
@Immutable
data class RippleState(
    val cell: Int,
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
 * @param sweep 指针已经扫过的比例, 0..1. 表盘一整圈就是配置里填的那个时长,
 * 所以"指针停在哪"完全由 `已用时间 / 预算` 决定, 不需要在任何地方硬编码角度.
 * @param overlayAlpha 超时后罩在表盘上那层底色的不透明度.
 */
@Immutable
data class ClockState(
    val id: ClockId,
    val alpha: Float,
    val sweep: Float,
    val tone: ClockTone,
    val overlayAlpha: Float,
) {
    /** 指针角度, 12 点为 0°, 顺时针. */
    val handDegrees: Float get() = sweep * 360f

    companion object {
        fun hidden(id: ClockId) = ClockState(id, alpha = 0f, sweep = 0f, tone = ClockTone.Running, overlayAlpha = 0f)
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
data class WindowState(val tone: WindowTone)

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
)

/**
 * 请求列表的滚动位置.
 *
 * @param rowOffset 往上滚了几行. Canvas 乘以行高就是位移.
 */
@Immutable
data class ScrollState(val rowOffset: Float)
