/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.playback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.app.ui.lang.cache_subject_delete
import me.him188.ani.app.ui.lang.playback_history_cover
import me.him188.ani.app.ui.lang.playback_history_delete_confirmation
import me.him188.ani.app.ui.lang.playback_history_delete_title
import me.him188.ani.app.ui.lang.playback_history_empty
import me.him188.ani.app.ui.lang.playback_history_episode_label
import me.him188.ani.app.ui.lang.playback_history_exit_selection
import me.him188.ani.app.ui.lang.playback_history_progress_unknown_duration
import me.him188.ani.app.ui.lang.playback_history_select_all
import me.him188.ani.app.ui.lang.playback_history_selected_count
import me.him188.ani.app.ui.lang.playback_history_sync_delete_all
import me.him188.ani.app.ui.lang.playback_history_sync_empty
import me.him188.ani.app.ui.lang.playback_history_sync_op_delete
import me.him188.ani.app.ui.lang.playback_history_sync_op_mark_watched
import me.him188.ani.app.ui.lang.playback_history_sync_op_unmark_watched
import me.him188.ani.app.ui.lang.playback_history_sync_op_upsert
import me.him188.ani.app.ui.lang.playback_history_sync_pending_episode
import me.him188.ani.app.ui.lang.playback_history_sync_status_pending
import me.him188.ani.app.ui.lang.playback_history_sync_status_synced
import me.him188.ani.app.ui.lang.playback_history_sync_status_title
import me.him188.ani.app.ui.lang.playback_history_title
import me.him188.ani.app.ui.lang.playback_history_unknown_episode
import me.him188.ani.app.ui.lang.playback_history_unknown_subject
import me.him188.ani.app.ui.lang.tv_playback_history_back
import me.him188.ani.app.ui.lang.tv_playback_history_refresh
import me.him188.ani.app.ui.lang.tv_playback_history_select
import me.him188.ani.app.ui.playback.PlaybackHistoryUiItem
import me.him188.ani.tv.ui.foundation.formatPlaybackTime
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.foundation.layout.TvScreenScaffold
import me.him188.ani.tv.ui.foundation.layout.tvModalUnderlay
import me.him188.ani.tv.ui.foundation.widgets.TvOptionChip
import me.him188.ani.tv.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvOptionModal
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import me.him188.ani.tv.ui.foundation.widgets.TvOptionsRow
import me.him188.ani.tv.ui.foundation.widgets.tvOptionSurfaceColors
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/** 播放记录页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvPlaybackHistoryFocus : TvFocusKey {
    /** 主操作行 (同步状态/刷新/选择). */
    Chips,

    /** 播放记录列表容器 (进页初始焦点). */
    List,

    /** 待同步面板操作行 (返回/全部删除). */
    SyncChips,

    /** 待同步列表容器. */
    SyncList,
}

private enum class TvPlaybackHistoryDialogFocus : TvFocusKey {
    Cancel,
}

/**
 * TV 播放记录页: 单列大行列表 + 同步/多选操作行, 待同步状态为页内二级视图.
 * 功能与手机端播放记录页一致 (记录列表与续播、删除、同步与待同步管理), 布局按 10-foot 重排.
 */
