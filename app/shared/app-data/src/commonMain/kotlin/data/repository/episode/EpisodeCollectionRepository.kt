/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.episode

import androidx.paging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.network.EpisodeService
import me.him188.ani.app.data.network.toBangumiEpType
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionDao
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.subject.GetEpisodeTypeFiltersUseCase
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.toEpisodeType
import me.him188.ani.app.data.repository.subject.toUnifiedCollectionType
import me.him188.ani.app.domain.episode.EpisodeCollections
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.serialization.BigNum
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class EpisodeCollectionRepository(
    private val subjectDao: SubjectCollectionDao,
    private val episodeCollectionDao: EpisodeCollectionDao,
    private val episodeService: EpisodeService,
    private val animeScheduleRepository: AnimeScheduleRepository,
    subjectCollectionRepository: Lazy<SubjectCollectionRepository>,
    private val getEpisodeTypeFiltersUseCase: GetEpisodeTypeFiltersUseCase,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
    private val cacheExpiry: Duration = 1.hours,
) : Repository(defaultDispatcher) {

    private val subjectCollectionRepository by subjectCollectionRepository

    private fun EpisodeCollectionEntity.isExpired(): Boolean {
        return (currentTimeMillis() - lastFetched).milliseconds > cacheExpiry
    }

    private fun SubjectCollectionEntity.isExpired(): Boolean {
        return (currentTimeMillis() - lastFetched).milliseconds > cacheExpiry
    }

    /**
     * 获取指定条目的指定剧集信息, 如果没有则从网络获取并缓存
     */
    fun episodeCollectionInfoFlow(subjectId: Int, episodeId: Int): Flow<EpisodeCollectionInfo> {
        return episodeCollectionDao.findByEpisodeId(episodeId).map { entity ->
            entity?.takeIf { !it.isExpired() }
                ?.toEpisodeCollectionInfo()
                ?: kotlin.run {
                    episodeService.getEpisodeCollectionById(subjectId, episodeId)
                        ?.also {
                            episodeCollectionDao.upsert(it.toEntity(subjectId))
                        }
                        ?: throw NoSuchElementException("Episode $episodeId not found")
                }
        }.flowOn(defaultDispatcher)
    }

    /**
     * 获取指定条目的所有剧集信息, 如果没有则从网络获取.
     *
     * 如果 [subjectId] 对应的 [SubjectCollectionEntity] 缓存存在, 则获取到的剧集信息还会插入到缓存 ([EpisodeCollectionEntity]). 否则不会操作缓存 (因为 foreign key).
     *
     * 当网络错误时, 总是会使用缓存.
     */
    fun subjectEpisodeCollectionInfosFlow(
        subjectId: Int,
    ): Flow<List<EpisodeCollectionInfo>> = subjectCollectionRepository.subjectCollectionFlow(subjectId).map {
        it.episodes
    }.flowOn(defaultDispatcher)

    private suspend fun shouldUseCache(
        allowCached: Boolean,
        cachedEpisodes: List<EpisodeCollectionEntity>,
        subjectId: Int,
    ): Boolean {
        if (!allowCached) return false
        if (cachedEpisodes.isEmpty()) {
            subjectDao.findById(subjectId).first()
                ?.takeIf { !it.isExpired() }
                ?.totalEpisodes
                ?: // 无法确定条目是否有剧集, 无法确认缓存是否有效, 保守判定为无效
                return false

            // 不能这样判断, 因为 bangumi 数据上 subjectTotalEpisodes 可能一直都是 0, 但是实际上有剧集.
//            if (subjectTotalEpisodes == 0) {
//                // 条目没有剧集, 缓存有效 (为空)
//                return true
//            }

            // 条目有剧集而缓存未空. 缓存肯定无效. 已经无效了就不用再判断时间了
            return false
        }

        val lastUpdated = cachedEpisodes.maxOf { it.lastFetched }
        return (currentTimeMillis() - lastUpdated).milliseconds <= cacheExpiry
    }

    fun subjectEpisodeCollectionsPager(
        subjectId: Int,
        pagingConfig: PagingConfig = defaultPagingConfig,
    ): Flow<PagingData<EpisodeCollectionInfo>> = Pager(
        config = pagingConfig,
        remoteMediator = EpisodeCollectionsRemoteMediator(
            episodeCollectionDao, episodeService,
            subjectId,
        ),
        pagingSourceFactory = {
            episodeCollectionDao.filterBySubjectIdPaging(subjectId)
        },
    ).flow.map { data ->
        data.map {
            it.toEpisodeCollectionInfo()
        }
    }.flowOn(defaultDispatcher)

    /**
     * 设置指定条目的所有剧集为已看.
     */
    suspend fun setAllEpisodesWatched(subjectId: Int) = withContext(defaultDispatcher) {
        val episodeIds = subjectEpisodeCollectionInfosFlow(subjectId)
            .first()
            .map { it.episodeId }

        episodeService.setEpisodeCollection(subjectId, episodeIds, UnifiedCollectionType.DONE)
        episodeCollectionDao.setAllEpisodesWatched(subjectId)
    }

    suspend fun setEpisodeCollectionType(
        subjectId: Int,
        episodeId: Int,
        collectionType: UnifiedCollectionType,
    ) = withContext(defaultDispatcher) {
        if (subjectCollectionRepository.subjectCollectionFlow(subjectId)
                .first().collectionType == UnifiedCollectionType.NOT_COLLECTED
        ) {
            logger.warn { "User has not yet collected subject $subjectId when we want to setEpisodeCollectionType, ignoring." }
//            subjectCollectionRepository.setSubjectCollectionTypeOrDelete(subjectId, UnifiedCollectionType.DOING)
        }
        episodeService.setEpisodeCollection(subjectId, listOf(episodeId), collectionType)
        episodeCollectionDao.updateSelfCollectionType(subjectId, episodeId, collectionType)
    }

    /**
     * 获取指定条目的指定剧集的收藏状态.
     *
     * @param allowNetwork 是否允许网络请求. 如果不允许, 将只返回本地缓存, 即使已经失效.
     * @return 收藏状态. 当无法确定时返回 `null`.
     */
    suspend fun getEpisodeCollectionType(
        subjectId: Int,
        episodeId: Int,
        allowNetwork: Boolean,
    ): UnifiedCollectionType? = withContext(defaultDispatcher) {
        try {
            val local = episodeCollectionDao.findByEpisodeId(episodeId).first()

            if (local != null && (!local.isExpired() || !allowNetwork)) {
                return@withContext local.selfCollectionType
            } else {
                val remote = episodeService.getEpisodeCollectionById(subjectId, episodeId)
                if (remote != null) {
                    return@withContext remote.collectionType
                }

                return@withContext null
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * 获取指定条目是否已经完结. 不是用户是否看完, 只要条目本身完结了就算.
     */
    fun subjectCompletedFlow(subjectId: Int): Flow<Boolean> {
        return subjectEpisodeCollectionInfosFlow(subjectId)
            .combine(subjectCollectionRepository.subjectCollectionFlow(subjectId)) { epCollection, subject ->
                EpisodeCollections.isSubjectCompleted(epCollection.map { it.episodeInfo }, subject.recurrence)
            }
    }

    /**
     * Loads [EpisodeCollectionEntity]
     */
    private inner class EpisodeCollectionsRemoteMediator<T : Any>(
        private val episodeCollectionDao: EpisodeCollectionDao,
        private val episodeService: EpisodeService,
        val subjectId: Int,
    ) : RemoteMediator<Int, T>() {
        override suspend fun initialize(): InitializeAction {
            return withContext(defaultDispatcher) {
                if ((currentTimeMillis() - episodeCollectionDao.lastFetched(subjectId)).milliseconds > cacheExpiry) {
                    InitializeAction.LAUNCH_INITIAL_REFRESH
                } else {
                    InitializeAction.SKIP_INITIAL_REFRESH
                }
            }
        }

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult = withContext(defaultDispatcher) {
            val offset = when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> state.pages.size * state.config.pageSize
            }

            try {
                val episodeTypes = getEpisodeTypeFiltersUseCase().first()
                val episodes = episodeService.getEpisodeCollectionInfosPaged(
                    subjectId,
                    // TODO: 2025/4/10 这里实际上不可以用 singleOrNull.
                    //  为 null 时会查询所有类型, 然后再过滤, 导致结果数量可能少于服务器数量, UI paging 反馈的 index 可能错误, 导致无限加载某一页.
                    episodeType = episodeTypes.singleOrNull()?.toBangumiEpType(),
                    offset = offset,
                    limit = state.config.pageSize,
                )
                episodes.page.filter { it.episodeInfo.type in episodeTypes }.takeIf { it.isNotEmpty() }?.let { list ->
                    episodeCollectionDao.upsert(
                        list.map { it.toEntity(subjectId) },
                    )
                }

                MediatorResult.Success(endOfPaginationReached = episodes.hasMore)
            } catch (e: Exception) {
                return@withContext MediatorResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }

        }
    }
}

suspend inline fun EpisodeCollectionRepository.setEpisodeWatched(subjectId: Int, episodeId: Int, watched: Boolean) =
    setEpisodeCollectionType(
        subjectId,
        episodeId,
        if (watched) UnifiedCollectionType.DONE else UnifiedCollectionType.WISH,
    )

fun EpisodeCollectionInfo.toEntity(
    subjectId: Int,
    lastFetched: Long = currentTimeMillis(),
): EpisodeCollectionEntity {
    return EpisodeCollectionEntity(
        subjectId = subjectId,
        episodeId = episodeId,
        episodeType = episodeInfo.type,
        name = episodeInfo.name,
        nameCn = episodeInfo.nameCn,
        airDate = episodeInfo.airDate,
        comment = episodeInfo.comment,
        desc = episodeInfo.desc,
        sort = episodeInfo.sort,
        sortNumber = episodeInfo.sort.number ?: Float.MAX_VALUE,
        ep = episodeInfo.ep,
        selfCollectionType = collectionType,
        lastFetched = lastFetched,
    )
}

fun EpisodeCollectionEntity.toEpisodeCollectionInfo() =
    EpisodeCollectionInfo(
        episodeInfo = toEpisodeInfo(),
        collectionType = selfCollectionType,
    )

fun AniEpisodeCollection.toEpisodeCollectionInfo() =
    EpisodeCollectionInfo(
        episodeInfo = toEpisodeInfo(),
        collectionType = collectionType.toUnifiedCollectionType(),
    )

private fun EpisodeCollectionEntity.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        episodeId = this.episodeId,
        type = this.episodeType,
        name = this.name,
        nameCn = this.nameCn,
        airDate = this.airDate,
        comment = this.comment,
        desc = this.desc,
        sort = this.sort,
        ep = this.ep,
    )
}

private fun AniEpisodeCollection.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        episodeId = this.episodeId.toInt(),
        type = this.type.toEpisodeType(),
        name = this.name,
        nameCn = this.nameCn,
        airDate = this.airdate?.let { PackedDate.parseFromDate(it) } ?: PackedDate.Invalid,
        comment = 0,
        desc = this.description,
        sort = EpisodeSort(BigNum(this.sort), this.type.toEpisodeType()),
        ep = this.ep?.let { EpisodeSort(BigNum(it), this.type.toEpisodeType()) },
    )
}
