/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheEntity
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.time.Duration

/**
 * Web 源搜索结果的播放 session 级缓存.
 *
 * 由 `SelectorMediaSource` 在真实搜索成功后写入 ([addCache]), 并在下次搜索前读取 ([getCache]):
 * 缓存的条目页面剧集列表包含请求的剧集时 (典型场景: 切集), 无需发起网络请求.
 *
 * ## 有效期
 *
 * 每行的 TTL 在写入时确定, 取数据源配置 (`SelectorSearchConfig.searchCacheTtl`) 与
 * 用户设置 (`MediaSelectorSettings.webSearchCacheTtl`) 中的较小者. 过期行不会被读取.
 *
 * 清理时机:
 * - 进入播放页: [purgeExpired] 清除所有已过期的行 (短暂退出后重进可复用未过期的缓存);
 * - 退出播放页: [scheduleExitCleanup] 在用户设置的 TTL 之后清除该条目的已过期行;
 * - 手动重新查询: [clearAll] 立即清空全部.
 */
class SelectorMediaSourceEpisodeCacheRepository(
    private val dao: WebSearchSessionCacheDao,
    /**
     * 用户设置的缓存有效期, 通常来自 `MediaSelectorSettings.webSearchCacheTtl`.
     */
    private val userTtlFlow: Flow<Duration>,
) : Repository() {

    private suspend fun userTtl(): Duration = userTtlFlow.first()

    suspend fun addCache(
        requesterSubjectId: Int?,
        mediaSourceId: String,
        subjectName: String,
        subjectInfo: WebSearchSubjectInfo,
        episodeInfos: List<WebSearchEpisodeInfo>,
        sourceCacheTtl: Duration,
    ) = withContext(defaultDispatcher) {
        val ttl = minOf(sourceCacheTtl, userTtl())
        if (ttl <= Duration.ZERO) {
            // 缓存被禁用. 同时删除该页面可能残留的旧行 (例如用户刚把 TTL 改为 0).
            dao.deletePage(mediaSourceId, subjectName, subjectInfo.fullUrl)
            return@withContext
        }
        val now = currentTimeMillis()
        dao.replacePage(
            mediaSourceId, subjectName, subjectInfo.fullUrl,
            episodeInfos.map {
                it.toEntity(
                    requesterSubjectId, mediaSourceId, subjectName, subjectInfo,
                    cachedAt = now,
                    expiresAt = now + ttl.inWholeMilliseconds,
                )
            },
        )
    }

    suspend fun clearByRequestedSubject(requesterSubjectId: Int) = withContext(defaultDispatcher) {
        dao.deleteByRequestedSubject(requesterSubjectId)
    }

    /**
     * 清除所有已过期的行. 在进入播放页时调用.
     */
    suspend fun purgeExpired() = withContext(defaultDispatcher) {
        dao.deleteExpired(currentTimeMillis())
    }

    /**
     * 返回该查询名下缓存的所有未过期的条目页面, 每个页面附带其全部剧集 (保持页面上的顺序).
     */
    suspend fun getCache(mediaSourceId: String, subjectName: String): List<WebSearchCache> =
        withContext(defaultDispatcher) {
            dao.filterBySubjectName(mediaSourceId, subjectName, currentTimeMillis())
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
    requesterSubjectId: Int?,
    mediaSourceId: String,
    subjectName: String,
    subjectInfo: WebSearchSubjectInfo,
    cachedAt: Long,
    expiresAt: Long,
): WebSearchSessionCacheEntity {
    return WebSearchSessionCacheEntity(
        requesterSubjectId = requesterSubjectId,
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
        cachedAt = cachedAt,
        expiresAt = expiresAt,
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
