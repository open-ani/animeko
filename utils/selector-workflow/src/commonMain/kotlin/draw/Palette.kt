/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow.draw

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import me.him188.ani.utils.selectorworkflow.ChipTone
import me.him188.ani.utils.selectorworkflow.ClockTone
import me.him188.ani.utils.selectorworkflow.RequestTone
import me.him188.ani.utils.selectorworkflow.WindowTone

/**
 * 语义色 → 实际颜色. 状态层只给枚举, 到这一层才落到 M3 token 上.
 *
 * 数据源的颜色按下标取 [sourceColors] 循环, 所以源多了也不会没色可用.
 */
@Immutable
data class WorkflowPalette(
    /** 数据源与它们的结果. */
    val sourceColors: List<Color>,
    /** 结果容器、窗口内容区的底. */
    val surfaceLow: Color,
    /** 窗口的面. */
    val surfaceHigh: Color,
    /** 还没画到的那段连线; 窗口未打开时的描边. */
    val trackline: Color,
    /** 容器描边、请求横条、mac 三圆点、表圈. */
    val outlineVariant: Color,
    /** 页面打开后窗口的激活描边; 表盘刻度. */
    val outline: Color,
    /** 遍历 cursor 的描边与状态层; 表针. */
    val focus: Color,
    /** 请求行左侧的图标底色. */
    val requestIcon: Color,
    /** 结果块上的标记 (候选圆点、高优先级菱形). */
    val mark: Color,
    /** 选中 / 命中. M3 基线里没有这个角, 按自定义色角处理. */
    val success: Color,
    /** 失败 / 超时. */
    val error: Color,
) {
    fun source(index: Int): Color = sourceColors[index.mod(sourceColors.size)]

    fun chip(tone: ChipTone, sourceIndex: Int): Color = when (tone) {
        ChipTone.Source -> source(sourceIndex)
        ChipTone.Selected -> success
        ChipTone.Failed -> error
    }

    fun windowStroke(tone: WindowTone): Color = when (tone) {
        WindowTone.Closed -> trackline
        WindowTone.Open -> outline
        WindowTone.Failed -> error
    }

    fun clock(tone: ClockTone): Color = when (tone) {
        ClockTone.Running -> outlineVariant
        ClockTone.Stopped -> success
        ClockTone.Expired -> error
    }

    fun clockHand(tone: ClockTone): Color = when (tone) {
        ClockTone.Running -> focus
        ClockTone.Stopped -> success
        ClockTone.Expired -> error
    }

    fun requestBar(tone: RequestTone): Color = when (tone) {
        RequestTone.Idle -> outlineVariant
        RequestTone.Hit -> success
    }

    companion object {
        /** M3 基线里没有 success 角, 用这个绿色补上. 与设计稿一致. */
        val DefaultSuccess = Color(0xFF5FD48F)
        val DefaultSuccessLight = Color(0xFF1E6F42)
    }
}

/**
 * 从当前 [MaterialTheme] 取一份调色板.
 */
@Composable
fun rememberWorkflowPalette(
    success: Color = if (MaterialTheme.colorScheme.surface.luminanceIsDark()) {
        WorkflowPalette.DefaultSuccess
    } else {
        WorkflowPalette.DefaultSuccessLight
    },
): WorkflowPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme, success) {
        WorkflowPalette(
            sourceColors = listOf(scheme.primary, scheme.secondary, scheme.tertiary),
            surfaceLow = scheme.surfaceContainerLow,
            surfaceHigh = scheme.surfaceContainerHigh,
            trackline = scheme.surfaceContainerHighest,
            outlineVariant = scheme.outlineVariant,
            outline = scheme.outline,
            focus = scheme.onSurface,
            requestIcon = scheme.secondaryContainer,
            mark = scheme.surface,
            success = success,
            error = scheme.error,
        )
    }
}

/**
 * 不在 Composition 里 (预览、测试、截图) 时用这个手搭一份.
 */
fun workflowPaletteOf(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    surface: Color,
    surfaceContainerLow: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    outline: Color,
    outlineVariant: Color,
    onSurface: Color,
    secondaryContainer: Color,
    error: Color,
    success: Color = WorkflowPalette.DefaultSuccess,
): WorkflowPalette = WorkflowPalette(
    sourceColors = listOf(primary, secondary, tertiary),
    surfaceLow = surfaceContainerLow,
    surfaceHigh = surfaceContainerHigh,
    trackline = surfaceContainerHighest,
    outlineVariant = outlineVariant,
    outline = outline,
    focus = onSurface,
    requestIcon = secondaryContainer,
    mark = surface,
    success = success,
    error = error,
)

private fun Color.luminanceIsDark(): Boolean = red * 0.299f + green * 0.587f + blue * 0.114f < 0.5f
