/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 数据源在一次查询中向调用方报告的搜索明细. [MediaFetcher] 把它放进收集 `MediaSource.fetch` 结果的协程上下文,
 * 关键词链 (`keywordSearchChain`, 在线站点类数据源共用) 在每个关键词搜索完成后调用 [onSearch].
 *
 * 数据源不依赖它: 上下文中没有本元素时什么都不发生.
 */
class MediaSourceFetchTelemetry(
    val onSearch: (keyword: String, cacheHit: Boolean) -> Unit,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<MediaSourceFetchTelemetry>
}

/**
 * 一个数据源一代查询内的搜索统计.
 *
 * @property cacheHits 命中搜索缓存、没有发网络请求的关键词数.
 * @property onlineSearches 向站点发起搜索的关键词数.
 */
data class MediaSourceSearchStats(
    val cacheHits: Int = 0,
    val onlineSearches: Int = 0,
) {
    /**
     * 本代查询完全由搜索缓存满足.
     */
    val isCacheOnly: Boolean get() = cacheHits > 0 && onlineSearches == 0
}

internal val NoSearchStats: StateFlow<MediaSourceSearchStats?> = MutableStateFlow(null)
