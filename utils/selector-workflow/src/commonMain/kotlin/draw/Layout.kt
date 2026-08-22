/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow.draw

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import me.him188.ani.utils.selectorworkflow.SelectorWorkflowConfig
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 画法尺寸. 全部是 **虚拟单位** —— 绘制时整体缩放到实际可用空间, 所以这些数字只表达比例.
 *
 * 想改画面比例改这里, 不用碰任何绘制代码.
 */
@Immutable
data class WorkflowMetrics(
    /** 结果块. */
    val chipWidth: Float = 23f,
    val chipHeight: Float = 10f,
    val chipGapX: Float = 6f,
    val chipGapY: Float = 6f,
    /** 结果容器的内边距与圆角. */
    val containerPadding: Float = 6f,
    val containerRadius: Float = 10f,
    /** 数据源节点. */
    val nodeRadius: Float = 5f,
    val haloRadius: Float = 7f,
    val nodeSpacing: Float = 18f,
    /** 节点列到结果容器之间留多宽. */
    val linkLength: Float = 28f,
    /** 结果容器到 WebView 窗口之间留多宽 (交棒线). */
    val handoffLength: Float = 24f,
    /** WebView 窗口. */
    val windowWidth: Float = 110f,
    val windowHeight: Float = 70f,
    val windowRadius: Float = 10f,
    val windowInset: Float = 8f,
    val titleBarHeight: Float = 21f,
    val chromeDotRadius: Float = 3.5f,
    /** 请求行. */
    val rowHeight: Float = 10f,
    val rowIconRadius: Float = 3.5f,
    val rowBarHeight: Float = 4f,
    /** 计时器. */
    val clockRadius: Float = 6f,
    /** 计时器旁边那个读数的字高与它跟表之间的留白. 字高按 M3 labelMedium 的观感定. */
    val readoutHeight: Float = 8f,
    val readoutGap: Float = 3f,
    /**
     * 单个字符占多宽, 按字高的比例给. 读数只有数字、小数点和 s, 宽度基本固定,
     * 所以不用真去量文字就能把浮层的宽度算准.
     */
    val readoutCharWidth: Float = 0.58f,
    /** 第三步的计时器浮在内容区上: 离内容区边缘多远、自身内边距、圆角. */
    val overlayInset: Float = 3f,
    val overlayPadding: Float = 2.5f,
    val overlayRadius: Float = 4f,
    /** 遍历 cursor 框比结果块大出来的量. */
    val cursorInflate: Float = 2f,
    /** 候选圆点 / 高优先级菱形. */
    val candidateDotRadius: Float = 2f,
    val priorityMarkRadius: Float = 2.2f,
    val markGap: Float = 2f,
    /** 画面四周留白. */
    val outerPadding: Float = 8f,
    /** 线宽. */
    val hairline: Float = 1f,
    val strokeThin: Float = 1.2f,
    val strokeMedium: Float = 1.5f,
    val strokeBold: Float = 2.5f,
) {
    companion object {
        val Default = WorkflowMetrics()
    }
}

/**
 * 算好的几何. 纯数据、纯函数 —— 只由 [SelectorWorkflowConfig] 与 [WorkflowMetrics] 决定,
 * 改几个源、改几条结果, 画面自动跟着排, 不需要动绘制代码.
 *
 * 坐标都在 [canvasSize] 这个虚拟画布里, 绘制时统一缩放.
 */
