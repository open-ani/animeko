/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.TestMatchMetadata
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.icons.EditSquare
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_more
import me.him188.ani.app.ui.mediafetch.request.MediaFetchRequestEditorDialog
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.mediaselect.selector.MediaSelectorWebSourcesColumn
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.PreviewLightDark


private inline val WINDOW_VERTICAL_PADDING get() = 8.dp

// For search: "数据源"
/**
 * 通用的数据源选择器. See preview
 */
@Composable
fun MediaSelectorView(
    state: MediaSelectorState,
    viewKind: ViewKind,
    onViewKindChange: (ViewKind) -> Unit,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    sourceResults: MediaSourceResultListPresentation,
    onRestartSource: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    stickyHeaderBackgroundColor: Color = Color.Unspecified,
    onClickItem: (Media) -> Unit = { state.select(it) },
    singleLineFilter: Boolean = false,
    scrollable: Boolean = true,
) {
    val bringIntoViewRequesters = remember { mutableStateMapOf<Media, BringIntoViewRequester>() }
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()

    Column(modifier) {
        val lazyListState = rememberLazyListState()
        var showExcluded by rememberSaveable { mutableStateOf(false) }

        // 编辑查询请求的对话框
        var showEditRequest by rememberSaveable { mutableStateOf(false) }
        if (showEditRequest && fetchRequest != null) {
            MediaFetchRequestEditorDialog(
                fetchRequest,
                onDismissRequest = { showEditRequest = false },
                onFetchRequestChange = {
                    onFetchRequestChange(it)
                    showEditRequest = false
                },
            )
        }

        // 切换数据源类型的按钮
        ViewKindAndMoreRow(
            viewKind,
            onViewKindChange,
            onRequestFetchRequestEdit = { showEditRequest = true },
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )

        AnimatedContent(
            viewKind,
            transitionSpec = LocalAniMotionScheme.current.animatedContent.topLevel,
            contentAlignment = Alignment.TopCenter,
        ) { target ->
            val bottomPadding = if (LocalPlatform.current.isMobile()) 0.dp else WINDOW_VERTICAL_PADDING
            when (target) {
                ViewKind.WEB -> {
                    MediaSelectorWebSourcesColumn(
                        presentation.webSources,
                        selectedSource = { presentation.selectedWebSource },
                        selectedChannel = { presentation.selectedWebSourceChannel },
                        onSelect = { _, channel ->
                            channel.original?.let { onClickItem(it) }
                        },
                        onRefresh = { onRestartSource(it.instanceId) },
                        onRequestQueryEdit = { showEditRequest = true },
                        Modifier.padding(bottom = bottomPadding)
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .ifThen(scrollable) { verticalScroll(rememberScrollState()) },
                    )
                }

                ViewKind.BT -> {
                    Column {
                        LegacyBTSourceColumn(
                            lazyListState,
                            presentation,
                            sourceResults = {
                                MediaSourceResultsView(
                                    sourceResults,
                                    state,
                                    onRefresh,
                                    onRestartSource,
                                )
                            },
                            stickyHeaderBackgroundColor,
                            state,
                            singleLineFilter,
                            bringIntoViewRequesters,
                            onClickItem,
                            showExcluded,
                            onShowExcludedChange = { showExcluded = !showExcluded },
                            Modifier.padding(bottom = bottomPadding)
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        )
                    }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewKindAndMoreRow(
    viewKind: ViewKind,
    onViewKindChange: (ViewKind) -> Unit,
    onRequestFetchRequestEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow(
            Modifier.weight(1f),
        ) {
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                onClick = { onViewKindChange(ViewKind.WEB) },
                selected = viewKind == ViewKind.WEB,
            ) {
                Text("简单模式", softWrap = false)
            }
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                onClick = { onViewKindChange(ViewKind.BT) },
                selected = viewKind == ViewKind.BT,
            ) {
                Text("详细模式", softWrap = false)
            }
        }

        Box {
            IconButton(onRequestFetchRequestEdit) {
                Icon(Icons.Rounded.EditSquare, contentDescription = stringResource(Lang.settings_media_source_more))
            }
//            DropdownMenu(showDropdown, { showDropdown = false }) {
//                DropdownMenuItem(
//                    text = { Text("编辑查询请求") },
//                    onClick = {
//                        showEditRequest = true
//                        showDropdown = false
//                    },
//                )
//            }

            // 编辑请求
        }
    }
}


@Composable
private fun LegacyBTSourceColumn(
    lazyListState: LazyListState,
    presentation: MediaSelectorState.Presentation,
    sourceResults: @Composable() (LazyItemScope.() -> Unit),
    stickyHeaderBackgroundColor: Color,
    state: MediaSelectorState,
    singleLineFilter: Boolean,
    bringIntoViewRequesters: SnapshotStateMap<Media, BringIntoViewRequester>,
    onClickItem: (Media) -> Unit,
    showExcluded: Boolean,
    onShowExcludedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier,
        lazyListState,
    ) {
        if (currentAniBuildConfig.isDebug) {
            item {
                Surface {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Debug tools: ")
                        FilledTonalButton(onClick = { MediaSelectorDebugTools.dumpSubjectNames(presentation.filteredCandidates) }) {
                            Text("Dump unique media lists")
                        }
                        FilledTonalButton(onClick = { MediaSelectorDebugTools.dumpEpisodeRanges(presentation.filteredCandidates) }) {
                            Text("Dump EpisodeRanges")
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
            MediaItemGroup(group, bringIntoViewRequesters, state, presentation, onClickItem)
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
                    Switch(showExcluded, { onShowExcludedChange() })
                }
            }
        }
        if (showExcluded) {
            items(presentation.groupedMediaListExcluded, key = { it.groupId }) { group ->
                MediaItemGroup(group, bringIntoViewRequesters, state, presentation, onClickItem)
            }
        }

        item { } // dummy spacer
    }
}

@Serializable
enum class ViewKind {
    WEB,
    BT,
}

@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
private fun LazyItemScope.MediaItemGroup(
    group: MediaGroup,
    bringIntoViewRequesters: SnapshotStateMap<Media, BringIntoViewRequester>,
    state: MediaSelectorState,
    presentation: MediaSelectorState.Presentation,
    onClickItem: (Media) -> Unit,
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
        val scope = rememberCoroutineScope()
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
            onPreferResolution = { scope.launch { state.resolution.preferOrRemove(it) } },
            preferredSubtitleLanguageId = { presentation.subtitleLanguageId.finalSelected },
            onPreferSubtitleLanguageId = { scope.launch { state.subtitleLanguageId.preferOrRemove(it) } },
            Modifier
                .animateItem()
                .fillMaxWidth()
                .bringIntoViewRequester(requester),
        )
        Spacer(Modifier.height(8.dp))
    }
}


