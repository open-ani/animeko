/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.collection.IntList
import androidx.collection.mutableIntListOf
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectAndEpisodes
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.network.EpisodeService
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionDao
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectRelations
import me.him188.ani.app.data.persistent.database.dao.SubjectRelationsDao
import me.him188.ani.app.data.persistent.database.dao.deleteAll
import me.him188.ani.app.data.persistent.database.dao.filterMostRecentUpdated
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.toEpisodeCollectionInfo
import me.him188.ani.app.data.repository.shouldRetry
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.checkAccessAniApiNow
import me.him188.ani.app.domain.session.restartOnNewLogin
import me.him188.ani.client.models.AniAnimeRecurrence
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.client.models.AniFavourite
import me.him188.ani.client.models.AniSelfRatingInfo
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectRelations
import me.him188.ani.client.models.AniTag
import me.him188.ani.client.models.AniUpdateSubjectCollectionRequest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.UTC9
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import me.him188.ani.utils.coroutines.combine
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.collections.toIntArray
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.serialization.BigNum
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

typealias BangumiSubjectApi = DefaultApi

/**
 * 条目信息和条目收藏的仓库.
 *
 * [SubjectInfo], [SubjectCollectionInfo], [SubjectCollectionCounts]
 */
sealed class SubjectCollectionRepository(
    defaultDispatcher: CoroutineContext = Dispatchers.Default
) : Repository(defaultDispatcher) {
    /**
     * 获取条目收藏统计信息 cold [Flow]. Flow 将会 emit 至少一个值, 失败时 emit `null`.
     */
    abstract fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?>

    abstract fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo>

    /**
     * 批量获取多个条目的基本信息和前 100 剧集列表.
     */
    abstract fun batchLightSubjectAndEpisodesFlow(subjectIds: IntList): Flow<List<LightSubjectAndEpisodes>>

    abstract fun subjectCollectionsPager(
        query: CollectionsFilterQuery = CollectionsFilterQuery.Empty,
        pagingConfig: PagingConfig = PagingConfig(
            pageSize = 10,
            prefetchDistance = 30,
        ),
    ): Flow<PagingData<SubjectCollectionInfo>>

    /**
     * 获取本地所有缓存的 [SubjectCollectionInfo] 的 [subjectId][SubjectCollectionInfo.subjectId]
     */
    abstract fun cachedValidSubjectIds(): Flow<List<Int>>

    /**
     * 更新根据服务器上记录的最近有修改的条目收藏. 也就是用户最近操作过的条目收藏.
     */
    abstract suspend fun updateRecentlyUpdatedSubjectCollections(
        limit: Int,
        type: UnifiedCollectionType?,
        offset: Int = 0,
    )

    /**
     * 获取最近更新的条目收藏 cold [Flow].
     */
    abstract fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>? = null, // null for all
    ): Flow<List<SubjectCollectionInfo>>

    /**
     * @param score 0 to remove rating
     * @param comment set empty to remove
     * @param tags set empty to remove
     */
    abstract suspend fun updateRating(
        subjectId: Int,
        score: Int? = null,
        comment: String? = null,
        tags: List<String>? = null,
        isPrivate: Boolean? = null,
    )

    /**
     * @throws me.him188.ani.app.data.repository.RepositoryAuthorizationException
     */
    abstract suspend fun setSubjectCollectionTypeOrDelete(
        subjectId: Int,
        type: UnifiedCollectionType?,
    )

    abstract suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>>

    abstract suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>>

    abstract suspend fun performBangumiFullSync()

    abstract suspend fun getBangumiFullSyncState(): BangumiSyncState?
}

