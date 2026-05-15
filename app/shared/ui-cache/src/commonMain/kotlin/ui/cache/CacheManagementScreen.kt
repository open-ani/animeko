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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import me.him188.ani.app.ui.cache.components.CacheSelectionState
import me.him188.ani.app.ui.cache.components.DownloadStateIcon
import me.him188.ani.app.ui.cache.components.TestCacheGroupSates
import me.him188.ani.app.ui.cache.components.createTestMediaStats
import me.him188.ani.app.ui.cache.components.rememberCacheFilterAndSortState
import me.him188.ani.app.ui.cache.components.rememberCacheSelectionState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.rememberCurrentTopAppBarContainerColor
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_episode_pause_download
import me.him188.ani.app.ui.lang.cache_episode_resume_download
import me.him188.ani.app.ui.lang.cache_management_delete_cache_confirmation
import me.him188.ani.app.ui.lang.cache_management_delete_cache_title
import me.him188.ani.app.ui.lang.cache_management_delete_selected
import me.him188.ani.app.ui.lang.cache_management_downloading_count
import me.him188.ani.app.ui.lang.cache_management_enter_selection_mode
import me.him188.ani.app.ui.lang.cache_management_episode_label
import me.him188.ani.app.ui.lang.cache_management_exit_selection
import me.him188.ani.app.ui.lang.cache_management_finished_count
import me.him188.ani.app.ui.lang.cache_management_invalid_cache_info
import me.him188.ani.app.ui.lang.cache_management_more_actions
import me.him188.ani.app.ui.lang.cache_management_more_info
import me.him188.ani.app.ui.lang.cache_management_play
import me.him188.ani.app.ui.lang.cache_management_select_all
import me.him188.ani.app.ui.lang.cache_management_select_item_for_details
import me.him188.ani.app.ui.lang.cache_management_selected_count
import me.him188.ani.app.ui.lang.cache_management_streaming_not_supported
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.cache_subject_delete
import me.him188.ani.app.ui.lang.cache_unknown
import me.him188.ani.app.ui.lang.main_screen_page_cache_management
import me.him188.ani.app.ui.settings.rendering.P2p
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

/**
 * 全局缓存管理页面状态
 */
