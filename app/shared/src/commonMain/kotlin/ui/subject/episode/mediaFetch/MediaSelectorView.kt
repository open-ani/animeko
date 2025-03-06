/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.datasources.api.Media


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
        val showIndicator by remember(group) {
            derivedStateOf {
                group.list.any { it.original === presentation.selected }
            }
        }
        FastLinearProgressIndicator(
            showIndicator,
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
            if (currentAniBuildConfig.isDebug) {
                item {
                    Surface {
                        Row {
                            Text("Debug tools: ")
                            FilledTonalButton(onClick = { MediaSelectorDebugTools.dumpSubjectNames(presentation.filteredCandidates) }) {
                                Text("Dump unique subject names")
                            }
                            FilledTonalButton(
                                onClick = {
                                    MediaSelectorDebugTools.dumpMediaSubjectNameAndEpisodes(
                                        presentation.filteredCandidates,
                                    )
                                },
                            ) {
                                Text("Dump unique subject names with eps")
                            }
                        }
                    }
                }
            }
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
        MediaSelectorItem(
            group,
            groupState = state.getGroupState(group.groupId),
            state.mediaSourceInfoProvider,
            selected = group.list.any { it.original === presentation.selected },
            onSelect = {
                // 点击这个卡片时, 如果这个卡片是一个 group, 那么应当取用 group 的选中项目
                onClickItem(state.getGroupState(group.groupId).selectedItem ?: it)
            },
            preferredResolution = { presentation.resolution.finalSelected },
            onPreferResolution = { state.resolution.preferOrRemove(it) },
            preferredSubtitleLanguageId = { presentation.subtitleLanguageId.finalSelected },
            onPreferSubtitleLanguageId = { state.subtitleLanguageId.preferOrRemove(it) },
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