@Composable
fun TvPlaybackHistoryScreen(
    state: TvPlaybackHistoryUiState,
    onIntent: (TvPlaybackHistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
    navigationRailInsets: PaddingValues = PaddingValues(0.dp),
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val listState = rememberLazyListState()
    val syncListState = rememberLazyListState()

    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedEpisodeIds by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var syncPanelOpen by rememberSaveable { mutableStateOf(false) }
    var pendingFocus by remember { mutableStateOf<TvPlaybackHistoryFocus?>(null) }

    val histories = state.histories
    val pendingSync = state.pendingSync
    val allSelected = histories.isNotEmpty() && selectedEpisodeIds.size == histories.size

    // 数据变化后清理已不存在的选中项, 并把删除后的焦点还给列表 (与手机端清理选中项一致).
    LaunchedEffect(histories, pendingSync) {
        val validIds = histories.mapTo(mutableSetOf()) { it.episodeId }
        selectedEpisodeIds = selectedEpisodeIds.filterTo(mutableSetOf()) { it in validIds }
        pendingFocus?.let { target ->
            pendingFocus = null
            focus.request(
                when (target) {
                    TvPlaybackHistoryFocus.SyncList ->
                        if (pendingSync.isNotEmpty()) target else TvPlaybackHistoryFocus.SyncChips

                    else -> if (histories.isNotEmpty()) target else TvPlaybackHistoryFocus.Chips
                },
            )
        }
    }

    focus.InitialFocus(if (histories.isNotEmpty()) TvPlaybackHistoryFocus.List else TvPlaybackHistoryFocus.Chips)

    // 进页时记录可能还在加载, 初始焦点先落在操作行; 首批数据到达且用户尚未操作时把焦点交给列表.
    var listReceivedInitialFocus by remember { mutableStateOf(histories.isNotEmpty()) }
    LaunchedEffect(histories) {
        if (!listReceivedInitialFocus && histories.isNotEmpty() && focus.userNavGeneration == 0) {
            listReceivedInitialFocus = true
            if (!selectionMode && !syncPanelOpen) {
                focus.request(TvPlaybackHistoryFocus.List)
            }
        }
    }

    fun closeSyncPanel() {
        syncPanelOpen = false
        focus.request(TvPlaybackHistoryFocus.Chips)
    }

    fun openSyncPanel() {
        syncPanelOpen = true
        focus.request(if (pendingSync.isNotEmpty()) TvPlaybackHistoryFocus.SyncList else TvPlaybackHistoryFocus.SyncChips)
    }

    BackHandler(selectionMode) {
        selectionMode = false
        selectedEpisodeIds = emptySet()
        focus.request(TvPlaybackHistoryFocus.Chips)
    }
    BackHandler(syncPanelOpen && !selectionMode) { closeSyncPanel() }

    TvScreenScaffold(
        modifier
            .fillMaxSize()
            .background(tvShellBackgroundColor())
            .padding(navigationRailInsets)
            .testTag("tv-playback-history")
            .tvFocusNavSignal(focus),
    ) {
        Column(Modifier.fillMaxSize().tvModalUnderlay(showDeleteDialog)) {
            Text(
                when {
                    selectionMode -> stringResource(Lang.playback_history_selected_count, selectedEpisodeIds.size)
                    syncPanelOpen -> stringResource(Lang.playback_history_sync_status_title)
                    else -> stringResource(Lang.playback_history_title)
                },
                Modifier.testTag("tv-playback-history-title"),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))
            when {
                syncPanelOpen -> {
                    TvPlaybackSyncChips(
                        hasPending = pendingSync.isNotEmpty(),
                        focus = focus,
                        onBack = ::closeSyncPanel,
                        onDeleteAll = {
                            onIntent(TvPlaybackHistoryIntent.DeleteAllPending)
                            pendingFocus = TvPlaybackHistoryFocus.SyncChips
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    if (pendingSync.isEmpty()) {
                        TvPlaybackEmptyText(
                            stringResource(Lang.playback_history_sync_empty),
                            Modifier.weight(1f).testTag("tv-playback-sync-empty"),
                        )
                    } else {
                        LazyColumn(
                            state = syncListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .tvFocusAnchor(focus, TvPlaybackHistoryFocus.SyncList)
                                .focusGroup()
                                .testTag("tv-playback-sync-list"),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(pendingSync, key = { _, item -> item.episodeId }) { index, item ->
                                TvPlaybackSyncRow(
                                    item = item,
                                    onDelete = {
                                        onIntent(TvPlaybackHistoryIntent.DeletePending(item))
                                        pendingFocus = TvPlaybackHistoryFocus.SyncList
                                    },
                                    modifier = Modifier.testTag("tv-playback-sync-item-${item.episodeId}")
                                        .then(
                                            if (index == 0) {
                                                Modifier.onPreviewKeyEvent { event ->
                                                    if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown) {
                                                        focus.request(TvPlaybackHistoryFocus.SyncChips)
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }

                else -> {
                    if (selectionMode) {
                        TvPlaybackHistorySelectionChips(
                            allSelected = allSelected,
                            selectedCount = selectedEpisodeIds.size,
                            focus = focus,
                            onToggleAll = {
                                selectedEpisodeIds =
                                    if (allSelected) emptySet() else histories.mapTo(mutableSetOf()) { it.episodeId }
                            },
                            onDelete = { showDeleteDialog = true },
                            onExit = {
                                selectionMode = false
                                selectedEpisodeIds = emptySet()
                                focus.request(TvPlaybackHistoryFocus.List)
                            },
                        )
                    } else {
                        TvPlaybackHistoryChips(
                            pendingSync = pendingSync,
                            hasHistories = histories.isNotEmpty(),
                            syncing = state.syncing,
                            focus = focus,
                            onOpenSyncPanel = ::openSyncPanel,
                            onRefresh = { onIntent(TvPlaybackHistoryIntent.Sync) },
                            onSelect = {
                                selectionMode = true
                                focus.request(TvPlaybackHistoryFocus.List)
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (histories.isEmpty()) {
                        TvPlaybackEmptyText(
                            stringResource(Lang.playback_history_empty),
                            Modifier.weight(1f).testTag("tv-playback-history-empty"),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .tvFocusAnchor(focus, TvPlaybackHistoryFocus.List)
                                .focusGroup()
                                .testTag("tv-playback-history-list"),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(histories, key = { _, item -> item.episodeId }) { index, item ->
                                TvPlaybackHistoryRow(
                                    item = item,
                                    selectionMode = selectionMode,
                                    selected = item.episodeId in selectedEpisodeIds,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedEpisodeIds = selectedEpisodeIds.toggle(item.episodeId)
                                        } else {
                                            item.subjectId?.let {
                                                onIntent(
                                                    TvPlaybackHistoryIntent.OpenEpisode(it, item.episodeId),
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .tvFocusMemorable("playback-history-${item.episodeId}")
                                        .testTag("tv-playback-history-item-${item.episodeId}")
                                        .then(
                                            if (index == 0) {
                                                Modifier.onPreviewKeyEvent { event ->
                                                    if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown) {
                                                        focus.request(TvPlaybackHistoryFocus.Chips)
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            TvPlaybackHistoryDeleteDialog(
                onDismiss = {
                    showDeleteDialog = false
                    focus.request(TvPlaybackHistoryFocus.Chips)
                },
                onConfirm = {
                    onIntent(TvPlaybackHistoryIntent.Delete(selectedEpisodeIds))
                    showDeleteDialog = false
                    selectionMode = false
                    selectedEpisodeIds = emptySet()
                    pendingFocus = TvPlaybackHistoryFocus.List
                },
            )
        }
    }
}

@Composable
private fun TvPlaybackHistoryChips(
    pendingSync: List<TvPlaybackSyncItem>,
    hasHistories: Boolean,
    syncing: Boolean,
    focus: TvFocusScope,
    onOpenSyncPanel: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: () -> Unit,
) {
    val hasPending = pendingSync.isNotEmpty()
    TvOptionsRow(
        modifier = Modifier
            .tvFocusAnchor(focus, TvPlaybackHistoryFocus.Chips)
            .focusGroup()
            .testTag("tv-playback-history-chips"),
    ) {
        TvOptionChip(
            label = if (hasPending) {
                stringResource(Lang.playback_history_sync_status_pending, pendingSync.size)
            } else {
                stringResource(Lang.playback_history_sync_status_synced)
            },
            icon = if (hasPending) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDone,
            active = hasPending,
            onClick = onOpenSyncPanel,
            modifier = Modifier.testTag("tv-playback-history-sync"),
        )
        TvOptionChip(
            label = stringResource(Lang.tv_playback_history_refresh),
            icon = Icons.Rounded.Refresh,
            active = syncing,
            onClick = onRefresh,
            modifier = Modifier.testTag("tv-playback-history-refresh"),
        )
        if (hasHistories) {
            TvOptionChip(
                label = stringResource(Lang.tv_playback_history_select),
                icon = Icons.Default.Checklist,
                active = false,
                onClick = onSelect,
                modifier = Modifier.testTag("tv-playback-history-select"),
            )
        }
    }
}

@Composable
private fun TvPlaybackHistorySelectionChips(
    allSelected: Boolean,
    selectedCount: Int,
    focus: TvFocusScope,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    onExit: () -> Unit,
) {
    TvOptionsRow(
        modifier = Modifier
            .tvFocusAnchor(focus, TvPlaybackHistoryFocus.Chips)
            .focusGroup()
            .testTag("tv-playback-history-selection-chips"),
    ) {
        TvOptionChip(
            label = stringResource(Lang.playback_history_select_all),
            icon = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
            active = allSelected,
            onClick = onToggleAll,
            modifier = Modifier.testTag("tv-playback-history-select-all"),
        )
        if (selectedCount > 0) {
            TvOptionChip(
                label = stringResource(Lang.cache_subject_delete),
                icon = Icons.Rounded.Delete,
                active = false,
                onClick = onDelete,
                modifier = Modifier.testTag("tv-playback-history-delete"),
            )
        }
        TvOptionChip(
            label = stringResource(Lang.playback_history_exit_selection),
            icon = Icons.Rounded.Close,
            active = false,
            onClick = onExit,
            modifier = Modifier.testTag("tv-playback-history-exit-selection"),
        )
    }
}

@Composable
private fun TvPlaybackSyncChips(
    hasPending: Boolean,
    focus: TvFocusScope,
    onBack: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    TvOptionsRow(
        modifier = Modifier
            .tvFocusAnchor(focus, TvPlaybackHistoryFocus.SyncChips)
            .focusGroup()
            .testTag("tv-playback-sync-chips"),
    ) {
        TvOptionChip(
            label = stringResource(Lang.tv_playback_history_back),
            icon = Icons.Rounded.Close,
            active = false,
            onClick = onBack,
            modifier = Modifier.testTag("tv-playback-sync-back"),
        )
        if (hasPending) {
            TvOptionChip(
                label = stringResource(Lang.playback_history_sync_delete_all),
                icon = Icons.Rounded.Delete,
                active = false,
                onClick = onDeleteAll,
                modifier = Modifier.testTag("tv-playback-sync-delete-all"),
            )
        }
    }
}

@Composable
private fun TvPlaybackEmptyText(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TvPlaybackHistoryRow(
    item: PlaybackHistoryUiItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val displayName = item.subjectName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_unknown_subject)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        colors = tvOptionSurfaceColors(selected = selected, focused = focused),
        shape = ClickableSurfaceDefaults.shape(TvOptionDefaults.ItemShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvPlaybackHistoryCover(
                imageUrl = item.subjectImageUrl,
                contentDescription = stringResource(Lang.playback_history_cover, displayName),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    episodeText(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = .75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val metaColor = LocalContentColor.current.copy(alpha = .6f)
                    Text(
                        formatDateTime(item.updatedAtMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = metaColor,
                        maxLines = 1,
                    )
                    Text(
                        progressText(item),
                        style = MaterialTheme.typography.labelMedium,
                        color = metaColor,
                        maxLines = 1,
                    )
                }
                val progress = item.durationMillis?.takeIf { it > 0L }
                    ?.let { (item.positionMillis.toFloat() / it).coerceIn(0f, 1f) }
                if (progress != null) {
                    Box(
                        Modifier.fillMaxWidth().height(3.dp)
                            .clip(CircleShape)
                            .background(LocalContentColor.current.copy(alpha = .2f)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(progress).fillMaxHeight()
                                .background(LocalContentColor.current.copy(alpha = .6f)),
                        )
                    }
                }
            }
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun TvPlaybackHistoryCover(imageUrl: String?, contentDescription: String) {
    Box(
        Modifier.width(56.dp).height(76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                Icons.Rounded.History,
                contentDescription,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                imageUrl,
                contentDescription,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun TvPlaybackSyncRow(
    item: TvPlaybackSyncItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = item.subjectName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_sync_pending_episode, item.episodeId)
    val episodeName = item.episodeName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_unknown_episode)
    val operationNames = item.operations.map { stringResource(it.labelRes()) }
    val dateText = formatDateTime(item.versionMillis)
    val supportingText = buildList {
        addAll(operationNames)
        add(episodeName)
        if (dateText.isNotEmpty()) add(dateText)
    }.joinToString(" · ")
    TvOptionRow(
        title = title,
        supportingText = supportingText,
        trailingIcon = Icons.Rounded.Delete,
        modifier = modifier.fillMaxWidth(),
        onClick = onDelete,
    )
}

@Composable
private fun TvPlaybackHistoryDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvPlaybackHistoryDialogFocus.Cancel)
    TvModalOverlay(
        onClose = onDismiss,
        modifier = Modifier.tvFocusNavSignal(focus),
        background = {},
    ) {
        TvOptionModal(
            title = stringResource(Lang.playback_history_delete_title),
            subtitle = stringResource(Lang.playback_history_delete_confirmation),
            modifier = Modifier.testTag("tv-playback-history-delete-dialog"),
            width = 460.dp,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvOptionRow(
                    title = stringResource(Lang.cache_subject_cancel),
                    modifier = Modifier.weight(1f)
                        .testTag("tv-playback-history-delete-cancel")
                        .tvFocusAnchor(focus, TvPlaybackHistoryDialogFocus.Cancel),
                    onClick = onDismiss,
                )
                TvOptionRow(
                    title = stringResource(Lang.cache_subject_delete),
                    modifier = Modifier.weight(1f).testTag("tv-playback-history-delete-confirm"),
                    onClick = onConfirm,
                )
            }
        }
    }
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
    val position = formatPlaybackTime(item.positionMillis)
    val duration = item.durationMillis?.takeIf { it > 0L }?.let(::formatPlaybackTime)
        ?: stringResource(Lang.playback_history_progress_unknown_duration)
    return "$position / $duration"
}

private fun TvPlaybackSyncOperation.labelRes(): StringResource = when (this) {
    TvPlaybackSyncOperation.ProgressUpsert -> Lang.playback_history_sync_op_upsert
    TvPlaybackSyncOperation.ProgressDelete -> Lang.playback_history_sync_op_delete
    TvPlaybackSyncOperation.MarkWatched -> Lang.playback_history_sync_op_mark_watched
    TvPlaybackSyncOperation.UnmarkWatched -> Lang.playback_history_sync_op_unmark_watched
}

private fun Float.formatEpisodeSort(): String {
    return if (this == floor(this)) this.toInt().toString() else toString()
}

private fun Set<Int>.toggle(id: Int): Set<Int> {
    return if (id in this) this - id else this + id
}
