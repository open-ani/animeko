/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.subject

import androidx.compose.runtime.Stable
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.episode.EpisodeProgressInfo
import me.him188.ani.app.data.repository.EpisodeCollectionRepository
import me.him188.ani.app.domain.media.cache.MediaCacheManager

class SubjectProgressManager(
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val cacheManager: MediaCacheManager,
) {
    @Stable
    fun subjectProgressPager(
        subjectId: Int,
        pagingConfig: PagingConfig = PagingConfig(
            pageSize = 20,
        ),
    ): Flow<PagingData<EpisodeProgressInfo>> {
//        val episodeInfos = episodeRepository.getEpisodes(subjectId)
//        val episodeCollections =
//            episodeCollectionRepository.episodeCollectionsFlow(subjectId, pagingConfig = pagingConfig)
//
//        episodes.map { episode ->
//            cacheManager.cacheStatusForEpisode(
//                subjectId = subjectId,
//                episodeId = episode.episode.id,
//            ).onStart {
//                emit(EpisodeCacheStatus.NotCached)
//            }.map { cacheStatus ->
//                EpisodeProgressInfo(
//                    episode.episode,
//                    episode.collectionType,
//                    cacheStatus = cacheStatus,
//                )
//            }
//        },
//        combine()
        TODO()
    }
}
