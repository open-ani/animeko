/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

class DmhyPagedSourceImpl(
    private val query: DownloadSearchQuery,
    private val network: Network,
) : PagedSource<Topic>, AbstractPageBasedPagedSource<Topic>() {
    override val currentPage: MutableStateFlow<Int> = MutableStateFlow(1)

    private val keyword = toDmhyKeyword(query.keywords)
    private var fetchedCount = 0

    override suspend fun nextPageImpl(page: Int): List<Topic> {
        if (keyword.isBlank()) {
            // 空关键词等于列出全站资源
            noMorePages()
            return emptyList()
        }
        val (_, rawResults) = network.list(
            page = page,
            keyword = keyword,
            sortId = getCategoryId(),
            teamId = query.alliance?.id,
            orderId = query.ordering?.id,
        )
        fetchedCount += rawResults.size
        if (fetchedCount >= MAX_SEARCH_RESULTS) {
            logger.info { "Search '$keyword' reached $fetchedCount results at page $page, stop paging" }
            noMorePages()
        }
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
            query.allowAny || query.matches(it, allowEpMatch = false)
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

    private companion object {
        private val logger = logger<DmhyPagedSourceImpl>()

        /**
         * 動漫花園的关键词搜索最多返回 1000 条. 关键词被站点忽略时会返回全站资源 (五千多页), 拿满这个数量就停止翻页.
         */
        const val MAX_SEARCH_RESULTS = 1000
    }
}

/**
 * 動漫花園会丢弃含 ASCII 冒号的词 (例如 `Re:ゼロ`, `ID:INVADED`). 关键词只剩这种词时, 站点按无关键词处理, 返回全站资源.
 * 冒号换成空格后按两个词搜索, 结果与使用全角冒号相同.
 */
private fun toDmhyKeyword(keywords: String): String = keywords.replace(':', ' ').trim()
