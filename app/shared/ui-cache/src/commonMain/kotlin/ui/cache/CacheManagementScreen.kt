/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.tools.getOrZero
import me.him188.ani.app.ui.adaptive.AniListDetailPaneScaffold
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.ListDetailLayoutParameters
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheFilterAndSortBar
import me.him188.ani.app.ui.cache.components.CacheFilterAndSortState
import me.him188.ani.app.ui.cache.components.CacheGroupState
import me.him188.ani.app.ui.cache.components.CacheManagementOverallStats
import me.him188.ani.app.ui.cache.components.CacheStatusFilter
import me.him188.ani.app.ui.cache.components.TestCacheGroupSates
import me.him188.ani.app.ui.cache.components.createTestMediaStats
import me.him188.ani.app.ui.cache.components.rememberCacheFilterAndSortState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.rememberCurrentTopAppBarContainerColor
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.settings.rendering.P2p
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * 全局缓存管理页面状态
 */
@Immutable
data class CacheManagementState(
    val overallStats: MediaStats,
    val groups: List<CacheGroupState>,
) {
    internal val entries = groups.flatMap { group ->
        val subjectName = group.commonInfo?.subjectDisplayName ?: "未知条目"
        group.episodes.map { episode ->
            CacheListEntry(
                subjectName = subjectName,
                groupId = group.id,
                engineKey = group.engineKey,
                collectionType = group.collectionType,
                episode = episode,
            )
        }
    }

    internal val entriesGroupedBySubject = entries
        .groupBy { it.episode.subjectId }
        .map { (subjectId, entries) ->
            CacheSubjectGroup(
                key = "subject_$subjectId",
                subjectId = subjectId,
                subjectName = entries.first().subjectName,
                entries = entries.sortedBy { it.episode.sort },
            )
        }
        .sortedWith(
            compareByDescending<CacheSubjectGroup> { it.entries.any { entry -> !entry.episode.isFinished } }
                .thenByDescending { it.entries.maxOfOrNull { entry -> entry.episode.creationTime ?: 0 } },
        )

    companion object {
        val Placeholder = CacheManagementState(
            MediaStats.Unspecified,
            emptyList(),
        )
    }
}

@Immutable
internal data class CacheSubjectGroup(
    val key: String,
    val subjectId: Int?,
    val subjectName: String,
    val entries: List<CacheListEntry>,
) {
    val finishedCount: Int = entries.count { it.status == CacheStatusFilter.Finished }
    val downloadingCount: Int = entries.size - finishedCount
    val averageProgress: Float =
        entries.map { it.episode.progress.getOrZero() }.ifEmpty { listOf(0f) }.average().toFloat()
}


@Immutable
internal data class CacheListEntry(
    val subjectName: String,
    val groupId: String,
    val engineKey: MediaCacheEngineKey?,
    val collectionType: UnifiedCollectionType?,
    val episode: CacheEpisodeState,
) {
    val status: CacheStatusFilter
        get() = if (episode.isFinished) CacheStatusFilter.Finished else CacheStatusFilter.Downloading
}

@Stable
private class CacheSelectionState(
    initialInSelection: Boolean,
    initialSelectedIds: Set<String>,
) {
    var inSelection by mutableStateOf(initialInSelection)
    var selectedIds by mutableStateOf(initialSelectedIds)

    fun overrideSelected(list: Set<String>) {
        selectedIds = list
    }

    fun toggleSelection(vararg ids: String) {
        val allSelected = ids.all { it in selectedIds }
        val nextIds = selectedIds.toMutableSet().apply {
            if (allSelected) removeAll(ids) else addAll(ids)
        }
        selectedIds = nextIds.toSet()
    }

    fun enterSelectionWith(list: Set<String>) {
        inSelection = true
        selectedIds = list
    }

    fun clear() {
        inSelection = false
        selectedIds = emptySet()
    }

    companion object {
        val Saver: Saver<CacheSelectionState, List<String>> = Saver(
            save = {
                buildList {
                    add(it.inSelection.toString())
                    addAll(it.selectedIds)
                }
            },
            restore = {
                val inSelection = it.getOrNull(0)?.toBoolean() ?: false
                val selectedIds = it.drop(1).toSet()
                CacheSelectionState(inSelection, selectedIds)
            },
        )
    }
}

