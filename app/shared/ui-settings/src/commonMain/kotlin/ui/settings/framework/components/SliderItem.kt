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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.SliderValueIndicator
import me.him188.ani.app.ui.foundation.rememberHoverExitFilteredInteractionSource

/**
 * 遥控器上 slider 聚焦时把返回键改成"向左移焦点": 左键被 slider 当成调值吃掉,
 * 返回键代替它退出 slider (向左进入左侧导航列表时经其 focusGroup 的 onEnter 跳回选中项).
 * 只在向左真有落点时才改写 —— 窄窗口的单栏布局里左侧列表不在组合中, 返回键仍是页面返回.
 *
 * 默认关闭: slider 也出现在播放器面板等场景, 那里返回键有各自的语义, 只有设置页打开.
 */
internal val LocalSliderBackKeyExitsLeft = compositionLocalOf { false }

@SettingsDsl
@Composable
fun SettingsScope.SliderItem(
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    valueLabel: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // 方向键驱动的界面上, M3 Slider 会把上下键也当成调值吃掉, 焦点困在 slider 上
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    val backKeyExitsLeft = LocalSliderBackKeyExitsLeft.current
    val focusManager = LocalFocusManager.current
    // 返回键那一下的处理结论: KeyDown 时算出来, KeyUp 沿用同一结论, 不能一半消费一半放行.
    // 用普通数组而不是 State: 这里每按一次返回键都会写, 变成可观察状态就是白白多一次重组.
    val backConsumed = remember { booleanArrayOf(false) }
    val effectiveModifier = if (focusDriven) {
        modifier.onPreviewKeyEvent { keyEvent ->
            when (keyEvent.key) {
                Key.DirectionUp, Key.DirectionDown -> {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        focusManager.moveFocus(
                            if (keyEvent.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down,
                        )
                        true
                    } else false
                }

                // 返回键不退出页面, 把焦点向左送出 slider —— 但只有真的移出去了才消费.
                // 窄窗口下设置页是单栏 (左侧导航列表根本不在组合里), 向左没有候选, moveFocus 返回
                // false; 此时若照样消费, 事件就到不了根部那个把 Back 转成页面返回的处理器
                // (AniApp 的 onKeyEvent 在冒泡阶段, 而这里是预览阶段), 返回键彻底失灵.
                // 移成功时两个事件一起消费: 焦点已经走了, 之后的 KeyUp 也不会再经过这条链, 而根部
                // 对没配对 KeyDown 的孤儿 KeyUp 本来就忽略.
                Key.Back, Key.Escape -> {
                    if (!backKeyExitsLeft) return@onPreviewKeyEvent false
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        backConsumed[0] = focusManager.moveFocus(FocusDirection.Left)
                    }
                    backConsumed[0]
                }

                else -> false
            }
        }
    } else modifier
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
        modifier = effectiveModifier,
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
