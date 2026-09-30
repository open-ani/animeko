/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FeaturedPlayList
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.episode.danmaku.DanmakuServiceIcon
import me.him188.ani.app.ui.episode.danmaku.DanmakuSourceChips
import me.him188.ani.app.ui.foundation.lists.LazyListVerticalScrollbar
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_collapse
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_empty
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_empty_filtered
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_title
import me.him188.ani.app.ui.lang.subject_episode_expand
import me.him188.ani.danmaku.api.DanmakuServiceId
import org.jetbrains.compose.resources.stringResource

/**
 * 弹幕列表区域组件，提供弹幕源选择和弹幕列表显示功能。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmakuListSection(
    state: DanmakuListState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetEnabled: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    onAdjustShift: (DanmakuServiceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listTitleText = stringResource(Lang.subject_episode_danmaku_list_title)
    val collapseText = stringResource(Lang.subject_episode_collapse)
    val expandText = stringResource(Lang.subject_episode_expand)

    Box(modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().offset(y = (-1).dp),
                ) {
                    DanmakuListContent(
                        state = state,
                        onSetEnabled = onSetEnabled,
                        onManualMatch = onManualMatch,
                        onAdjustShift = onAdjustShift,
                        modifier = Modifier.padding(top = 64.dp),
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                headlineContent = {
                    Text(listTitleText)
                },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Outlined.FeaturedPlayList, contentDescription = null)
                },
                trailingContent = {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) collapseText else expandText,
                    )
                },
                modifier = Modifier.clickable { onToggleExpanded() },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
            )
        }
    }
}

/**
 * 弹幕列表的实际内容，不包含可收起标题栏。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmakuListContent(
    state: DanmakuListState,
    onSetEnabled: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    onAdjustShift: (DanmakuServiceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emptyText = if (state.isEmpty) {
        stringResource(Lang.subject_episode_danmaku_list_empty)
    } else {
        stringResource(Lang.subject_episode_danmaku_list_empty_filtered)
    }

    Column(modifier = modifier) {
        // 弹幕源chips
        if (state.sourceItems.isNotEmpty()) {
            DanmakuSourceChips(
                sourceItems = state.sourceItems,
                onToggleSource = onSetEnabled,
                onManualMatch = onManualMatch,
                onAdjustShift = onAdjustShift,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        // 弹幕列表
        if (state.danmakuItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    items(
                        items = state.danmakuItems,
                        key = { it.randomId.toString() },
                    ) { danmaku ->
                        DanmakuListItemView(danmaku)
                    }
                }
                LazyListVerticalScrollbar(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp)
                        .placeScrollbarToAbsoluteRight(),
                )
            }
        }
    }
}

/**
 * 弹幕列表项视图组件，显示单条弹幕的详细信息。
 */
@Composable
private fun DanmakuListItemView(danmaku: DanmakuListItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (danmaku.isSelf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = danmaku.content,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (danmaku.isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${(danmaku.timeMillis / 1000 / 60).toInt()}:${
                        (danmaku.timeMillis / 1000 % 60).toInt().toString().padStart(2, '0')
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (danmaku.isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (danmaku.isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DanmakuServiceIcon(
                    serviceId = danmaku.serviceId,
                    size = 24,
                )
            }
        }
    }
}

/**
 * Places the scrollbar on the visual right edge regardless of layout direction.
 * Because Modifier.align(Alignment.CenterEnd) will perform mirroring based on the layout direction,
 * and we want the scroll bar to always be visually on the right side.
 */
private fun Modifier.placeScrollbarToAbsoluteRight(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val x = (width - placeable.width).coerceAtLeast(0)
        layout(width, height) {
            // use absolute positioning to ignore layout direction mirroring
            placeable.place(x, 0)
        }
    },
)
