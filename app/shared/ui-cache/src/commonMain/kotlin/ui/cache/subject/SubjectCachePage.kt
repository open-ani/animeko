/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.subject

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.cache.DeleteActionDialog
import me.him188.ani.app.ui.cache.components.CacheEpisodeRow
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheSelectionFloatingToolbar
import me.him188.ani.app.ui.cache.components.CacheSelectionState
import me.him188.ani.app.ui.cache.components.rememberCacheSelectionState
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.appChromeHazeSource
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_filter_collection_done
import me.him188.ani.app.ui.lang.cache_filter_collection_dropped
import me.him188.ani.app.ui.lang.cache_management_deselect_all
import me.him188.ani.app.ui.lang.cache_management_downloading_count
import me.him188.ani.app.ui.lang.cache_management_episode_label
import me.him188.ani.app.ui.lang.cache_management_exit_selection
import me.him188.ani.app.ui.lang.cache_management_finished_count
import me.him188.ani.app.ui.lang.cache_management_select_all_action
import me.him188.ani.app.ui.lang.cache_management_selected_count
import me.him188.ani.app.ui.lang.cache_management_selection_downloading_count
import me.him188.ani.app.ui.lang.cache_management_selection_summary
import me.him188.ani.app.ui.lang.cache_subject_pause_all
import me.him188.ani.app.ui.lang.cache_subject_resume_all
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

object SubjectCachePageTestTags {
    const val SUMMARY_ROW = "subject_cache_summary_row"
    const val PAUSE_ALL = "subject_cache_pause_all"
    const val RESUME_ALL = "subject_cache_resume_all"
}

/**
 * 创建跟随组合生命周期的 [SubjectCacheViewModelImpl].
 *
 * [SubjectCacheViewModelImpl] 会在后台持续收集网络与缓存状态 flow.
 * 若使用宿主页面的 ViewModelStore 按 subjectId 分 key 存储, 浏览多个条目会累积无法释放的 VM.
 * 这里为每个 [subjectId] 创建独立的 [ViewModelStore], 并在切换条目或离开组合时清空,
 * 以取消其 backgroundScope.
 */
@Composable
fun rememberSubjectCacheViewModel(subjectId: Int): SubjectCacheViewModel {
    val owner = remember(subjectId) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose {
            owner.viewModelStore.clear()
        }
    }
    return viewModel<SubjectCacheViewModelImpl>(viewModelStoreOwner = owner) {
        SubjectCacheViewModelImpl(subjectId)
    }
}

/**
 * 条目缓存页 (统一入口).
 *
 * 展示该条目的全部剧集: 已缓存的剧集可播放/暂停/继续/删除, 未缓存的剧集可以追加缓存.
 * 长按已缓存的剧集进入多选模式, 底部浮动工具栏支持批量操作.
 *
 * 设计稿: [Figma](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=1657-414)
 */
@Composable
fun SubjectCacheScreen(
    vm: SubjectCacheViewModel,
    onPlay: (CacheEpisodeState) -> Unit,
    onNavigateCacheDetail: (cacheId: String) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    val cachedEpisodes by vm.cacheEpisodesFlow.collectAsStateWithLifecycle()
    SubjectCachePage(
        title = vm.subjectTitle,
        cacheListState = vm.cacheListState,
        cachedEpisodes = cachedEpisodes,
        mediaSourceInfoProvider = vm.mediaSourceInfoProvider,
        mediaSelectorSettingsProvider = { vm.mediaSelectorSettingsFlow },
        onPlay = onPlay,
        onResume = { vm.resumeCache(it) },
        onPause = { vm.pauseCache(it) },
        onDelete = { vm.deleteCache(it) },
        onViewDetail = { onNavigateCacheDetail(it.cacheId) },
        onPauseAll = { vm.pauseAllCaches() },
        onResumeAll = { vm.resumeAllCaches() },
        modifier = modifier,
        windowInsets = windowInsets,
        navigationIcon = navigationIcon,
    )
}

/**
 * [SubjectCacheScreen] 的无状态版本, 供测试与预览使用.
 */
