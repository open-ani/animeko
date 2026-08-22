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
    fun `more results grow the container downwards, not the chips`() {
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
    fun `title bar fits chrome dots, address bar and clock without overlap`() {
        val layout = layoutOf(SelectorWorkflowPresets.threeSources())
        val lastDot = layout.chromeDots.last()
        assertTrue(
            layout.addressBar.left > lastDot.x + metrics.chromeDotRadius,
            "地址栏压到 caption button 上了",
        )
        assertTrue(
            layout.addressBar.right <= layout.interceptClockCenter.x - metrics.clockRadius + 0.01f,
            "地址栏压到计时器上了",
        )
        assertTrue(
            layout.interceptClockCenter.x + metrics.clockRadius <= layout.listViewport.right + 0.01f,
            "计时器该和内容区右缘对齐, 不该探出窗口",
        )
        assertEquals(
            layout.listViewport.left, layout.chromeDots.first().x - metrics.chromeDotRadius, 0.01f,
            "caption button 左缘该和内容区左缘对齐",
        )
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
    fun `metrics drive the picture, not hard coded numbers`() {
        val config = SelectorWorkflowPresets.threeSources()
        val wide = WorkflowLayout.of(config, metrics.copy(chipWidth = metrics.chipWidth * 2))
        val base = layoutOf(config)
        assertTrue(wide.container.width > base.container.width)
        assertTrue(wide.canvasSize.width > base.canvasSize.width)
        assertEquals(base.container.height, wide.container.height, 0.01f, "只改宽不该影响高")
    }
}
