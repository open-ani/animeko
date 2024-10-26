/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.network.BangumiEpisodeService
import me.him188.ani.app.data.persistent.database.EpisodeCollectionDao
import me.him188.ani.app.data.persistent.database.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.EpisodeDao
import me.him188.ani.app.data.persistent.database.EpisodeEntity
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.EpisodeType.ED
import me.him188.ani.datasources.api.EpisodeType.MAD
import me.him188.ani.datasources.api.EpisodeType.MainStory
import me.him188.ani.datasources.api.EpisodeType.OP
import me.him188.ani.datasources.api.EpisodeType.PV
import me.him188.ani.datasources.api.EpisodeType.SP
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.datasources.bangumi.models.BangumiEpType
import me.him188.ani.datasources.bangumi.models.BangumiEpisode
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.serialization.BigNum
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

data class EpisodeCollectionInfo(
    val episodeId: Int,
    val type: UnifiedCollectionType,
    val episodeInfo: EpisodeInfo,
)

class EpisodeCollectionRepository(
    private val episodeApi: DefaultApi,
    private val episodeDao: EpisodeDao,
    private val bangumiEpisodeService: BangumiEpisodeService,
//    private val episodeRepository: EpisodeRepository,
    private val episodeCollectionDao: EpisodeCollectionDao,
) {
    fun episodeCollectionsFlow(
        subjectId: Int,
        epType: BangumiEpType = BangumiEpType.MainStory,
    ): Flow<List<EpisodeCollectionInfo>> {
        return episodeCollectionDao.filterBySubjectId(subjectId)
            .map { collections ->
                if (collections.isEmpty()) { // 缓存没有
                    bangumiEpisodeService.getSubjectEpisodeCollections(subjectId, epType)
                    val episodes = episodeRepository.episodesFlow(subjectId).first()

                }
                collections.map {
                    EpisodeCollectionInfo(
                        episodeId = it.episodeId,
                        type = it.type,
                        episodeInfo = episodeRepository.episodeInfoFlow(subjectId, it.episodeId).first(
                        )
                }
            }
    }

    fun episodeCollectionsPager(
        subjectId: Int,
        epType: BangumiEpType = BangumiEpType.MainStory,
        pagingConfig: PagingConfig = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
        ),
    ): Flow<PagingData<EpisodeCollectionInfo>> = Pager(
        config = pagingConfig,
        remoteMediator = EpisodeCollectionsRemoteMediator(
            episodeCollectionDao, episodeApi, episodeDao,
            subjectId, epType,
        ),
        pagingSourceFactory = {
            episodeCollectionDao.filterBySubjectIdPaging(subjectId)
            object : PagingSource<Int, EpisodeCollectionInfo>() {
                override fun getRefreshKey(state: PagingState<Int, EpisodeCollectionInfo>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, EpisodeCollectionInfo> {
                    val collections = episodeCollectionDao.filterBySubjectId(subjectId).first()
//                        val items = episodes.map { entity ->
//                            EpisodeCollectionInfo(
//                                episodeInfo = entity.toEpisodeInfo(),
//                                type = collections.find { it.episodeId == entity.id }?.type
//                                    ?: UnifiedCollectionType.NOT_COLLECTED,
//                            )
//                        }
                    return LoadResult.Page(
                        data = collections.map {
                            EpisodeCollectionInfo(
                                episodeId = it.episodeId,
                                type = it.type,
                            )
                        },
                        prevKey = null,
                        nextKey = null,
                    )
                }
            }
        },
    ).flow.map { data ->
        data.map { entity ->
            EpisodeCollectionInfo(
                episodeId = entity.episodeId,
                type = entity.type,
            )
        }
    }

    private class EpisodeCollectionsRemoteMediator<T : Any>(
        private val episodeCollectionDao: EpisodeCollectionDao,
        private val episodeApi: DefaultApi,
        private val episodeDao: EpisodeDao,
        val subjectId: Int,
        val epType: BangumiEpType,
    ) : RemoteMediator<Int, T>() {
        override suspend fun initialize(): InitializeAction {
            if ((episodeCollectionDao.lastUpdated() - currentTimeMillis()).milliseconds > 1.hours) {
                return InitializeAction.LAUNCH_INITIAL_REFRESH
            }
            return InitializeAction.SKIP_INITIAL_REFRESH
        }

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult {
            val offset = when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> state.pages.size * state.config.pageSize
            }

            val episodes = episodeApi.getUserSubjectEpisodeCollection(
                subjectId,
                episodeType = epType,
                offset = offset,
                limit = state.config.pageSize,
            ).body()

            episodes.data?.takeIf { it.isNotEmpty() }?.let { list ->
                episodeDao.upsert(
                    list.map {
                        it.episode.toEntity(subjectId)
                    },
                )

                episodeCollectionDao.upsert(
                    list.map {
                        EpisodeCollectionEntity(
                            subjectId = subjectId,
                            episodeId = it.episode.id,
                            type = it.type.toCollectionType(),
                        )
                    },
                )
            }

            return MediatorResult.Success(endOfPaginationReached = episodes.data.isNullOrEmpty())
        }
    }
}

internal fun BangumiEpisode.toEntity(subjectId: Int): EpisodeEntity {
    return EpisodeEntity(
        subjectId = subjectId,
        id = this.id,
        type = getEpisodeTypeByBangumiCode(this.type),
        name = this.name,
        nameCn = this.nameCn,
        airDate = PackedDate.parseFromDate(this.airdate),
        comment = this.comment,
//        duration = this.duration,
        desc = this.desc,
//        disc = this.disc,
        sort = EpisodeSort(this.sort),
        ep = EpisodeSort(this.ep ?: BigNum.ONE),
//        durationSeconds = this.durationSeconds
    )
}

private fun getEpisodeTypeByBangumiCode(code: Int): EpisodeType? {
    return when (code) {
        0 -> MainStory
        1 -> SP
        2 -> OP
        3 -> ED
        4 -> PV
        5 -> MAD
        else -> null
    }
}

