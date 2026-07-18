/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Slider 固定步进, 所有可调倍速都落在该网格上.
 */
const val SLIDER_VALUE_STEP = 0.25f

private const val FLOAT_EPSILON = 1e-4f

/**
 * 以 [SLIDER_VALUE_STEP] 为固定步进的 Slider, 带拖动数值气泡.
 *
 * 档位由 Material Slider 原生 `steps` 与内部量化共同实现拖动吸附；当范围只有两个端点时，
 * Material 的 `steps = 0` 会退化为连续 Slider，因此仍需内部量化。刻度指示点不绘制 (0.25 步进下过密).
 * 拖动期间由内部值驱动 thumb, 避免外部状态提交期间短暂回跳;
 * [onValueChangeFinished] 收到最终档位值, 由调用方完成提交.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SteppedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueIndicator: @Composable (Float) -> Unit,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val labelInteractionSource = rememberHoverExitFilteredInteractionSource(interactionSource)
    var displayedValue by remember(valueRange) { mutableStateOf(value.coerceIn(valueRange)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!dragging) displayedValue = value.coerceIn(valueRange)
    }

    Slider(
        value = displayedValue,
        onValueChange = {
            dragging = true
            val quantizedValue = quantizeSliderValue(it, valueRange)
            displayedValue = quantizedValue
            onValueChange(quantizedValue)
        },
        onValueChangeFinished = {
            dragging = false
            onValueChangeFinished(displayedValue)
        },
        valueRange = valueRange,
        steps = sliderStepsInRange(valueRange),
        interactionSource = interactionSource,
        thumb = { sliderState ->
            Label(
                label = {
                    SliderValueIndicator {
                        valueIndicator(displayedValue)
                    }
                },
                interactionSource = labelInteractionSource,
            ) {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    sliderState = sliderState,
                    colors = colors,
                )
            }
        },
        colors = colors,
        track = { sliderState ->
            // M3E 样式: 小圆角轨道 + 末端 stop 圆点.
            // 刻度点默认不画 (M3E spec 中 stop indicators 默认关闭, 0.25 步进下也太密).
            SliderDefaults.Track(
                sliderState = sliderState,
                trackCornerSize = 4.dp,
                colors = colors,
                drawTick = { _, _ -> },
            )
        },
        modifier = modifier,
    )
}

/**
 * 将 [rawValue] 限制在 [range] 内并量化到最近的 [SLIDER_VALUE_STEP] 档位.
 */
fun quantizeSliderValue(
    rawValue: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    if (!rawValue.isFinite()) return range.start
    return ((rawValue.coerceIn(range) / SLIDER_VALUE_STEP).roundToInt() * SLIDER_VALUE_STEP).coerceIn(range)
}

/**
 * 返回 [range] 内 [SLIDER_VALUE_STEP] 步进对应的 Material Slider `steps` 数.
 */
fun sliderStepsInRange(range: ClosedFloatingPointRange<Float>): Int {
    val intervals = ((range.endInclusive - range.start) / SLIDER_VALUE_STEP + FLOAT_EPSILON).toInt()
    return (intervals - 1).coerceAtLeast(0)
}

/**
 * 转发 [source] 的 Hover 和 Drag 交互事件，忽略 Press 交互事件.
 *
 * Android Slider 开始触摸拖动时会发送 `Press → Press.Cancel → Drag.Start`。Material `Label`
 * 使用 `collectLatest` 消费事件，未处理的 `Press.Cancel` 仍会取消正在执行的 `show()`，导致
 * Indicator 闪现后消失。Label 只需要 Hover 支持鼠标悬停、Drag 支持各平台拖动，因此不转发
 * Press 序列；拖动期间也忽略鼠标移出 thumb 产生的 Hover Exit.
 */
@Composable
fun rememberHoverExitFilteredInteractionSource(
    source: MutableInteractionSource,
): MutableInteractionSource {
    val filtered = remember { MutableInteractionSource() }
    LaunchedEffect(source, filtered) {
        var dragging = false
        source.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragging = true
                    filtered.emit(interaction)
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    dragging = false
                    filtered.emit(interaction)
                }
                is HoverInteraction.Exit -> {
                    if (!dragging) filtered.emit(interaction)
                }
                is PressInteraction -> Unit
                else -> filtered.emit(interaction)
            }
        }
    }
    return filtered
}

/**
 * Slider 拖动数值气泡，供 Material `Label` 的 `label` 插槽使用.
 */
@Composable
fun TooltipScope.SliderValueIndicator(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(48.dp, 44.dp),
        shape = RoundedCornerShape(22.dp),
        color = TooltipDefaults.plainTooltipContainerColor,
        contentColor = TooltipDefaults.plainTooltipContentColor,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content)
        }
    }
}