@Immutable
class WorkflowLayout internal constructor(
    val config: SelectorWorkflowConfig,
    val metrics: WorkflowMetrics,
    val canvasSize: Size,
    /** 每个数据源节点的圆心. */
    val nodeCenters: List<Offset>,
    /** 每条数据源连线的起止点. 终点已经贴到结果容器左边的直边上. */
    val linkSegments: List<Pair<Offset, Offset>>,
    val container: Rect,
    /** 每一格结果块的矩形, 下标即 `cell`. */
    val cells: List<Rect>,
    /** 交棒线的起止点. */
    val handoffSegment: Pair<Offset, Offset>,
    val window: Rect,
    val chromeDots: List<Offset>,
    val addressBar: Rect,
    /** 请求列表的可视区. */
    val listViewport: Rect,
    /** 每条请求的横条宽度, 在 [listViewport] 里按行号取. */
    val rowBarWidths: List<Float>,
    val priorityClockCenter: Offset,
    /** 读数的锚点: 文字左端的竖直中心. */
    val priorityReadoutAnchor: Offset,
    /** 第三步计时器浮层的右上角. 浮层贴着它长, 宽度随读数变, 所以只有这个角是定的. */
    val interceptOverlayAnchor: Offset,
) {
    /**
     * 第三步计时器的浮层. 宽度按 **当前这一帧的读数** 算 —— 读数从 `0.0s` 数到 `12.0s`,
     * 位数一变浮层就跟着变宽, 因为它贴的是右上角, 所以只往左长.
     */
    fun interceptOverlay(readoutSeconds: Float): ClockOverlay = with(metrics) {
        val readoutW = readoutTextWidth(readoutSeconds, readoutHeight, readoutCharWidth)
        val height = clockRadius * 2 + overlayPadding * 2
        val width = overlayPadding * 2 + clockRadius * 2 + readoutGap + readoutW
        val bounds = Rect(
            interceptOverlayAnchor.x - width,
            interceptOverlayAnchor.y,
            interceptOverlayAnchor.x,
            interceptOverlayAnchor.y + height,
        )
        val clockCenter = Offset(bounds.left + overlayPadding + clockRadius, bounds.center.y)
        ClockOverlay(
            bounds = bounds,
            clockCenter = clockCenter,
            readoutAnchor = Offset(clockCenter.x + clockRadius + readoutGap, bounds.center.y),
            readoutWidth = readoutW,
        )
    }
    /** 第 [index] 行请求在 **未滚动** 时的行内基线 (行中心 y). */
    fun rowCenterY(index: Int): Float {
        val inset = (listViewport.height - config.resolve.visibleRows * metrics.rowHeight) / 2f
        return listViewport.top + inset + metrics.rowHeight * (index + 0.5f)
    }

    val rowIconCenterX: Float get() = listViewport.left + metrics.windowInset + metrics.rowIconRadius * 0.5f
    val rowBarLeft: Float get() = listViewport.left + metrics.windowInset * 2f + metrics.rowIconRadius

    /**
     * `cell` 可以是小数 —— cursor 在两格之间时取插值中心.
     */
    fun cellCenter(cell: Float): Offset {
        if (cells.isEmpty()) return container.center
        val lo = floor(cell).toInt().coerceIn(0, cells.lastIndex)
        val hi = ceil(cell).toInt().coerceIn(0, cells.lastIndex)
        val f = (cell - lo).coerceIn(0f, 1f)
        val a = cells[lo].center
        val b = cells[hi].center
        return Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }

    companion object {
        /**
         * 按配置排出整张图.
         */
        fun of(
            config: SelectorWorkflowConfig,
            metrics: WorkflowMetrics = WorkflowMetrics.Default,
        ): WorkflowLayout = with(metrics) {
            val columns = config.gridColumns
            val rows = config.gridRows.coerceAtLeast(1)

            val containerWidth = containerPadding * 2 + columns * chipWidth + (columns - 1) * chipGapX
            val containerHeight = containerPadding * 2 + rows * chipHeight + (rows - 1) * chipGapY

            // 画面高度取"结果容器"与"WebView 窗口"里高的那个, 再加上下留白
            val contentHeight = max(containerHeight, windowHeight)
            val height = contentHeight + outerPadding * 2

            val nodeColumnX = outerPadding + haloRadius
            val containerLeft = nodeColumnX + haloRadius + linkLength
            val windowLeft = containerLeft + containerWidth + handoffLength
            val width = windowLeft + windowWidth + outerPadding

            val containerTop = outerPadding + (contentHeight - containerHeight) / 2f
            val container = Rect(
                containerLeft, containerTop,
                containerLeft + containerWidth, containerTop + containerHeight,
            )
            val windowTop = outerPadding + (contentHeight - windowHeight) / 2f
            val window = Rect(windowLeft, windowTop, windowLeft + windowWidth, windowTop + windowHeight)

            // 数据源节点: 以结果容器的竖直中心为轴等距排开
            val n = config.sources.size
            val axis = container.center.y
            val nodeCenters = List(n) { i ->
                Offset(nodeColumnX, axis + (i - (n - 1) / 2f) * nodeSpacing)
            }

            // 连线终点贴在容器左侧的 **直边** 上, 不碰圆角
            val straightTop = container.top + containerRadius
            val straightBottom = container.bottom - containerRadius
            val linkSegments = nodeCenters.map { c ->
                val y = c.y.coerceIn(straightTop, straightBottom)
                Offset(c.x + nodeRadius + 1f, c.y) to Offset(container.left, y)
            }

            val cells = List(config.results.size) { index ->
                val col = index % columns
                val row = index / columns
                val left = container.left + containerPadding + col * (chipWidth + chipGapX)
                val top = container.top + containerPadding + row * (chipHeight + chipGapY)
                Rect(left, top, left + chipWidth, top + chipHeight)
            }

            val listViewport = Rect(
                window.left + windowInset,
                window.top + titleBarHeight,
                window.right - windowInset,
                window.top + titleBarHeight + config.resolve.visibleRows * rowHeight + 2f,
            )

            val titleBarCenterY = window.top + titleBarHeight / 2f
            val chromeDots = List(CHROME_DOT_COUNT) { i ->
                Offset(
                    listViewport.left + chromeDotRadius + i * (chromeDotRadius * 2 + CHROME_DOT_GAP),
                    titleBarCenterY,
                )
            }
            // 地址栏占满标题栏剩下的宽度 —— 计时器不在这一行, 它浮在内容区上
            val addressLeft = chromeDots.last().x + chromeDotRadius + ADDRESS_BAR_GAP
            val addressBar = Rect(
                addressLeft,
                titleBarCenterY - chromeDotRadius,
                max(listViewport.right, addressLeft + chromeDotRadius * 2),
                titleBarCenterY + chromeDotRadius,
            )

            // 第三步的计时器做成浮层, 贴在内容区的右上角: [表][gap][读数].
            // 宽度不在这里定 —— 见 interceptOverlay(), 它按当前这一帧的读数算
            val overlayAnchor = Offset(
                listViewport.right - overlayInset,
                listViewport.top + overlayInset,
            )

            val barSpan = listViewport.right - (listViewport.left + windowInset * 2f + rowIconRadius) - windowInset
            val rowBarWidths = List(config.resolve.requestCount) { i ->
                barSpan * (BAR_MIN_RATIO + (BAR_MAX_RATIO - BAR_MIN_RATIO) * pseudoRandom(i))
            }

            WorkflowLayout(
                config = config,
                metrics = metrics,
                canvasSize = Size(width, height),
                nodeCenters = nodeCenters,
                linkSegments = linkSegments,
                container = container,
                cells = cells,
                handoffSegment = Offset(container.right, axis) to Offset(window.left, axis),
                window = window,
                chromeDots = chromeDots,
                addressBar = addressBar,
                listViewport = listViewport,
                rowBarWidths = rowBarWidths,
                priorityClockCenter = Offset(nodeColumnX, outerPadding),
                priorityReadoutAnchor = Offset(nodeColumnX + clockRadius + readoutGap, outerPadding),
                interceptOverlayAnchor = overlayAnchor,
            )
        }

        /**
         * 这一行读数占多宽. 只有数字、小数点和 s, 字宽基本固定, 所以按字符数估就够准,
         * 不必把 TextMeasurer 塞进布局层.
         */
        internal fun readoutTextWidth(seconds: Float, height: Float, charWidth: Float): Float =
            formatSeconds(seconds).length * height * charWidth

        private const val CHROME_DOT_COUNT = 3
        private const val CHROME_DOT_GAP = 2f
        private const val ADDRESS_BAR_GAP = 6f
        private const val BAR_MIN_RATIO = 0.52f
        private const val BAR_MAX_RATIO = 0.96f

        /**
         * 让每行请求的横条长短不一, 但对同一个下标恒定 —— 不用随机数, 免得每帧都在变.
         */
        private fun pseudoRandom(index: Int): Float = ((index * 37 + 11) % 23) / 22f
    }
}

/**
 * 一帧里第三步计时器浮层的几何.
 */
@Immutable
data class ClockOverlay(
    val bounds: Rect,
    val clockCenter: Offset,
    val readoutAnchor: Offset,
    val readoutWidth: Float,
)