class SubjectCollectionRepositoryImpl(
    private val api: ApiInvoker<DefaultApi>,
    private val subjectService: SubjectService,
    private val subjectCollectionDao: SubjectCollectionDao,
    private val subjectRelationsDao: SubjectRelationsDao,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val animeScheduleRepository: AnimeScheduleRepository,
    private val episodeService: EpisodeService,
    private val episodeCollectionDao: EpisodeCollectionDao,
    private val sessionManager: SessionStateProvider,
    private val nsfwModeSettingsFlow: Flow<NsfwMode>,
    private val getCurrentDate: () -> PackedDate = { PackedDate.now() },
    private val getEpisodeTypeFiltersUseCase: GetEpisodeTypeFiltersUseCase,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
    private val cacheExpiry: Duration = 1.hours,
) : SubjectCollectionRepository(defaultDispatcher) {
    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> {
        return (subjectService.subjectCollectionCountsFlow() as Flow<SubjectCollectionCounts?>)
            .restartOnNewLogin(sessionManager)
            .retry(2) { e ->
                RepositoryException.shouldRetry(e)
            }
            .catch {
                logger.error("Failed to get subject collection counts", it)
                emit(null)
            }
            .flowOn(defaultDispatcher)
//        return combine(
//            subjectCollectionDao.countCollected(UnifiedCollectionType.WISH),
//            subjectCollectionDao.countCollected(UnifiedCollectionType.DOING),
//            subjectCollectionDao.countCollected(UnifiedCollectionType.DONE),
//            subjectCollectionDao.countCollected(UnifiedCollectionType.ON_HOLD),
//            subjectCollectionDao.countCollected(UnifiedCollectionType.DROPPED),
//        ) { wish, doing, done, onHold, dropped ->
//            SubjectCollectionCounts(
//                wish = wish,
//                doing = doing,
//                done = done,
//                onHold = onHold,
//                dropped = dropped,
//                total = wish + doing + done + onHold + dropped,
//            )
//        }
    }

    private fun SubjectCollectionEntity.isExpired(): Boolean {
        return (currentTimeMillis() - lastFetched).milliseconds > cacheExpiry
    }

    override fun subjectCollectionFlow(
        subjectId: Int
    ): Flow<SubjectCollectionInfo> = getEpisodeTypeFiltersUseCase().flatMapLatest { epTypes ->
        subjectCollectionDao.findById(subjectId)
            .restartOnNewLogin(sessionManager)
            .onEach { existing ->
                // 如果没有缓存, 则 fetch 然后插入 subject 缓存
                if (existing == null || existing.isExpired()) {
                    val subject = subjectService.getSubjectCollection(subjectId)
                    val lastFetched = currentTimeMillis()
                    val subjectEntity = subject?.toEntity(
                        lastFetched = lastFetched,
                    )
                    if (subjectEntity != null) {
                        val episodeEntities = subject.episodes.map {
                            it.toEntity1(subjectId, lastFetched = lastFetched)
                        }
                        subjectCollectionDao.upsert(subjectEntity)

                        // 更新剧集列表
                        val oldIds = episodeCollectionDao.listIdBySubjectId(subjectId).first().toMutableList()
                        episodeCollectionDao.upsert(episodeEntities)
                        for (newEntity in episodeEntities) {
                            oldIds.remove(newEntity.episodeId)
                        }
                        if (oldIds.isNotEmpty()) { // 删除本地存的多余的剧集 (通常没有)
                            episodeCollectionDao.deleteAllByEpisodeIds(subjectId, oldIds)
                        }
                    }
                    // TODO: 2025/5/24 handle subject not found 
                }
            }
            .filterNotNull()
            // 有 subject 缓存后才能从 episodeCollectionRepository fetch episodes
            .combine(
                episodeCollectionDao
                    .filterBySubjectId(subjectId, epTypes)
                    .map { list -> list.map { it.toEpisodeCollectionInfo() } }
                    .distinctUntilChanged(),
                nsfwModeSettingsFlow,
            ) { entity, episodes, nsfwModeSettings ->
                entity.toSubjectCollectionInfo(
                    episodes = episodes,
                    currentDate = getCurrentDate(),
                    nsfwModeSettings = nsfwModeSettings,
                )
            }
    }.flowOn(defaultDispatcher)

    override fun batchLightSubjectAndEpisodesFlow(subjectIds: IntList): Flow<List<LightSubjectAndEpisodes>> {
        return flow {
            val existing = subjectCollectionDao.filterByIds(subjectIds.toIntArray()).first()
            val missingIds = mutableIntListOf()
            subjectIds.forEach { subjectId ->
                val existingSubject = existing.find { it.subjectId == subjectId }
                if (existingSubject == null || existingSubject.isExpired()) {
                    missingIds.add(subjectId)
                }
            }

            coroutineScope {
                val fromExistingDeferred = async {
                    existing.asFlow()
                        .flatMapMerge(concurrency = 4) { entity ->
                            episodeCollectionRepository
                                .subjectEpisodeCollectionInfosFlow(entity.subjectId)
                                .take(1)
                                .map { episodes ->
                                    LightSubjectAndEpisodes(
                                        entity.toLightSubjectInfo(),
                                        episodes.map { it.episodeInfo.toLightEpisodeInfo() },
                                    )
                                }
                        }
                        .toList()
                }
                val fromMissingDeferred = async {
                    subjectService.batchGetLightSubjectAndEpisodes(missingIds) // TODO: 2025/1/14 batchGetLightSubjectEpisodes 没有按 epType 过滤
                }
                emit(fromExistingDeferred.await() + fromMissingDeferred.await())
            }
        }
    }

    override fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>?, // null for all
    ): Flow<List<SubjectCollectionInfo>> = subjectCollectionDao.filterMostRecentUpdated(types, limit)
        .restartOnNewLogin(sessionManager)
        .combine(nsfwModeSettingsFlow) { list, nsfwModeSettings ->
            list to nsfwModeSettings
        }
        .flatMapLatest { (list, nsfwModeSettings) ->
            if (list.isEmpty()) {
                return@flatMapLatest flowOfEmptyList()
            }
            combine(
                list.map { entity ->
                    episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(entity.subjectId).map { episodes ->
                        entity.toSubjectCollectionInfo(
                            episodes = episodes,
                            currentDate = getCurrentDate(),
                            nsfwModeSettings = nsfwModeSettings,
                        )
                    }
                },
            ) {
                it.toList()
            }
        }
        .flowOn(defaultDispatcher)

    override fun subjectCollectionsPager(
        query: CollectionsFilterQuery,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<SubjectCollectionInfo>> =
        combine(getEpisodeTypeFiltersUseCase(), nsfwModeSettingsFlow) { epTypes, nsfwModeSettings ->
            epTypes to nsfwModeSettings
        }.restartOnNewLogin(sessionManager).flatMapLatest { (epTypes, nsfwModeSettings) ->
            Pager(
                config = pagingConfig,
                initialKey = 0,
                remoteMediator = SubjectCollectionRemoteMediator(query),
                pagingSourceFactory = {
                    subjectCollectionDao.filterByCollectionTypePaging(
                        query.type,
                        includeNsfw = nsfwModeSettings != NsfwMode.HIDE,
                    )
                },
            ).flow.map { data ->
                data.map { (entity, episodesOfAnyType) ->
                    val date = getCurrentDate()
                    entity.toSubjectCollectionInfo(
                        episodes = episodesOfAnyType
                            .asSequence()
                            .let { sequence ->
                                sequence.filter { it.episodeType in epTypes }
                            }
                            .map { it.toEpisodeCollectionInfo() }
                            .toList(),
                        currentDate = date,
                        nsfwModeSettings = nsfwModeSettings,
                    )
                }
            }
        }.flowOn(defaultDispatcher)

    override fun cachedValidSubjectIds(): Flow<List<Int>> {
        return subjectCollectionDao.subjectIdsWithValidEpisodeCollection().flowOn(defaultDispatcher)
    }

    private val updateRecentlyUpdatedSubjectCollectionsMutex = Mutex()
    override suspend fun updateRecentlyUpdatedSubjectCollections(
        limit: Int,
        type: UnifiedCollectionType?,
        offset: Int
    ) {
        try {
            withContext(defaultDispatcher) {
                // 只允许同时一个请求. 防止多个请求浪费带宽.
                // 一般来说不会有多个请求. 最常见的并行请求可能是用户刚刚打开 APP 进入探索页自动刷新"继续观看"栏目, 在刷新还在进行时切换到收藏页触发自动刷新.
                updateRecentlyUpdatedSubjectCollectionsMutex.withLock {
                    fetchAndSaveSubjectCollectionsWithEpisodes(type, limit, offset)
                }
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    // transparent exception
    /**
     * 执行网络查询条目收藏及其剧集列表, 在所有网络请求都成功后调用 [onFetched], 然后保存查询结果到数据库.
     *
     * @param onFetched 当所有网络请求都成功后调用
     */
    private suspend inline fun fetchAndSaveSubjectCollectionsWithEpisodes(
        type: UnifiedCollectionType?,
        limit: Int,
        offset: Int,
        onFetched: (items: List<AniSubjectCollection>) -> Unit = {},
    ) {
        require(type != UnifiedCollectionType.NOT_COLLECTED) { "type must not be NOT_COLLECTED" }
        require(limit > 0) { "limit must be positive" }

        // 执行网络请求查询好需要的 subject 和 episodes
        val items = subjectService.getSubjectCollections(
            type = type?.toSubjectCollectionType(),
            offset = offset,
            limit = limit,
        )

        onFetched(items)

        // 批量插入条目信息
        val lastFetched = currentTimeMillis()
        subjectCollectionDao.upsert(
            items.mapIndexed { index, batchSubjectCollection ->
                batchSubjectCollection.toEntity(lastFetched = lastFetched)
            },
        )

        // 必须先插入好条目信息, 否则插入 episode 会 foreign key constraint failed
        episodeCollectionDao.upsert(
            items
                .flatMap { it.episodes }
                .map { episode ->
                    episode.toEntity1(
                        subjectId = episode.subjectId.toInt(),
                        lastFetched = lastFetched,
                    )
                },
        )
    }

    override suspend fun updateRating(
        subjectId: Int,
        score: Int?, // 0 to remove rating
        comment: String?, // set empty to remove
        tags: List<String>?,
        isPrivate: Boolean?,
    ) {
        withContext(defaultDispatcher) {
            subjectService.patchSubjectCollection(
                subjectId,
                AniUpdateSubjectCollectionRequest(
                    selfRating = AniSelfRatingInfo(
                        score = score ?: 0,
                        comment = comment,
                        tags = tags.orEmpty(),
                        isPrivate = isPrivate ?: false,
                    ),
                ),
            )

            subjectCollectionDao.updateRating(
                subjectId,
                score,
                comment,
                tags,
                isPrivate,
            )
        }
    }

    private inner class SubjectCollectionRemoteMediator<T : Any>(
        private val query: CollectionsFilterQuery,
    ) : RemoteMediator<Int, T>() {
        override suspend fun initialize(): InitializeAction = withContext(defaultDispatcher) {
            val lastUpdated = subjectCollectionDao.lastFetched(query.type)
            if ((currentTimeMillis() - lastUpdated).milliseconds > cacheExpiry) {
                InitializeAction.LAUNCH_INITIAL_REFRESH
            } else {
                InitializeAction.SKIP_INITIAL_REFRESH
            }
        }

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult = try {
            withContext(defaultDispatcher) {
                val (offset, limit) = calculateIndexBasedLoadInfo(loadType, state)
                    ?: return@withContext MediatorResult.Success(endOfPaginationReached = true)
                logger.debug { "${loadType}, Loading $offset, limit=$limit" }

                fetchAndSaveSubjectCollectionsWithEpisodes(
                    type = query.type,
                    limit = limit,
                    offset = offset,
                    onFetched = { items ->
                        if (loadType == LoadType.REFRESH) {
                            // 仅在网络请求成功后才删除缓存, 否则会导致无网络时清空缓存
                            // 必须清除缓存, 让顺序与服务器同步, 否则会死循环刷新
                            subjectCollectionDao.deleteAll(query.type)
                        }

                        if (items.isEmpty()) {
                            return@withContext MediatorResult.Success(endOfPaginationReached = items.isEmpty())
                        }
                    },
                )

                MediatorResult.Success(endOfPaginationReached = false)
            }
        } catch (e: Exception) {
            MediatorResult.Error(RepositoryException.wrapOrThrowCancellation(e))
        }
    }

    override suspend fun setSubjectCollectionTypeOrDelete(
        subjectId: Int,
        type: UnifiedCollectionType?,
    ) {
        return withContext(defaultDispatcher) {
            sessionManager.checkAccessAniApiNow()
            if (type == null || type == UnifiedCollectionType.NOT_COLLECTED) {
                deleteSubjectCollection(subjectId)
            } else {
                patchSubjectCollection(
                    subjectId,
                    AniUpdateSubjectCollectionRequest(collectionType = type.toAniSubjectCollectionType()),
                )
            }
        }
    }

    override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> {
        return subjectCollectionDao.subjectIdsByCollectionType(types).flowOn(defaultDispatcher)
    }

    override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> {
        return subjectCollectionDao.subjectNamesCnByCollectionType(types).flowOn(defaultDispatcher)
    }

    private suspend fun patchSubjectCollection(
        subjectId: Int,
        payload: AniUpdateSubjectCollectionRequest,
    ) {
        withContext(defaultDispatcher) {
            subjectService.patchSubjectCollection(subjectId, payload)
            subjectCollectionDao.updateType(subjectId, payload.collectionType.toUnifiedCollectionType())
        }
    }

    private suspend fun deleteSubjectCollection(subjectId: Int) {
        withContext(defaultDispatcher) {
            subjectService.deleteSubjectCollection(subjectId)
            subjectCollectionDao.delete(subjectId)
        }
    }

    override suspend fun performBangumiFullSync() {
        try {
            withContext(defaultDispatcher) {
                subjectService.performBangumiFullSync()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    override suspend fun getBangumiFullSyncState(): BangumiSyncState? {
        return try {
            withContext(defaultDispatcher) {
                subjectService.getBangumiFullSyncState()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private companion object {
        private val logger = logger<SubjectCollectionRepository>()
    }
}

internal fun EpisodeInfo.toLightEpisodeInfo(): LightEpisodeInfo {
    return LightEpisodeInfo(
        episodeId = episodeId,
        name = name,
        nameCn = nameCn,
        airDate = airDate,
        timezone = UTC9,
        sort = sort,
        ep = ep,
    )
}

internal fun SubjectCollectionEntity.toLightSubjectInfo() =
    LightSubjectInfo(subjectId, name, nameCn, imageLarge)

data class CollectionsFilterQuery(
    val type: UnifiedCollectionType?,
) {
    companion object {
        val Empty = CollectionsFilterQuery(null)
    }
}

private fun SubjectCollectionEntity.toSubjectInfo(): SubjectInfo {
    return SubjectInfo(
        subjectId = subjectId,
        subjectType = SubjectType.ANIME,
        name = name,
        nameCn = nameCn,
        summary = summary,
        nsfw = nsfw,
        imageLarge = imageLarge,
        totalEpisodes = totalEpisodes,
        airDate = airDate,
        tags = tags,
        aliases = aliases,
        ratingInfo = ratingInfo,
        collectionStats = collectionStats,
        completeDate = completeDate,
    )
}

private fun SubjectCollectionEntity.toSubjectCollectionInfo(
    episodes: List<EpisodeCollectionInfo>,
    currentDate: PackedDate,
    nsfwModeSettings: NsfwMode,
): SubjectCollectionInfo {
    val subjectInfo = toSubjectInfo()
    return SubjectCollectionInfo(
        collectionType = collectionType,
        subjectInfo = subjectInfo,
        selfRatingInfo = selfRatingInfo,
        episodes = episodes,
        airingInfo = SubjectAiringInfo.computeFromEpisodeList(
            episodes.map { it.episodeInfo },
            airDate,
            recurrence,
        ),
        progressInfo = SubjectProgressInfo.compute(subjectInfo, episodes, currentDate, recurrence),
//        isOnAir = ,
        recurrence = recurrence,
        cachedStaffUpdated = cachedStaffUpdated,
        cachedCharactersUpdated = cachedCharactersUpdated,
        lastUpdated = lastUpdated,
        nsfwMode = if (nsfw) nsfwModeSettings else NsfwMode.DISPLAY,
        relations = relations ?: SubjectRelations.Empty,
    )
}


data class LoadInfo(
    val offset: Int,
    val limit: Int,
)

fun <T : Any> calculateIndexBasedLoadInfo(
    loadType: LoadType,
    state: PagingState<Int, T>
): LoadInfo? {
    return when (loadType) {
        LoadType.REFRESH -> {
            LoadInfo(0, state.config.pageSize)
        }

        LoadType.PREPEND -> {
            val firstLoadedPage = state.pages.firstOrNull()
            if (firstLoadedPage != null) {
                if (firstLoadedPage.itemsBefore == 0) {
                    // 没有更多数据了
                    return null
                }
                val offset = firstLoadedPage.itemsBefore - state.config.pageSize
                if (offset >= 0) {
                    LoadInfo(
                        offset,
                        state.config.pageSize,
                    )
                } else {
                    LoadInfo(
                        0,
                        (state.config.pageSize + offset).coerceAtLeast(1),
                    )
                }
            } else {
                LoadInfo(
                    0,
                    state.config.pageSize,
                )
            }
        }

        LoadType.APPEND -> {
            val lastLoadedPage = state.pages.lastOrNull()
            //                        logger.warn { "Mediator APPEND, lastLoadedPage ${}" }
            val offset = if (lastLoadedPage != null) {
                lastLoadedPage.itemsBefore + lastLoadedPage.data.size
            } else {
                0
            }
            LoadInfo(
                offset,
                state.config.pageSize,
            )
        }
    }
}

fun AniSubjectCollection.toEntity(
    lastFetched: Long,
): SubjectCollectionEntity {
    return SubjectCollectionEntity(
        subjectId = id.toInt(),
        name = name,
        nameCn = nameCn,
        summary = summary,
        nsfw = nsfw,
        imageLarge = "https://api.bgm.tv/v0/subjects/${id}/image?type=large",
        totalEpisodes = episodes.size,
        airDate = PackedDate.parseFromDate(airDate),
        aliases = aliases,
        tags = tags.map { it.toTag() },
        collectionStats = favorite.toSubjectCollectionStats(),
        ratingInfo = RatingInfo(
            rank = rank ?: 0,
            total = scoreDetails.values.sum(),
            count = RatingCounts(
                s1 = scoreDetails["1"] ?: 0,
                s2 = scoreDetails["2"] ?: 0,
                s3 = scoreDetails["3"] ?: 0,
                s4 = scoreDetails["4"] ?: 0,
                s5 = scoreDetails["5"] ?: 0,
                s6 = scoreDetails["6"] ?: 0,
                s7 = scoreDetails["7"] ?: 0,
                s8 = scoreDetails["8"] ?: 0,
                s9 = scoreDetails["9"] ?: 0,
                s10 = scoreDetails["10"] ?: 0,
            ),
            score = score ?: "0",
        ),
        completeDate = PackedDate.Invalid,
        selfRatingInfo = selfRating.toSelfRatingInfo(),
        collectionType = collectionType.toUnifiedCollectionType(),
        recurrence = airingInfo?.recurrence?.toSubjectRecurrence(),
        relations = relations.toSubjectRelationsEntity(),
        lastUpdated = updatedAt?.let { Instant.parse(it) }?.toEpochMilliseconds() ?: 0,
        lastFetched = lastFetched,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )
}

fun AniSubjectRelations.toSubjectRelationsEntity(): SubjectRelations {
    return SubjectRelations(
        seriesMainSubjectIds,
        seriesMainSubjectNames,
        sequelSubjects,
        sequelSubjectNames,
    )
}

fun AniTag.toTag(): Tag = Tag(
    name = name,
    count = count,
)

fun AniFavourite.toSubjectCollectionStats(): SubjectCollectionStats {
    return SubjectCollectionStats(
        wish = wish,
        doing = doing,
        done = done,
        onHold = onHold,
        dropped = dropped,
    )
}

fun AniAnimeRecurrence.toSubjectRecurrence(): SubjectRecurrence? {
    return SubjectRecurrence(
        Instant.parse(startTime),
        interval = intervalMillis.milliseconds,
    )
}

fun AniCollectionType?.toUnifiedCollectionType(): UnifiedCollectionType {
    return when (this) {
        AniCollectionType.WISH -> UnifiedCollectionType.WISH
        AniCollectionType.DOING -> UnifiedCollectionType.DOING
        AniCollectionType.DONE -> UnifiedCollectionType.DONE
        AniCollectionType.ON_HOLD -> UnifiedCollectionType.ON_HOLD
        AniCollectionType.DROPPED -> UnifiedCollectionType.DROPPED
        null -> UnifiedCollectionType.NOT_COLLECTED
    }
}

fun AniEpisodeCollection.toEntity1(
    subjectId: Int,
    lastFetched: Long,
): EpisodeCollectionEntity {
    return EpisodeCollectionEntity(
        subjectId = subjectId,
        episodeId = episodeId.toInt(),
        episodeType = type.toEpisodeType(),
        name = name,
        nameCn = nameCn,
        airDate = airdate?.let { PackedDate.parseFromDate(it) } ?: PackedDate.Invalid,
        comment = 0,
        desc = description,
        sort = EpisodeSort(BigNum(sort), type.toEpisodeType()),
        sortNumber = sort.toFloatOrNull() ?: 0f,
        selfCollectionType = collectionType.toUnifiedCollectionType(),
        lastFetched = lastFetched,
    )
}

fun AniEpisodeType.toEpisodeType(): EpisodeType? {
    return when (this) {
        AniEpisodeType.MAIN -> EpisodeType.MainStory
        AniEpisodeType.SPECIAL -> EpisodeType.SP
        AniEpisodeType.OP -> EpisodeType.OP
        AniEpisodeType.ED -> EpisodeType.ED
        AniEpisodeType.TRAILER -> EpisodeType.PV
        AniEpisodeType.MAD -> EpisodeType.MAD
        AniEpisodeType.OTHER -> null
    }
}

fun AniEpisodeCollectionType?.toUnifiedCollectionType(): UnifiedCollectionType {
    return when (this) {
        null -> UnifiedCollectionType.NOT_COLLECTED
        AniEpisodeCollectionType.DONE -> UnifiedCollectionType.DONE
    }
}

fun AniSelfRatingInfo.toSelfRatingInfo(): SelfRatingInfo {
    return SelfRatingInfo(
        score = score, comment = comment, tags = tags, isPrivate = isPrivate,
    )
}

fun UnifiedCollectionType.toAniSubjectCollectionType(): AniCollectionType? {
    return when (this) {
        UnifiedCollectionType.WISH -> AniCollectionType.WISH
        UnifiedCollectionType.DOING -> AniCollectionType.DOING
        UnifiedCollectionType.DONE -> AniCollectionType.DONE
        UnifiedCollectionType.ON_HOLD -> AniCollectionType.ON_HOLD
        UnifiedCollectionType.DROPPED -> AniCollectionType.DROPPED
        UnifiedCollectionType.NOT_COLLECTED -> null
    }
}
