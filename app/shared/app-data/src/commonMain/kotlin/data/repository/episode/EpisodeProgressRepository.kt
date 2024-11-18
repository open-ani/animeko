/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.episode.EpisodeProgressInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import kotlin.coroutines.CoroutineContext

class EpisodeProgressRepository(
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val cacheManager: MediaCacheManager,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository {
    fun subjectEpisodeProgressesInfoFlow(subjectId: Int): Flow<List<EpisodeProgressInfo>> {
        return episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId).flatMapLatest { list ->
            if (list.isEmpty()) {
                return@flatMapLatest flowOfEmptyList()
            }
            combine(
                list.map { info ->
                    cacheManager.cacheStatusForEpisode(subjectId, episodeId = info.episodeInfo.episodeId)
                        .map { cache ->
                            EpisodeProgressInfo(info.episodeInfo, info.collectionType, cache)
                        }
                },
            ) {
                it.toList()
            }
        }.flowOn(defaultDispatcher)
    }
}