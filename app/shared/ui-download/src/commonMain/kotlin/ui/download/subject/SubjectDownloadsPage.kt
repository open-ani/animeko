/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.download.DeleteActionDialog
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.DownloadSelectionFloatingToolbar
import me.him188.ani.app.ui.download.components.DownloadSelectionState
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_management_deselect_all
import me.him188.ani.app.ui.lang.cache_management_exit_selection
import me.him188.ani.app.ui.lang.cache_management_select_all_action
import me.him188.ani.app.ui.lang.cache_management_selected_count
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubjectDownloadsPage(
    state: SubjectDownloadsUiState,
    selection: DownloadSelectionState,
    actions: SubjectDownloadActions,
    sourceInfoProvider: MediaSourceInfoProvider,
    onPlay: (DownloadItem) -> Unit,
    onViewDetail: ((DownloadItem) -> Unit)?,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    val selected = state.downloads.filter { it.id in selection.selectedIds }
    LaunchedEffect(state.downloads, state.downloadsLoading) {
        if (!state.downloadsLoading && !state.downloadsFailed && selection.inSelection) {
            val validIds = state.downloads.mapTo(hashSetOf()) { it.id }
            val remaining = selection.selectedIds.intersect(validIds)
            if (remaining.isEmpty() && selection.selectedIds.isNotEmpty()) selection.clear()
            else selection.overrideSelected(remaining)
        }
    }
    BackHandler(selection.inSelection) { selection.clear() }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>?>(null) }
    pendingDeleteIds?.let { ids ->
        DeleteActionDialog(
            onDismiss = { pendingDeleteIds = null },
            onConfirm = {
                actions.delete(ids)
                pendingDeleteIds = null
            },
        )
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            AniTopAppBar(
                title = {
                    Text(
                        if (selection.inSelection) stringResource(Lang.cache_management_selected_count, selected.size)
                        else state.title.orEmpty(),
                    )
                },
                navigationIcon = {
                    if (selection.inSelection) {
                        IconButton(onClick = { selection.clear() }) { Icon(Icons.Rounded.Close, stringResource(Lang.cache_management_exit_selection)) }
                    } else navigationIcon()
                },
                actions = {
                    if (selection.inSelection) {
                        val allSelected = state.downloads.isNotEmpty() && selected.size == state.downloads.size
                        TextButton(onClick = {
                            selection.overrideSelected(
                                if (allSelected) emptySet() else state.downloads.mapTo(hashSetOf()) { it.id },
                            )
                        }) {
                            Text(stringResource(if (allSelected) Lang.cache_management_deselect_all else Lang.cache_management_select_all_action))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selection.inSelection) {
                DownloadSelectionFloatingToolbar(
                    resumeEnabled = selected.any { it.isPaused && !it.isFinished },
                    pauseEnabled = selected.any { !it.isPaused && !it.isFinished && !it.isFailed },
                    deleteEnabled = selected.isNotEmpty(),
                    onResumeSelected = { actions.resume(selected.filter { it.isPaused && !it.isFinished }.mapTo(hashSetOf()) { it.id }) },
                    onPauseSelected = { actions.pause(selected.filter { !it.isPaused && !it.isFinished && !it.isFailed }.mapTo(hashSetOf()) { it.id }) },
                    onDeleteSelected = { pendingDeleteIds = selected.mapTo(hashSetOf()) { it.id } },
                    windowInsets = windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().wrapContentWidth().widthIn(max = 1300.dp)) {
            SubjectDownloadsSummaryRow(
                downloads = state.downloads,
                totalEpisodeCount = state.totalEpisodes,
                inSelection = selection.inSelection,
                selectedEntries = selected,
                onPauseAll = actions.pauseAll,
                onResumeAll = actions.resumeAll,
            )
            SubjectDownloadsContent(
                state = state,
                selection = selection,
                actions = actions,
                sourceInfoProvider = sourceInfoProvider,
                onPlay = onPlay,
                onViewDetail = onViewDetail,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
