/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.DownloadSelectionState
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider

/**
 * [presenter] 为 `null` 时显示加载态, 与实例就绪后的首个状态一致; 全局页切换条目时用它填补实例创建前的那一帧.
 */
@Composable
fun SubjectDownloadsDetailPane(
    presenter: SubjectDownloadsPresenter?,
    selectionState: DownloadSelectionState,
    onPlay: (DownloadItem) -> Unit,
    onViewDetail: ((DownloadItem) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    singlePane: Boolean = false,
) {
    if (presenter == null) {
        SubjectDownloadsDetailPaneContent(
            state = SubjectDownloadsUiState(),
            actions = SubjectDownloadActions.None,
            sourceInfoProvider = remember { MediaSourceInfoProvider(getSourceInfoFlow = { flowOf(null) }) },
            selectionState = selectionState,
            onPlay = onPlay,
            onViewDetail = onViewDetail,
            modifier = modifier,
            contentPadding = contentPadding,
            singlePane = singlePane,
        )
    } else {
        SubjectDownloadsHost(presenter) { state, actions, sourceInfo ->
            SubjectDownloadsDetailPaneContent(
                state = state,
                actions = actions,
                sourceInfoProvider = sourceInfo,
                selectionState = selectionState,
                onPlay = onPlay,
                onViewDetail = onViewDetail,
                modifier = modifier,
                contentPadding = contentPadding,
                singlePane = singlePane,
            )
        }
    }
}

@Composable
private fun SubjectDownloadsDetailPaneContent(
    state: SubjectDownloadsUiState,
    actions: SubjectDownloadActions,
    sourceInfoProvider: MediaSourceInfoProvider,
    selectionState: DownloadSelectionState,
    onPlay: (DownloadItem) -> Unit,
    onViewDetail: ((DownloadItem) -> Unit)?,
    modifier: Modifier,
    contentPadding: PaddingValues,
    singlePane: Boolean,
) {
    SubjectDownloadsContent(
        state = state,
        selection = selectionState,
        actions = actions,
        sourceInfoProvider = sourceInfoProvider,
        onPlay = onPlay,
        onViewDetail = onViewDetail,
        modifier = modifier,
        rowShape = if (singlePane) RectangleShape else MaterialTheme.shapes.medium,
        contentPadding = contentPadding,
        header = {
            if (singlePane) {
                SubjectDownloadsSummaryRow(
                    downloads = state.downloads,
                    totalEpisodeCount = state.totalEpisodes,
                    inSelection = selectionState.inSelection,
                    selectedEntries = state.downloads.filter { it.id in selectionState.selectedIds },
                    onPauseAll = actions.pauseAll,
                    onResumeAll = actions.resumeAll,
                )
            } else {
                SubjectDownloadsHeader(
                    title = state.title,
                    downloads = state.downloads,
                    totalEpisodeCount = state.totalEpisodes,
                    onPauseAll = actions.pauseAll,
                    onResumeAll = actions.resumeAll,
                )
            }
        },
    )
}