@Composable
private fun rememberCacheSelectionState(
    initialInSelection: Boolean = false,
    initialSelectedIds: Set<String> = emptySet(),
): CacheSelectionState {
    return rememberSaveable(saver = CacheSelectionState.Saver) {
        CacheSelectionState(initialInSelection, initialSelectedIds)
    }
}

/**
 * 全局缓存管理页面
 */
@Composable
fun CacheManagementScreen(
    vm: CacheManagementViewModel,
    selfInfo: SelfInfoUiState?,
    onPlay: (CacheEpisodeState) -> Unit,
    onClickLogin: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val state by vm.stateFlow.collectAsStateWithLifecycle()
    CacheManagementScreen(
        state,
        selfInfo,
        onPlay,
        onResume = {
            vm.resumeCache(it)
        },
        onPause = {
            vm.pauseCache(it)
        },
        onDelete = {
            vm.deleteCache(it)
        },
        onClickLogin = onClickLogin,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}


@Composable
fun CacheManagementScreen(
    state: CacheManagementState,
    selfInfo: SelfInfoUiState?,
    onPlay: (CacheEpisodeState) -> Unit,
    onResume: (CacheEpisodeState) -> Unit,
    onPause: (CacheEpisodeState) -> Unit,
    onDelete: (CacheEpisodeState) -> Unit,
    onClickLogin: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val appBarColors = AniThemeDefaults.topAppBarColors()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val listState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val cacheFilterState = rememberCacheFilterAndSortState()
    val selectionState = rememberCacheSelectionState()

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val listDetailLayoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective)

    // 已过滤的 entries
    val selectionEntries = if (listDetailLayoutParameters.preferSinglePane)
        cacheFilterState.applyFilterAndSort(state.entries) else state.entries

    // region selection
    var deleteSelectedCacheDialog by rememberSaveable { mutableStateOf(false) }

    // 当前选中的 entries 数量
    val selectionCount = selectionState.selectedIds.size
    // 是否已经全选
    val allSelected = remember(selectionEntries, selectionCount) {
        selectionEntries.isNotEmpty() && selectionCount == selectionEntries.size
    }

    // 当 list detail pane 的类型改变并且在编辑模式时, 需要确保 selectedIds 只能是当前可见的 entries
    LaunchedEffect(selectionEntries, selectionState.inSelection) {
        if (selectionState.inSelection) {
            selectionState.selectedIds = selectionState.selectedIds.filter { id ->
                selectionEntries.any { it.episode.cacheId == id }
            }.toSet()
        }
    }

    // 当前正在浏览的 cache group
    var currentViewingGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.entriesGroupedBySubject) {
        if (state.entriesGroupedBySubject.isEmpty()) {
            currentViewingGroupKey = null
        } else if (state.entriesGroupedBySubject.none { it.key == currentViewingGroupKey }) {
            currentViewingGroupKey = state.entriesGroupedBySubject.first().key
        }
    }
    val currentViewingGroup = remember(state.entriesGroupedBySubject, currentViewingGroupKey) {
        state.entriesGroupedBySubject.firstOrNull { it.key == currentViewingGroupKey }
    }

    // 确认删除的对话框
    if (deleteSelectedCacheDialog) {
        DeleteActionDialog(
            onDismiss = { deleteSelectedCacheDialog = false },
            onConfirm = {
                selectionEntries.filter { it.episode.cacheId in selectionState.selectedIds }
                    .forEach { onDelete(it.episode) }
                selectionState.clear()
                deleteSelectedCacheDialog = false
            },
        )
    }

    CacheManagementLayout(
        state = state,
        cacheFilterState = cacheFilterState,
        selectionState = selectionState,
        navigator = navigator,
        cacheEntries = state.entries,
        filteredEntries = selectionEntries,
        groupedEntries = state.entriesGroupedBySubject,
        topBar = {
            CacheManagementTopBar(
                selectionMode = selectionState.inSelection,
                selectionCount = selectionCount,
                allSelected = allSelected,
                hasEntries = selectionEntries.isNotEmpty(),
                onExitSelection = { selectionState.clear() },
                onToggleSelectAll = {
                    selectionState.enterSelectionWith(
                        if (allSelected) emptySet() else selectionEntries.map { it.episode.cacheId }.toSet(),
                    )
                },
                onDeleteSelected = { deleteSelectedCacheDialog = true },
                selfInfo = selfInfo,
                onClickLogin = onClickLogin,
                navigationIcon = navigationIcon,
                appBarColors = appBarColors,
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
                scrollBehavior = scrollBehavior,
            )
        },
        selectedGroup = currentViewingGroup,
        appBarColors = appBarColors,
        scrollBehavior = scrollBehavior,
        listState = listState,
        detailListState = detailListState,
        onSelectGroup = { currentViewingGroupKey = it?.key },
        onToggleSelected = { entry -> selectionState.toggleSelection(entry.episode.cacheId) },
        onEnterSelection = { entry ->
            selectionState.enterSelectionWith(selectionState.selectedIds + entry.episode.cacheId)
        },
        onToggleGroupSelection = { group ->
            selectionState.toggleSelection(*group.entries.map { it.episode.cacheId }.toTypedArray())
        },
        onEnterGroupSelection = { group ->
            selectionState.enterSelectionWith(selectionState.selectedIds + group.entries.map { it.episode.cacheId })
        },
        onPlay = onPlay,
        onResume = onResume,
        onPause = onPause,
        onDelete = onDelete,
        windowInsets = windowInsets,
        listDetailLayoutParameters = listDetailLayoutParameters,
        modifier = modifier,
    )
}