@Composable
fun SubjectCachePage(
    title: String?,
    cacheListState: EpisodeCacheListState,
    cachedEpisodes: List<CacheEpisodeState>,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    mediaSelectorSettingsProvider: () -> Flow<MediaSelectorSettings>,
    onPlay: (CacheEpisodeState) -> Unit,
    onResume: (CacheEpisodeState) -> Unit,
    onPause: (CacheEpisodeState) -> Unit,
    onDelete: (CacheEpisodeState) -> Unit,
    onViewDetail: ((CacheEpisodeState) -> Unit)?,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    val selectionState = rememberCacheSelectionState()

    // 缓存列表变化时, 剔除已不存在的选择项. 列表尚未加载时 (为空) 跳过, 避免清空刚恢复的选择状态.
    LaunchedEffect(cachedEpisodes, selectionState.inSelection) {
        if (selectionState.inSelection && cachedEpisodes.isNotEmpty()) {
            val validIds = cachedEpisodes.mapTo(hashSetOf()) { it.cacheId }
            selectionState.overrideSelected(selectionState.selectedIds.filter { it in validIds }.toSet())
        }
    }

    // 选择模式下导航返回应该退出选择模式
    BackHandler(selectionState.inSelection) { selectionState.clear() }

    val selectedEntries = remember(cachedEpisodes, selectionState.selectedIds) {
        cachedEpisodes.filter { it.cacheId in selectionState.selectedIds }
    }
    val allSelected = remember(cachedEpisodes, selectionState.selectedIds) {
        cachedEpisodes.isNotEmpty() && cachedEpisodes.all { it.cacheId in selectionState.selectedIds }
    }

    var showDeleteSelectedDialog by rememberSaveable { mutableStateOf(false) }
    if (showDeleteSelectedDialog) {
        DeleteActionDialog(
            onDismiss = { showDeleteSelectedDialog = false },
            onConfirm = {
                selectedEntries.forEach(onDelete)
                selectionState.clear()
                showDeleteSelectedDialog = false
            },
        )
    }

    // 用户是否关闭了 media selector. 这种情况下优先考虑是想置于后台或者点错了, 之后重新点击可以复用查询结果
    var hideMediaSelector by remember(cacheListState.currentSelectMediaTask) {
        mutableStateOf(false)
    }
    EpisodeCacheRequesterDialogs(
        cacheListState,
        mediaSourceInfoProvider,
        mediaSelectorSettingsProvider,
        hideMediaSelector = hideMediaSelector,
        onRequestHideMediaSelector = { hideMediaSelector = true },
    )

    val appBarColors = AniThemeDefaults.topAppBarColors()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val gridState = rememberLazyGridState()

    val uiScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        modifier,
        topBar = {
            if (selectionState.inSelection) {
                AniTopAppBar(
                    title = {
                        AniTopAppBarDefaults.Title(
                            stringResource(Lang.cache_management_selected_count, selectionState.selectedIds.size),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectionState.clear() }) {
                            Icon(Icons.Rounded.Close, stringResource(Lang.cache_management_exit_selection))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                selectionState.overrideSelected(
                                    if (allSelected) emptySet() else cachedEpisodes.map { it.cacheId }.toSet(),
                                )
                            },
                            enabled = cachedEpisodes.isNotEmpty(),
                        ) {
                            Text(
                                stringResource(
                                    if (allSelected) Lang.cache_management_deselect_all
                                    else Lang.cache_management_select_all_action,
                                ),
                            )
                        }
                    },
                    avatar = { },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                AniTopAppBar(
                    title = {
                        Text(
                            title.orEmpty(),
                            Modifier.placeholder(title == null),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = navigationIcon,
                    colors = appBarColors,
                    windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            AniAnimatedVisibility(selectionState.inSelection) {
                CacheSelectionFloatingToolbar(
                    resumeEnabled = selectedEntries.any { !it.isFinished && it.isPaused },
                    pauseEnabled = selectedEntries.any { !it.isFinished && !it.isPaused && !it.isFailed },
                    deleteEnabled = selectedEntries.isNotEmpty(),
                    onResumeSelected = {
                        selectedEntries.filter { !it.isFinished && it.isPaused }.forEach(onResume)
                    },
                    onPauseSelected = {
                        selectedEntries.filter { !it.isFinished && !it.isPaused && !it.isFailed }.forEach(onPause)
                    },
                    onDeleteSelected = { showDeleteSelectedDialog = true },
                    windowInsets = windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
            }
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            Modifier
                .appChromeHazeSource(backgroundColor = AniThemeDefaults.pageContentBackgroundColor)
                .padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                )
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = 1300.dp),
        ) {
            SubjectCacheSummaryRow(
                cachedEpisodes = cachedEpisodes,
                totalEpisodeCount = cacheListState.episodes.size.takeIf { it > 0 },
                inSelection = selectionState.inSelection,
                selectedEntries = selectedEntries,
                onPauseAll = onPauseAll,
                onResumeAll = onResumeAll,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 480.dp),
                modifier = Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .fillMaxWidth(),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(
                    bottom = paddingValues.calculateBottomPadding(),
                ),
            ) {
                subjectCacheEpisodeItems(
                    episodes = cacheListState.episodes,
                    cachedEpisodes = cachedEpisodes,
                    mediaSourceInfoProvider = mediaSourceInfoProvider,
                    selectionState = selectionState,
                    isRequestHidden = hideMediaSelector,
                    onClickNotCached = { episode ->
                        if (episode != cacheListState.currentSelectMediaTask?.episode) {
                            cacheListState.requestCache(episode, autoSelectCached = true)
                            uiScope.launch {
                                KoinPlatform.getKoin().get<PermissionManager>()
                                    .requestNotificationPermission(context)
                            }
                        }
                        hideMediaSelector = false
                    },
                    onCancelRequest = { it.actionTasker.cancel() },
                    onPlay = onPlay,
                    onResume = onResume,
                    onPause = onPause,
                    onDelete = onDelete,
                    onViewDetail = onViewDetail,
                )
            }
        }
    }
}

