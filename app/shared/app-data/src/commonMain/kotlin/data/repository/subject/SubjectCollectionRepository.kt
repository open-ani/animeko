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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectAndEpisodes
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.data.network.BangumiEpisodeService
import me.him188.ani.app.data.network.BangumiSubjectService
import me.him188.ani.app.data.network.BatchSubjectCollection
import me.him188.ani.app.data.network.BatchSubjectDetails
import me.him188.ani.app.data.network.toSelfRatingInfo
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionDao
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectRelationsDao
import me.him188.ani.app.data.persistent.database.dao.deleteAll
import me.him188.ani.app.data.persistent.database.dao.filterMostRecentUpdated
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.toEntity
import me.him188.ani.app.data.repository.episode.toEpisodeCollectionInfo
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.verifiedAccessToken
import me.him188.ani.datasources.api.EpisodeType.MainStory
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.UTC9
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import me.him188.ani.utils.coroutines.combine
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.collections.toIntArray
import me.him188.ani.utils.platform.currentTimeMillis
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
        pagingConfig: PagingConfig = defaultPagingConfig,
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

    abstract suspend fun setSubjectCollectionTypeOrDelete(
        subjectId: Int,
        type: UnifiedCollectionType?,
    )
}

class SubjectCollectionRepositoryImpl(
    private val api: ApiInvoker<DefaultApi>,
    private val bangumiSubjectService: BangumiSubjectService,
    private val subjectCollectionDao: SubjectCollectionDao,
    private val subjectRelationsDao: SubjectRelationsDao,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val animeScheduleRepository: AnimeScheduleRepository,
    private val bangumiEpisodeService: BangumiEpisodeService,
    private val episodeCollectionDao: EpisodeCollectionDao,
    private val sessionManager: SessionManager,
    private val nsfwModeSettingsFlow: Flow<NsfwMode>,
    private val getCurrentDate: () -> PackedDate = { PackedDate.now() },
    private val enableAllEpisodeTypes: Flow<Boolean>,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
    private val cacheExpiry: Duration = 1.hours,
) : SubjectCollectionRepository(defaultDispatcher) {
    @OptIn(OpaqueSession::class)
    private fun <T> Flow<T>.restartOnNewLogin(): Flow<T> =
        sessionManager.verifiedAccessToken.flatMapLatest { this }

    private val epTypeFilter get() = enableAllEpisodeTypes.map { if (it) null else MainStory }

    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> {
        return (bangumiSubjectService.subjectCollectionCountsFlow() as Flow<SubjectCollectionCounts?>)
            .restartOnNewLogin()
            .retry(2)
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

    override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> =
        subjectCollectionDao.findById(subjectId)
            .restartOnNewLogin()
            .onEach {
                // 如果没有缓存, 则 fetch 然后插入 subject 缓存
                if (it == null || it.isExpired()) {
                    coroutineScope {
                        val subjectCollectionDeferred = async { bangumiSubjectService.getSubjectCollection(subjectId) }
                        val recurrenceDeferred = async { animeScheduleRepository.getSubjectRecurrence(subjectId) }

                        val (batch, collection) = subjectCollectionDeferred.await()
                        val entity = batch.toEntity(
                            collection?.type.toCollectionType(),
                            selfRatingInfo = collection?.toSelfRatingInfo() ?: SelfRatingInfo.Empty,
                            lastUpdated = collection?.updatedAt?.toEpochMilliseconds() ?: 0,
                            lastFetched = currentTimeMillis(),
                            recurrence = recurrenceDeferred.await(),
                        )
                        subjectCollectionDao.upsert(entity) // 插入后, `subjectCollectionDao.findById(subjectId)` 会重新 emit
                    }
                }
            }
            .filterNotNull()
            // 有 subject 缓存后才能从 episodeCollectionRepository fetch episodes
            .combine(
                episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId),
                nsfwModeSettingsFlow,
            ) { entity, episodes, nsfwModeSettings ->
                entity.toSubjectCollectionInfo(
                    episodes = episodes,
                    currentDate = getCurrentDate(),
                    nsfwModeSettings = nsfwModeSettings,
                )
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
                    batchGetLightSubjectEpisodes(missingIds) // TODO: 2025/1/14 batchGetLightSubjectEpisodes 没有按 epType 过滤
                }
                emit(fromExistingDeferred.await() + fromMissingDeferred.await())
            }
        }
    }

    override fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>?, // null for all
    ): Flow<List<SubjectCollectionInfo>> = subjectCollectionDao.filterMostRecentUpdated(types, limit)
        .restartOnNewLogin()
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
        combine(epTypeFilter, nsfwModeSettingsFlow) { epType, nsfwModeSettings ->
            epType to nsfwModeSettings
        }.restartOnNewLogin().flatMapLatest { (epType, nsfwModeSettings) ->
            Pager(
                config = pagingConfig,
                initialKey = 0,
                remoteMediator = SubjectCollectionRemoteMediator(query),
                pagingSourceFactory = {
                    subjectCollectionDao.filterByCollectionTypePaging(query.type)
                },
            ).flow.map { data ->
                data.map { (entity, episodesOfAnyType) ->
                    val date = getCurrentDate()
                    entity.toSubjectCollectionInfo(
                        episodes = episodesOfAnyType
                            .asSequence()
                            .let { sequence ->
                                if (epType == null) {
                                    sequence
                                } else {
                                    sequence.filter { it.episodeType == epType }
                                }
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
        onFetched: (items: List<BatchSubjectCollection>) -> Unit = {},
    ) {
        require(type != UnifiedCollectionType.NOT_COLLECTED) { "type must not be NOT_COLLECTED" }
        require(limit > 0) { "limit must be positive" }

        // 执行网络请求查询好需要的 subject 和 episodes
        val items = bangumiSubjectService.getSubjectCollections(
            type = type?.toSubjectCollectionType(),
            offset = offset,
            limit = limit,
        )

        // 提前'批量'查询剧集收藏状态, 防止在收藏页显示结果时一个一个查导致太慢
        val episodes: List<EpisodeCollectionEntity>
        val recurrences: List<SubjectRecurrence?>
        coroutineScope {
            // 并行加载
            val episodesDeferred = async { batchGetSubjectEpisodes(items) }
            val recurrencesDeferred =
                async { animeScheduleRepository.batchGetSubjectRecurrence(items.map { it.batchSubjectDetails.subjectInfo.subjectId }) }
            episodes = episodesDeferred.await()
            recurrences = recurrencesDeferred.await()
        }

        onFetched(items)

        // 批量插入条目信息
        val lastFetched = currentTimeMillis()
        subjectCollectionDao.upsert(
            items.mapIndexed { index, batchSubjectCollection ->
                batchSubjectCollection.toEntity(lastFetched, recurrences[index])
            },
        )

        // 必须先插入好条目信息, 否则插入 episode 会 foreign key constraint failed
        episodeCollectionDao.upsert(episodes)
    }

    override suspend fun updateRating(
        subjectId: Int,
        score: Int?, // 0 to remove rating
        comment: String?, // set empty to remove
        tags: List<String>?,
        isPrivate: Boolean?,
    ) {
        withContext(defaultDispatcher) {
            bangumiSubjectService.patchSubjectCollection(
                subjectId,
                BangumiUserSubjectCollectionModifyPayload(
                    rate = score,
                    comment = comment,
                    tags = tags,
                    private = isPrivate,
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
                val offset = when (loadType) {
                    LoadType.REFRESH -> {
                        0
                    }

                    LoadType.PREPEND -> return@withContext MediatorResult.Success(
                        endOfPaginationReached = true,
                    )

                    LoadType.APPEND -> {
                        val lastLoadedPage = state.pages.lastOrNull()
//                        logger.warn { "Mediator APPEND, lastLoadedPage ${}" }
                        if (lastLoadedPage != null) {
                            lastLoadedPage.itemsBefore + lastLoadedPage.data.size
                        } else {
                            0
                        }
                    }
                }

                fetchAndSaveSubjectCollectionsWithEpisodes(
                    type = query.type,
                    limit = state.config.pageSize,
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

    private suspend fun batchGetLightSubjectEpisodes(subjectIds: IntList): List<LightSubjectAndEpisodes> {
        return bangumiSubjectService.batchGetLightSubjectAndEpisodes(subjectIds)
    }

    private suspend fun batchGetSubjectEpisodes(items: List<BatchSubjectCollection>): List<EpisodeCollectionEntity> {
        return coroutineScope {
            // 并发
            val concurrency = Semaphore(4)
            items.mapNotNull { subjectCollection ->
                subjectCollection.collection?.subjectId?.let { subjectId ->
                    async {
                        concurrency.withPermit {
                            bangumiEpisodeService.getEpisodeCollectionInfosBySubjectId(subjectId, null)
                                .map {
                                    it.toEntity(subjectId)
                                }
                                .toList()
                        }
                    }
                }
            }.flatMap {
                it.await()
            }
        }
    }

    override suspend fun setSubjectCollectionTypeOrDelete(
        subjectId: Int,
        type: UnifiedCollectionType?,
    ) {
        return withContext(defaultDispatcher) {
            if (type == null) {
                deleteSubjectCollection(subjectId)
            } else {
                patchSubjectCollection(
                    subjectId,
                    BangumiUserSubjectCollectionModifyPayload(type.toSubjectCollectionType()),
                )
            }
        }
    }

    private suspend fun patchSubjectCollection(
        subjectId: Int,
        payload: BangumiUserSubjectCollectionModifyPayload,
    ) {
        withContext(defaultDispatcher) {
            api { postUserCollection(subjectId, payload) }
            subjectCollectionDao.updateType(subjectId, payload.type.toCollectionType())
        }
    }

    private suspend fun deleteSubjectCollection(subjectId: Int) {
        // TODO: deleteSubjectCollection
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
    )
}


internal fun BatchSubjectDetails.toEntity(
    collectionType: UnifiedCollectionType,
    selfRatingInfo: SelfRatingInfo,
    recurrence: SubjectRecurrence?,
    lastUpdated: Long,
    lastFetched: Long,
): SubjectCollectionEntity =
    subjectInfo.run {
        SubjectCollectionEntity(
            subjectId = subjectId,
//            subjectType = SubjectType.ANIME,
            name = name,
            nameCn = nameCn,
            summary = summary,
            nsfw = nsfw,
            imageLarge = imageLarge,
            totalEpisodes =
            @Suppress("DEPRECATION")
            totalEpisodes,
            airDate = airDate,
            tags = tags,
            aliases = aliases,
            ratingInfo = ratingInfo,
            collectionStats = collectionStats,
            completeDate = completeDate,
            selfRatingInfo = selfRatingInfo,
            collectionType = collectionType,
            recurrence = recurrence,
            lastUpdated = lastUpdated,
            lastFetched = lastFetched,
            cachedStaffUpdated = 0,
            cachedCharactersUpdated = 0,
        )
    }

internal fun BatchSubjectCollection.toEntity(
    lastFetched: Long,
    recurrence: SubjectRecurrence?,
): SubjectCollectionEntity {
    val subject = batchSubjectDetails
    return subject.toEntity(
        collection?.type.toCollectionType(),
        collection.toSelfRatingInfo(),
        lastUpdated = collection?.updatedAt?.toEpochMilliseconds() ?: 0,
        lastFetched = lastFetched,
        recurrence = recurrence,
    )
}

