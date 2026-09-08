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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionColors

/** Neutral layers keep sidebar content consistent with its opaque black background. */
private val SidebarColors = TvOptionColors(
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
    trapFocus: Boolean,
    modifier: Modifier = Modifier,
    endPadding: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalTvOptionColors provides SidebarColors,
        LocalContentColor provides SidebarColors.content,
    ) {
        Column(
            modifier
                .fillMaxSize()
                .background(SidebarColors.container)
                .padding(start = 16.dp, end = endPadding, top = 28.dp, bottom = 28.dp)
                .semantics { paneTitle = title }
                .testTag("tv-player-sidebar")
                .focusProperties { onExit = { if (trapFocus) cancelFocusChange() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, Modifier.testTag("tv-player-sidebar-title"), style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