/**
 * 将一个条目的全部剧集 (已缓存 + 未缓存) 添加到 [LazyVerticalGrid] 中.
 *
 * 全局缓存管理页宽屏布局的详情栏与条目缓存页共用.
 */
fun LazyGridScope.subjectCacheEpisodeItems(
    episodes: List<EpisodeCacheState>,
    cachedEpisodes: List<CacheEpisodeState>,
    mediaSourceInfoProvider: MediaSourceInfoProvider?,
    selectionState: CacheSelectionState,
    isRequestHidden: Boolean,
    onClickNotCached: (EpisodeCacheState) -> Unit,
    onCancelRequest: (EpisodeCacheState) -> Unit,
    onPlay: (CacheEpisodeState) -> Unit,
    onResume: (CacheEpisodeState) -> Unit,
    onPause: (CacheEpisodeState) -> Unit,
    onDelete: (CacheEpisodeState) -> Unit,
    onViewDetail: ((CacheEpisodeState) -> Unit)?,
) {
    val cachesByEpisodeId = cachedEpisodes.groupBy { it.episodeId }
    val consumedCacheIds = mutableSetOf<String>()

    fun LazyGridScope.cachedEpisodeItem(cache: CacheEpisodeState) {
        item(key = "cache-${cache.cacheId}", contentType = "cache") {
            CacheEpisodeRow(
                episode = cache,
                mediaSourceInfoProvider = mediaSourceInfoProvider,
                selectionMode = selectionState.inSelection,
                selected = cache.cacheId in selectionState.selectedIds,
                onToggleSelected = { selectionState.toggleSelection(cache.cacheId) },
                onEnterSelection = {
                    selectionState.enterSelectionWith(selectionState.selectedIds + cache.cacheId)
                },
                onPlay = { onPlay(cache) },
                onResume = { onResume(cache) },
                onPause = { onPause(cache) },
                onDelete = { onDelete(cache) },
                onViewDetail = onViewDetail?.let { { it(cache) } },
            )
        }
    }

    episodes.forEach { episode ->
        val caches = cachesByEpisodeId[episode.episodeId]
        if (!caches.isNullOrEmpty()) {
            caches.forEach { cache ->
                consumedCacheIds += cache.cacheId
                cachedEpisodeItem(cache)
            }
        } else {
            item(key = "episode-${episode.episodeId}", contentType = "episode") {
                EpisodeNotCachedRow(
                    episode = episode,
                    isRequestHidden = isRequestHidden,
                    inSelectionMode = selectionState.inSelection,
                    onClick = { onClickNotCached(episode) },
                    onCancel = { onCancelRequest(episode) },
                )
            }
        }
    }

    // 剧集列表未加载完成或数据不一致时, 兜底展示剩余的缓存.
    cachedEpisodes.forEach { cache ->
        if (cache.cacheId !in consumedCacheIds) {
            cachedEpisodeItem(cache)
        }
    }
}

