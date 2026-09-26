/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.playback

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.repository.episode.EpisodeCollectionPendingOp
import me.him188.ani.app.data.repository.episode.EpisodeCollectionPendingOpNames
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionSyncer
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.PlaybackHistoryPendingOp
import me.him188.ani.app.data.repository.player.PlaybackHistorySyncer
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.rememberCurrentTopAppBarContainerColor
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.cache_subject_delete
import me.him188.ani.app.ui.lang.playback_history_cover
import me.him188.ani.app.ui.lang.playback_history_delete_confirmation
import me.him188.ani.app.ui.lang.playback_history_delete_selected
import me.him188.ani.app.ui.lang.playback_history_delete_title
import me.him188.ani.app.ui.lang.playback_history_empty
import me.him188.ani.app.ui.lang.playback_history_enter_selection_mode
import me.him188.ani.app.ui.lang.playback_history_episode_label
import me.him188.ani.app.ui.lang.playback_history_exit_selection
import me.him188.ani.app.ui.lang.playback_history_progress_unknown_duration
import me.him188.ani.app.ui.lang.playback_history_select_all
import me.him188.ani.app.ui.lang.playback_history_selected_count
import me.him188.ani.app.ui.lang.playback_history_sync_delete_all
import me.him188.ani.app.ui.lang.playback_history_sync_delete_pending
import me.him188.ani.app.ui.lang.playback_history_sync_empty
import me.him188.ani.app.ui.lang.playback_history_sync_op_delete
import me.him188.ani.app.ui.lang.playback_history_sync_op_mark_watched
import me.him188.ani.app.ui.lang.playback_history_sync_op_unmark_watched
import me.him188.ani.app.ui.lang.playback_history_sync_op_upsert
import me.him188.ani.app.ui.lang.playback_history_sync_pending_episode
import me.him188.ani.app.ui.lang.playback_history_sync_pending_title
import me.him188.ani.app.ui.lang.playback_history_sync_status_pending
import me.him188.ani.app.ui.lang.playback_history_sync_status_synced
import me.him188.ani.app.ui.lang.playback_history_sync_status_title
import me.him188.ani.app.ui.lang.playback_history_title
import me.him188.ani.app.ui.lang.playback_history_unknown_episode
import me.him188.ani.app.ui.lang.playback_history_unknown_subject
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.koin.core.component.inject
import kotlin.math.floor

@Immutable
data class PlaybackHistoryUiItem(
    val episodeId: Int,
    val subjectId: Int?,
    val episodeSort: Float?,
    val subjectName: String?,
    val subjectImageUrl: String?,
    val episodeName: String?,
    val positionMillis: Long,
    val durationMillis: Long?,
    val updatedAtMillis: Long,
)

/**
 * 同步状态页面的一张卡: 一集的所有待同步操作 (播放进度、看过状态) 合在一起显示.
 */
@Immutable
data class PlaybackHistorySyncStatusUiItem(
    val episodeId: Int,
    val operationNames: List<String>,
    val subjectName: String?,
    val episodeName: String?,
    val versionMillis: Long,
    val playbackOpId: Long? = null,
    val collectionOpId: Long? = null,
)

/**
 * 一集的待同步操作, 由播放记录和剧集收藏两个仓库的队列按 episodeId 合并而来. 不含界面文案.
 */
@Immutable
data class PendingSyncEpisode(
    val episodeId: Int,
    val subjectName: String?,
    val episodeName: String?,
    val playbackOp: PlaybackHistoryPendingOp?,
    val collectionOp: EpisodeCollectionPendingOp?,
) {
    val versionMillis: Long
        get() = maxOf(playbackOp?.versionMillis ?: 0L, collectionOp?.updatedAtMillis ?: 0L)
}

@Stable
class PlaybackHistoryViewModel : AbstractViewModel(), KoinComponent {
    private val repository: EpisodePlayHistoryRepository by inject()
    private val syncer: PlaybackHistorySyncer by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val episodeCollectionSyncer: EpisodeCollectionSyncer by inject()

    val stateFlow = repository.flow
        .stateInBackground(emptyList())