@Immutable
data class CacheManagementState(
    val overallStats: MediaStats,
    val groups: List<CacheGroupState>,
) {
    internal val entries = groups.flatMap { it.entries }

    companion object {
        val Placeholder = CacheManagementState(
            MediaStats.Unspecified,
            emptyList(),
        )
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
    onNavigateCacheDetail: (cacheId: String) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val state by vm.stateFlow.collectAsStateWithLifecycle()
    CacheManagementScreen(
        state,
        selfInfo,
        onPlay,
        onResume = { vm.resumeCache(it) },
        onPause = { vm.pauseCache(it) },
        onDelete = { vm.deleteCache(it) },
        onViewDetail = { onNavigateCacheDetail(it.cacheId) },
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
    onViewDetail: (CacheEpisodeState) -> Unit,
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
    LaunchedEffect(state.entries, selectionState.inSelection) {
        if (selectionState.inSelection) {
            val validIds = state.entries.map { it.cacheId }.toSet()
            selectionState.overrideSelected(selectionState.selectedIds.filter { id -> id in validIds }.toSet())
        }
    }

    // 选择模式下导航返回应该退出选择模式
    BackHandler(selectionState.inSelection) { selectionState.clear() }

    // 当前正在浏览的 cache group
    var currentViewingGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.groups) {
        if (state.groups.isEmpty()) {
            currentViewingGroupKey = null
        } else if (state.groups.none { it.key == currentViewingGroupKey }) {
            currentViewingGroupKey = state.groups.first().key
        }
    }
    val currentViewingGroup = remember(state.groups, currentViewingGroupKey) {
        state.groups.firstOrNull { it.key == currentViewingGroupKey }
    }

    // 确认删除的对话框
    if (deleteSelectedCacheDialog) {
        DeleteActionDialog(
            onDismiss = { deleteSelectedCacheDialog = false },
            onConfirm = {
                selectionEntries.filter { it.cacheId in selectionState.selectedIds }
                    .forEach { onDelete(it) }
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
        groupedEntries = state.groups,
        topBar = {
            CacheManagementTopBar(
                selectionMode = selectionState.inSelection,
                selectionCount = selectionCount,
                allSelected = allSelected,
                hasEntries = selectionEntries.isNotEmpty(),
                onEnterSelection = { selectionState.enterSelectionWith(emptySet()) },
                onExitSelection = { selectionState.clear() },
                onToggleSelectAll = {
                    selectionState.enterSelectionWith(
                        if (allSelected) emptySet() else selectionEntries.map { it.cacheId }.toSet(),
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
        onToggleSelected = { entry -> selectionState.toggleSelection(entry.cacheId) },
        onEnterSelection = { entry ->
            selectionState.enterSelectionWith(selectionState.selectedIds + entry.cacheId)
        },
        onToggleGroupSelection = { group ->
            selectionState.toggleSelection(*group.entries.map { it.cacheId }.toTypedArray())
        },
        onEnterGroupSelection = { group ->
            selectionState.enterSelectionWith(selectionState.selectedIds + group.entries.map { it.cacheId })
        },
        onPlay = onPlay,
        onResume = onResume,
        onPause = onPause,
        onDelete = onDelete,
        onViewDetail = onViewDetail,
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
    cacheEntries: List<CacheEpisodeState>,
    filteredEntries: List<CacheEpisodeState>,
    groupedEntries: List<CacheGroupState>,
    selectedGroup: CacheGroupState?,
    onSelectGroup: (CacheGroupState?) -> Unit,
    onToggleSelected: (CacheEpisodeState) -> Unit,
    onEnterSelection: (CacheEpisodeState) -> Unit,
    onToggleGroupSelection: (CacheGroupState) -> Unit,
    onEnterGroupSelection: (CacheGroupState) -> Unit,
    onPlay: (CacheEpisodeState) -> Unit,
    onResume: (CacheEpisodeState) -> Unit,
    onPause: (CacheEpisodeState) -> Unit,
    onDelete: (CacheEpisodeState) -> Unit,
    onViewDetail: (CacheEpisodeState) -> Unit,
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
                        items(filteredEntries, key = { it.listItemKey }) { entry ->
                            CacheListItem(
                                entry = entry,
                                selectionMode = selectionState.inSelection,
                                selected = entry.cacheId in selectionState.selectedIds,
                                onToggleSelected = { onToggleSelected(entry) },
                                onEnterSelection = { onEnterSelection(entry) },
                                onPlay = { onPlay(entry) },
                                onResume = { onResume(entry) },
                                onPause = { onPause(entry) },
                                onViewDetail = { onViewDetail(entry) },
                                onDelete = { onDelete(entry) },
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
                            .paneWindowInsetsPadding()
                            .fillMaxHeight(),
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
                                    Text(stringResource(Lang.cache_management_select_item_for_details))
                                }
                            }
                        } else {
                            items(entries, key = { it.listItemKey }) { entry ->
                                CacheListItem(
                                    entry = entry,
                                    selectionMode = selectionState.inSelection,
                                    selected = entry.cacheId in selectionState.selectedIds,
                                    onToggleSelected = { onToggleSelected(entry) },
                                    onEnterSelection = { onEnterSelection(entry) },
                                    onPlay = { onPlay(entry) },
                                    onResume = { onResume(entry) },
                                    onPause = { onPause(entry) },
                                    onViewDetail = { onViewDetail(entry) },
                                    onDelete = { onDelete(entry) },
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
    onEnterSelection: () -> Unit,
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
        val selectedCountText = stringResource(Lang.cache_management_selected_count, selectionCount)
        val exitSelectionText = stringResource(Lang.cache_management_exit_selection)
        val selectAllText = stringResource(Lang.cache_management_select_all)
        val deleteSelectedText = stringResource(Lang.cache_management_delete_selected)
        AniTopAppBar(
            title = { AniTopAppBarDefaults.Title(selectedCountText) },
            navigationIcon = {
                IconButton(onClick = onExitSelection) { Icon(Icons.Rounded.Close, exitSelectionText) }
            },
            actions = {
                IconButton(
                    onClick = onToggleSelectAll,
                    enabled = hasEntries,
                ) {
                    Icon(
                        if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        selectAllText,
                    )
                }
            },
            avatar = {
                IconButton(
                    onClick = onDeleteSelected,
                    enabled = selectionCount > 0,
                ) {
                    Icon(Icons.Rounded.Delete, deleteSelectedText, tint = MaterialTheme.colorScheme.error)
                }
            },
            colors = appBarColors,
            windowInsets = windowInsets,
            scrollBehavior = scrollBehavior,
        )
    } else {
        AniTopAppBar(
            title = { AniTopAppBarDefaults.Title(stringResource(Lang.main_screen_page_cache_management)) },
            navigationIcon = navigationIcon,
            actions = {
                val enterSelectionModeText = stringResource(Lang.cache_management_enter_selection_mode)
                IconButton(
                    onClick = onEnterSelection,
                    enabled = hasEntries,
                ) {
                    Icon(Icons.Default.Checklist, enterSelectionModeText)
                }
            },
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
internal fun DeleteActionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Lang.cache_management_delete_cache_title)) },
        text = { Text(stringResource(Lang.cache_management_delete_cache_confirmation)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) { Text(stringResource(Lang.cache_subject_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onDismiss) { Text(stringResource(Lang.cache_subject_cancel)) }
        },
    )
}

@Composable
private fun CacheSubjectListItem(
    group: CacheGroupState,
    selected: Boolean,
    selectionMode: Boolean,
    selectedCacheIds: Set<String>,
    onToggleGroupSelection: (CacheGroupState) -> Unit,
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
                val finishedCountText = stringResource(
                    Lang.cache_management_finished_count,
                    group.finishedCount,
                    group.entries.size,
                )
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
                        finishedCountText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (group.downloadingCount > 0) {
                        val downloadingCountText = stringResource(
                            Lang.cache_management_downloading_count,
                            group.downloadingCount,
                        )
                        Text(
                            downloadingCountText,
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
                val allGroupSelected = group.entries.all { it.cacheId in selectedCacheIds }
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
    entry: CacheEpisodeState,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onViewDetail: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    transparentBackgroundIfUnselected: Boolean = false,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }

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
                            val icon = renderEngineIcon(key)
                            val desc = when (key) {
                                MediaCacheEngineKey.Anitorrent -> "BT"
                                MediaCacheEngineKey.WebM3u -> "Web"
                                else -> stringResource(Lang.cache_unknown)
                            }
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
                        stringResource(Lang.cache_management_episode_label, entry.sort, entry.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DownloadStateIcon(entry.state)
                    if (selectionMode) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelected() },
                        )
                    } else {
                        val moreActionsText = stringResource(Lang.cache_management_more_actions)
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, moreActionsText)
                        }
                    }

                    CacheActionDropdown(
                        show = showMenu,
                        onDismiss = { showMenu = false },
                        episode = entry,
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
                        onViewDetail = {
                            onViewDetail()
                            showMenu = false
                        },
                        onDelete = {
                            showConfirm = true
                        },
                    )
                }
            }

            AniAnimatedVisibility(
                !entry.isFinished,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progress by animateFloatAsState(entry.progress.getOrZero())
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f),
                        strokeCap = StrokeCap.Round,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        entry.speedText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        entry.progressText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

private fun renderEngineIcon(key: MediaCacheEngineKey) = when (key) {
    MediaCacheEngineKey.Anitorrent -> Icons.Filled.P2p
    MediaCacheEngineKey.WebM3u -> Icons.Filled.Language
    else -> Icons.AutoMirrored.Rounded.HelpOutline
}

@Composable
internal fun CacheActionDropdown(
    show: Boolean,
    onDismiss: () -> Unit,
    episode: CacheEpisodeState,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onViewDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
) {
    val toaster = LocalToaster.current
    val resumeDownloadText = stringResource(Lang.cache_episode_resume_download)
    val pauseDownloadText = stringResource(Lang.cache_episode_pause_download)
    val playText = stringResource(Lang.cache_management_play)
    val invalidCacheInfoText = stringResource(Lang.cache_management_invalid_cache_info)
    val streamingNotSupportedText = stringResource(Lang.cache_management_streaming_not_supported)
    val moreInfoText = stringResource(Lang.cache_management_more_info)
    DropdownMenu(
        expanded = show,
        onDismissRequest = onDismiss,
        modifier = modifier,
        offset = offset,
    ) {
        if (!episode.isFinished) {
            if (episode.isPaused) {
                DropdownMenuItem(
                    text = { Text(resumeDownloadText) },
                    leadingIcon = { Icon(Icons.Rounded.Restore, null) },
                    onClick = {
                        onResume()
                        onDismiss()
                    },
                )
            } else if (!episode.isFailed) {
                DropdownMenuItem(
                    text = { Text(pauseDownloadText) },
                    leadingIcon = { Icon(Icons.Rounded.Pause, null) },
                    onClick = {
                        onPause()
                        onDismiss()
                    },
                )
            }
        }
        if (!episode.isFailed) {
            DropdownMenuItem(
                text = { Text(playText) },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                onClick = {
                    when (episode.playability) {
                        CacheEpisodeState.Playability.PLAYABLE -> {
                            onPlay()
                            onDismiss()
                        }

                        CacheEpisodeState.Playability.INVALID_SUBJECT_EPISODE_ID -> {
                            toaster.toast(invalidCacheInfoText)
                        }

                        CacheEpisodeState.Playability.STREAMING_NOT_SUPPORTED -> {
                            toaster.toast(streamingNotSupportedText)
                        }
                    }
                },
            )
        }
        onViewDetail?.let {
            DropdownMenuItem(
                text = { Text(moreInfoText) },
                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                onClick = {
                    it()
                    onDismiss()
                },
            )
        }

        DropdownMenuItem(
            text = { Text(stringResource(Lang.cache_subject_delete), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
                onDelete()
                onDismiss()
            },
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
            onViewDetail = { },
            navigationIcon = { BackNavigationIconButton({ }) },
        )
    }
}
