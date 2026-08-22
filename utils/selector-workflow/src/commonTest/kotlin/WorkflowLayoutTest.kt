/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import me.him188.ani.utils.selectorworkflow.draw.WorkflowLayout
import me.him188.ani.utils.selectorworkflow.draw.WorkflowMetrics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * 几何全部由配置推出来 —— 这些用例盯的就是"改配置画面自动跟着排", 不是盯某个具体数值.
 */
class WorkflowLayoutTest {

    private val metrics = WorkflowMetrics.Default

    private fun layoutOf(config: SelectorWorkflowConfig) = WorkflowLayout.of(config, metrics)

    // ------------------------------------------------------------------ 高亮框

    @Test
    fun `each highlight box wraps the thing it points at`() {
        val layout = layoutOf(SelectorWorkflowPresets.threeSources())

        val results = layout.highlight(HighlightRegion.Results).bounds
        assertTrue(results.contains(layout.container.topLeft), "第二步的框该罩在容器外面")
        assertTrue(results.left < layout.container.left && results.right > layout.container.right)
        assertTrue(results.top < layout.container.top && results.bottom > layout.container.bottom)

        val resolve = layout.highlight(HighlightRegion.Resolve).bounds
        assertTrue(resolve.left < layout.window.left && resolve.right > layout.window.right)
        assertTrue(resolve.top < layout.window.top && resolve.bottom > layout.window.bottom)

        val sources = layout.highlight(HighlightRegion.Sources).bounds
        layout.nodeCenters.forEachIndexed { i, c ->
            assertTrue(sources.contains(c), "第 $i 个源节点没被罩住")
            assertTrue(c.x - metrics.haloRadius > sources.left, "第 $i 个源的光环探出框外了")
        }
        // 连线一路画到容器边上, 框还得再越过去一截 —— 它是 overlay, 交叉过去无所谓
        assertTrue(sources.right > layout.container.left, "框该越过结果容器的边线")
        layout.linkSegments.forEach { (from, to) ->
            assertTrue(from.x >= sources.left && to.x <= sources.right, "连线没被完全罩住")
        }
        assertTrue(sources.overlaps(results), "两块一起亮时该是交叉的, 不是并排")
    }

    @Test
    fun `no highlight box escapes the canvas`() {
        // 框画在内容外面, highlightInset 比 outerPadding 大就会被画布裁掉
        listOf(
            SelectorWorkflowPresets.threeSources(),
            SelectorWorkflowPresets.threeSources(priorityWait = 5.seconds),
        ).forEach { config ->
            val layout = layoutOf(config)
            HighlightRegion.entries.forEach { region ->
                val b = layout.highlight(region).bounds
                assertTrue(b.left >= 0f, "$region 的框左边出画布了: ${b.left}")
                assertTrue(b.top >= 0f, "$region 的框上边出画布了: ${b.top}")
                assertTrue(b.right <= layout.canvasSize.width, "$region 的框右边出画布了")
                assertTrue(b.bottom <= layout.canvasSize.height, "$region 的框下边出画布了")
            }
        }
    }

    @Test
    fun `a highlight box is concentric with what it wraps`() {
        // 圆角要跟着外扩量一起长, 两条弧才是同心的
        val layout = layoutOf(SelectorWorkflowPresets.threeSources())
        assertEquals(
            metrics.containerRadius + metrics.highlightInset,
            layout.highlight(HighlightRegion.Results).cornerRadius,
            1e-3f,
        )
        assertEquals(
            metrics.windowRadius + metrics.highlightInset,
            layout.highlight(HighlightRegion.Resolve).cornerRadius,
            1e-3f,
        )
        // 第一步没有被罩者, 圆角是自由的 —— 照同一个公式算会圆得不成样子
        assertTrue(
            layout.highlight(HighlightRegion.Sources).cornerRadius <
                    layout.highlight(HighlightRegion.Results).cornerRadius,
        )
    }

