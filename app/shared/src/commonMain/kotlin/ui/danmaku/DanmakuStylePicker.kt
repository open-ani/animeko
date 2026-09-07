/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.danmaku

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.danmaku_send_style
import me.him188.ani.app.ui.lang.danmaku_send_style_color
import me.him188.ani.app.ui.lang.danmaku_send_style_custom_color
import me.him188.ani.app.ui.lang.danmaku_send_style_location
import me.him188.ani.app.ui.lang.subject_episode_video_settings_bottom
import me.him188.ani.app.ui.lang.subject_episode_video_settings_floating
import me.him188.ani.app.ui.lang.subject_episode_video_settings_top
import me.him188.ani.danmaku.api.DanmakuLocation
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 用户发送弹幕时选择的样式: 颜色和位置.
 */
@Immutable
data class DanmakuSendStyle(
    /**
     * RGB, 不含 alpha. 例如白色为 `0xFFFFFF`.
     */
    val color: Int,
    val location: DanmakuLocation,
) {
    companion object {
        val Default = DanmakuSendStyle(
            color = DanmakuSettings.DEFAULT_SEND_COLOR,
            location = DanmakuLocation.NORMAL,
        )
    }
}

fun DanmakuSettings.toDanmakuSendStyle(): DanmakuSendStyle = DanmakuSendStyle(sendColor, sendLocation)

object DanmakuSendColors {
    /**
     * 可选的预设颜色, RGB. 都偏亮, 在视频画面上可读; 面板里最后一格是自定义颜色.
     */
    val Presets: List<Int> = listOf(
        0xFFFFFF, // 白
        0xFF4D4D, // 红
        0xFF7A2F, // 橙
        0xFFC53D, // 黄
        0xA6F03A, // 黄绿
        0x3DDC84, // 绿
        0x22D3EE, // 青
        0x5AB4FF, // 天蓝
        0x7C8CFF, // 蓝
        0xC084FC, // 紫
        0xFF6FB5, // 粉
    )

    /**
     * 可供 [DanmakuStylePanel] 选择的位置, 按 UI 显示顺序.
     */
    val Locations: List<DanmakuLocation> = listOf(
        DanmakuLocation.TOP,
        DanmakuLocation.NORMAL,
        DanmakuLocation.BOTTOM,
    )
}

const val TAG_DANMAKU_STYLE_BUTTON = "danmakuStyleButton"
const val TAG_DANMAKU_STYLE_PANEL = "danmakuStylePanel"
const val TAG_DANMAKU_CUSTOM_COLOR_SWATCH = "danmakuCustomColorSwatch"
const val TAG_DANMAKU_CUSTOM_COLOR_HEX = "danmakuCustomColorHex"

fun danmakuLocationTileTag(location: DanmakuLocation): String = "danmakuLocationTile-${location.name}"
fun danmakuColorSwatchTag(color: Int): String = "danmakuColorSwatch-${color.toRgbHex()}"

/**
 * 弹幕样式入口按钮与弹层. 点击按钮在上方弹出 [DanmakuStylePanel].
 *
 * @param onExpandedChanged 弹层展开状态变化时回调, 可用于让播放器控制器保持显示.
 */
