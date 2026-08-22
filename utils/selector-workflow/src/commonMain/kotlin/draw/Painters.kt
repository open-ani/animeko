/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow.draw

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import me.him188.ani.utils.selectorworkflow.ClockState
import me.him188.ani.utils.selectorworkflow.CursorState
import me.him188.ani.utils.selectorworkflow.LineState
import me.him188.ani.utils.selectorworkflow.RequestIcon
import me.him188.ani.utils.selectorworkflow.RequestRowState
import me.him188.ani.utils.selectorworkflow.RequestTone
import me.him188.ani.utils.selectorworkflow.ResultChipState
import me.him188.ani.utils.selectorworkflow.RippleState
import me.him188.ani.utils.selectorworkflow.ScrollState
import me.him188.ani.utils.selectorworkflow.SourceNodeState
import me.him188.ani.utils.selectorworkflow.WindowState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration

/**
 * 每个可控制单元一支画笔. 每支都只做三件事: 拿状态、拿几何、拿颜色, 然后画.
 *
 * 画笔之间互不知道对方存在, 顺序由 `drawSelectorWorkflow` 统一安排.
 */

// ------------------------------------------------------------------ 单元 1: 数据源节点

/** 光环脉冲一圈用多久. 与时间线无关, 直接由播放位置取相位, 所以不需要额外的轨道. */
private val HaloPeriod: Duration = Duration.parse("1.1s")

internal fun DrawScope.drawSourceNode(
    node: SourceNodeState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    time: Duration,
) {
    val m = layout.metrics
    val center = layout.nodeCenters[node.index]
    val color = palette.source(node.index)

    if (node.pulsing) {
        val phase = (time.inWholeMilliseconds.toFloat() / HaloPeriod.inWholeMilliseconds) % 1f
        drawCircle(
            color = color,
            radius = m.haloRadius * (HALO_FROM + (HALO_TO - HALO_FROM) * phase),
            center = center,
            alpha = (HALO_ALPHA * (1f - phase)).coerceIn(0f, 1f),
            style = Stroke(width = m.strokeMedium),
        )
    }
    drawCircle(color = color, radius = m.nodeRadius, center = center, alpha = node.alpha)
}

private const val HALO_FROM = 0.55f
private const val HALO_TO = 1.2f
private const val HALO_ALPHA = 0.55f

// ------------------------------------------------------------------ 单元 2: 线

/**
 * 一条按 [LineState.progress] 逐渐画出来的线. 背后压一条底纹, 表示"还没画到的那一段".
 */
internal fun DrawScope.drawProgressLine(
    line: LineState,
    from: Offset,
    to: Offset,
    color: Color,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    drawTrack: Boolean = true,
) {
    val m = layout.metrics
    if (drawTrack) {
        drawLine(palette.trackline, from, to, strokeWidth = m.strokeMedium, cap = StrokeCap.Round)
    }
    if (line.alpha <= 0.001f || line.progress <= 0.001f) return
    val head = Offset(
        from.x + (to.x - from.x) * line.progress,
        from.y + (to.y - from.y) * line.progress,
    )
    drawLine(color, from, head, strokeWidth = m.strokeBold, cap = StrokeCap.Round, alpha = line.alpha)
}

// ------------------------------------------------------------------ 单元 3: 结果块

internal fun DrawScope.drawResultChip(
    chip: ResultChipState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (chip.alpha <= 0.001f) return
    val m = layout.metrics
    val rect = layout.cells[chip.cell]
    val color = palette.chip(chip.tone, chip.key.source)

    scaleAbout(chip.scale, rect.center) {
        drawRoundRect(
            color = color,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(rect.height / 2f),
            alpha = chip.alpha,
        )
        drawChipMarks(chip, rect, layout, palette)
    }
}

/**
 * 结果块上的标记. 只有一个就它自己居中; 两个就并排, 这一对整体居中.
 */
