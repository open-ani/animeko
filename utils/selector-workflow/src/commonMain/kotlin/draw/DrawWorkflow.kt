/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow.draw

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import me.him188.ani.utils.selectorworkflow.ClockId
import me.him188.ani.utils.selectorworkflow.SelectorWorkflowState
import kotlin.math.min

/**
 * 把一帧画出来.
 *
 * 这里只做两件事: 把虚拟画布缩放居中到实际尺寸, 然后按 z 序把各支画笔叫一遍.
 * 每支画笔的实现见 `Painters.kt`.
 */
fun DrawScope.drawSelectorWorkflow(
    state: SelectorWorkflowState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    /** 画计时器旁边那个读数用. 传 `null` 就不画读数 (例如脱离 composition 的截图测试). */
    textMeasurer: TextMeasurer? = null,
    /** 读数的排版. 默认对齐 M3 labelMedium. */
    readoutStyle: TextStyle = DefaultReadoutStyle,
) {
    val factor = min(size.width / layout.canvasSize.width, size.height / layout.canvasSize.height)
    if (factor <= 0f) return
    val dx = (size.width - layout.canvasSize.width * factor) / 2f
    val dy = (size.height - layout.canvasSize.height * factor) / 2f

    translate(dx, dy) {
        scale(factor, factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawContent(state, layout, palette, textMeasurer, readoutStyle)
        }
    }
}

/**
 * z 序: 底 → 顶.
 *
 * 连线画在结果容器之后, 这样线头压在容器边上而不是被容器盖住;
 * 数据源节点最后画, 光环才不会被连线切开.
 */
private fun DrawScope.drawContent(
    state: SelectorWorkflowState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    textMeasurer: TextMeasurer?,
    readoutStyle: TextStyle,
) {
    // 第三步: 窗口与请求列表 (在最底下, 交棒线会压过来)
    drawBrowserWindow(state.window, layout, palette)
    drawRequestList(state.requestRows, state.scroll, layout, palette)
    // 计时器浮在请求列表之上, 所以要在列表之后画, 而且自带一层底.
    // 浮层的宽度按这一帧的读数算, 读数位数一变它就跟着变宽
    val intercept = state.clocks.getValue(ClockId.InterceptBudget)
    val overlay = layout.interceptOverlay(intercept.elapsedSeconds)
    drawClockOverlay(intercept, overlay.bounds, layout, palette)
    drawClock(
        intercept,
        overlay.clockCenter, overlay.readoutAnchor, layout, palette,
        textMeasurer, readoutStyle,
    )

    // 第二步: 结果容器、结果、涟漪、cursor
    drawResultContainer(layout, palette)
    state.results.forEach { drawResultChip(it, layout, palette) }
    state.ripples.forEach { drawRipple(it, layout, palette) }
    state.cursors.forEach { drawCursor(it, layout, palette) }

    // 交棒线
    val (handoffFrom, handoffTo) = layout.handoffSegment
    drawProgressLine(
        line = state.handoff,
        from = handoffFrom,
        to = handoffTo,
        color = palette.success,
        layout = layout,
        palette = palette,
        drawTrack = false,
    )

    // 第一步: 连线与数据源节点
    state.sourceLinks.forEachIndexed { index, line ->
        val (from, to) = layout.linkSegments[index]
        drawProgressLine(line, from, to, palette.source(index), layout, palette)
    }
    state.sourceNodes.forEach { drawSourceNode(it, layout, palette, state.time) }
    drawClock(
        state.clocks.getValue(ClockId.PriorityWait),
        layout.priorityClockCenter, layout.priorityReadoutAnchor, layout, palette,
        textMeasurer, readoutStyle,
    )
}
