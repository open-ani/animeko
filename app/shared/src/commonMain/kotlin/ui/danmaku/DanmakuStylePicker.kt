/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.danmaku_send_style
import me.him188.ani.app.ui.lang.danmaku_send_style_color
import me.him188.ani.app.ui.lang.danmaku_send_style_location
import me.him188.ani.app.ui.lang.subject_episode_video_settings_bottom
import me.him188.ani.app.ui.lang.subject_episode_video_settings_floating
import me.him188.ani.app.ui.lang.subject_episode_video_settings_top
import me.him188.ani.danmaku.api.DanmakuLocation
import org.jetbrains.compose.resources.stringResource

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
     * 可选的预设颜色, RGB.
     */
    val Presets: List<Int> = listOf(
        0xFFFFFF, // 白
        0xFE0302, // 红
        0xFF7204, // 橙
        0xFFAA02, // 金
        0xFFD302, // 黄
        0xA0EE00, // 黄绿
        0x00CD00, // 绿
        0x019899, // 青
        0x4266BE, // 蓝
        0x89D5FF, // 浅蓝
        0xCC0273, // 紫
        0x000000, // 黑
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

fun danmakuLocationChipTag(location: DanmakuLocation): String = "danmakuLocationChip-${location.name}"
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

    Box(modifier, contentAlignment = Alignment.Center) {
        DanmakuStyleButton(style, onClick = { setExpanded(!expanded) })

        if (expanded) {
            Popup(
                popupPositionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                    spacingBetweenTooltipAndAnchor = 8.dp,
                ),
                onDismissRequest = { setExpanded(false) },
                // 不抢焦点, 这样输入框保持聚焦, 视频也不会因为失焦而恢复播放
                properties = PlatformPopupProperties(focusable = false, clippingEnabled = false),
            ) {
                AniTheme(darkModeOverride = DarkMode.DARK) {
                    Surface(
                        Modifier.width(300.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 8.dp,
                    ) {
                        DanmakuStylePanel(style, onStyleChange, Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 显示当前样式的按钮: 一个当前颜色的圆点, 非滚动位置时叠加一个方向图标.
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
            .testTag(TAG_DANMAKU_STYLE_BUTTON)
            // 不抢输入框的焦点 (桌面端鼠标点击会请求焦点)
            .focusProperties { canFocus = false }
            .semantics { contentDescription = description },
    ) {
        val fill = style.color.rgbToColor()
        Box(
            Modifier.size(22.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, LocalContentColor.current.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (style.location) {
                DanmakuLocation.TOP -> Icon(
                    Icons.Rounded.VerticalAlignTop, null,
                    Modifier.size(16.dp), tint = contentColorOn(fill),
                )

                DanmakuLocation.BOTTOM -> Icon(
                    Icons.Rounded.VerticalAlignBottom, null,
                    Modifier.size(16.dp), tint = contentColorOn(fill),
                )

                DanmakuLocation.NORMAL -> {}
            }
        }
    }
}

/**
 * 弹幕样式选择面板: 位置 (顶部/滚动/底部) 和预设颜色.
 */
@Composable
fun DanmakuStylePanel(
    style: DanmakuSendStyle,
    onStyleChange: (DanmakuSendStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .testTag(TAG_DANMAKU_STYLE_PANEL)
            // 面板内的选项都不抢焦点, 输入框保持聚焦
            .focusProperties { canFocus = false },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Lang.danmaku_send_style_location),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (location in DanmakuSendColors.Locations) {
                val selected = style.location == location
                FilterChip(
                    selected = selected,
                    onClick = { onStyleChange(style.copy(location = location)) },
                    label = { Text(location.displayName(), maxLines = 1) },
                    modifier = Modifier.testTag(danmakuLocationChipTag(location)),
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
                    } else null,
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
                    selected = style.color == color,
                    onClick = { onStyleChange(style.copy(color = color)) },
                    modifier = Modifier.testTag(danmakuColorSwatchTag(color)),
                )
            }
        }
    }
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
 * 在 [background] 上清晰可见的前景色.
 */
private fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White
