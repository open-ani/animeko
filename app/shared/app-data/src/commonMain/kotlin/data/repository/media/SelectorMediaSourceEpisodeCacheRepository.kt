/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import kotlinx.coroutines.withContext
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheEntity
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo

/**
 * Web 源搜索结果的播放 session 级缓存.
 *
 * 由 `SelectorMediaSource` 在真实搜索成功后写入 ([addCache]), 并在下次搜索前读取 ([getCache]):
 * 缓存的条目页面剧集列表包含请求的剧集时 (典型场景: 切集), 无需发起网络请求.
 *
 * 缓存仅在当前播放 session 内有效: 进入/退出播放页与手动重新查询时调用 [clearAll] 全部清空.
 */
class SelectorMediaSourceEpisodeCacheRepository(
    private val dao: WebSearchSessionCacheDao,
) : Repository() {
    suspend fun addCache(
        mediaSourceId: String,
        subjectName: String,
        subjectInfo: WebSearchSubjectInfo,
        episodeInfos: List<WebSearchEpisodeInfo>,
    ) = withContext(defaultDispatcher) {
        dao.replacePage(
            mediaSourceId, subjectName, subjectInfo.fullUrl,
            episodeInfos.map { it.toEntity(mediaSourceId, subjectName, subjectInfo) },
        )
    }

    suspend fun clearAll() = withContext(defaultDispatcher) {
        dao.deleteAll()
    }

    /**
     * 返回该查询名下缓存的所有条目页面, 每个页面附带其全部剧集 (保持页面上的顺序).
     */
    suspend fun getCache(mediaSourceId: String, subjectName: String): List<WebSearchCache> =
        withContext(defaultDispatcher) {
            dao.filterBySubjectName(mediaSourceId, subjectName)
                .groupBy { it.subjectUrl } // preserves encounter (insertion) order
                .map { (_, rows) ->
                    val first = rows.first()
                    WebSearchCache(
                        webSubjectInfo = WebSearchSubjectInfo(
                            internalId = first.subjectInternalId,
                            name = first.subjectPageName,
                            fullUrl = first.subjectUrl,
                            partialUrl = first.subjectPartialUrl,
                            origin = null,
                        ),
                        webEpisodeInfos = rows.map { it.toWebSearchEpisodeInfo() },
                    )
                }
        }
}

data class WebSearchCache(
    val webSubjectInfo: WebSearchSubjectInfo,
    val webEpisodeInfos: List<WebSearchEpisodeInfo>,
)

private fun WebSearchEpisodeInfo.toEntity(
    mediaSourceId: String,
    subjectName: String,
    subjectInfo: WebSearchSubjectInfo,
): WebSearchSessionCacheEntity {
    return WebSearchSessionCacheEntity(
        mediaSourceId = mediaSourceId,
        subjectName = subjectName,
        subjectPageName = subjectInfo.name,
        subjectInternalId = subjectInfo.internalId,
        subjectUrl = subjectInfo.fullUrl,
        subjectPartialUrl = subjectInfo.partialUrl,
        channel = channel.orEmpty(),
        episodeName = name,
        episodeSortOrEp = episodeSortOrEp,
        playUrl = playUrl,
    )
}

private fun WebSearchSessionCacheEntity.toWebSearchEpisodeInfo(): WebSearchEpisodeInfo {
    return WebSearchEpisodeInfo(
        channel = channel.ifEmpty { null },
        name = episodeName,
        episodeSortOrEp = episodeSortOrEp,
        playUrl = playUrl,
    )
}