@Composable
fun DanmakuStylePicker(
    style: DanmakuSendStyle,
    onStyleChange: (DanmakuSendStyle) -> Unit,
    modifier: Modifier = Modifier,
    onExpandedChanged: (expanded: Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        onExpandedChanged(value)
    }
    // 自定义模式提升到这里, 这样弹层因 focusable 变化而重建时不会丢失
    var customMode by remember { mutableStateOf(style.color !in DanmakuSendColors.Presets) }

    Box(modifier, contentAlignment = Alignment.Center) {
        DanmakuStyleButton(style, onClick = { setExpanded(!expanded) })

        if (expanded) {
            // 只选预设时不抢焦点, 弹幕输入框保持聚焦, 视频不会因为失焦而恢复播放;
            // 展开自定义颜色后需要键入十六进制, 非 focusable 的弹层内文本框拿不到焦点, 此时改为可聚焦.
            // 桌面端 Popup 不支持运行时切换 focusable, 用 key 重建.
            key(customMode) {
                Popup(
                    popupPositionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                        spacingBetweenTooltipAndAnchor = 8.dp,
                    ),
                    onDismissRequest = { setExpanded(false) },
                    properties = PlatformPopupProperties(focusable = customMode, clippingEnabled = false),
                ) {
                    AniTheme(darkModeOverride = DarkMode.DARK) {
                        Surface(
                            Modifier.width(300.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 8.dp,
                        ) {
                            DanmakuStylePanel(
                                style,
                                onStyleChange,
                                customMode = customMode,
                                onCustomModeChange = { customMode = it },
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 显示当前样式的按钮: 一个迷你屏幕图标, 弹幕条画在当前位置 (与面板里的缩略屏同一套语言), 弹幕条用当前颜色.
 * 放在弹幕输入框内部最前面.
 */
@Composable
fun DanmakuStyleButton(
    style: DanmakuSendStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Lang.danmaku_send_style)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(32.dp)
            .testTag(TAG_DANMAKU_STYLE_BUTTON)
            // 不抢输入框的焦点 (桌面端鼠标点击会请求焦点)
            .focusProperties { canFocus = false }
            .semantics { contentDescription = description },
    ) {
        DanmakuStyleIcon(style, Modifier.size(22.dp, 16.dp))
    }
}

/**
 * 迷你屏幕: 圆角框 + 当前位置的弹幕条. 框用当前内容色, 弹幕条用 [DanmakuSendStyle.color].
 */
@Composable
private fun DanmakuStyleIcon(style: DanmakuSendStyle, modifier: Modifier = Modifier) {
    val frameColor = LocalContentColor.current.copy(alpha = 0.7f)
    val barColor = style.color.rgbToColor()
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = frameColor,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(2.5.dp.toPx()),
            style = Stroke(stroke),
        )
        val barHeight = 2.dp.toPx()
        val radius = CornerRadius(1.dp.toPx())
        fun bar(x: Float, y: Float, w: Float, alpha: Float = 1f) = drawRoundRect(
            color = barColor.copy(alpha = alpha),
            topLeft = Offset(x.dp.toPx(), y.dp.toPx()),
            size = Size(w.dp.toPx(), barHeight),
            cornerRadius = radius,
        )
        when (style.location) {
            DanmakuLocation.TOP -> bar(6f, 3.5f, 10f)
            DanmakuLocation.BOTTOM -> bar(6f, 10.5f, 10f)
            // 三条错开的弹幕条, 和面板缩略屏一致
            DanmakuLocation.NORMAL -> {
                bar(4f, 3.5f, 7f)
                bar(9f, 7f, 9f, 0.85f)
                bar(5.5f, 10.5f, 6f, 0.6f)
            }
        }
    }
}

/**
 * 弹幕样式选择面板: 位置 (顶部/滚动/底部), 预设颜色和自定义颜色. 自定义模式的状态由面板自己保存.
 */
@Composable
fun DanmakuStylePanel(
    style: DanmakuSendStyle,
    onStyleChange: (DanmakuSendStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 当前颜色不在预设里时, 说明是自定义颜色; 用户点了自定义格之后即使颜色仍等于某个预设, 也保持在自定义模式
    var customMode by remember { mutableStateOf(style.color !in DanmakuSendColors.Presets) }
    DanmakuStylePanel(
        style,
        onStyleChange,
        customMode = customMode,
        onCustomModeChange = { customMode = it },
        modifier = modifier,
    )
}

/**
 * [DanmakuStylePanel] 的无状态版本, [customMode] 为是否展开自定义颜色编辑区.
 */
@Composable
fun DanmakuStylePanel(
    style: DanmakuSendStyle,
    onStyleChange: (DanmakuSendStyle) -> Unit,
    customMode: Boolean,
    onCustomModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .testTag(TAG_DANMAKU_STYLE_PANEL),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Lang.danmaku_send_style_location),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (location in DanmakuSendColors.Locations) {
                DanmakuLocationTile(
                    location = location,
                    selected = style.location == location,
                    onClick = { onStyleChange(style.copy(location = location)) },
                    modifier = Modifier.testTag(danmakuLocationTileTag(location)),
                )
            }
        }

        Text(
            stringResource(Lang.danmaku_send_style_color),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (color in DanmakuSendColors.Presets) {
                DanmakuColorSwatch(
                    color = color,
                    selected = !customMode && style.color == color,
                    onClick = {
                        onCustomModeChange(false)
                        onStyleChange(style.copy(color = color))
                    },
                    modifier = Modifier.testTag(danmakuColorSwatchTag(color)),
                )
            }
            DanmakuCustomColorSwatch(
                color = style.color,
                selected = customMode,
                onClick = { onCustomModeChange(true) },
                modifier = Modifier.testTag(TAG_DANMAKU_CUSTOM_COLOR_SWATCH),
            )
        }
        if (customMode) {
            DanmakuCustomColorEditor(
                color = style.color,
                onColorChange = { onStyleChange(style.copy(color = it)) },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * 自定义颜色入口: 彩虹环, 选中时中间显示当前颜色.
 */
@Composable
private fun DanmakuCustomColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Lang.danmaku_send_style_custom_color)
    val rainbow = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF4D4D), Color(0xFFFFC53D), Color(0xFF3DDC84),
                Color(0xFF22D3EE), Color(0xFF7C8CFF), Color(0xFFC084FC), Color(0xFFFF4D4D),
            ),
        )
    }
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(rainbow)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
            )
            // 选项不抢焦点, 弹幕输入框保持聚焦 (不能放在面板整体上, 否则十六进制输入框也无法聚焦)
            .focusProperties { canFocus = false }
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(color.rgbToColor()))
        } else {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = Color.White)
        }
    }
}

