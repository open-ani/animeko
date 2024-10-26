/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.network.BangumiEpisodeService
import me.him188.ani.app.data.network.toEpisodeInfo
import me.him188.ani.app.data.persistent.database.EpisodeDao
import me.him188.ani.app.data.persistent.database.EpisodeEntity
import me.him188.ani.datasources.api.EpisodeType.MainStory
import me.him188.ani.datasources.bangumi.models.BangumiEpType

class EpisodeRepository(
    private val bangumiEpisodeService: BangumiEpisodeService,
    private val episodeDao: EpisodeDao,
    private val enableAllEpisodeTypes: Flow<Boolean>,
) {
    fun episodeInfoFlow(subjectId: Int, episodeId: Int): Flow<EpisodeInfo> {
        return episodeDao.findByEpisodeId(episodeId).map { entity ->
            entity?.toEpisodeInfo()
                ?: kotlin.run {
                    bangumiEpisodeService.getEpisodeById(episodeId)?.toEpisodeInfo()?.also {
                        episodeDao.upsert(it.toEntity(subjectId))
                    } ?: throw NoSuchElementException("Episode $episodeId not found")
                }
        }
    }

    fun episodesFlow(subjectId: Int): Flow<List<EpisodeInfo>> {
        return episodeDao.filterBySubjectId(subjectId).mapLatest { episodes ->
            if (episodes.isEmpty()) {
                bangumiEpisodeService.getEpisodesBySubjectId(
                    subjectId,
                    type = if (enableAllEpisodeTypes.first()) null else BangumiEpType.MainStory,
                )
                    .map {
                        it.toEntity(subjectId)
                    }
                    .map { entity ->
                        episodeDao.upsert(entity)
                        entity.toEpisodeInfo()
                    }
                    .toList() // 直接全部获取了, 因为多数时候剧集数量很少
            } else {
                episodes.map { it.toEpisodeInfo() }
            }
        }
    }
}

private fun EpisodeInfo.toEntity(subjectId: Int): EpisodeEntity {
    return EpisodeEntity(
        subjectId = subjectId,
        id = this.id,
        type = this.type,
        name = this.name,
        nameCn = this.nameCn,
        airDate = this.airDate,
        comment = this.comment,
        desc = this.desc,
        sort = this.sort,
        ep = this.ep,
    )
}

internal fun EpisodeEntity.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        id = this.id,
        type = this.type ?: MainStory,
        name = this.name,
        nameCn = this.nameCn,
        airDate = this.airDate,
        comment = this.comment,
        desc = this.desc,
        sort = this.sort,
        ep = this.ep,
    )
}