/**
 * "15/28 已完成 · 12.4 GB · 2 个下载中" + "全部暂停/全部继续".
 * 多选模式下变为 "已选 3 项 · 共 3.4 GB · 含 1 个下载中".
 */
@Composable
fun SubjectCacheSummaryRow(
    cachedEpisodes: List<CacheEpisodeState>,
    totalEpisodeCount: Int?,
    inSelection: Boolean,
    selectedEntries: List<CacheEpisodeState>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .testTag(SubjectCachePageTestTags.SUMMARY_ROW)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val summaryText = if (inSelection) {
            selectionSummaryText(selectedEntries)
        } else {
            cacheSummaryText(cachedEpisodes, totalEpisodeCount)
        }
        Text(
            summaryText,
            Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!inSelection) {
            PauseOrResumeAllTextButton(cachedEpisodes, onPauseAll, onResumeAll)
        }
    }
}

/**
 * "全部暂停" / "全部继续" 按钮. 无可操作缓存时不显示.
 */
@Composable
private fun PauseOrResumeAllTextButton(
    cachedEpisodes: List<CacheEpisodeState>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anyDownloading = cachedEpisodes.any {
        !it.isFinished && !it.isPaused && !it.isFailed
    }
    val anyPaused = cachedEpisodes.any { it.isPaused }
    when {
        anyDownloading -> {
            TextButton(onClick = onPauseAll, modifier = modifier.testTag(SubjectCachePageTestTags.PAUSE_ALL)) {
                Text(stringResource(Lang.cache_subject_pause_all))
            }
        }

        anyPaused -> {
            TextButton(onClick = onResumeAll, modifier = modifier.testTag(SubjectCachePageTestTags.RESUME_ALL)) {
                Text(stringResource(Lang.cache_subject_resume_all))
            }
        }
    }
}

