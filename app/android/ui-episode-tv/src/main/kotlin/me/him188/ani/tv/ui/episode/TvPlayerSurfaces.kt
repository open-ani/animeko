/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Player surfaces stay legible over both bright and dark video frames. */
internal object TvPlayerSurfaceDefaults {
    val Container = Color(0xFF202127)
    val Raised = Color(0xFF2D2F37)
    val Content = Color(0xFFF2F2F6)
    val Muted = Color(0xFFB9BBC6)
    val Outline = Color.White.copy(alpha = .08f)
    val FocusedContainer = Color(0xFFF2F2F6)
    val FocusedContent = Color(0xFF1B1B20)
    val PanelShape = RoundedCornerShape(20.dp)
    val ItemShape = RoundedCornerShape(12.dp)
    val PanelMaxHeight = 276.dp
    val ModalMaxHeight = 460.dp
}

@Composable
internal fun tvPlayerOptionColors(selected: Boolean = false, filled: Boolean = false) = ClickableSurfaceDefaults.colors(
    containerColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = .16f)
            .compositeOver(TvPlayerSurfaceDefaults.Container)

        filled -> TvPlayerSurfaceDefaults.Raised
        else -> Color.Transparent
    },
    contentColor = TvPlayerSurfaceDefaults.Content,
    focusedContainerColor = TvPlayerSurfaceDefaults.FocusedContainer,
    focusedContentColor = TvPlayerSurfaceDefaults.FocusedContent,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = TvPlayerSurfaceDefaults.Muted.copy(alpha = .5f),
)

internal fun Modifier.tvPlayerSurface() = shadow(16.dp, TvPlayerSurfaceDefaults.PanelShape)
    .background(TvPlayerSurfaceDefaults.Container, TvPlayerSurfaceDefaults.PanelShape)
    .border(1.dp, TvPlayerSurfaceDefaults.Outline, TvPlayerSurfaceDefaults.PanelShape)

@Composable
internal fun TvPlayerPanelSurface(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showHeader: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .tvPlayerSurface()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHeader) Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TvPlayerSurfaceDefaults.Content)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TvPlayerSurfaceDefaults.Muted)
                }
            }
        }
        content()
    }
}

@Composable
internal fun TvPlayerSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = TvPlayerSurfaceDefaults.Muted,
    )
}

@Composable
internal fun TvPlayerDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TvPlayerSurfaceDefaults.Outline),
    )
}

@Composable
internal fun TvRemoteHint(key: String, action: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            key,
            Modifier
                .background(TvPlayerSurfaceDefaults.Raised, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TvPlayerSurfaceDefaults.Content,
            maxLines = 1,
        )
        Text(
            action, style = MaterialTheme.typography.bodySmall, color = TvPlayerSurfaceDefaults.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
