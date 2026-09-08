/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerDialog
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanel
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanelPresentation
import me.him188.ani.leanback.ui.episode.presentation.title
import me.him188.ani.leanback.ui.foundation.layout.tvPanelScrollEdges
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDivider

@Composable
internal fun TvPlayerDialogSurface(
    dialog: TvPlayerDialog,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    if (dialog != TvPlayerDialog.Speed) {
        TvOptionModal(title, modifier, subtitle, content = content)
        return
    }
    Box(Modifier.fillMaxSize().padding(end = 48.dp, bottom = 150.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier.width(320.dp).heightIn(max = 340.dp)
                .tvPlayerSurface().padding(16.dp)
                .testTag("tv-speed-popup")
                .focusProperties { onExit = { cancelFocus() } }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TvOptionDefaults.Content)
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun TvOptionModal(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp = 480.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier
                .width(width)
                .heightIn(max = TvPlayerSurfaceDefaults.ModalMaxHeight)
                .tvPlayerSurface()
                .padding(24.dp)
                .focusProperties { onExit = { cancelFocus() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TvOptionDefaults.Content,
                    )
                }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TvOptionDefaults.Muted,
                    )
                }
            }
            TvOptionDivider()
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/** Shared panel chrome and scrolling; callers fill the list with their own content. */
@Composable
internal fun TvPlayerOptionPanelLayout(
    panel: TvPlayerPanel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val colors = LocalTvOptionColors.current
    val list: @Composable () -> Unit = {
        LazyColumn(
            listModifier.tvPanelScrollEdges(listState, colors.container).focusGroup(),
            state = listState,
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
    if (panel.presentation == TvPlayerPanelPresentation.Sidebar) {
        Box(modifier.fillMaxSize()) { list() }
    } else {
        TvPlayerPanelSurface(
            title = panel.title,
            icon = panel.icon,
            showHeader = panel != TvPlayerPanel.Collection,
            modifier = modifier.width(panel.width).heightIn(max = TvPlayerSurfaceDefaults.PanelMaxHeight),
        ) { list() }
    }
}