/**
 * 自定义颜色编辑: 十六进制输入 + R/G/B 三个滑条, 任一改动立即回调.
 */
@Composable
private fun DanmakuCustomColorEditor(
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var hexText by remember { mutableStateOf(color.toRgbHex()) }
    // 滑条 (或外部) 改了颜色时同步输入框; 用户正在输入的不完整文本不被覆盖
    LaunchedEffect(color) {
        if (parseRgbHex(hexText) != color) hexText = color.toRgbHex()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(color.rgbToColor())
                    .border(1.dp, scheme.outline, CircleShape),
            )
            Text("#", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            BasicTextField(
                value = hexText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                        .take(6).uppercase()
                    hexText = filtered
                    parseRgbHex(filtered)?.let(onColorChange)
                },
                modifier = Modifier.width(96.dp).testTag(TAG_DANMAKU_CUSTOM_COLOR_HEX),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = scheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                ),
                singleLine = true,
                cursorBrush = SolidColor(scheme.primary),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .border(1.dp, scheme.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        if (hexText.isEmpty()) {
                            Text(
                                "RRGGBB",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        inner()
                    }
                },
            )
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
            ChannelSlider("R", (color shr 16) and 0xFF, Color(0xFFFF4D4D)) { onColorChange((color and 0x00FFFF) or (it shl 16)) }
            ChannelSlider("G", (color shr 8) and 0xFF, Color(0xFF3DDC84)) { onColorChange((color and 0xFF00FF) or (it shl 8)) }
            ChannelSlider("B", color and 0xFF, Color(0xFF5AB4FF)) { onColorChange((color and 0xFFFF00) or it) }
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    tint: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            Modifier.width(12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            modifier = Modifier.weight(1f).height(28.dp).focusProperties { canFocus = false },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
        )
        Text(
            value.toString(),
            Modifier.width(28.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * 位置选项: 一个迷你屏幕, 把弹幕条画在对应位置 (滚动带拖尾), 所见即所得.
 */
@Composable
private fun DanmakuLocationTile(
    location: DanmakuLocation,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val barColor = if (selected) scheme.primary else scheme.onSurfaceVariant
    val label = location.displayName()
    Column(
        modifier
            .width(84.dp)
            // 选项不抢焦点, 弹幕输入框保持聚焦 (不能放在面板整体上, 否则十六进制输入框也无法聚焦)
            .focusProperties { canFocus = false }
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(84.dp, 50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) scheme.primary else scheme.outlineVariant,
                    shape = RoundedCornerShape(6.dp),
                ),
        ) {
            when (location) {
                DanmakuLocation.TOP -> DanmakuBar(x = 24.dp, y = 8.dp, width = 36.dp, color = barColor)
                DanmakuLocation.BOTTOM -> DanmakuBar(x = 24.dp, y = 38.dp, width = 36.dp, color = barColor)
                DanmakuLocation.NORMAL -> ScrollingDanmakuBars(
                    barColor,
                    // 留出描边的宽度, 弹幕条出界时被裁在描边内侧
                    Modifier.fillMaxSize().padding(2.dp).clipToBounds(),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 滚动弹幕示例: 三条轨道上错开的弹幕条持续向左滚动, 出界后从右边回来.
 */
@Composable
private fun ScrollingDanmakuBars(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scrollingDanmaku")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing)),
        label = "progress",
    )
    Canvas(modifier) {
        val width = size.width
        val barHeight = 4.dp.toPx()
        val radius = CornerRadius(2.dp.toPx())
        // (轨道 y, 初始 x, 宽度, 透明度): 每条以自己的周期循环, 位置错开, 看起来像真实的弹幕流
        val bars = listOf(
            Triple(7.dp, 12.dp, 30.dp) to 1f,
            Triple(21.dp, 46.dp, 34.dp) to 0.8f,
            Triple(35.dp, 26.dp, 24.dp) to 0.6f,
        )
        for ((bar, alpha) in bars) {
            val (y, x0, w) = bar
            val barWidth = w.toPx()
            val period = width + barWidth
            val travelled = progress * period * 1.5f // 6 秒走 1.5 个周期
            val x = ((x0.toPx() - travelled) % period + period) % period - barWidth
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, y.toPx()),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun DanmakuBar(x: Dp, y: Dp, width: Dp, color: Color) {
    Box(
        Modifier
            .offset(x, y)
            .size(width, 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

@Composable
private fun DanmakuColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = color.rgbToColor()
    val description = "#" + color.toRgbHex()
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            // 选项不抢焦点, 弹幕输入框保持聚焦 (不能放在面板整体上, 否则十六进制输入框也无法聚焦)
            .focusProperties { canFocus = false }
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = contentColorOn(fill))
        }
    }
}

@Composable
private fun DanmakuLocation.displayName(): String = when (this) {
    DanmakuLocation.TOP -> stringResource(Lang.subject_episode_video_settings_top)
    DanmakuLocation.NORMAL -> stringResource(Lang.subject_episode_video_settings_floating)
    DanmakuLocation.BOTTOM -> stringResource(Lang.subject_episode_video_settings_bottom)
}

/**
 * 把 RGB int (不含 alpha) 转换为不透明的 [Color].
 */
private fun Int.rgbToColor(): Color = Color(0xFF_00_00_00L or (this.toLong() and 0xFF_FF_FFL))

private fun Int.toRgbHex(): String = (this and 0xFF_FF_FF).toString(16).uppercase().padStart(6, '0')

/**
 * 解析 6 位十六进制 RGB (不带 #), 不合法返回 null.
 */
private fun parseRgbHex(text: String): Int? = if (text.length == 6) text.toIntOrNull(16) else null

/**
 * 在 [background] 上清晰可见的前景色.
 */
private fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White