    /**
     * 待同步的播放记录操作涉及的本地记录, 含已删除的. 删除操作本身不带条目名, 显示时从这里补.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pendingOpHistoriesFlow = repository.pendingOpsFlow
        .map { ops -> ops.mapTo(mutableSetOf()) { it.episodeId } }
        .distinctUntilChanged()
        .flatMapLatest { repository.allHistoriesFlowByEpisodeIds(it) }
        .map { histories -> histories.associateBy { it.episodeId } }

    /**
     * 播放记录和看过状态两条队列按剧集合并后的待同步列表, 页面上一集一张卡.
     */
    val pendingSyncEpisodesFlow = combine(
        repository.pendingOpsFlow,
        pendingOpHistoriesFlow,
        episodeCollectionRepository.pendingOpsFlow,
        episodeCollectionRepository.pendingOpNamesFlow(),
        ::buildPendingSyncEpisodes,
    ).stateInBackground(emptyList())

    fun delete(episodeIds: Collection<Int>) {
        if (episodeIds.isEmpty()) return
        backgroundScope.launch {
            repository.removeAll(episodeIds)
        }
    }

    fun deletePendingItems(items: Collection<PlaybackHistorySyncStatusUiItem>) {
        val playbackOpIds = items.mapNotNull { it.playbackOpId }
        val collectionOpIds = items.mapNotNull { it.collectionOpId }
        if (playbackOpIds.isEmpty() && collectionOpIds.isEmpty()) return
        backgroundScope.launch {
            repository.deletePendingOps(playbackOpIds)
            episodeCollectionRepository.deletePendingOps(collectionOpIds)
        }
    }

    suspend fun syncOnce() {
        syncer.syncOnce()
        episodeCollectionSyncer.syncOnce()
    }
}

/**
 * 按 episodeId 合并两条队列. 名字优先取操作自带的, 其次是本地播放记录 (含已删除墓碑), 最后是剧集缓存.
 * 顺序按每集最近一次操作的时间, 早的在前.
 */
internal fun buildPendingSyncEpisodes(
    playbackOps: List<PlaybackHistoryPendingOp>,
    playbackHistories: Map<Int, EpisodeHistory>,
    collectionOps: List<EpisodeCollectionPendingOp>,
    collectionNames: Map<Int, EpisodeCollectionPendingOpNames>,
): List<PendingSyncEpisode> {
    val playbackByEpisode = playbackOps.associateBy { it.episodeId }
    val collectionByEpisode = collectionOps.associateBy { it.episodeId }
    return (playbackByEpisode.keys + collectionByEpisode.keys).map { episodeId ->
        val playbackOp = playbackByEpisode[episodeId]
        val history = playbackHistories[episodeId]
        val names = collectionNames[episodeId]
        PendingSyncEpisode(
            episodeId = episodeId,
            subjectName = (playbackOp as? PlaybackHistoryPendingOp.Upsert)?.subjectName
                ?: history?.subjectName ?: names?.subjectName,
            episodeName = (playbackOp as? PlaybackHistoryPendingOp.Upsert)?.episodeName
                ?: history?.episodeName ?: names?.episodeName,
            playbackOp = playbackOp,
            collectionOp = collectionByEpisode[episodeId],
        )
    }.sortedWith(compareBy({ it.versionMillis }, { it.episodeId }))
}

