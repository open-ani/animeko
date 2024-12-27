/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.paging.map
import me.him188.ani.datasources.api.paging.merge
import me.him188.ani.datasources.api.topic.Alliance
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.Topic
import me.him188.ani.datasources.api.topic.TopicCategory

/**
 * A [remote][MediaSourceLocation.Online] [MediaSource] that uses [Topic] internally.
 */
abstract class TopicMediaSource : HttpMediaSource() {
    override val location: MediaSourceLocation get() = MediaSourceLocation.Online
    override val kind: MediaSourceKind get() = MediaSourceKind.BitTorrent

    // For backward compatibility
    protected abstract suspend fun startSearch(query: DownloadSearchQuery): SizedSource<Topic>

    final override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        return query.subjectNames
            .map { name ->
                startSearch(
                    DownloadSearchQuery(
                        keywords = name,
                        category = TopicCategory.ANIME,
                        episodeSort = query.episodeSort,
                        episodeEp = query.episodeEp,
                    ),
                ).map {
                    MediaMatch(it.toOnlineMedia(mediaSourceId), MatchKind.FUZZY)
                }
            }.merge()
    }
}

// Only used by data source modules
data class DownloadSearchQuery(
    val keywords: String,
    val category: TopicCategory? = null,
    val alliance: Alliance? = null,
    val ordering: SearchOrdering? = null,
    val episodeSort: EpisodeSort? = null,
    val episodeEp: EpisodeSort? = null,
    /**
     * 覆盖设置, 直接返回所有搜索结果, 不要使用 [episodeSort] 等匹配
     */
    val allowAny: Boolean = false,
)

interface SearchOrdering {
    val id: String
    val name: String
}

fun Topic.toOnlineMedia(mediaSourceId: String): DefaultMedia {
    val details = details
    return DefaultMedia(
        mediaId = "$mediaSourceId.${topicId}",
        mediaSourceId = mediaSourceId,
        originalUrl = originalLink,
        download = downloadLink,
        originalTitle = rawTitle,
        publishedTime = publishedTimeMillis ?: 0,
        episodeRange = details?.episodeRange,
        properties = MediaProperties(
            subjectName = null, // L1 can't (precisely) parse this
            episodeName = null, // Usually does not exist in torrent titles
            subtitleLanguageIds = details?.subtitleLanguages?.map { it.id } ?: emptyList(),
            resolution = details?.resolution?.toString() ?: Resolution.R1080P.toString(),
            alliance = alliance,
            size = size,
            subtitleKind = details?.subtitleKind,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    )
}
