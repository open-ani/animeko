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
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.player.EpisodeHistory
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

@Immutable
data class PlaybackHistorySyncStatusUiItem(
    val id: Long,
    val episodeId: Int,
    val operationName: String,
    val subjectName: String?,
    val episodeName: String?,
    val versionMillis: Long,
)

@Stable
class PlaybackHistoryViewModel : AbstractViewModel(), KoinComponent {
    private val repository: EpisodePlayHistoryRepository by inject()
    private val syncer: PlaybackHistorySyncer by inject()

    val stateFlow = repository.flow
        .stateInBackground(emptyList())
    val pendingOpsFlow = repository.pendingOpsFlow
        .stateInBackground(emptyList())

    fun delete(episodeIds: Collection<Int>) {
        if (episodeIds.isEmpty()) return
        backgroundScope.launch {
            repository.removeAll(episodeIds)
        }
    }

    fun deletePendingOps(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        backgroundScope.launch {
            repository.deletePendingOps(ids)
        }
    }

    suspend fun syncOnce() {
        syncer.syncOnce()
    }
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
    val pendingOps by vm.pendingOpsFlow.collectAsStateWithLifecycle()
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
        pendingOpCount = pendingOps.size,
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
    val pendingOps by vm.pendingOpsFlow.collectAsStateWithLifecycle()
    PlaybackHistorySyncStatusScreen(
        pendingOps = pendingOps.toSyncStatusUiItems(),
        onNavigateBack = onNavigateBack,
        onDeletePendingOps = vm::deletePendingOps,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

@Composable
fun PlaybackHistorySyncStatusScreen(
    pendingOps: List<PlaybackHistorySyncStatusUiItem>,
    onNavigateBack: () -> Unit,
    onDeletePendingOps: (Collection<Long>) -> Unit,
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
                        IconButton(onClick = { onDeletePendingOps(pendingOps.map { it.id }) }) {
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
                items(pendingOps, key = { it.id }) { item ->
                    PlaybackHistorySyncPendingItem(
                        item = item,
                        onDelete = { onDeletePendingOps(listOf(item.id)) },
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
            .testTag("${PlaybackHistoryTestTags.SYNC_PENDING_ITEM_PREFIX}${item.id}"),
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
                    "${item.operationName} · $episodeName",
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
private fun List<PlaybackHistoryPendingOp>.toSyncStatusUiItems(): List<PlaybackHistorySyncStatusUiItem> {
    val upsertName = stringResource(Lang.playback_history_sync_op_upsert)
    val deleteName = stringResource(Lang.playback_history_sync_op_delete)
    return map { op ->
        when (op) {
            is PlaybackHistoryPendingOp.Upsert -> PlaybackHistorySyncStatusUiItem(
                id = op.id,
                episodeId = op.episodeId,
                operationName = upsertName,
                subjectName = op.subjectName,
                episodeName = op.episodeName,
                versionMillis = op.updatedAtMillis,
            )

            is PlaybackHistoryPendingOp.Delete -> PlaybackHistorySyncStatusUiItem(
                id = op.id,
                episodeId = op.episodeId,
                operationName = deleteName,
                subjectName = null,
                episodeName = null,
                versionMillis = op.deletedAtMillis,
            )
        }
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