@Composable
private fun cacheSummaryText(
    cachedEpisodes: List<CacheEpisodeState>,
    totalEpisodeCount: Int?,
): String {
    val finishedCount = cachedEpisodes.count { it.isFinished }
    val totalCount = totalEpisodeCount?.coerceAtLeast(cachedEpisodes.size) ?: cachedEpisodes.size
    // 设计稿: "2 个下载中" 统计所有未完成的下载任务 (含暂停).
    val downloadingCount = cachedEpisodes.count { !it.isFinished && !it.isFailed }

    val parts = buildList {
        add(stringResource(Lang.cache_management_finished_count, finishedCount, totalCount))
        totalCacheSize(cachedEpisodes)?.let { add("$it") }
        if (downloadingCount > 0) {
            add(stringResource(Lang.cache_management_downloading_count, downloadingCount))
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun selectionSummaryText(selectedEntries: List<CacheEpisodeState>): String {
    val totalSize = totalCacheSize(selectedEntries) ?: 0.bytes
    val downloadingCount = selectedEntries.count { !it.isFinished && !it.isPaused && !it.isFailed }
    val summary = stringResource(Lang.cache_management_selection_summary, selectedEntries.size, "$totalSize")
    return if (downloadingCount > 0) {
        "$summary · ${stringResource(Lang.cache_management_selection_downloading_count, downloadingCount)}"
    } else {
        summary
    }
}

private fun totalCacheSize(episodes: List<CacheEpisodeState>): FileSize? {
    var sum = 0L
    var any = false
    episodes.forEach { episode ->
        if (episode.totalSize != FileSize.Unspecified) {
            sum += episode.totalSize.inBytes
            any = true
        }
    }
    return if (any) sum.bytes else null
}

/**
 * 未缓存的剧集行: 标题 + (看过/抛弃 chip) + 追加缓存按钮.
 *
 * 看过或未开播的剧集颜色减淡; 多选模式下整行降低透明度且不可交互.
 */
@Composable
fun EpisodeNotCachedRow(
    episode: EpisodeCacheState,
    isRequestHidden: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = episode.info
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (inSelectionMode) 0.38f else 1f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColorForWatchStatus(info.watchStatus, info.hasPublished),
        ) {
            Text(
                stringResource(Lang.cache_management_episode_label, info.sort, info.title),
                Modifier.weight(1f).placeholder(episode.isInfoLoading),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when (info.watchStatus) {
                UnifiedCollectionType.DONE -> WatchStatusChip(stringResource(Lang.cache_filter_collection_done))
                UnifiedCollectionType.DROPPED -> WatchStatusChip(stringResource(Lang.cache_filter_collection_dropped))
                else -> {}
            }

            // 设计稿: 追加缓存图标为 primary 色; 看过/未开播的行保持减淡的颜色.
            val dimmed = info.watchStatus.isDoneOrDropped() || !info.hasPublished
            CompositionLocalProvider(
                LocalContentColor provides
                        if (dimmed) LocalContentColor.current else MaterialTheme.colorScheme.primary,
            ) {
                EpisodeCacheActionIcon(
                    isLoadingIndefinitely = !isRequestHidden &&
                            episode.showProgressIndicator.collectAsStateWithLifecycle().value,
                    hasActionRunning = episode.actionTasker.isRunning.collectAsStateWithLifecycle().value,
                    cacheStatus = episode.cacheStatus,
                    canCache = episode.canCache,
                    onClick = { if (!inSelectionMode) onClick() },
                    onCancel = onCancel,
                )
            }
        }
    }
}

/**
 * 全局缓存管理页宽屏布局中, 详情栏 (右侧) 展示的条目缓存内容:
 * 标题 + 汇总 + 全部暂停/继续, 以及该条目的全部剧集 (含未缓存, 可追加缓存).
 *
 * 设计稿: [Figma](https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=1662-414)
 */
@Composable
fun SubjectCacheDetailPaneContent(
    vm: SubjectCacheViewModel,
    selectionState: CacheSelectionState,
    onPlay: (CacheEpisodeState) -> Unit,
    onViewDetail: ((CacheEpisodeState) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val cachedEpisodes by vm.cacheEpisodesFlow.collectAsStateWithLifecycle()

    var hideMediaSelector by remember(vm.cacheListState.currentSelectMediaTask) {
        mutableStateOf(false)
    }
    EpisodeCacheRequesterDialogs(
        vm.cacheListState,
        vm.mediaSourceInfoProvider,
        mediaSelectorSettingsProvider = { vm.mediaSelectorSettingsFlow },
        hideMediaSelector = hideMediaSelector,
        onRequestHideMediaSelector = { hideMediaSelector = true },
    )

    val uiScope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 480.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = contentPadding,
    ) {
        item(key = "detail_header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            SubjectCacheDetailHeader(
                title = vm.subjectTitle,
                cachedEpisodes = cachedEpisodes,
                totalEpisodeCount = vm.cacheListState.episodes.size.takeIf { it > 0 },
                onPauseAll = { vm.pauseAllCaches() },
                onResumeAll = { vm.resumeAllCaches() },
            )
        }
        subjectCacheEpisodeItems(
            episodes = vm.cacheListState.episodes,
            cachedEpisodes = cachedEpisodes,
            mediaSourceInfoProvider = vm.mediaSourceInfoProvider,
            selectionState = selectionState,
            isRequestHidden = hideMediaSelector,
            onClickNotCached = { episode ->
                if (episode != vm.cacheListState.currentSelectMediaTask?.episode) {
                    vm.cacheListState.requestCache(episode, autoSelectCached = true)
                    uiScope.launch {
                        KoinPlatform.getKoin().get<PermissionManager>()
                            .requestNotificationPermission(context)
                    }
                }
                hideMediaSelector = false
            },
            onCancelRequest = { it.actionTasker.cancel() },
            onPlay = onPlay,
            onResume = { vm.resumeCache(it) },
            onPause = { vm.pauseCache(it) },
            onDelete = { vm.deleteCache(it) },
            onViewDetail = onViewDetail,
        )
    }
}

/**
 * 详情栏头部: 条目名 + "15/28 已完成 · 12.4 GB · 2 个下载中" + "全部暂停/全部继续".
 */
@Composable
fun SubjectCacheDetailHeader(
    title: String?,
    cachedEpisodes: List<CacheEpisodeState>,
    totalEpisodeCount: Int?,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 两个文本均可收缩, 长条目名不会把汇总和按钮挤出屏幕.
            Text(
                title.orEmpty(),
                Modifier.weight(1f, fill = false).placeholder(title == null),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                cacheSummaryText(cachedEpisodes, totalEpisodeCount),
                Modifier.padding(start = 12.dp).weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PauseOrResumeAllTextButton(cachedEpisodes, onPauseAll, onResumeAll)
    }
}

@Composable
private fun WatchStatusChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.small,
        ),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