@Composable
private fun CacheManagementLayout(
    state: CacheManagementState,
    cacheFilterState: CacheFilterAndSortState,
    selectionState: CacheSelectionState,
    navigator: ThreePaneScaffoldNavigator<String>,
    cacheEntries: List<CacheListEntry>,
    filteredEntries: List<CacheListEntry>,
    groupedEntries: List<CacheSubjectGroup>,
    selectedGroup: CacheSubjectGroup?,
    onSelectGroup: (CacheSubjectGroup?) -> Unit,
    onToggleSelected: (CacheListEntry) -> Unit,
    onEnterSelection: (CacheListEntry) -> Unit,
    onToggleGroupSelection: (CacheSubjectGroup) -> Unit,
    onEnterGroupSelection: (CacheSubjectGroup) -> Unit,
    onPlay: (CacheEpisodeState) -> Unit,
    onResume: (CacheEpisodeState) -> Unit,
    onPause: (CacheEpisodeState) -> Unit,
    onDelete: (CacheEpisodeState) -> Unit,
    appBarColors: TopAppBarColors,
    topBar: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    listState: LazyListState,
    detailListState: LazyListState,
    windowInsets: WindowInsets,
    listDetailLayoutParameters: ListDetailLayoutParameters,
    modifier: Modifier = Modifier
) {
    val tasker = rememberAsyncHandler()

    Scaffold(
        modifier = modifier,
        topBar = topBar,
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        AniListDetailPaneScaffold(
            modifier = Modifier.padding(paddingValues),
            navigator = navigator,
            listPaneTopAppBar = null,
            listPaneContent = {
                val listSpacedBy = if (isSinglePane) 0.dp else 24.dp
                if (isSinglePane) {
                    val topAppBarContainerColor by rememberCurrentTopAppBarContainerColor(appBarColors, scrollBehavior)
                    LazyColumn(
                        modifier = Modifier
                            .paneWindowInsetsPadding()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .fillMaxWidth()
                            .wrapContentWidth()
                            .widthIn(max = 1300.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item("overall_stats") {
                            Surface(
                                color = appBarColors.containerColor,
                                contentColor = contentColorFor(appBarColors.containerColor),
                            ) {
                                CacheManagementOverallStats(
                                    { state.overallStats },
                                    Modifier
                                        .paneContentPadding()
                                        .padding(horizontal = listSpacedBy)
                                        .fillMaxWidth(),
                                )
                            }
                        }
                        stickyHeader("filter_row") {
                            CacheFilterAndSortBar(
                                state = cacheFilterState,
                                modifier = Modifier.paneContentPadding().fillMaxWidth(),
                                containerColor = topAppBarContainerColor,
                                mediaCacheEngineOptions = remember(cacheEntries) {
                                    cacheEntries.mapNotNull { it.engineKey }.distinct()
                                },
                            )
                        }
                        items(filteredEntries, key = { it.episode.cacheId }) { entry ->
                            CacheListItem(
                                entry = entry,
                                selectionMode = selectionState.inSelection,
                                selected = entry.episode.cacheId in selectionState.selectedIds,
                                onToggleSelected = { onToggleSelected(entry) },
                                onEnterSelection = { onEnterSelection(entry) },
                                onPlay = { onPlay(entry.episode) },
                                onResume = { onResume(entry.episode) },
                                onPause = { onPause(entry.episode) },
                                onDelete = { onDelete(entry.episode) },
                                modifier = Modifier.paneContentPadding().padding(horizontal = listSpacedBy),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .paneContentPadding()
                            .paneWindowInsetsPadding()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item("overall_stats") {
                            Surface(
                                color = appBarColors.containerColor,
                                contentColor = contentColorFor(appBarColors.containerColor),
                            ) {
                                CacheManagementOverallStats(
                                    { state.overallStats },
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(groupedEntries, key = { it.key }) { group ->
                            CacheSubjectListItem(
                                group = group,
                                selected = group.key == selectedGroup?.key,
                                selectionMode = selectionState.inSelection,
                                selectedCacheIds = selectionState.selectedIds,
                                onToggleGroupSelection = onToggleGroupSelection,
                                onLongClick = { onEnterGroupSelection(group) },
                                onClick = {
                                    onSelectGroup(group)
                                    tasker.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            detailPane = {
                if (isSinglePane) {
                    Box(
                        Modifier
                            .paneContentPadding()
                            .paneWindowInsetsPadding(),
                    )
                } else {
                    val itemContentPadding = 16.dp
                    LazyColumn(
                        modifier = Modifier
                            .paneContentPadding(extraStart = -itemContentPadding, extraEnd = -itemContentPadding)
                            .paneWindowInsetsPadding(),
                        state = detailListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding),
                    ) {
                        val entries = selectedGroup?.entries.orEmpty()
                        if (entries.isEmpty()) {
                            item("empty_detail") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("选择一个条目查看具体缓存")
                                }
                            }
                        } else {
                            items(entries, key = { it.episode.cacheId }) { entry ->
                                CacheListItem(
                                    entry = entry,
                                    selectionMode = selectionState.inSelection,
                                    selected = entry.episode.cacheId in selectionState.selectedIds,
                                    onToggleSelected = { onToggleSelected(entry) },
                                    onEnterSelection = { onEnterSelection(entry) },
                                    onPlay = { onPlay(entry.episode) },
                                    onResume = { onResume(entry.episode) },
                                    onPause = { onPause(entry.episode) },
                                    onDelete = { onDelete(entry.episode) },
                                    contentPadding = PaddingValues(itemContentPadding),
                                    transparentBackgroundIfUnselected = true,
                                )
                            }
                        }
                    }
                }
            },
            contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            useSharedTransition = false,
        )
    }
}

@Composable
private fun CacheManagementTopBar(
    selectionMode: Boolean,
    selectionCount: Int,
    allSelected: Boolean,
    hasEntries: Boolean,
    onExitSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    selfInfo: SelfInfoUiState?,
    onClickLogin: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    appBarColors: TopAppBarColors,
    windowInsets: WindowInsets,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    if (selectionMode) {
        AniTopAppBar(
            title = { AniTopAppBarDefaults.Title("$selectionCount 个已选") },
            navigationIcon = {
                IconButton(onClick = onExitSelection) { Icon(Icons.Rounded.Close, "退出选择") }
            },
            actions = {
                IconButton(
                    onClick = onToggleSelectAll,
                    enabled = hasEntries,
                ) {
                    Icon(
                        if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        "选择所有",
                    )
                }
            },
            avatar = {
                IconButton(
                    onClick = onDeleteSelected,
                    enabled = selectionCount > 0,
                ) {
                    Icon(Icons.Rounded.Delete, "删除所选", tint = MaterialTheme.colorScheme.error)
                }
            },
            colors = appBarColors,
            windowInsets = windowInsets,
            scrollBehavior = scrollBehavior,
        )
    } else {
        AniTopAppBar(
            title = { AniTopAppBarDefaults.Title("缓存管理") },
            navigationIcon = navigationIcon,
            avatar = selfInfo?.let {
                { recommendedSize ->
                    SelfAvatar(
                        state = it,
                        onClick = onClickLogin,
                        size = recommendedSize,
                    )
                }
            } ?: { },
            colors = appBarColors,
            windowInsets = windowInsets,
            scrollBehavior = scrollBehavior,
        )
    }
}


@Composable
private fun DeleteActionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("删除缓存") },
        text = { Text("删除后不可恢复，确认删除吗？") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CacheSubjectListItem(
    group: CacheSubjectGroup,
    selected: Boolean,
    selectionMode: Boolean,
    selectedCacheIds: Set<String>,
    onToggleGroupSelection: (CacheSubjectGroup) -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 6.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {

            Column(
                Modifier.weight(1f).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    group.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${group.finishedCount}/${group.entries.size} 已完成",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (group.downloadingCount > 0) {
                        Text(
                            "${group.downloadingCount} 个下载中",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row {
                    LinearProgressIndicator(
                        progress = { group.averageProgress.coerceIn(0f, 1f) },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (selectionMode) {
                val allGroupSelected = group.entries.all { it.episode.cacheId in selectedCacheIds }
                Checkbox(
                    checked = allGroupSelected,
                    onCheckedChange = { onToggleGroupSelection(group) },
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun CacheListItem(
    entry: CacheListEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    transparentBackgroundIfUnselected: Boolean = false,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    val statusIcon =
        if (entry.status == CacheStatusFilter.Finished) Icons.Rounded.DownloadDone else Icons.Rounded.Downloading

    if (showConfirm) {
        DeleteActionDialog(
            onDismiss = { showConfirm = false },
            onConfirm = {
                onDelete()
                showConfirm = false
            },
        )
    }

    Surface(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelected()
                    } else {
                        showMenu = true
                    }
                },
                onLongClick = {
                    onEnterSelection()
                },
            ),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 6.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else
            (if (transparentBackgroundIfUnselected) Color.Transparent else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        entry.engineKey?.let { key ->
                            val (icon, desc) = renderEngineIcon(key)
                            Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text(
                            entry.subjectName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "第${entry.episode.sort}话 · ${entry.episode.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(statusIcon, null)
                    if (selectionMode) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelected() },
                        )
                    } else {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, "更多操作")
                        }
                    }

                    CacheActionDropdown(
                        show = showMenu,
                        onDismiss = { showMenu = false },
                        episode = entry.episode,
                        onPlay = {
                            onPlay()
                            showMenu = false
                        },
                        onResume = {
                            onResume()
                            showMenu = false
                        },
                        onPause = {
                            onPause()
                            showMenu = false
                        },
                        onDelete = {
                            showConfirm = true
                        },
                    )
                }
            }

            AniAnimatedVisibility(
                !entry.episode.isFinished,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progress by animateFloatAsState(entry.episode.progress.getOrZero())
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f),
                        strokeCap = StrokeCap.Round,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        entry.episode.speedText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        entry.episode.progressText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

private fun renderEngineIcon(key: MediaCacheEngineKey) = when (key) {
    MediaCacheEngineKey.Anitorrent -> Icons.Filled.P2p to "BT"
    MediaCacheEngineKey.WebM3u -> Icons.Filled.Language to "Web"
    else -> Icons.AutoMirrored.Rounded.HelpOutline to "未知"
}

@Composable
private fun CacheActionDropdown(
    show: Boolean,
    onDismiss: () -> Unit,
    episode: CacheEpisodeState,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val toaster = LocalToaster.current
    DropdownMenu(
        expanded = show,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = (-20).dp, y = 0.dp),
    ) {
        if (!episode.isFinished) {
            if (episode.isPaused) {
                DropdownMenuItem(
                    text = { Text("继续下载") },
                    leadingIcon = { Icon(Icons.Rounded.Restore, null) },
                    onClick = onResume,
                )
            } else {
                DropdownMenuItem(
                    text = { Text("暂停下载") },
                    leadingIcon = { Icon(Icons.Rounded.Pause, null) },
                    onClick = onPause,
                )
            }
        }
        DropdownMenuItem(
            text = { Text("播放") },
            leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
            onClick = {
                when (episode.playability) {
                    CacheEpisodeState.Playability.PLAYABLE -> onPlay()
                    CacheEpisodeState.Playability.INVALID_SUBJECT_EPISODE_ID -> toaster.toast("缓存信息无效，无法播放")
                    CacheEpisodeState.Playability.STREAMING_NOT_SUPPORTED -> toaster.toast("此资源不支持边下边播，请等待下载完成")
                }
            },
        )
        DropdownMenuItem(
            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = onDelete,
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewCacheManagementScreen() {
    ProvideCompositionLocalsForPreview {
        CacheManagementScreen(
            state = remember {
                CacheManagementState(
                    createTestMediaStats(),
                    TestCacheGroupSates,
                )
            },
            selfInfo = null,
            onPlay = { },
            onResume = {},
            onPause = {},
            onDelete = {},
            onClickLogin = { },
            navigationIcon = { BackNavigationIconButton({ }) },
        )
    }
}
