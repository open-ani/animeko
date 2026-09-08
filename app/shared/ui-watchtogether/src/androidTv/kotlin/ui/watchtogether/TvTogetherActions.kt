/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.watchtogether

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.tvOptionSurfaceColors

internal enum class TvTogetherActionStyle { Primary, Secondary, Destructive }

/** One stable TV button across join/retry/cancel states, so progress never removes its focus anchor. */
@Composable
internal fun TvTogetherAction(
    text: String,
    modifier: Modifier = Modifier,
    style: TvTogetherActionStyle = TvTogetherActionStyle.Primary,
    icon: ImageVector? = null,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalTvOptionColors.current
    val primary = style == TvTogetherActionStyle.Primary
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = when {
                primary -> MaterialTheme.colorScheme.onPrimary
                style == TvTogetherActionStyle.Destructive -> MaterialTheme.colorScheme.error
                else -> colors.content
            },
            focusedContainerColor = colors.focusedContainer,
            focusedContentColor = colors.focusedContent,
            pressedContainerColor = colors.focusedContainer.copy(alpha = .85f),
        ),
        border = ButtonDefaults.border(
            border = if (primary) Border.None else Border(BorderStroke(1.dp, colors.outline), shape = CircleShape),
        ),
        scale = ButtonDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale, pressedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            else icon?.let { Icon(it, null, Modifier.size(20.dp)) }
            if (loading || icon != null) Spacer(Modifier.size(8.dp))
            Text(text, maxLines = 1)
        }
    }
}

@Composable
internal fun TvTogetherJoinError(text: String) {
    val color = MaterialTheme.colorScheme.error
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = .12f), TvOptionDefaults.ItemShape)
            .padding(12.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("tv-together-error"),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(20.dp), tint = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
internal fun TvTogetherJoinAction(joining: Boolean, retry: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (joining) Text(
        "正在加入房间…", Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium, color = LocalTvOptionColors.current.muted,
    )
    TvTogetherAction(
        if (joining) "取消加入" else if (retry) "重新加入" else "加入 / 创建房间",
        modifier.testTag("tv-together-submit"),
        style = if (joining) TvTogetherActionStyle.Secondary else TvTogetherActionStyle.Primary,
        icon = Icons.Rounded.Groups, loading = joining, onClick = onClick,
    )
}

@Composable
internal fun TvTogetherLeaveAction(isHost: Boolean, modifier: Modifier = Modifier, confirm: Boolean = false, onClick: () -> Unit) {
    TvTogetherAction(
        (if (confirm) "确定" else "") + if (isHost) "解散房间" else "退出房间",
        modifier.testTag(if (confirm) "tv-together-confirm-leave" else "tv-together-leave"),
        style = TvTogetherActionStyle.Destructive,
        icon = Icons.AutoMirrored.Rounded.Logout,
        onClick = onClick,
    )
}

@Composable
internal fun TvTogetherFollowAction(following: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics {
            role = Role.Switch
            toggleableState = if (following) ToggleableState.On else ToggleableState.Off
        },
        shape = ClickableSurfaceDefaults.shape(TvOptionDefaults.ItemShape),
        colors = tvOptionSurfaceColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale, pressedScale = 1f),
    ) {
        val color = LocalContentColor.current
        val colors = LocalTvOptionColors.current
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("跟随房主", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (following) "同步播放、暂停与进度" else "按自己的进度观看",
                    style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = .8f),
                )
            }
            // The row is the only input target; the trailing switch is a visual selection indicator.
            Switch(
                checked = following, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {},
                thumbContent = { if (following) Icon(Icons.Rounded.Check, null, Modifier.size(12.dp)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color, checkedTrackColor = color.copy(alpha = .35f),
                    checkedBorderColor = Color.Transparent,
                    checkedIconColor = if (color == colors.focusedContent) colors.focusedContainer else colors.container,
                    uncheckedThumbColor = color, uncheckedTrackColor = Color.Transparent,
                    uncheckedBorderColor = color.copy(alpha = .6f),
                ),
            )
        }
    }
}
