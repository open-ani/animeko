/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.persistent.database.createTestAniDatabase
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.data.repository.subject.GetEpisodeTypeFiltersUseCase
import me.him188.ani.app.data.repository.subject.OfflineSubjectDisplayInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentTimeMillis

@OptIn(TestOnly::class)
internal class AddDownloadsFixture(val testScope: TestScope) {
    val dispatcher = StandardTestDispatcher(testScope.testScheduler)
    private val database = createTestAniDatabase(dispatcher)
    private val applicationJob = SupervisorJob(testScope.backgroundScope.coroutineContext[Job])
    private val applicationScope = CoroutineScope(testScope.backgroundScope.coroutineContext + applicationJob)
    val prepared = mutableListOf<Int>()
    val released = mutableSetOf<Int>()
    val created = mutableListOf<Int>()
    val saved = mutableSetOf<Int>()
    var failPreparation: Int? = null
    var failPreference: Int? = null
    var failCreation: Int? = null
    var createGate: CompletableDeferred<Unit>? = null
    val creationStarted = CompletableDeferred<Unit>()
    private var currentEpisodeId = 0
    private val jobs = mutableListOf<Job>()
    val storage = DownloadTestStorage().apply {
        create = { _, metadata, _ ->
            val id = metadata.episodeId.toInt()
            creationStarted.complete(Unit)
            createGate?.await()
            check(id != failCreation) { "create failed" }
            created += id
            testDownload(id)
        }
    }
    private val subjects = DownloadSubjectRepository()
    private val episodes = EpisodeCollectionRepository(
        database.subjectCollection(), database.episodeCollection(),
        EpisodeServiceImpl(unusedApi()),
        AnimeScheduleRepository(AnimeScheduleService(unusedApi())),
        lazy { subjects }, GetEpisodeTypeFiltersUseCase { flowOf(EpisodeType.entries) }, dispatcher,
    )
    private val preferences = object : EpisodePreferencesRepository {
        override fun mediaPreferenceFlow(subjectId: Int) = flowOf(MediaPreference.Empty)
        override suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference) {
            check(currentEpisodeId != failPreference) { "save failed" }
            saved += currentEpisodeId
        }
        override suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String) = error("Not used")
        override fun getPreferredWebMediaSource(subjectId: Int): Flow<String?> = error("Not used")
        override suspend fun removePreferredWebMediaSource(subjectId: Int) = error("Not used")
    }
    private val selectors = object : MediaSelectorFactory {
        override fun create(subjectId: Int, episodeId: Int, mediaList: Flow<List<Media>>, flowCoroutineContext: CoroutineContext) =
            testDownloadSelection(episodeId).selector.also {
                currentEpisodeId = episodeId
                prepared += episodeId
                CoroutineScope(flowCoroutineContext).launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { released += episodeId }
                }
                check(episodeId != failPreparation) { "prepare failed" }
            }
    }
    private val sources = DownloadSourceManager(object : MediaFetcher {
        override fun newSession(requestLazy: Flow<MediaFetchRequest>, flowContext: CoroutineContext) = object : MediaFetchSession {
            override val request = requestLazy
            override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()
            override val cumulativeResults = flow {
                emit(TestMediaList)
                awaitCancellation()
            }
            override val hasCompleted = flowOf(CompletedConditions.AllCompleted)
            override fun setFetchRequest(request: MediaFetchRequest) = Unit
        }
    })
    val factory = AddDownloadsSessionFactory(
        subjects, episodes, preferences, sources, selectors,
        MediaDownloadManager(listOf(storage), applicationScope, cacheDanmaku = {}),
    )

    suspend fun seed() {
        database.subjectCollection().upsert(subject(1, currentTimeMillis()))
        database.episodeCollection().upsert((1..6).map { id ->
            val info = testDownloadRequest(id).episode
            EpisodeCollectionEntity(
                subjectId = 1, episodeId = id, episodeType = EpisodeType.MainStory,
                name = info.name, nameCn = "", airDate = PackedDate.Invalid, comment = 0, desc = "",
                sort = info.sort, sortNumber = id.toFloat(), ep = info.ep,
                selfCollectionType = UnifiedCollectionType.NOT_COLLECTED, lastFetched = currentTimeMillis(),
            )
        })
    }

    fun start(session: AddDownloadsSession, context: CoroutineContext = dispatcher): Job =
        testScope.backgroundScope.launch(context) { session.run() }.also { jobs += it }

    suspend fun close() {
        jobs.forEach { it.cancelAndJoin() }
        applicationJob.cancelAndJoin()
        database.close()
    }

    private fun <T> unusedApi(): ApiInvoker<T> = object : ApiInvoker<T> {
        override suspend fun <R> invoke(action: suspend T.() -> R): R = error("Network not expected")
    }

    private fun subject(
        subjectId: Int,
        lastFetched: Long,
        type: UnifiedCollectionType = UnifiedCollectionType.DOING,
        score: Int = 0,
    ) = SubjectCollectionEntity(
        subjectId = subjectId,
        name = "subject-$subjectId",
        nameCn = "条目 $subjectId",
        summary = "",
        nsfw = false,
        imageLarge = "",
        totalEpisodes = 12,
        airDate = PackedDate.Invalid,
        aliases = emptyList(),
        tags = emptyList(),
        collectionStats = SubjectCollectionStats.Zero,
        ratingInfo = RatingInfo.Empty,
        completeDate = PackedDate.Invalid,
        selfRatingInfo = SelfRatingInfo(score = score, comment = null, tags = emptyList(), isPrivate = false),
        collectionType = type,
        recurrence = null,
        lastUpdated = subjectId.toLong(),
        lastFetched = lastFetched,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )

    private class DownloadSubjectRepository : SubjectCollectionRepository() {
        override suspend fun invalidateAllCaches() = error("Not used")

        override suspend fun invalidateCache(subjectIds: List<Int>) = throw UnsupportedOperationException()

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> =
            throw UnsupportedOperationException()

        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> =
            flowOf(TestSubjectCollections.first().copy(subjectInfo = testDownloadRequest(1).subject))

        override fun subjectCollectionsPager(
            query: CollectionsFilterQuery,
            pagingConfig: PagingConfig,
        ): Flow<PagingData<SubjectCollectionInfo>> = throw UnsupportedOperationException()

        override fun cachedValidSubjectIds(): Flow<List<Int>> = throw UnsupportedOperationException()

        override suspend fun updateRecentlyUpdatedSubjectCollections(
            limit: Int,
            type: UnifiedCollectionType?,
            offset: Int,
        ) = throw UnsupportedOperationException()

        override fun mostRecentlyUpdatedSubjectCollectionsFlow(
            limit: Int,
            types: List<UnifiedCollectionType>?,
        ): Flow<List<SubjectCollectionInfo>> = throw UnsupportedOperationException()

        override suspend fun updateRating(
            subjectId: Int,
            score: Int?,
            comment: String?,
            tags: List<String>?,
            isPrivate: Boolean?,
        ) = throw UnsupportedOperationException()

        override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) =
            throw UnsupportedOperationException()

        override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> =
            throw UnsupportedOperationException()

        override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> =
            throw UnsupportedOperationException()

        override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
            throw UnsupportedOperationException()

        override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
            throw UnsupportedOperationException()

        override suspend fun performBangumiFullSync() = throw UnsupportedOperationException()

        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = throw UnsupportedOperationException()
    }

    private class DownloadSourceManager(
        initialFetcher: MediaFetcher,
    ) : MediaSourceManager {
        val mediaFetcherState = MutableStateFlow(initialFetcher)

        override val allInstances: Flow<List<MediaSourceInstance>> =
            flowOf(emptyList())
        override val allFactories: List<MediaSourceFactory> = emptyList()
        override val allFactoryIds: List<FactoryId> = emptyList()
        override val mediaFetcher: Flow<MediaFetcher> = mediaFetcherState
        override val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader =
            MediaSourceWebVideoMatcherLoader(flowOf(emptyList<MediaSource>()))

        override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)

        override suspend fun addInstance(
            instanceId: String,
            mediaSourceId: String,
            factoryId: FactoryId,
            config: MediaSourceConfig,
        ) = error("Not needed in test")

        override suspend fun getListBySubscriptionId(subscriptionId: String) = error("Not needed in test")
        override suspend fun partiallyReorderInstances(instanceIds: List<String>) = error("Not needed in test")
        override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig) = error("Not needed in test")
        override suspend fun setEnabled(instanceId: String, enabled: Boolean) = error("Not needed in test")
        override suspend fun removeInstance(instanceId: String) = error("Not needed in test")
        override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> = flowOf(MediaSelectorSourceTiers.Empty)
    }

}
