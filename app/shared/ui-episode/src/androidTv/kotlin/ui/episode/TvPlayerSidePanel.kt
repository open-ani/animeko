/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Neutral layers keep sidebar content consistent with its opaque black background. */
private val SidebarColors = TvPlayerSurfaceColors(
    container = Color.Black,
    raised = Color(0xFF181818),
    content = Color(0xFFF2F2F2),
    muted = Color(0xFFB3B3B3),
    outline = Color.White.copy(alpha = .16f),
    focusedContainer = Color.White,
    focusedContent = Color.Black,
    selectedContainer = Color(0xFF303030),
)

/** The screen owns animation and return focus; nested pages share this focus boundary. */
@Composable
internal fun TvPlayerSidePanel(
    title: String,
    onBack: () -> Unit,
    trapFocus: Boolean,
    modifier: Modifier = Modifier,
    backModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var backFocused by remember { mutableStateOf(false) }
    CompositionLocalProvider(
        LocalTvPlayerSurfaceColors provides SidebarColors,
        LocalContentColor provides SidebarColors.content,
    ) {
        Column(
            modifier
                .fillMaxSize()
                .background(SidebarColors.container)
                .padding(start = 16.dp, end = 28.dp, top = 28.dp, bottom = 28.dp)
                .semantics { paneTitle = title }
                .testTag("tv-player-sidebar")
                .focusProperties { onExit = { if (trapFocus) cancelFocus() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onBack,
                    modifier = backModifier
                        .testTag("tv-sidebar-back")
                        .onFocusChanged { backFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (!backFocused || event.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onBack()
                            true
                        },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = tvPlayerOptionColors(),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.padding(10.dp).size(20.dp))
                }
                Text(
                    title,
                    Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            content()
        }
    }
}

/** Indicates offscreen list content without adding another focus target. */
internal fun Modifier.tvPanelScrollEdges(
    state: LazyListState,
    backgroundColor: Color,
): Modifier = drawWithContent {
    drawContent()
    val edge = 12.dp.toPx().coerceAtMost(size.height / 2)
    if (state.canScrollBackward) drawRect(
        Brush.verticalGradient(listOf(backgroundColor, Color.Transparent), endY = edge),
        size = Size(size.width, edge),
    )
    if (state.canScrollForward) drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, backgroundColor),
            startY = size.height - edge,
            endY = size.height,
        ),
        topLeft = Offset(0f, size.height - edge),
        size = Size(size.width, edge),
    )
}