private fun DrawScope.drawChipMarks(
    chip: ResultChipState,
    rect: Rect,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (!chip.candidate && !chip.priority) return
    val m = layout.metrics
    val center = rect.center
    val both = chip.candidate && chip.priority
    // 两个符号并排时按外缘对齐算偏移, 半径不同也能真居中
    val half = (m.priorityMarkRadius * 2 + m.markGap + m.candidateDotRadius * 2) / 2f
    val priorityDx = if (both) -half + m.priorityMarkRadius else 0f
    val candidateDx = if (both) half - m.candidateDotRadius else 0f

    if (chip.priority) {
        drawPath(
            path = diamondPath(Offset(center.x + priorityDx, center.y), m.priorityMarkRadius),
            color = palette.mark,
            alpha = chip.alpha,
        )
    }
    if (chip.candidate) {
        drawCircle(
            color = palette.mark,
            radius = m.candidateDotRadius,
            center = Offset(center.x + candidateDx, center.y),
            alpha = chip.alpha,
        )
    }
}

// ------------------------------------------------------------------ 单元 4: 涟漪

internal fun DrawScope.drawRipple(
    ripple: RippleState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (ripple.alpha <= 0.001f) return
    val m = layout.metrics
    val rect = layout.cells.getOrNull(ripple.cell) ?: return
    scaleAbout(ripple.scale, rect.center) {
        drawRoundRect(
            color = palette.success,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(rect.height / 2f),
            alpha = ripple.alpha,
            style = Stroke(width = m.strokeMedium),
        )
    }
}

// ------------------------------------------------------------------ 单元 5: 遍历 cursor

internal fun DrawScope.drawCursor(
    cursor: CursorState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (cursor.alpha <= 0.001f) return
    val m = layout.metrics
    val center = layout.cellCenter(cursor.cell)
    val size = Size(m.chipWidth + m.cursorInflate * 2, m.chipHeight + m.cursorInflate * 2)
    val topLeft = Offset(center.x - size.width / 2f, center.y - size.height / 2f)
    val radius = CornerRadius(size.height / 2f)
    // 归属源的颜色描边; 全局 cursor (owner == null) 用中性色
    val stroke = cursor.owner?.let { palette.source(it) } ?: palette.focus

    drawRoundRect(palette.focus, topLeft, size, radius, alpha = cursor.alpha * FOCUS_LAYER_ALPHA)
    drawRoundRect(stroke, topLeft, size, radius, alpha = cursor.alpha, style = Stroke(m.strokeThin))
}

/** M3 状态层不透明度. */
private const val FOCUS_LAYER_ALPHA = 0.08f

// ------------------------------------------------------------------ 单元 6: 计时器

internal fun DrawScope.drawClock(
    clock: ClockState,
    center: Offset,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (clock.alpha <= 0.001f) return
    val m = layout.metrics
    val r = m.clockRadius
    val ring = palette.clock(clock.tone)

    drawCircle(palette.surfaceLow, radius = r, center = center, alpha = clock.alpha)
    if (clock.overlayAlpha > 0.001f) {
        drawCircle(palette.error, radius = r, center = center, alpha = clock.alpha * clock.overlayAlpha)
    }
    drawCircle(ring, radius = r, center = center, alpha = clock.alpha, style = Stroke(m.strokeThin))
    // 12 点刻度
    drawCircle(
        palette.outline, radius = TICK_RADIUS, alpha = clock.alpha,
        center = Offset(center.x, center.y - r + TICK_INSET),
    )
    // 指针: 12 点为 0°, 顺时针
    val angle = (clock.handDegrees - 90f) * PI.toFloat() / 180f
    val length = r - TICK_INSET
    drawLine(
        color = palette.clockHand(clock.tone),
        start = center,
        end = Offset(center.x + cos(angle) * length, center.y + sin(angle) * length),
        strokeWidth = m.strokeMedium,
        cap = StrokeCap.Round,
        alpha = clock.alpha,
    )
}

