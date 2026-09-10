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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.DownloadSelectionState

@Composable
fun SubjectDownloadsDetailPane(
    subjectId: Int,
    selectionState: DownloadSelectionState,
    onPlay: (DownloadItem) -> Unit,
    onViewDetail: ((DownloadItem) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    singlePane: Boolean = false,
) {
    SubjectDownloadsFeature(subjectId) { state, actions, sourceInfo ->
        SubjectDownloadsContent(
            state = state,
            selection = selectionState,
            actions = actions,
            sourceInfoProvider = sourceInfo,
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
}
