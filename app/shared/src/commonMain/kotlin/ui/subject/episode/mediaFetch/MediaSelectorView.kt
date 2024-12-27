/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.FileSize


private inline val WINDOW_VERTICAL_PADDING get() = 8.dp

// For search: "数据源"
/**
 * 通用的数据源选择器. See preview
 *
 * @param bottomActions shown at the bottom
 */
@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
fun MediaSelectorView(
    state: MediaSelectorState,
    sourceResults: @Composable LazyItemScope.() -> Unit,
    modifier: Modifier = Modifier,
    stickyHeaderBackgroundColor: Color = Color.Unspecified,
    itemProgressBar: @Composable RowScope.(MediaGroup) -> Unit = { group ->
        val presentation by state.presentationFlow.collectAsStateWithLifecycle()
        FastLinearProgressIndicator(
            group.list.any { it.original === presentation.selected },
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            delayMillis = 300,
        )
    },
    onClickItem: ((Media) -> Unit) = { state.select(it) },
    bottomActions: (@Composable RowScope.() -> Unit)? = null,
    singleLineFilter: Boolean = false,
) {
    val bringIntoViewRequesters = remember { mutableStateMapOf<Media, BringIntoViewRequester>() }
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    Column(modifier) {
        val lazyListState = rememberLazyListState()
        var showExcluded by rememberSaveable { mutableStateOf(false) }
        LazyColumn(
            Modifier.padding(bottom = WINDOW_VERTICAL_PADDING).weight(1f, fill = false),
            lazyListState,
        ) {
            item {
                Row(Modifier.padding(bottom = 12.dp)) {
                    sourceResults()
                }
            }

            stickyHeader {
                val isStuck by remember(lazyListState) {
                    derivedStateOf {
                        lazyListState.firstVisibleItemIndex == 1
                    }
                }
                Column(
                    Modifier.background(stickyHeaderBackgroundColor).padding(bottom = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        remember(presentation.preferredCandidates.size, presentation.filteredCandidates.size) {
                            "筛选到 ${presentation.preferredCandidates.size}/${presentation.filteredCandidates.size} 条资源"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )

                    MediaSelectorFilters(
                        resolution = state.resolution,
                        subtitleLanguageId = state.subtitleLanguageId,
                        alliance = state.alliance,
                        singleLine = singleLineFilter,
                    )
                }
                if (isStuck) {
                    HorizontalDivider(Modifier.fillMaxWidth(), thickness = 2.dp)
                }
            }

            items(presentation.groupedMediaListIncluded, key = { it.groupId }) { group ->
                MediaItemGroup(group, bringIntoViewRequesters, state, presentation, onClickItem, itemProgressBar)
            }

            if (presentation.groupedMediaListExcluded.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "显示已被排除的资源 (${presentation.groupedMediaListExcluded.size})",
                            Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Switch(showExcluded, { showExcluded = !showExcluded })
                    }
                }
            }
            if (showExcluded) {
                items(presentation.groupedMediaListExcluded, key = { it.groupId }) { group ->
                    MediaItemGroup(group, bringIntoViewRequesters, state, presentation, onClickItem, itemProgressBar)
                }
            }

            item { } // dummy spacer
        }

        if (bottomActions != null) {
            HorizontalDivider(Modifier.padding(bottom = 8.dp))

            Row(
                Modifier.align(Alignment.End).padding(bottom = 8.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    bottomActions()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // 当选择一个资源时 (例如自动选择)，自动滚动到该资源 #667
        snapshotFlow { presentation.selected }
            .filterNotNull()
            .collectLatest {
                bringIntoViewRequesters[it]?.bringIntoView()
            }
    }
}

@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
private fun LazyItemScope.MediaItemGroup(
    group: MediaGroup,
    bringIntoViewRequesters: SnapshotStateMap<Media, BringIntoViewRequester>,
    state: MediaSelectorState,
    presentation: MediaSelectorState.Presentation,
    onClickItem: (Media) -> Unit,
    itemProgressBar: @Composable (RowScope.(MediaGroup) -> Unit)
) {
    Column {
        val requester = remember { BringIntoViewRequester() }
        // 记录 item 对应的 requester
        for (item in group.list) {
            DisposableEffect(requester) {
                bringIntoViewRequesters[item.original] = requester
                onDispose {
                    bringIntoViewRequesters.remove(item.original)
                }
            }
        }
        MediaItem(
            group,
            state.mediaSourceInfoProvider,
            group.list.any { it.original === presentation.selected },
            state,
            onSelect = {
                // 点击这个卡片时, 如果这个卡片是一个 group, 那么应当取用 group 的选中项目
                onClickItem(state.getGroupState(group.groupId).selectedItem ?: it)
            },
            Modifier
                .animateItem()
                .fillMaxWidth()
                .bringIntoViewRequester(requester),
        )
        Row(Modifier.height(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            itemProgressBar(group)
        }
    }
}

/**
 * 一个资源的卡片
 */
@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
internal fun MediaItem(
    group: MediaGroup,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    selected: Boolean,
    state: MediaSelectorState,
    onSelect: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    val media: Media = group.first.original // 信息都一样的, 用第一个的就行
    ElevatedCard(
        { onSelect(media) },
        modifier
            .width(IntrinsicSize.Min),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        val horizontalPadding = 16.dp
        Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                Text(media.originalTitle, Modifier.padding(horizontal = horizontalPadding))
            }

            // Labels
            FlowRow(
                Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (media.properties.size != FileSize.Zero && media.properties.size != FileSize.Unspecified) {
                    InputChip(
                        false,
                        onClick = {},
                        label = { Text(media.properties.size.toString()) },
                    )
                }
                val resolutionPresentation by state.resolution.presentationFlow.collectAsStateWithLifecycle()
                InputChip(
                    false,
                    onClick = { state.resolution.preferOrRemove(media.properties.resolution) },
                    label = { Text(media.properties.resolution) },
                    enabled = resolutionPresentation.finalSelected != media.properties.resolution,
                )
                val subjectLanguageIdPresentation by state.subtitleLanguageId.presentationFlow.collectAsStateWithLifecycle()
                media.properties.subtitleLanguageIds.map {
                    InputChip(
                        false,
                        onClick = { state.subtitleLanguageId.preferOrRemove(it) },
                        label = { Text(renderSubtitleLanguage(it)) },
                        enabled = subjectLanguageIdPresentation.finalSelected != it,
                    )
                }

                group.exclusionReason?.let { reason ->
                    val reasonText = if (currentAniBuildConfig.isDebug) {
                        reason.toString()
                    } else {
                        when (reason) {
                            MediaExclusionReason.MediaWithoutSubtitle -> "无字幕"
                            is MediaExclusionReason.SingleEpisodeForCompleteSubject -> "单集资源"
                            MediaExclusionReason.UnsupportedByPlatformPlayer -> "不支持播放"
                            MediaExclusionReason.FromSequelSeason -> "季度不匹配"
                            MediaExclusionReason.FromSeriesSeason -> "季度不匹配(2)"
                        }
                    }
                    InputChip(
                        false,
                        onClick = { },
                        label = { Text(reasonText) },
                        colors = InputChipDefaults.inputChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            true,
                            false,
                            borderColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
            }

            // Bottom row: source, alliance, published time
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Row(
                    Modifier.padding(
                        start = horizontalPadding - 8.dp, // icon
                        end = horizontalPadding,
                    ).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Layout note:
                    // On overflow, only the alliance will be ellipsized.

                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExposedMediaSourceMenu(
                            group, mediaSourceInfoProvider, state,
                            onSelect = onSelect,
                        )

                        Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                            Text(
                                media.properties.alliance,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Text(
                        formatDateTime(media.publishedTime, showTime = false),
                        Modifier.padding(start = 16.dp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
private fun ExposedMediaSourceMenu(
    group: MediaGroup,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    state: MediaSelectorState,
    onSelect: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(showMenu, { showMenu = it }, modifier) {
        val currentItem = state.getGroupState(group.groupId).selectedItem ?: group.first.original
        val currentSourceInfo by mediaSourceInfoProvider.rememberMediaSourceInfo(currentItem.mediaSourceId)
        TextField(
            value = currentSourceInfo?.displayName ?: "未知",
            onValueChange = {},
            Modifier
                .widthIn(min = 48.dp) // override default
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            maxLines = 1,
            leadingIcon = {
                Icon(MediaSourceIcons.location(currentItem.location, currentItem.kind), null)
            },
            trailingIcon = if (group.list.size > 1) {
                {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = showMenu,
                        Modifier.menuAnchor(MenuAnchorType.SecondaryEditable),
                    )
                }
            } else null,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )
        ExposedDropdownMenu(showMenu, { showMenu = false }) {
            for (maybeExcluded in group.list) {
                val item = maybeExcluded.original
                val sourceInfo by mediaSourceInfoProvider.rememberMediaSourceInfo(item.mediaSourceId)
                DropdownMenuItem(
                    text = { Text(sourceInfo?.displayName ?: "未知") },
                    leadingIcon = { MediaSourceIcon(sourceInfo, Modifier.size(24.dp)) },
                    onClick = {
                        state.getGroupState(group.groupId).selectedItem = item
                        onSelect(item)
                        showMenu = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
