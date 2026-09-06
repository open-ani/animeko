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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.datasources.api.Media
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal

private enum class SourceFocus : TvFocusKey { FirstResult }

private object TvSourceDialogDefaults {
    const val WidthFraction = 2f / 3f
    const val DimmedLabelAlpha = .38f
    val HorizontalMargin = 48.dp
    val VerticalMargin = 28.dp
    val ChipMinWidth = 72.dp
    val ChipMaxWidth = 240.dp
    val ChipMinHeight = 44.dp
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun TvPlayerSourceDialog(
    state: TvSourceSelectionState,
    selected: Media?,
    containerModifier: Modifier,
    entryAnchorModifier: Modifier,
    onIntent: (TvEpisodeIntent) -> Boolean,
) {
    val resultsState = rememberLazyListState()
    val tabsState = rememberLazyListState()
    val resultFocus = rememberTvFocusScope()
    resultFocus.Resolver()
    // Removing a focused result can briefly focus a mode tab. Do not treat that fallback as a mode choice.
    var restoreResultFocus by remember { mutableStateOf(false) }
    val showDetailedAtRowEnd = Modifier.onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionRight) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            restoreResultFocus = !resultFocus.isFocused(SourceFocus.FirstResult)
            onIntent(TvEpisodeIntent.MoveSource(1))
        }
        true
    }
    val resultKeys = Modifier.onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            restoreResultFocus = !resultFocus.isFocused(SourceFocus.FirstResult)
            onIntent(TvEpisodeIntent.MoveSource(if (event.key == Key.DirectionRight) 1 else -1))
        }
        true
    }
    LaunchedEffect(state.mode, state.selectedSourceId, state.showExcluded, restoreResultFocus) {
        if (restoreResultFocus) {
            resultsState.scrollToItem(0)
            resultFocus.request(SourceFocus.FirstResult)
        }
    }
    LaunchedEffect(state.mode, state.selectedGroup?.instanceId) {
        if (state.mode == TvSourceMode.Detailed) {
            val index = state.groups.indexOfFirst { it.instanceId == state.selectedGroup?.instanceId }
            if (index >= 0 && tabsState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                tabsState.scrollToItem(index)
            }
        }
    }
    val groups = if (state.mode == TvSourceMode.Simple) {
        state.groups.filter { group -> group.items.any { it.excludedReason == null } }
    } else {
        listOfNotNull(state.selectedGroup)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .8f))
            .padding(
                horizontal = TvSourceDialogDefaults.HorizontalMargin,
                vertical = TvSourceDialogDefaults.VerticalMargin,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            containerModifier
                .tvFocusNavSignal(resultFocus)
                .fillMaxWidth(TvSourceDialogDefaults.WidthFraction)
                .fillMaxHeight()
                .tvPlayerSurface()
                .padding(24.dp)
                .testTag("tv-source-dialog")
                .focusProperties { onExit = { cancelFocus() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .background(TvPlayerSurfaceDefaults.Raised, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TvSourceTab(
                        "简单模式",
                        state.mode == TvSourceMode.Simple,
                        Modifier
                            .then(if (state.mode == TvSourceMode.Simple) entryAnchorModifier else Modifier)
                            .onFocusChanged {
                                if (it.isFocused && !restoreResultFocus) {
                                    onIntent(TvEpisodeIntent.SetSourceMode(TvSourceMode.Simple))
                                }
                            }
                            .testTag("tv-source-simple"),
                    ) { onIntent(TvEpisodeIntent.SetSourceMode(TvSourceMode.Simple)) }
                    TvSourceTab(
                        "详细模式",
                        state.mode == TvSourceMode.Detailed,
                        Modifier
                            .then(if (state.mode == TvSourceMode.Detailed) entryAnchorModifier else Modifier)
                            .onFocusChanged {
                                if (it.isFocused && !restoreResultFocus) {
                                    onIntent(TvEpisodeIntent.SetSourceMode(TvSourceMode.Detailed))
                                }
                            }
                            .testTag("tv-source-detailed"),
                    ) { onIntent(TvEpisodeIntent.SetSourceMode(TvSourceMode.Detailed)) }
                }
            }
            if (state.mode == TvSourceMode.Detailed) {
                TvPlayerDivider()
                LazyRow(
                    state = tabsState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.testTag("tv-source-tabs"),
                ) {
                    items(state.groups, key = { it.instanceId }) { group ->
                        val dimmed = !group.loading &&
                                (group.failed || group.items.none { it.excludedReason == null })
                        TvSourceTab(
                            group.name, state.selectedGroup?.instanceId == group.instanceId,
                            Modifier
                                .onFocusChanged {
                                    if (it.isFocused && !restoreResultFocus) onIntent(
                                        TvEpisodeIntent.SelectSourceTab(
                                            group.instanceId
                                        )
                                    )
                                }
                                .semantics { stateDescription = group.status },
                            underline = true,
                            dimmed = dimmed,
                            leadingIcon = {
                                TvSourceIcon(
                                    group.iconUrl,
                                    loading = group.loading,
                                    dimmed = dimmed,
                                )
                            },
                        ) {
                            onIntent(TvEpisodeIntent.SelectSourceTab(group.instanceId))
                        }
                    }
                }
                TvOptionRow(
                    "显示排除的源",
                    checked = state.showExcluded,
                    modifier = Modifier.testTag("tv-source-excluded"),
                ) {
                    onIntent(TvEpisodeIntent.ToggleExcludedSources)
                }
            }
            TvPlayerDivider()
            LazyColumn(
                state = resultsState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (state.mode == TvSourceMode.Detailed) resultKeys else Modifier)
                    .testTag("tv-source-results"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                // A full-width refresh row is always focusable, including empty/error states.
                item(key = "retry") {
                    val status = when {
                        state.loading -> "查询中…"
                        state.mode == TvSourceMode.Simple -> "${groups.size} 个数据源"
                        else -> state.selectedGroup?.status.orEmpty()
                    }
                    TvOptionRow(
                        title = status,
                        value = "重新查询",
                        valueIcon = Icons.Rounded.Refresh,
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) restoreResultFocus = false }
                            .tvFocusAnchor(resultFocus, SourceFocus.FirstResult),
                    ) {
                        onIntent(TvEpisodeIntent.RetrySources(if (state.mode == TvSourceMode.Detailed) state.selectedGroup?.instanceId else null))
                    }
                }
                if (groups.isEmpty()) item {
                    Text(
                        state.error ?: when {
                            state.loading -> "正在查询数据源…"
                            state.groups.isEmpty() -> "没有可用的在线数据源，请在设置中添加"
                            else -> "没有找到可用线路"
                        },
                        color = TvPlayerSurfaceDefaults.Muted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                groups.forEach { group ->
                    if (state.mode == TvSourceMode.Simple) item(key = "header-${group.instanceId}") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 10.dp, bottom = 2.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!group.iconUrl.isNullOrBlank()) AsyncImage(
                                group.iconUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            ) else Icon(
                                Icons.Rounded.VideoLibrary,
                                null,
                                Modifier.size(24.dp),
                                tint = TvPlayerSurfaceDefaults.Muted,
                            )
                            Text(
                                group.name,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = TvPlayerSurfaceDefaults.Content,
                            )
                            Text(
                                group.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = TvPlayerSurfaceDefaults.Muted,
                            )
                        }
                    }
                    val results =
                        group.items.filter { state.mode == TvSourceMode.Detailed && state.showExcluded || it.excludedReason == null }
                    if (results.isEmpty()) item(key = "empty-${group.instanceId}") {
                        TvOptionRow(
                            if (group.items.isNotEmpty()) "结果已被排除" else group.status,
                            filled = true,
                        ) {
                            onIntent(TvEpisodeIntent.RetrySources(group.instanceId))
                        }
                    }
                    if (state.mode == TvSourceMode.Simple && results.isNotEmpty()) {
                        item(key = "channels-${group.instanceId}") {
                            TvSourceChannelRow(
                                results,
                                selected?.mediaId,
                                endOfRowModifier = showDetailedAtRowEnd,
                            ) { onIntent(TvEpisodeIntent.SelectMedia(it)) }
                        }
                    } else {
                        items(results, key = { "${group.instanceId}-${it.media.mediaId}" }) { item ->
                            TvSourceResultCard(item, selected?.mediaId == item.media.mediaId) {
                                onIntent(TvEpisodeIntent.SelectMedia(item.media))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSourceChannelRow(
    items: List<TvSourceItem>,
    selectedMediaId: String?,
    endOfRowModifier: Modifier,
    onSelect: (Media) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .testTag("tv-source-channels"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.media.mediaId }) { index, item ->
            Surface(
                onClick = { onSelect(item.media) },
                modifier = Modifier
                    .heightIn(min = TvSourceDialogDefaults.ChipMinHeight)
                    .widthIn(min = TvSourceDialogDefaults.ChipMinWidth, max = TvSourceDialogDefaults.ChipMaxWidth)
                    .focusProperties { if (index == 0) left = FocusRequester.Cancel }
                    .then(if (index == items.lastIndex) endOfRowModifier else Modifier),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = if (selectedMediaId == item.media.mediaId) ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    focusedContainerColor = TvPlayerSurfaceDefaults.FocusedContainer,
                    focusedContentColor = TvPlayerSurfaceDefaults.FocusedContent,
                ) else tvPlayerOptionColors(filled = true),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        item.media.properties.alliance.ifBlank { "默认线路" },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSourceTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    underline: Boolean = false,
    dimmed: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier.onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                    .compositeOver(TvPlayerSurfaceDefaults.Container) else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.primary else TvPlayerSurfaceDefaults.Content,
                focusedContainerColor = TvPlayerSurfaceDefaults.FocusedContainer,
                focusedContentColor = TvPlayerSurfaceDefaults.FocusedContent,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.invoke()
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    color = when {
                        !dimmed -> Color.Unspecified
                        focused -> TvPlayerSurfaceDefaults.FocusedContent.copy(alpha = TvSourceDialogDefaults.DimmedLabelAlpha)
                        else -> TvPlayerSurfaceDefaults.Content.copy(alpha = TvSourceDialogDefaults.DimmedLabelAlpha)
                    },
                )
            }
        }
        if (underline) Box(
            Modifier
                .padding(top = 6.dp)
                .size(20.dp, 3.dp)
                .background(
                    if (selected && !focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun TvSourceResultCard(item: TvSourceItem, selected: Boolean, onClick: () -> Unit) {
    val media = item.media
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(TvPlayerSurfaceDefaults.ItemShape),
        colors = tvPlayerOptionColors(selected, filled = true),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(LocalContentColor.current.copy(alpha = .1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected) Icons.Rounded.Check else Icons.Rounded.PlayArrow,
                    if (selected) "已选择" else null,
                    Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    media.originalTitle.ifBlank { media.properties.alliance.ifBlank { "播放资源" } },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val info = listOf(
                    media.properties.alliance,
                    media.properties.resolution,
                    media.properties.subtitleLanguageIds.joinToString(" / "),
                ).filter { it.isNotBlank() }.distinct()
                if (info.isNotEmpty()) FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    info.forEach { label ->
                        Text(
                            label,
                            Modifier
                                .background(LocalContentColor.current.copy(alpha = .08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item.excludedReason?.let {
                    Text(
                        "排除原因：$it · 仍可手动选择",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