    @Test
    fun `grid holds exactly one cell per result and they do not overlap`() {
        val config = SelectorWorkflowPresets.threeSources()
        val layout = layoutOf(config)
        assertEquals(config.results.size, layout.cells.size)
        layout.cells.forEachIndexed { i, a ->
            layout.cells.drop(i + 1).forEach { b ->
                assertTrue(a.overlaps(b).not(), "cell $i overlaps another: $a / $b")
            }
            assertTrue(layout.container.contains(a.topLeft), "cell $i escapes the container")
            assertTrue(layout.container.contains(a.bottomRight - androidx.compose.ui.geometry.Offset(0.01f, 0.01f)))
        }
    }

    @Test
    fun `more results grow the container downwards not the chips`() {
        val small = layoutOf(SelectorWorkflowPresets.threeSources())
        val big = layoutOf(
            SelectorWorkflowConfig(
                sources = List(4) { SourceSpec("源 $it", (it + 1).seconds, resultCount = 4, candidates = setOf(0)) },
                resolve = ResolveSpec(budget = 30.seconds),
            ),
        )
        assertEquals(small.cells[0].width, big.cells[0].width, "结果块尺寸不该随数量变")
        assertTrue(big.container.height > small.container.height, "结果多了容器该变高")
        assertTrue(big.canvasSize.height > small.canvasSize.height)
    }

    @Test
    fun `source links land on the straight part of the container edge`() {
        // 源很多时, 首尾两个节点会超出容器的竖直范围; 连线终点必须夹到直边上, 不能落在圆角里
        val config = SelectorWorkflowConfig(
            sources = List(7) { SourceSpec("源 $it", (it + 1).seconds, resultCount = 1, candidates = setOf(0)) },
            resolve = ResolveSpec(budget = 30.seconds),
        )
        val layout = layoutOf(config)
        val top = layout.container.top + metrics.containerRadius
        val bottom = layout.container.bottom - metrics.containerRadius
        layout.linkSegments.forEachIndexed { i, (_, end) ->
            assertEquals(layout.container.left, end.x, "连线终点该贴在容器左边: $i")
            assertTrue(end.y in top..bottom, "连线 $i 落到圆角上了: ${end.y} 不在 $top..$bottom")
        }
    }

    @Test
    fun `source nodes are centred on the container axis`() {
        val layout = layoutOf(SelectorWorkflowPresets.threeSources())
        val mid = layout.nodeCenters.sumOf { it.y.toDouble() } / layout.nodeCenters.size
        assertTrue(
            abs(mid - layout.container.center.y) < 0.01,
            "节点该以容器竖直中心为轴排开, 实际 $mid vs ${layout.container.center.y}",
        )
    }

    @Test
    fun `the address bar fills the rest of the title bar`() {
        val layout = layoutOf(SelectorWorkflowPresets.threeSources())
        val lastDot = layout.chromeDots.last()
        assertTrue(
            layout.addressBar.left > lastDot.x + metrics.chromeDotRadius,
            "地址栏压到 caption button 上了",
        )
        assertEquals(
            layout.listViewport.right, layout.addressBar.right, 0.01f,
            "计时器挪走之后地址栏该占满剩下的宽度, 右缘与内容区对齐",
        )
        assertEquals(
            layout.listViewport.left, layout.chromeDots.first().x - metrics.chromeDotRadius, 0.01f,
            "caption button 左缘该和内容区左缘对齐",
        )
    }

    @Test
    fun `the intercept clock floats in the top right of the content area`() {
        val layout = layoutOf(SelectorWorkflowPresets.threeSources())
        val overlay = layout.interceptOverlay(3.4f).bounds
        val list = layout.listViewport
        assertTrue(overlay.left > list.left && overlay.right < list.right, "浮层该整个在内容区里")
        assertTrue(overlay.top > list.top && overlay.bottom < list.bottom, "浮层该整个在内容区里")
        assertEquals(
            list.right - overlay.right, overlay.top - list.top, 0.01f,
            "浮层离右缘和离顶缘该一样远 —— 它贴的是右上角",
        )
        // 表在左、读数在右, 两个都在浮层里
        val o = layout.interceptOverlay(3.4f)
        assertTrue(o.clockCenter.x - metrics.clockRadius >= overlay.left - 0.01f, "表该在浮层里")
        assertTrue(o.readoutAnchor.x >= o.clockCenter.x + metrics.clockRadius, "读数该在表的右边")
        assertTrue(o.readoutAnchor.x + o.readoutWidth <= overlay.right + 0.01f, "读数该在浮层里")
        assertEquals(overlay.center.y, o.clockCenter.y, 0.01f)
        assertEquals(overlay.center.y, o.readoutAnchor.y, 0.01f)
    }

