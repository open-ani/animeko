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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.him188.ani.utils.selectorworkflow.ClockState
import me.him188.ani.utils.selectorworkflow.CursorState
import me.him188.ani.utils.selectorworkflow.LineState
import me.him188.ani.utils.selectorworkflow.RequestIcon
import me.him188.ani.utils.selectorworkflow.RequestRowState
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
    if (node.priority) {
        drawPath(diamondPath(center, m.priorityNodeMarkRadius), palette.mark, alpha = node.alpha)
    }
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
    val color = blendTone(chip.previousTone, chip.tone, chip.toneBlend) { palette.chip(it, chip.key.source) }

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

internal fun DrawScope.drawResultRipple(
    ripple: RippleState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (ripple.alpha <= 0.001f) return
    val m = layout.metrics
    val rect = layout.cells.getOrNull(ripple.index) ?: return
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

/**
 * 命中那条请求的涟漪. 与结果块那圈是同一个动作, 只是锚在请求行的图标上 ——
 * 行本身太宽, 按同样的倍数扩会整个冲出可视区.
 */
private fun DrawScope.drawRequestRipple(
    ripple: RippleState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (ripple.alpha <= 0.001f) return
    val m = layout.metrics
    val center = Offset(layout.rowIconCenterX, layout.rowCenterY(ripple.index))
    drawCircle(
        color = palette.success,
        radius = m.rowIconRadius * ripple.scale,
        center = center,
        alpha = ripple.alpha,
        style = Stroke(width = m.strokeMedium),
    )
}

/**
 * 走缓存时数据源节点上那一圈涟漪. 与结果块、请求行那两圈仍是同一个动作, 只是锚在节点上.
 */
internal fun DrawScope.drawSourceRipple(
    ripple: RippleState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (ripple.alpha <= 0.001f) return
    val m = layout.metrics
    val center = layout.nodeCenters.getOrNull(ripple.index) ?: return
    drawCircle(
        color = palette.success,
        radius = m.nodeRadius * ripple.scale,
        center = center,
        alpha = ripple.alpha,
        style = Stroke(width = m.strokeMedium),
    )
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

/**
 * 计时器 + 旁边的读数.
 *
 * 指针转多快由时间线定; 读数是把指针走过的比例映射回设置项配的秒数 —— 于是读数就是
 * "这个表在数哪个设置项"的标识. [textMeasurer] 为 `null` 时不画读数.
 */
internal fun DrawScope.drawClock(
    clock: ClockState,
    center: Offset,
    readoutAnchor: Offset,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    textMeasurer: TextMeasurer?,
    readoutStyle: TextStyle,
) {
    if (clock.alpha <= 0.001f) return
    val m = layout.metrics
    val r = m.clockRadius
    val ring = blendTone(clock.previousTone, clock.tone, clock.toneBlend, palette::clock)
    val hand = blendTone(clock.previousTone, clock.tone, clock.toneBlend, palette::clockHand)

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
        color = hand,
        start = center,
        end = Offset(center.x + cos(angle) * length, center.y + sin(angle) * length),
        strokeWidth = m.strokeMedium,
        cap = StrokeCap.Round,
        alpha = clock.alpha,
    )

    if (textMeasurer != null && clock.hasReadout) {
        drawScaledText(
            textMeasurer = textMeasurer,
            text = formatSeconds(clock.elapsedSeconds),
            anchor = readoutAnchor,
            targetHeight = m.readoutHeight,
            color = hand,
            alpha = clock.alpha,
            style = readoutStyle,
        )
    }
}

/** 读数格式: 一位小数 + 秒. */
internal fun formatSeconds(seconds: Float): String {
    val tenths = (seconds * 10f + 0.5f).toInt().coerceAtLeast(0)
    return "${tenths / 10}.${tenths % 10}s"
}

/**
 * 在虚拟坐标里画一行文字.
 *
 * 文字先按固定字号排版, 再缩放到 [targetHeight] 这个虚拟单位的高度 ——
 * 这样不管 composition 的 density 是多少, 文字在图里的相对大小都一样.
 * [anchor] 是文字左端的竖直中心.
 */
private fun DrawScope.drawScaledText(
    textMeasurer: TextMeasurer,
    text: String,
    anchor: Offset,
    targetHeight: Float,
    color: Color,
    alpha: Float,
    style: TextStyle,
) {
    val measured = textMeasurer.measure(text, style)
    val height = measured.size.height.toFloat()
    if (height <= 0f) return
    val k = targetHeight / height
    translate(anchor.x, anchor.y - targetHeight / 2f) {
        scale(k, k, pivot = Offset.Zero) {
            drawText(measured, color = color, alpha = alpha)
        }
    }
}

/**
 * 读数的默认排版, 形状对齐 M3 labelMedium (Medium 字重 + 0.5/12 的字距).
 *
 * 字号在这里只用来排版, 最终高度由 [WorkflowMetrics.readoutHeight] 决定 —— 画的时候会缩放到那个高度,
 * 所以这里填多少都不影响成图, 填大一点只是让排版更精确.
 */
val DefaultReadoutStyle = TextStyle(
    fontSize = 24.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.sp,
)

private const val TICK_RADIUS = 0.8f
private const val TICK_INSET = 1.4f

/**
 * 第三步计时器那块浮层的底. 它压在请求列表上, 得先把身下的横条挡掉, 不然读数没法看.
 */
internal fun DrawScope.drawClockOverlay(
    clock: ClockState,
    rect: Rect,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (clock.alpha <= 0.001f) return
    val m = layout.metrics
    val radius = CornerRadius(m.overlayRadius)
    drawRoundRect(palette.surfaceHigh, rect.topLeft, rect.size, radius, alpha = clock.alpha)
    drawRoundRect(
        color = palette.outlineVariant,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = radius,
        alpha = clock.alpha,
        style = Stroke(width = m.hairline),
    )
}

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
        blendTone(window.previousTone, window.tone, window.toneBlend, palette::windowStroke),
        rect.topLeft, rect.size, radius,
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
    ripples: List<RippleState>,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    val m = layout.metrics
    val viewport = layout.listViewport
    // 涟漪跟着列表一起滚, 所以画在同一个变换里
    clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom) {
        translate(top = -scroll.rowOffset * m.rowHeight) {
            rows.forEach { row -> drawRequestRow(row, layout, palette) }
            ripples.forEach { drawRequestRipple(it, layout, palette) }
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
    // 图标与横条读同一路 tone, 同时起变
    val iconColor = blendTone(row.previousTone, row.tone, row.toneBlend, palette::rowIcon)
    val barColor = blendTone(row.previousTone, row.tone, row.toneBlend, palette::requestBar)

    scaleAbout(row.iconScale, iconCenter) {
        when (row.icon) {
            RequestIcon.Request -> drawCircle(iconColor, m.rowIconRadius, iconCenter, alpha = row.alpha)
            RequestIcon.Media -> drawPlayTriangle(
                center = iconCenter,
                radius = m.rowIconRadius,
                corner = m.rowIconCornerRadius,
                color = iconColor,
                alpha = row.alpha,
            )
        }
    }
    val barWidth = layout.rowBarWidths.getOrElse(row.index) { m.chipWidth }
    drawRoundRect(
        color = barColor,
        topLeft = Offset(layout.rowBarLeft, cy - m.rowBarHeight / 2f),
        size = Size(barWidth, m.rowBarHeight),
        cornerRadius = CornerRadius(m.rowBarHeight / 2f),
        alpha = row.alpha,
    )
}

// ------------------------------------------------------------------ 高亮框

/** 呼吸一轮用多久. 和光环一样直接由播放位置取相位, 不需要轨道. */
private val BreathPeriod: Duration = Duration.parse("1.8s")

/**
 * 「刚动的那个设置项改的是这一步」的高亮框.
 *
 * 它是罩在内容之上的 overlay, 由 `drawSelectorWorkflow` 最后画, 直接压在图上.
 * 在金色与全透明之间呼吸: 用余弦而不是三角波, 两端各停一会儿, 才像呼吸而不是闪烁.
 */
internal fun DrawScope.drawHighlight(
    highlight: Highlight,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    time: Duration,
) {
    val phase = (time.inWholeMilliseconds.toFloat() / BreathPeriod.inWholeMilliseconds) % 1f
    val alpha = (1f - cos(phase * 2f * PI.toFloat())) / 2f
    if (alpha <= 0.001f) return
    drawRoundRect(
        color = palette.highlight,
        topLeft = highlight.bounds.topLeft,
        size = highlight.bounds.size,
        cornerRadius = CornerRadius(highlight.cornerRadius),
        alpha = alpha,
        style = Stroke(width = layout.metrics.strokeBold),
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

/**
 * 圆角的播放三角.
 *
 * 先按 `radius - corner` 画一个小一圈的三角形填上, 再用宽度 `2 * corner`、圆角连接的描边把它撑回原尺寸 ——
 * 描边的圆角 join 正好把三个尖角磨圆. 比手算三段圆弧简单, 结果一样.
 */
private fun DrawScope.drawPlayTriangle(
    center: Offset,
    radius: Float,
    corner: Float,
    color: Color,
    alpha: Float,
) {
    val r = (radius - corner).coerceAtLeast(0.1f)
    val path = Path().apply {
        moveTo(center.x - r * 0.85f, center.y - r)
        lineTo(center.x + r * 1.15f, center.y)
        lineTo(center.x - r * 0.85f, center.y + r)
        close()
    }
    drawPath(path, color, alpha = alpha)
    drawPath(
        path, color, alpha = alpha,
        style = Stroke(width = corner * 2, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
