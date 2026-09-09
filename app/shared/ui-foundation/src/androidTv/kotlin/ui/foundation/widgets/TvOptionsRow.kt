/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.foundation.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.leanback.ui.foundation.layout.TvOptionAnchors

@Immutable
data class TvOptionButtonDimensions(val sidePadding: Dp, val iconSize: Dp, val contentSpacing: Dp) {
    fun widthWithLabel(labelWidth: Dp): Dp = sidePadding * 2 + iconSize + contentSpacing + labelWidth
}

object TvOptionsRowDefaults {
    val ChipDimensions = TvOptionButtonDimensions(14.dp, 18.dp, 8.dp)
    val Spacing = 12.dp
}

/** A wrapping action row, optionally hosting a panel above one of its measured buttons. */
@Composable
fun TvOptionsRow(
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    anchors: TvOptionAnchors? = null,
    activeKey: Any? = null,
    panelWidth: Dp = TvOptionPanelDefaults.Width,
    panel: (@Composable () -> Unit)? = null,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column(modifier) {
        if (panel != null && activeKey != null) BoxWithConstraints(
            Modifier.fillMaxWidth().padding(bottom = TvOptionPanelDefaults.Gap),
        ) {
            var originX by remember { mutableStateOf<Float?>(null) }
            val density = LocalDensity.current
            val availableWidth = maxWidth
            val bounds = anchors?.boundsOf(activeKey)
            Box(Modifier.fillMaxWidth().onGloballyPositioned { originX = it.positionInWindow().x }) {
                originX?.let { origin ->
                    val start = with(density) { ((bounds?.left ?: origin) - origin).toDp() }
                        .coerceIn(0.dp, (availableWidth - panelWidth).coerceAtLeast(0.dp))
                    Box(Modifier.offset(x = start)) { panel() }
                }
            }
        }
        FlowRow(
            rowModifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(TvOptionsRowDefaults.Spacing),
            verticalArrangement = Arrangement.spacedBy(TvOptionsRowDefaults.Spacing),
            itemVerticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Outlined option chip, extracted from the player; active and keyboard focus are separate states. */
@Composable
fun TvOptionChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val colors = LocalTvOptionColors.current
    val dimensions = TvOptionsRowDefaults.ChipDimensions
    val outline = if (active) MaterialTheme.colorScheme.primary else colors.content.copy(alpha = .45f)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label; selected = active },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, focusedContainerColor = colors.focusedContainer,
            contentColor = if (active) MaterialTheme.colorScheme.primary else colors.content,
            focusedContentColor = colors.focusedContent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (active) 2.dp else 1.dp, outline), shape = CircleShape),
            focusedBorder = Border(BorderStroke(1.dp, colors.focusedContainer), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(horizontal = dimensions.sidePadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(dimensions.contentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(dimensions.iconSize))
            if (showLabel) Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