    @Test
    fun `the overlay width follows the readout of this frame`() {
        val layout = layoutOf(SelectorWorkflowPresets.threeSources())
        val short = layout.interceptOverlay(9.9f)    // "9.9s"
        val long = layout.interceptOverlay(10.0f)    // "10.0s" —— 多一位
        assertTrue(long.readoutWidth > short.readoutWidth, "读数多一位, 浮层该变宽")
        assertEquals(
            long.readoutWidth - short.readoutWidth,
            long.bounds.width - short.bounds.width,
            0.01f,
            "浮层只该按读数宽度那部分变宽",
        )
        assertEquals(
            short.bounds.right, long.bounds.right, 0.01f,
            "浮层贴的是右上角, 变宽只往左长",
        )
        // 位数一样就一样宽 —— 不会因为数字不同抖动
        assertEquals(layout.interceptOverlay(1.1f).bounds, layout.interceptOverlay(8.7f).bounds)
    }

    @Test
    fun `the overlay does not depend on the configured budget`() {
        // 宽度只看这一帧显示什么, 与配的秒数无关
        val a = layoutOf(SelectorWorkflowPresets.threeSources(interceptBudget = 8.seconds))
        val b = layoutOf(SelectorWorkflowPresets.threeSources(interceptBudget = 120.seconds))
        assertEquals(a.interceptOverlay(3.4f).bounds, b.interceptOverlay(3.4f).bounds)
        assertEquals(a.interceptOverlayAnchor, b.interceptOverlayAnchor)
    }

    @Test
    fun `visible rows exactly fill the list viewport`() {
        val config = SelectorWorkflowPresets.threeSources()
        val layout = layoutOf(config)
        val first = layout.rowCenterY(0)
        val last = layout.rowCenterY(config.resolve.visibleRows - 1)
        assertTrue(first - metrics.rowHeight / 2 >= layout.listViewport.top - 0.01f, "第一行探出可视区顶部")
        assertTrue(last + metrics.rowHeight / 2 <= layout.listViewport.bottom + 0.01f, "最后一行探出可视区底部")
        // 行距恒定
        val gaps = (1 until config.resolve.requestCount).map { layout.rowCenterY(it) - layout.rowCenterY(it - 1) }
        assertTrue(gaps.all { abs(it - metrics.rowHeight) < 0.001f })
    }

    @Test
    fun `cell centre interpolates between two cells`() {
        val layout = layoutOf(SelectorWorkflowPresets.threeSources())
        val a = layout.cellCenter(0f)
        val b = layout.cellCenter(1f)
        val mid = layout.cellCenter(0.5f)
        assertEquals((a.x + b.x) / 2f, mid.x, 0.01f)
        assertEquals((a.y + b.y) / 2f, mid.y, 0.01f)
    }

    @Test
    fun `bar widths are stable and stay inside the viewport`() {
        val config = SelectorWorkflowPresets.threeSources()
        val layout = layoutOf(config)
        val again = layoutOf(config)
        assertEquals(layout.rowBarWidths, again.rowBarWidths, "同一份配置两次排版必须一样")
        assertTrue(layout.rowBarWidths.distinct().size > 1, "横条该长短不一")
        layout.rowBarWidths.forEach {
            assertTrue(
                layout.rowBarLeft + it <= layout.listViewport.right + 0.01f,
                "横条探出可视区: $it",
            )
        }
    }

    @Test
    fun `metrics drive the picture not hard coded numbers`() {
        val config = SelectorWorkflowPresets.threeSources()
        val wide = WorkflowLayout.of(config, metrics.copy(chipWidth = metrics.chipWidth * 2))
        val base = layoutOf(config)
        assertTrue(wide.container.width > base.container.width)
        assertTrue(wide.canvasSize.width > base.canvasSize.width)
        assertEquals(base.container.height, wide.container.height, 0.01f, "只改宽不该影响高")
    }
}
