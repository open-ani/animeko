/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.download.components.TestDownloadItems
import me.him188.ani.app.ui.download.components.rememberDownloadSelectionState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSourceInfoProvider
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

@Preview
@Composable
@OptIn(TestOnly::class)
private fun SubjectDownloadsPreview() = ProvideCompositionLocalsForPreview {
    val downloads = TestDownloadItems
    val episodes = listOf(EpisodeDownloadItem(5, EpisodeSort(5), "新的剧集", UnifiedCollectionType.DOING, true))
    SubjectDownloadsPage(
        state = SubjectDownloadsUiState(
            title = "孤独摇滚",
            items = buildSubjectDownloadItems(episodes, downloads),
            downloads = downloads,
            totalEpisodes = 12,
            episodesLoading = false,
            downloadsLoading = false,
        ),
        selection = rememberDownloadSelectionState(),
        actions = SubjectDownloadActions({}, {}, {}, {}, {}, {}, {}, {}),
        sourceInfoProvider = rememberTestMediaSourceInfoProvider(),
        onPlay = {},
        onViewDetail = null,
    )
}
