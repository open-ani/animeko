/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.foundation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

object TvOptionPanelDefaults {
    val Shape = RoundedCornerShape(20.dp)
    val Width = 248.dp
    val MaxHeight = 276.dp
    val Gap = 12.dp
    val ScreenPadding = 24.dp
}

fun Modifier.tvOptionPanelSurface(): Modifier = shadow(16.dp, TvOptionPanelDefaults.Shape)
    .background(TvOptionDefaults.Container, TvOptionPanelDefaults.Shape)
    .border(1.dp, TvOptionDefaults.Outline, TvOptionPanelDefaults.Shape)

/** Shared option-panel chrome. The caller owns its content, placement and focus protocol. */
@Composable
fun TvOptionPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    TvOptionContent {
        Column(modifier.tvOptionPanelSurface().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) Box(
                    Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TvOptionDefaults.Content)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TvOptionDefaults.Muted) }
                }
            }
            content()
        }
    }
}