private const val TICK_RADIUS = 0.8f
private const val TICK_INSET = 1.4f

// ------------------------------------------------------------------ 单元 7: 窗口

internal fun DrawScope.drawBrowserWindow(
    window: WindowState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    val m = layout.metrics
    val rect = layout.window
    val radius = CornerRadius(m.windowRadius)

    drawRoundRect(palette.surfaceHigh, rect.topLeft, rect.size, radius)
    drawRoundRect(
        palette.windowStroke(window.tone), rect.topLeft, rect.size, radius,
        style = Stroke(m.strokeMedium),
    )
    layout.chromeDots.forEach {
        drawCircle(palette.outlineVariant, radius = m.chromeDotRadius, center = it)
    }
    drawRoundRect(
        palette.outlineVariant,
        layout.addressBar.topLeft,
        layout.addressBar.size,
        CornerRadius(layout.addressBar.height / 2f),
    )
    drawRoundRect(
        palette.surfaceLow,
        layout.listViewport.topLeft,
        layout.listViewport.size,
        CornerRadius(m.windowInset),
    )
}

// ------------------------------------------------------------------ 单元 8 + 9: 请求列表与滚动

internal fun DrawScope.drawRequestList(
    rows: List<RequestRowState>,
    scroll: ScrollState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    val m = layout.metrics
    val viewport = layout.listViewport
    clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom) {
        translate(top = -scroll.rowOffset * m.rowHeight) {
            rows.forEach { row -> drawRequestRow(row, layout, palette) }
        }
    }
}

private fun DrawScope.drawRequestRow(
    row: RequestRowState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (row.alpha <= 0.001f) return
    val m = layout.metrics
    val cy = layout.rowCenterY(row.index)
    val iconCenter = Offset(layout.rowIconCenterX, cy)
    val hit = row.tone == RequestTone.Hit
    val iconColor = if (hit) palette.success else palette.requestIcon

    when (row.icon) {
        RequestIcon.Request -> drawCircle(iconColor, m.rowIconRadius, iconCenter, alpha = row.alpha)
        RequestIcon.Media -> drawPath(
            path = playTrianglePath(iconCenter, m.rowIconRadius),
            color = iconColor,
            alpha = row.alpha,
        )
    }
    val barWidth = layout.rowBarWidths.getOrElse(row.index) { m.chipWidth }
    drawRoundRect(
        color = palette.requestBar(row.tone),
        topLeft = Offset(layout.rowBarLeft, cy - m.rowBarHeight / 2f),
        size = Size(barWidth, m.rowBarHeight),
        cornerRadius = CornerRadius(m.rowBarHeight / 2f),
        alpha = row.alpha,
    )
}

// ------------------------------------------------------------------ 静态部分

internal fun DrawScope.drawResultContainer(layout: WorkflowLayout, palette: WorkflowPalette) {
    val m = layout.metrics
    val rect = layout.container
    val radius = CornerRadius(m.containerRadius)
    drawRoundRect(palette.surfaceLow, rect.topLeft, rect.size, radius)
    drawRoundRect(palette.outlineVariant, rect.topLeft, rect.size, radius, style = Stroke(m.strokeMedium))
}

// ------------------------------------------------------------------ 小工具

/** 绕 [pivot] 缩放着画. */
private inline fun DrawScope.scaleAbout(factor: Float, pivot: Offset, crossinline block: DrawScope.() -> Unit) {
    if (factor == 1f) {
        block()
    } else {
        scale(factor, factor, pivot) { block() }
    }
}

private fun diamondPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius, center.y)
    lineTo(center.x, center.y + radius)
    lineTo(center.x - radius, center.y)
    close()
}

private fun playTrianglePath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x - radius * 0.85f, center.y - radius)
    lineTo(center.x + radius * 1.15f, center.y)
    lineTo(center.x - radius * 0.85f, center.y + radius)
    close()
}
