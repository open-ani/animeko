/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import androidx.compose.runtime.Immutable
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@Immutable
data class EpisodeDownloadItem(
    val episodeId: Int,
    val sort: EpisodeSort,
    val title: String,
    val watchStatus: UnifiedCollectionType,
    val hasPublished: Boolean,
)

sealed interface SubjectDownloadListItem {
    val key: String

    data class Episode(val episode: EpisodeDownloadItem) : SubjectDownloadListItem {
        override val key = "episode-${episode.episodeId}"
    }

    data class Download(val download: DownloadItem) : SubjectDownloadListItem {
        override val key = "download-${download.id}"
    }
}

@Immutable
data class DownloadRequestUiState(
    val episodeId: Int? = null,
    val busy: Boolean = false,
    val canCancel: Boolean = false,
)

@Immutable
data class SubjectDownloadsUiState(
    val title: String? = null,
    val items: List<SubjectDownloadListItem> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    val totalEpisodes: Int? = null,
    val episodesLoading: Boolean = true,
    val downloadsLoading: Boolean = true,
    val episodesFailed: Boolean = false,
    val downloadsFailed: Boolean = false,
    val failedOperationCount: Int = 0,
    val request: DownloadRequestUiState = DownloadRequestUiState(),
)
