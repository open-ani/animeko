/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.foundation.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Composable
fun TvOptionRow(
    title: String,
    value: String = "",
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    checked: Boolean? = null,
    supportingText: String? = null,
    icon: ImageVector? = null,
    valueIcon: ImageVector? = null,
    adjustable: Boolean = false,
    filled: Boolean = false,
    compact: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                if (checked != null) {
                    role = Role.Switch
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                }
            },
        colors = tvOptionSurfaceColors(selected, filled),
        shape = ClickableSurfaceDefaults.shape(TvOptionDefaults.ItemShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier
                .heightIn(min = if (compact) 36.dp else 48.dp)
                .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) leadingContent()
            else icon?.let { Icon(it, null, Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                supportingText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = .7f),
                    )
                }
            }
            if (value.isNotEmpty() || adjustable || valueIcon != null) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                valueIcon?.let { Icon(it, null, Modifier.size(20.dp)) }
                if (adjustable) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(16.dp))
                Text(value, style = MaterialTheme.typography.labelLarge)
                if (adjustable) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp))
            }
            if (selected && checked == null) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
            if (checked != null) {
                val color = LocalContentColor.current
                Canvas(Modifier.size(36.dp, 20.dp)) {
                    drawRoundRect(
                        color.copy(alpha = if (checked) .45f else .18f),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                    drawCircle(
                        color,
                        radius = size.height / 2 - 3.dp.toPx(),
                        center = Offset(
                            if (checked) size.width - size.height / 2 else size.height / 2,
                            size.height / 2,
                        ),
                    )
                }
            }
        }
    }
}
