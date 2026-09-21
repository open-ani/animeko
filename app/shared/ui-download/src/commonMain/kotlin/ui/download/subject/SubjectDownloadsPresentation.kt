/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.ui.download.components.DownloadItem

internal fun SubjectCollectionInfo.downloadEpisodes(): List<EpisodeDownloadItem> = episodes.map {
    EpisodeDownloadItem(it.episodeId, it.episodeInfo.sort, it.episodeInfo.displayName, it.collectionType, it.episodeInfo.isKnownCompleted(recurrence))
}

/**
 * 把条目的剧集与下载合并为页面列表项, 按剧集序号排序: 有下载的剧集显示其全部下载, 其余剧集显示可发起下载的剧集行.
 * 不属于 [episodes] 中任何剧集的下载 (剧集信息缺失或仍在加载) 也保留在列表中.
 */
fun buildSubjectDownloadItems(
    episodes: List<EpisodeDownloadItem>,
    downloads: List<DownloadItem>,
): List<SubjectDownloadListItem> {
    val byEpisode = downloads.distinctBy { it.id }.groupBy { it.episodeId }
    val episodeIds = episodes.mapTo(hashSetOf()) { it.episodeId }
    val items = episodes.distinctBy { it.episodeId }.map { episode ->
        byEpisode[episode.episodeId]?.map { SubjectDownloadListItem.Download(it) }
            ?: listOf(SubjectDownloadListItem.Episode(episode))
    }.flatten() + byEpisode.filterKeys { it !in episodeIds }.values.flatten().map { SubjectDownloadListItem.Download(it) }
    return items.sortedBy {
        when (it) {
            is SubjectDownloadListItem.Episode -> it.episode.sort
            is SubjectDownloadListItem.Download -> it.download.sort
        }
    }
}
