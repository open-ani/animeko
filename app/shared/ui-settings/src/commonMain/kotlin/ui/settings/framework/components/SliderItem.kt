package me.him188.ani.app.ui.settings.framework.components

import androidx.annotation.IntRange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderDefaults.TickSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.SliderValueIndicator
import me.him188.ani.app.ui.foundation.rememberHoverExitFilteredInteractionSource


@SettingsDsl
@Composable
fun SettingsScope.SliderItem(
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    valueLabel: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Item(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    title()
                }

                if (valueLabel != null) {
                    Box(Modifier.padding(start = 16.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                            valueLabel()
                        }
                    }
                }
            }
        },
        modifier = modifier,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                description?.invoke()
                content()
            }
        },
    )
}

@SettingsDsl
@Composable
fun SettingsScope.SliderItem(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0)
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    valueLabel: @Composable (() -> Unit)? = {
        Text(value.toString())
    },
    description: @Composable (() -> Unit)? = null,
    useThinSlider: Boolean = false,
) {
    if (useThinSlider) {
        ThinSliderItem(
            value,
            onValueChange,
            title,
            modifier,
            enabled,
            valueRange,
            steps,
            onValueChangeFinished,
            colors,
            interactionSource,
            valueLabel,
            description,
        )
    } else {
        SliderItem(title, modifier, description, valueLabel) {
            Slider(
                value,
                onValueChange,
                Modifier,
                enabled,
                valueRange,
                steps,
                onValueChangeFinished,
                colors,
                interactionSource,
            )
        }
    }
}

@SettingsDsl
@Composable
fun SettingsScope.RangeSliderItem(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0)
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    startInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    endInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    valueIndicator: (@Composable (Float) -> Unit)? = null,
    valueLabel: @Composable (() -> Unit)? = {
        Text("$value")
    },
    description: @Composable (() -> Unit)? = null,
) {
    SliderItem(title, modifier, description, valueLabel) {
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier,
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            startInteractionSource = startInteractionSource,
            endInteractionSource = endInteractionSource,
            startThumb = rangeSliderThumb(
                startInteractionSource, colors, enabled, { it.activeRangeStart }, valueIndicator,
            ),
            endThumb = rangeSliderThumb(
                endInteractionSource, colors, enabled, { it.activeRangeEnd }, valueIndicator,
            ),
            track = { rangeSliderState ->
                // M3E 样式: 小圆角轨道; 刻度点不画 (spec 默认关闭, 密步进下是「豌豆荚」).
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                SliderDefaults.Track(
                    rangeSliderState = rangeSliderState,
                    trackCornerSize = 4.dp,
                    enabled = enabled,
                    colors = colors,
                    drawTick = { _, _ -> },
                )
            },
            steps = steps,
        )
    }
}

private fun rangeSliderThumb(
    interactionSource: MutableInteractionSource,
    colors: SliderColors,
    enabled: Boolean,
    value: (RangeSliderState) -> Float,
    valueIndicator: (@Composable (Float) -> Unit)?,
): @Composable (RangeSliderState) -> Unit = { state ->
    if (valueIndicator == null) {
        SliderDefaults.Thumb(
            interactionSource = interactionSource,
            colors = colors,
            enabled = enabled,
        )
    } else {
        val labelInteractionSource = rememberHoverExitFilteredInteractionSource(interactionSource)
        Label(
            label = { SliderValueIndicator { valueIndicator(value(state)) } },
            interactionSource = labelInteractionSource,
        ) {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                enabled = enabled,
            )
        }
    }
}

@SettingsDsl
@Composable
fun SettingsScope.ThinSliderItem(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0)
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    valueLabel: @Composable (() -> Unit)? = {
        Text(value.toString())
    },
    description: @Composable (() -> Unit)? = null,
    drawTick: DrawScope.(Offset, Color) -> Unit = { offset, color ->
        with(this) { drawCircle(color = color, center = offset, radius = TickSize.toPx() / 2f) }
    },
) {
    SliderItem(title, modifier, description, valueLabel) {
        Slider(
            value,
            onValueChange,
            Modifier,
            enabled,
            onValueChangeFinished,
            colors,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    enabled = enabled,
                    thumbSize = DpSize(4.dp, 36.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    colors = colors, enabled = enabled, sliderState = sliderState,
                    thumbTrackGapSize = 6.dp,
                    drawTick = drawTick,
                )
            },
            valueRange = valueRange,
            steps = steps,
        )
    }
}
