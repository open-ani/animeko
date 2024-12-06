/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoEntity
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoEntity
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo


class SelectorMediaSourceEpisodeCacheRepository(
    private val webSubjectInfoDao: WebSearchSubjectInfoDao,
    private val webEpisodeInfoDao: WebSearchEpisodeInfoDao,
) : Repository() {
    suspend fun addCache(
        mediaSourceId: String,
        subjectName: String,
        subjectInfo: WebSearchSubjectInfo,
        episodeInfos: List<WebSearchEpisodeInfo>
    ) {
        val subjectInfoEntity = subjectInfo.toEntity(mediaSourceId, subjectName)
        webSubjectInfoDao.upsert(subjectInfoEntity)
        webEpisodeInfoDao.upsert(episodeInfos.map { it.toEntity(mediaSourceId, subjectName) })
    }

    suspend fun clearSubjectAndEpisodeCache() {
        webSubjectInfoDao.deleteAll()
        webEpisodeInfoDao.resetAutoIncrement()
    }

    suspend fun getCache(mediaSourceId: String, subjectName: String): List<WebSearchCache> {
        val webSearchSubjectInfoAndEpisodes =
            webSubjectInfoDao.filterByMediaSourceIdAndSubjectName(mediaSourceId, subjectName)
        return webSearchSubjectInfoAndEpisodes.map { info ->
            WebSearchCache(
                webSubjectInfo = info.webSubjectInfo.toWebSearchSubjectInfo(),
                webEpisodeInfos = info.webEpisodeInfos.map { it.toWebSearchEpisodeInfo() },
            )
        }
    }
}

data class WebSearchCache(
    val webSubjectInfo: WebSearchSubjectInfo,
    val webEpisodeInfos: List<WebSearchEpisodeInfo>
)


fun WebSearchSubjectInfo.toEntity(mediaSourceId: String, subjectName: String): WebSearchSubjectInfoEntity {
    return WebSearchSubjectInfoEntity(
        mediaSourceId = mediaSourceId,
        subjectName = subjectName,
        internalId = internalId,
        name = name,
        fullUrl = fullUrl,
        partialUrl = partialUrl,
    )
}

fun WebSearchSubjectInfoEntity.toWebSearchSubjectInfo(): WebSearchSubjectInfo {
    return WebSearchSubjectInfo(
        internalId = internalId,
        name = name,
        fullUrl = fullUrl,
        partialUrl = partialUrl,
        origin = null,
    )
}

fun WebSearchEpisodeInfo.toEntity(mediaSourceId: String, subjectName: String): WebSearchEpisodeInfoEntity {
    return WebSearchEpisodeInfoEntity(
        channel = channel,
        name = name,
        episodeSortOrEp = episodeSortOrEp,
        playUrl = playUrl,
        mediaSourceId = mediaSourceId,
        subjectName = subjectName,
    )
}

fun WebSearchEpisodeInfoEntity.toWebSearchEpisodeInfo(): WebSearchEpisodeInfo {
    return WebSearchEpisodeInfo(
        channel = channel,
        name = name,
        episodeSortOrEp = episodeSortOrEp,
        playUrl = playUrl,
    )
}