@Composable
fun PlaybackHistoryScreen(
    vm: PlaybackHistoryViewModel,
    onNavigateBack: () -> Unit,
    onOpenHistory: (PlaybackHistoryUiItem) -> Unit,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val asyncHandler = rememberAsyncHandler()
    val histories by vm.stateFlow.collectAsStateWithLifecycle()
    val pendingSyncEpisodes by vm.pendingSyncEpisodesFlow.collectAsStateWithLifecycle()
    fun requestSync() {
        if (asyncHandler.isWorking) return
        asyncHandler.launch {
            vm.syncOnce()
        }
    }

    LaunchedEffect(vm) {
        requestSync()
    }

    PlaybackHistoryScreen(
        histories = histories.toUiItems(),
        pendingOpCount = pendingSyncEpisodes.size,
        onNavigateBack = onNavigateBack,
        onOpenHistory = onOpenHistory,
        onOpenSyncStatus = onOpenSyncStatus,
        onDelete = vm::delete,
        isRefreshing = asyncHandler.isWorking,
        onRefresh = ::requestSync,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaybackHistoryScreen(
    histories: List<PlaybackHistoryUiItem>,
    pendingOpCount: Int = 0,
    onNavigateBack: () -> Unit,
    onOpenHistory: (PlaybackHistoryUiItem) -> Unit,
    onOpenSyncStatus: () -> Unit = {},
    onDelete: (Collection<Int>) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val appBarColors = AniThemeDefaults.topAppBarColors()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val topAppBarContainerColor by rememberCurrentTopAppBarContainerColor(appBarColors, scrollBehavior)
    val listState = rememberLazyListState()

    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedEpisodeIds by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val inSelection = selectionMode || selectedEpisodeIds.isNotEmpty()
    val allSelected = histories.isNotEmpty() && selectedEpisodeIds.size == histories.size

    LaunchedEffect(histories) {
        val validIds = histories.mapTo(mutableSetOf()) { it.episodeId }
        selectedEpisodeIds = selectedEpisodeIds.filterTo(mutableSetOf()) { it in validIds }
    }

    BackHandler(inSelection) {
        selectionMode = false
        selectedEpisodeIds = emptySet()
    }
    BackHandler(!inSelection) {
        onNavigateBack()
    }

    if (showDeleteDialog) {
        PlaybackHistoryDeleteDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDelete(selectedEpisodeIds)
                selectionMode = false
                selectedEpisodeIds = emptySet()
                showDeleteDialog = false
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            PlaybackHistoryTopBar(
                inSelection = inSelection,
                selectedCount = selectedEpisodeIds.size,
                allSelected = allSelected,
                hasEntries = histories.isNotEmpty(),
                pendingOpCount = pendingOpCount,
                onEnterSelection = { selectionMode = true },
                onOpenSyncStatus = onOpenSyncStatus,
                onExitSelection = {
                    selectionMode = false
                    selectedEpisodeIds = emptySet()
                },
                onToggleSelectAll = {
                    selectionMode = true
                    selectedEpisodeIds = if (allSelected) {
                        emptySet()
                    } else {
                        histories.mapTo(mutableSetOf()) { it.episodeId }
                    }
                },
                onDeleteSelected = { showDeleteDialog = true },
                navigationIcon = navigationIcon,
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize(),
            enabled = !inSelection,
            touchOnly = true,
        ) {
            if (histories.isEmpty()) {
                EmptyPlaybackHistory(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(PlaybackHistoryTestTags.LIST),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(histories, key = { it.episodeId }) { history ->
                        val selected = history.episodeId in selectedEpisodeIds
                        PlaybackHistoryListItem(
                            item = history,
                            selected = selected,
                            selectionMode = inSelection,
                            topAppBarContainerColor = topAppBarContainerColor,
                            onClick = {
                                if (inSelection) {
                                    selectedEpisodeIds = selectedEpisodeIds.toggle(history.episodeId)
                                } else {
                                    onOpenHistory(history)
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedEpisodeIds = selectedEpisodeIds + history.episodeId
                            },
                            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackHistoryTopBar(
    inSelection: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    hasEntries: Boolean,
    pendingOpCount: Int,
    onEnterSelection: () -> Unit,
    onOpenSyncStatus: () -> Unit,
    onExitSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    windowInsets: WindowInsets,
) {
    if (inSelection) {
        val selectedCountText = stringResource(Lang.playback_history_selected_count, selectedCount)
        val exitSelectionText = stringResource(Lang.playback_history_exit_selection)
        val selectAllText = stringResource(Lang.playback_history_select_all)
        val deleteSelectedText = stringResource(Lang.playback_history_delete_selected)
        AniTopAppBar(
            title = { AniTopAppBarDefaults.Title(selectedCountText) },
            navigationIcon = {
                IconButton(onClick = onExitSelection) {
                    Icon(Icons.Rounded.Close, exitSelectionText)
                }
            },
            actions = {
                IconButton(onClick = onToggleSelectAll, enabled = hasEntries) {
                    Icon(if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll, selectAllText)
                }
            },
            avatar = {
                IconButton(onClick = onDeleteSelected, enabled = selectedCount > 0) {
                    Icon(Icons.Rounded.Delete, deleteSelectedText, tint = MaterialTheme.colorScheme.error)
                }
            },
            colors = AniThemeDefaults.topAppBarColors(),
            windowInsets = windowInsets,
        )
    } else {
        val enterSelectionText = stringResource(Lang.playback_history_enter_selection_mode)
        val syncStatusText = if (pendingOpCount > 0) {
            stringResource(Lang.playback_history_sync_status_pending, pendingOpCount)
        } else {
            stringResource(Lang.playback_history_sync_status_synced)
        }
        AniTopAppBar(
            title = { AniTopAppBarDefaults.Title(stringResource(Lang.playback_history_title)) },
            navigationIcon = navigationIcon,
            actions = {
                IconButton(onClick = onOpenSyncStatus) {
                    Icon(
                        if (pendingOpCount > 0) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDone,
                        syncStatusText,
                        tint = if (pendingOpCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = onEnterSelection,
                    enabled = hasEntries,
                ) {
                    Icon(Icons.Default.Checklist, enterSelectionText)
                }
            },
            colors = AniThemeDefaults.topAppBarColors(),
            windowInsets = windowInsets,
        )
    }
}

@Composable
fun PlaybackHistorySyncStatusScreen(
    vm: PlaybackHistoryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val pendingSyncEpisodes by vm.pendingSyncEpisodesFlow.collectAsStateWithLifecycle()
    PlaybackHistorySyncStatusScreen(
        pendingOps = pendingSyncEpisodes.toSyncStatusUiItems(),
        onNavigateBack = onNavigateBack,
        onDeletePendingItems = vm::deletePendingItems,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

@Composable
fun PlaybackHistorySyncStatusScreen(
    pendingOps: List<PlaybackHistorySyncStatusUiItem>,
    onNavigateBack: () -> Unit,
    onDeletePendingItems: (Collection<PlaybackHistorySyncStatusUiItem>) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title(stringResource(Lang.playback_history_sync_status_title)) },
                navigationIcon = navigationIcon,
                actions = {
                    if (pendingOps.isNotEmpty()) {
                        IconButton(onClick = { onDeletePendingItems(pendingOps) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                stringResource(Lang.playback_history_sync_delete_all),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = AniThemeDefaults.topAppBarColors(),
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        BackHandler {
            onNavigateBack()
        }
        if (pendingOps.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Lang.playback_history_sync_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .testTag(PlaybackHistoryTestTags.SYNC_PENDING_LIST),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        stringResource(Lang.playback_history_sync_pending_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                items(pendingOps, key = { it.episodeId }) { item ->
                    PlaybackHistorySyncPendingItem(
                        item = item,
                        onDelete = { onDeletePendingItems(listOf(item)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackHistorySyncPendingItem(
    item: PlaybackHistorySyncStatusUiItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = item.subjectName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_sync_pending_episode, item.episodeId)
    val episodeName = item.episodeName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_unknown_episode)
    Surface(
        modifier
            .clip(MaterialTheme.shapes.large)
            .fillMaxWidth()
            .testTag("${PlaybackHistoryTestTags.SYNC_PENDING_ITEM_PREFIX}${item.episodeId}"),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (item.operationNames + episodeName).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatDateTime(item.versionMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    stringResource(Lang.playback_history_sync_delete_pending),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaybackHistoryListItem(
    item: PlaybackHistoryUiItem,
    selected: Boolean,
    selectionMode: Boolean,
    topAppBarContainerColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectName = item.subjectName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_unknown_subject)
    val coverContentDescription = stringResource(Lang.playback_history_cover, subjectName)

    Surface(
        modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("${PlaybackHistoryTestTags.ITEM_PREFIX}${item.episodeId}"),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 6.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else topAppBarContainerColor,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackHistoryCover(
                imageUrl = item.subjectImageUrl,
                contentDescription = coverContentDescription,
                modifier = Modifier.size(width = 72.dp, height = 96.dp),
            )
            Column(
                Modifier.weight(1f).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(PlaybackHistoryTestTags.SUBJECT_NAME),
                )
                Text(
                    episodeText(item),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(PlaybackHistoryTestTags.EPISODE_NAME),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatDateTime(item.updatedAtMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag(PlaybackHistoryTestTags.DATE),
                    )
                    Text(
                        progressText(item),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag(PlaybackHistoryTestTags.PROGRESS_TEXT),
                    )
                }
                val progress = item.durationMillis?.takeIf { it > 0L }?.let {
                    item.positionMillis.toFloat() / it
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.testTag("${PlaybackHistoryTestTags.CHECKBOX_PREFIX}${item.episodeId}"),
                )
            }
        }
    }
}

@Composable
private fun PlaybackHistoryCover(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun EmptyPlaybackHistory(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            stringResource(Lang.playback_history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaybackHistoryDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Lang.playback_history_delete_title)) },
        text = { Text(stringResource(Lang.playback_history_delete_confirmation)) },
        confirmButton = {
            TextButton(onConfirm) {
                Text(stringResource(Lang.cache_subject_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onDismiss) { Text(stringResource(Lang.cache_subject_cancel)) }
        },
    )
}

@Composable
private fun episodeText(item: PlaybackHistoryUiItem): String {
    val episodeLabel = item.episodeSort?.let { sort ->
        stringResource(Lang.playback_history_episode_label, sort.formatEpisodeSort())
    }
    val episodeName = item.episodeName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_unknown_episode)
    return if (episodeLabel == null) episodeName else "$episodeLabel · $episodeName"
}

@Composable
private fun progressText(item: PlaybackHistoryUiItem): String {
    val position = item.positionMillis.formatDuration()
    val duration = item.durationMillis?.takeIf { it > 0L }?.formatDuration()
        ?: stringResource(Lang.playback_history_progress_unknown_duration)
    return "$position / $duration"
}

private fun List<EpisodeHistory>.toUiItems(): List<PlaybackHistoryUiItem> {
    return asSequence()
        .filterNot(EpisodeHistory::isDeleted)
        .sortedByDescending { it.updatedAtMillis }
        .map {
            PlaybackHistoryUiItem(
                episodeId = it.episodeId,
                subjectId = it.subjectId,
                episodeSort = it.episodeSort,
                subjectName = it.subjectName,
                subjectImageUrl = it.subjectImageUrl,
                episodeName = it.episodeName,
                positionMillis = it.positionMillis,
                durationMillis = it.durationMillis,
                updatedAtMillis = it.updatedAtMillis,
            )
        }
        .toList()
}

@Composable
private fun List<PendingSyncEpisode>.toSyncStatusUiItems(): List<PlaybackHistorySyncStatusUiItem> {
    return toSyncStatusUiItems(
        upsertName = stringResource(Lang.playback_history_sync_op_upsert),
        deleteName = stringResource(Lang.playback_history_sync_op_delete),
        markWatchedName = stringResource(Lang.playback_history_sync_op_mark_watched),
        unmarkWatchedName = stringResource(Lang.playback_history_sync_op_unmark_watched),
    )
}

/**
 * 操作名按播放进度、看过状态的顺序列出. 服务端只区分看过和未看过, 所以非 DONE 的收藏状态都显示为取消看过.
 */
internal fun List<PendingSyncEpisode>.toSyncStatusUiItems(
    upsertName: String,
    deleteName: String,
    markWatchedName: String,
    unmarkWatchedName: String,
): List<PlaybackHistorySyncStatusUiItem> {
    return map { episode ->
        PlaybackHistorySyncStatusUiItem(
            episodeId = episode.episodeId,
            operationNames = buildList {
                when (episode.playbackOp) {
                    is PlaybackHistoryPendingOp.Upsert -> add(upsertName)
                    is PlaybackHistoryPendingOp.Delete -> add(deleteName)
                    null -> Unit
                }
                episode.collectionOp?.let { op ->
                    add(if (op.collectionType == UnifiedCollectionType.DONE) markWatchedName else unmarkWatchedName)
                }
            },
            subjectName = episode.subjectName,
            episodeName = episode.episodeName,
            versionMillis = episode.versionMillis,
            playbackOpId = episode.playbackOp?.id,
            collectionOpId = episode.collectionOp?.id,
        )
    }
}

private fun Set<Int>.toggle(id: Int): Set<Int> {
    return if (id in this) this - id else this + id
}

private fun Float.formatEpisodeSort(): String {
    return if (this == floor(this)) this.toInt().toString() else toString()
}

private fun Long.formatDuration(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}

object PlaybackHistoryTestTags {
    const val LIST = "playbackHistoryList"
    const val ITEM_PREFIX = "playbackHistoryItem-"
    const val CHECKBOX_PREFIX = "playbackHistoryCheckbox-"
    const val SYNC_PENDING_LIST = "playbackHistorySyncPendingList"
    const val SYNC_PENDING_ITEM_PREFIX = "playbackHistorySyncPendingItem-"
    const val SUBJECT_NAME = "playbackHistorySubjectName"
    const val EPISODE_NAME = "playbackHistoryEpisodeName"
    const val DATE = "playbackHistoryDate"
    const val PROGRESS_TEXT = "playbackHistoryProgressText"
}

@Composable
@Preview
private fun PreviewPlaybackHistoryScreen() {
    ProvideCompositionLocalsForPreview {
        PlaybackHistoryScreen(
            histories = listOf(
                PlaybackHistoryUiItem(
                    episodeId = 1,
                    subjectId = 100,
                    episodeSort = 1f,
                    subjectName = "孤独摇滚！",
                    subjectImageUrl = "",
                    episodeName = "转啊转",
                    positionMillis = 420_000,
                    durationMillis = 1_440_000,
                    updatedAtMillis = 1_700_000_000_000,
                ),
            ),
            onNavigateBack = {},
            onOpenHistory = {},
            onDelete = {},
        )
    }
}
