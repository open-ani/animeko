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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.mediasource.web.displayName
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_web_captcha_unsupported
import me.him188.ani.app.ui.lang.media_selector_web_rate_limited
import me.him188.ani.app.ui.lang.media_selector_web_waiting_captcha
import me.him188.ani.datasources.api.Media
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDivider
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import me.him188.ani.leanback.ui.foundation.widgets.tvOptionSurfaceColors
import org.jetbrains.compose.resources.stringResource

private enum class SourceFocus : TvFocusKey { Entry, FirstResult }

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
    dialogState: TvSourceDialogState,
    selected: Media?,
    containerModifier: Modifier,
    onIntent: (TvEpisodeIntent) -> Boolean,
) {
    val simpleGroups = state.groups.filter { it.showInSimpleMode }
    // Capture the opening selection once: later query updates must not steal focus from the user.
    val entrySelection = remember {
        selected?.mediaId?.let { mediaId ->
            simpleGroups.firstOrNull { group ->
                group.items.any { it.media.mediaId == mediaId && it.excludedReason == null }
            }?.let { it.instanceId to mediaId }
        }
    }
    val entryGroupIndex = entrySelection?.let { (instanceId, mediaId) ->
        simpleGroups.indexOfFirst { group ->
            group.instanceId == instanceId &&
                    group.items.any { it.media.mediaId == mediaId && it.excludedReason == null }
        }
    } ?: -1
    val selectedGroup = dialogState.selectedGroup(state.groups)
    // Each source is one lazy item, including its loading/error state and channel row.
    val resultsState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (entryGroupIndex >= 0) 1 + entryGroupIndex else 0,
    )
    val tabsState = rememberLazyListState()
    val resultFocus = rememberTvFocusScope()
    resultFocus.Resolver()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var entryFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(dialogState.mode) {
        if (entryFocusRequested) return@LaunchedEffect
        if (dialogState.mode != TvSourceMode.Simple) {
            dialogState.mode = TvSourceMode.Simple
            return@LaunchedEffect
        }
        entryFocusRequested = true
        entrySelection?.let { dialogState.selectedSourceId = it.first }
        resultFocus.requestPrepared {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            if (entryGroupIndex >= 0) resultsState.scrollToItem(1 + entryGroupIndex)
            SourceFocus.Entry
        }
    }
    // Removing a focused result can briefly focus a mode tab. Do not treat that fallback as a mode choice.
    var restoreResultFocus by remember { mutableStateOf(false) }
    val showDetailedAtRowEnd = Modifier.onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionRight) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            restoreResultFocus = !resultFocus.isFocused(SourceFocus.FirstResult)
            dialogState.moveHorizontally(1, state.groups)
        }
        true
    }
    val resultKeys = Modifier.onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            restoreResultFocus = !resultFocus.isFocused(SourceFocus.FirstResult)
            dialogState.moveHorizontally(if (event.key == Key.DirectionRight) 1 else -1, state.groups)
        }
        true
    }
    LaunchedEffect(dialogState.mode, dialogState.selectedSourceId, dialogState.showExcluded, restoreResultFocus) {
        if (restoreResultFocus) {
            resultFocus.requestPrepared {
                resultsState.scrollToItem(0)
                SourceFocus.FirstResult
            }
        }
    }
    LaunchedEffect(dialogState.mode, selectedGroup?.instanceId) {
        if (dialogState.mode == TvSourceMode.Detailed) {
            val index = state.groups.indexOfFirst { it.instanceId == selectedGroup?.instanceId }
            if (index >= 0) {
                val layout = snapshotFlow { tabsState.layoutInfo }.first {
                    it.totalItemsCount == state.groups.size && it.viewportSize.width > 0
                }
                val tab = layout.visibleItemsInfo.firstOrNull { it.index == index }
                if (tab == null || tab.offset < layout.viewportStartOffset + layout.beforeContentPadding ||
                    tab.offset + tab.size > layout.viewportEndOffset - layout.afterContentPadding
                ) tabsState.scrollToItem(index)
            }
        }
    }
    val groups = if (dialogState.mode == TvSourceMode.Simple) {
        simpleGroups
    } else {
        listOfNotNull(selectedGroup)
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .background(TvOptionDefaults.Raised, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TvSourceTab(
                        "简单模式",
                        dialogState.mode == TvSourceMode.Simple,
                        Modifier
                            .then(
                                if (entryGroupIndex < 0) Modifier.tvFocusAnchor(
                                    resultFocus,
                                    SourceFocus.Entry
                                ) else Modifier
                            )
                            .onFocusChanged {
                                if (it.isFocused && !restoreResultFocus) {
                                    dialogState.mode = TvSourceMode.Simple
                                }
                            }
                            .testTag("tv-source-simple"),
                    ) { dialogState.mode = TvSourceMode.Simple }
                    TvSourceTab(
                        "详细模式",
                        dialogState.mode == TvSourceMode.Detailed,
                        Modifier
                            .onFocusChanged {
                                if (it.isFocused && !restoreResultFocus) {
                                    dialogState.mode = TvSourceMode.Detailed
                                }
                            }
                            .testTag("tv-source-detailed"),
                    ) { dialogState.mode = TvSourceMode.Detailed }
                }
            }
            if (dialogState.mode == TvSourceMode.Detailed) {
                TvOptionDivider()
                LazyRow(
                    state = tabsState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.testTag("tv-source-tabs"),
                ) {
                    items(state.groups, key = { it.instanceId }) { group ->
                        val status = rememberSourceStatusText(group)
                        val dimmed = !group.loading &&
                                (group.failed || group.items.none { it.excludedReason == null })
                        TvSourceTab(
                            group.name, selectedGroup?.instanceId == group.instanceId,
                            Modifier
                                .onFocusChanged {
                                    if (it.isFocused && !restoreResultFocus) {
                                        dialogState.selectedSourceId = group.instanceId
                                    }
                                }
                                .semantics { stateDescription = status },
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
                            dialogState.selectedSourceId = group.instanceId
                        }
                    }
                }
                TvOptionRow(
                    "显示排除的源",
                    checked = dialogState.showExcluded,
                    compact = true,
                    modifier = Modifier.testTag("tv-source-excluded"),
                ) {
                    dialogState.showExcluded = !dialogState.showExcluded
                }
            }
            TvOptionDivider()
            LazyColumn(
                state = resultsState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (dialogState.mode == TvSourceMode.Detailed) resultKeys else Modifier)
                    .testTag("tv-source-results"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                // A full-width refresh row is always focusable, including empty/error states.
                item(key = "retry") {
                    val retryModifier = Modifier
                        .onFocusChanged { if (it.isFocused) restoreResultFocus = false }
                        .tvFocusAnchor(resultFocus, SourceFocus.FirstResult)
                    if (dialogState.mode == TvSourceMode.Detailed && selectedGroup != null) {
                        TvSourceStatusAction(selectedGroup, retryModifier, onIntent)
                    } else {
                        TvOptionRow(
                            title = if (state.loading) "查询中…" else "${groups.size} 个数据源",
                            value = "重新查询", valueIcon = Icons.Rounded.Refresh,
                            modifier = retryModifier,
                        ) { onIntent(TvEpisodeIntent.RetrySources()) }
                    }
                }
                if (groups.isEmpty()) item {
                    Text(
                        state.error ?: when {
                            state.loading -> "正在查询数据源…"
                            state.groups.isEmpty() -> "没有可用的在线数据源，请在设置中添加"
                            else -> "没有找到可用线路"
                        },
                        color = TvOptionDefaults.Muted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                groups.forEach { group ->
                    val results =
                        group.items.filter { dialogState.mode == TvSourceMode.Detailed && dialogState.showExcluded || it.excludedReason == null }
                    if (dialogState.mode == TvSourceMode.Simple) {
                        item(key = "source-${group.instanceId}") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides TvOptionDefaults.Content) {
                                        TvSourceIcon(group.iconUrl, loading = group.loading || group.isResolvingCaptcha)
                                    }
                                    Text(group.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = TvOptionDefaults.Content)
                                    if (group.loading) Text(rememberSourceStatusText(group), color = TvOptionDefaults.Muted)
                                }
                                if (group.state is MediaSourceFetchState.CaptchaRequired || group.failed || group.state is MediaSourceFetchState.RateLimited) {
                                    TvSourceStatusAction(group, Modifier, onIntent)
                                }
                                if (results.isNotEmpty()) TvSourceChannelRow(
                                    results,
                                    selected?.mediaId,
                                    entryMediaId = entrySelection?.takeIf { it.first == group.instanceId }?.second,
                                    entryAnchorModifier = Modifier.tvFocusAnchor(resultFocus, SourceFocus.Entry),
                                    endOfRowModifier = showDetailedAtRowEnd,
                                ) { onIntent(TvEpisodeIntent.SelectMedia(it)) }
                            }
                        }
                    } else {
                        if (results.isEmpty() && group.state is MediaSourceFetchState.Succeed) item {
                            Text(
                                if (group.items.isNotEmpty()) "结果已被排除" else "没有找到资源",
                                color = TvOptionDefaults.Muted, modifier = Modifier.padding(16.dp),
                            )
                        }
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
private fun rememberSourceStatusText(group: TvSourceGroup): String = when (val state = group.state) {
    MediaSourceFetchState.Idle -> "等待查询"
    MediaSourceFetchState.Working -> "正在查询…"
    MediaSourceFetchState.Disabled -> "未启用"
    is MediaSourceFetchState.CaptchaRequired -> when {
        !group.isCaptchaSupported -> stringResource(Lang.media_selector_web_captcha_unsupported)
        group.isResolvingCaptcha -> stringResource(Lang.media_selector_web_waiting_captcha)
        else -> "需要处理${state.request.kind.displayName()}"
    }
    is MediaSourceFetchState.RateLimited -> {
        val remaining by produceState(((state.retryAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0), state.retryAt) {
            while (value > 0) {
                delay(1_000)
                value = ((state.retryAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            }
        }
        stringResource(Lang.media_selector_web_rate_limited, remaining)
    }
    is MediaSourceFetchState.Failed, is MediaSourceFetchState.Abandoned -> "查询失败"
    is MediaSourceFetchState.Succeed -> if (group.items.isEmpty()) "没有找到资源" else "${group.items.size} 个结果"
}

@Composable
private fun TvSourceStatusAction(group: TvSourceGroup, modifier: Modifier, onIntent: (TvEpisodeIntent) -> Boolean) {
    val captcha = group.state is MediaSourceFetchState.CaptchaRequired
    TvOptionRow(
        title = rememberSourceStatusText(group),
        value = if (captcha) {
            if (group.isResolvingCaptcha || !group.isCaptchaSupported) "" else "处理验证"
        } else "重新查询",
        enabled = !captcha || group.isCaptchaSupported && !group.isResolvingCaptcha,
        compact = true,
        modifier = modifier.testTag("tv-source-action-${group.instanceId}"),
        valueIcon = if (captcha) null else Icons.Rounded.Refresh,
    ) {
        onIntent(if (captcha) TvEpisodeIntent.ResolveSourceCaptcha(group.instanceId) else TvEpisodeIntent.RetrySources(group.instanceId))
    }
}

@Composable
private fun TvSourceChannelRow(
    items: List<TvSourceItem>,
    selectedMediaId: String?,
    entryMediaId: String?,
    entryAnchorModifier: Modifier,
    endOfRowModifier: Modifier,
    onSelect: (Media) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = items.indexOfFirst { it.media.mediaId == entryMediaId }.coerceAtLeast(0),
    )
    LazyRow(
        state = listState,
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
                    .then(if (item.media.mediaId == entryMediaId) entryAnchorModifier else Modifier)
                    .semantics { this.selected = selectedMediaId == item.media.mediaId }
                    .focusProperties { if (index == 0) left = FocusRequester.Cancel }
                    .then(if (index == items.lastIndex) endOfRowModifier else Modifier),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = if (selectedMediaId == item.media.mediaId) ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    focusedContainerColor = TvOptionDefaults.FocusedContainer,
                    focusedContentColor = TvOptionDefaults.FocusedContent,
                ) else tvOptionSurfaceColors(filled = true),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedMediaId == item.media.mediaId) Icon(
                        Icons.Rounded.Check,
                        "正在播放",
                        Modifier.size(18.dp)
                    )
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
                    .compositeOver(TvOptionDefaults.Container) else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.primary else TvOptionDefaults.Content,
                focusedContainerColor = TvOptionDefaults.FocusedContainer,
                focusedContentColor = TvOptionDefaults.FocusedContent,
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
                        focused -> TvOptionDefaults.FocusedContent.copy(alpha = TvSourceDialogDefaults.DimmedLabelAlpha)
                        else -> TvOptionDefaults.Content.copy(alpha = TvSourceDialogDefaults.DimmedLabelAlpha)
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
        shape = ClickableSurfaceDefaults.shape(TvOptionDefaults.ItemShape),
        colors = tvOptionSurfaceColors(selected, filled = true),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    media.originalTitle.ifBlank { media.properties.alliance.ifBlank { "播放资源" } },
                    style = MaterialTheme.typography.titleSmall,
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
