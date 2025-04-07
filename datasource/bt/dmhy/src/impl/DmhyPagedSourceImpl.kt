/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.dmhy.impl

import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.datasources.api.paging.AbstractPageBasedPagedSource
import me.him188.ani.datasources.api.paging.PagedSource
import me.him188.ani.datasources.api.source.DownloadSearchQuery
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.Topic
import me.him188.ani.datasources.api.topic.TopicCategory
import me.him188.ani.datasources.api.topic.guessTorrentFromUrl
import me.him188.ani.datasources.api.topic.matches
import me.him188.ani.datasources.api.topic.titles.toTopicDetails
import me.him188.ani.datasources.dmhy.impl.protocol.Network

class DmhyPagedSourceImpl(
    private val query: DownloadSearchQuery,
    private val network: Network,
) : PagedSource<Topic>, AbstractPageBasedPagedSource<Topic>() {
    override val currentPage: MutableStateFlow<Int> = MutableStateFlow(1)

    override suspend fun nextPageImpl(page: Int): List<Topic> {
        val (_, rawResults) = network.list(
            page = page,
            keyword = query.keywords,
            sortId = getCategoryId(),
            teamId = query.alliance?.id,
            orderId = query.ordering?.id,
        )
        val results = rawResults.mapNotNull { topic ->
            Topic(
                topicId = topic.id,
                publishedTimeMillis = topic.publishedTimeMillis,
                category = TopicCategory.ANIME,
                rawTitle = topic.rawTitle,
                commentsCount = topic.commentsCount,
                downloadLink = ResourceLocation.guessTorrentFromUrl(topic.magnetLink)
                    ?: return@mapNotNull null,
                size = topic.size,
                alliance = topic.alliance?.name ?: topic.rawTitle.substringBeforeLast(']').substringAfterLast('['),
                author = topic.author,
                details = topic.details?.toTopicDetails(),
                originalLink = topic.link,
            )
        }.filter {
            query.matches(it, allowEpMatch = false)
        }
        if (results.none()) {
            noMorePages()
            return emptyList()
        }
        return results
    }

    private fun getCategoryId(): String? {
        return when (query.category) {
            TopicCategory.ANIME -> "2"
            null -> null
        }
    }
}