///////////////////////////////////////////////////////////////////////////
// Previews
///////////////////////////////////////////////////////////////////////////


@TestOnly
internal val previewMediaList = TestMediaList.run {
    listOf(
        CachedMedia(
            origin = this[0],
            cacheMediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
            download = ResourceLocation.LocalFile("file://test.txt"),
        ),
    ) + this
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewMediaSelector() {
    val scope = rememberCoroutineScope()
    val mediaSelector = rememberTestMediaSelectorPresentation(previewMediaList, scope)
    ProvideCompositionLocalsForPreview {
        Surface {
            val (viewKind, onViewKindChange) = rememberSaveable { mutableStateOf(ViewKind.WEB) }
            MediaSelectorView(
                state = mediaSelector,
                viewKind = viewKind,
                onViewKindChange = onViewKindChange,
                fetchRequest = TestMediaFetchRequest,
                onFetchRequestChange = { },
                sourceResults = TestMediaSourceResultListPresentation,
                onRestartSource = {
                },
                onRefresh = {},
            )
        }
    }
}

@Composable
@OptIn(TestOnly::class)
private fun rememberTestMediaSelectorPresentation(previewMediaList: List<Media>, scope: CoroutineScope) =
    rememberMediaSelectorState(
        rememberTestMediaSourceInfoProvider(),
        createTestMediaSourceResultsFilterer(scope).filteredSourceResults,
    ) {
        DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
            mediaListNotCached = MutableStateFlow(
                listOf(
                    CachedMedia(
                        origin = previewMediaList[0],
                        cacheMediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
                        download = ResourceLocation.LocalFile("file://test.txt"),
                    ),
                ) + previewMediaList,
            ),
            savedUserPreference = flowOf(MediaPreference.Empty),
            savedDefaultPreference = flowOf(
                MediaPreference.PlatformDefault.copy(
                    subtitleLanguageId = "CHS",
                ),
            ),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.AllVisible),
        )
    }

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewMediaItemIncluded(modifier: Modifier = Modifier) = ProvideCompositionLocalsForPreview {
    MediaSelectorItem(
        remember {
            MediaGroupBuilder("Test").apply {
                add(previewMediaList[0].let { MaybeExcludedMedia.Included(it, TestMatchMetadata) })
            }.build()
        },
        remember { MediaGroupState("test") },
        rememberTestMediaSourceInfoProvider(),
        selected = false,
        onSelect = {},
        preferredResolution = { null },
        onPreferResolution = {},
        preferredSubtitleLanguageId = { null },
        onPreferSubtitleLanguageId = {},
        modifier = modifier,
    )
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewMediaItemExcluded(modifier: Modifier = Modifier) = ProvideCompositionLocalsForPreview {
    MediaSelectorItem(
        remember {
            MediaGroupBuilder("Test").apply {
                add(previewMediaList[0].let { MaybeExcludedMedia.Excluded(it, MediaExclusionReason.FromSequelSeason) })
            }.build()
        },
        remember { MediaGroupState("test") },
        rememberTestMediaSourceInfoProvider(),
        selected = false,
        onSelect = {},
        preferredResolution = { null },
        onPreferResolution = {},
        preferredSubtitleLanguageId = { null },
        onPreferSubtitleLanguageId = {},
        modifier = modifier,
    )
}
