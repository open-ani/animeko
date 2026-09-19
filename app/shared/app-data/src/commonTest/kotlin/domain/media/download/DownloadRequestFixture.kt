/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.domain.media.download

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.createTestSubjectCollection
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.data.repository.subject.OfflineSubjectDisplayInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 第 [id] 集: episodeId、sort、ep 均为 [id].
 */
internal fun requestTestEpisode(id: Int): EpisodeInfo = EpisodeInfo(
    episodeId = id,
    type = EpisodeType.MainStory,
    name = "Episode $id",
    sort = EpisodeSort(id),
    ep = EpisodeSort(id),
)

/**
 * 条目 [subjectId] 的收藏信息, 包含 [episodeIds] 中的每一集 (见 [requestTestEpisode]).
 */
internal fun requestTestSubject(subjectId: Int = 1, episodeIds: Iterable<Int> = 1..6): SubjectCollectionInfo =
    createTestSubjectCollection(
        subjectId,
        episodeIds.map { EpisodeCollectionInfo(requestTestEpisode(it), UnifiedCollectionType.NOT_COLLECTED) },
        UnifiedCollectionType.DOING,
    )

/**
 * 与 [TestMediaList] 首项属性相同, 只有 id 与 [range] 不同的资源.
 */
internal fun requestTestMedia(id: Int, range: EpisodeRange? = EpisodeRange.single(EpisodeSort(id))): DefaultMedia =
    TestMediaList.first().copy(mediaId = "request-media-$id", episodeRange = range)

/**
 * 以 [media] 为来源、属于条目 [subjectId] 第 [episodeId] 集的下载记录.
 */
internal fun requestTestCache(media: Media, subjectId: Int, episodeId: Int): TestMediaCache = TestMediaCache(
    CachedMedia(media, DownloadRequestFixture.STORAGE_ID, ResourceLocation.LocalFile("/download-$episodeId")),
    MediaCacheMetadata(
        subjectId = subjectId.toString(),
        episodeId = episodeId.toString(),
        subjectNames = listOf("Subject $subjectId"),
        episodeSort = EpisodeSort(episodeId),
        episodeName = "Episode $episodeId",
    ),
)

/**
 * [DownloadRequestSession] 的测试夹具. 条目 1 有第 1..6 集, 每集的查询都立即返回 [TestMediaList] 并保持运行直到被取消.
 * 所有依赖都是内存假实现, 记录调用顺序并支持注入门控与失败.
 *
 * @param supervisedApplication 应用作用域是否使用 [SupervisorJob]. 为 `false` 时, 应用作用域中任何协程失败都会取消整个作用域.
 */
internal class DownloadRequestFixture(
    val testScope: TestScope,
    supervisedApplication: Boolean = true,
) {
    private val applicationJob = testScope.backgroundScope.coroutineContext[Job].let { parent ->
        if (supervisedApplication) SupervisorJob(parent) else Job(parent)
    }

    /**
     * 应用作用域, 即 [MediaDownloadManager.backgroundScope].
     */
    val applicationScope = CoroutineScope(testScope.backgroundScope.coroutineContext + applicationJob)

    private val sessionJob = SupervisorJob(testScope.backgroundScope.coroutineContext[Job])

    /**
     * 会话默认的父作用域, [close] 时取消.
     */
    val sessionScope = CoroutineScope(testScope.backgroundScope.coroutineContext + sessionJob)

    /**
     * 读取条目收藏信息的次数.
     */
    var subjectLoads = 0

    /**
     * 已开始查询的剧集, 按顺序.
     */
    /**
     * 每集查询返回的资源. 默认把 [TestMediaList] 的每个资源都收窄为只含当前集, 使选源后直接创建下载而不进入选集.
     */
    var mediaListFor: (episodeId: Int) -> List<Media> = { episodeId ->
        TestMediaList.map { it.copy(episodeRange = EpisodeRange.single(EpisodeSort(episodeId))) }
    }

    val queried = mutableListOf<Int>()

    /**
     * 查询已被取消的剧集, 按顺序.
     */
    val released = mutableListOf<Int>()

    /**
     * 已保存的偏好, 按顺序.
     */
    val savedPreferences = mutableListOf<MediaPreference>()

    /**
     * 已开始持久化 (可能被 [createGate] 挡住) 的剧集, 按顺序.
     */
    val creationStarted = mutableListOf<Int>()

    /**
     * 已完成的创建调用, 按顺序.
     */
    val created = mutableListOf<Creation>()

    /**
     * 偏好保存与创建的先后顺序, 元素形如 `save:1`、`create:1`.
     */
    val log = mutableListOf<String>()

    /**
     * 读取条目收藏信息时抛出的异常.
     */
    var subjectFailure: Throwable? = null

    /**
     * 保存这一集的偏好时失败.
     */
    var preferenceFailure: Int? = null

    /**
     * 持久化这一集时失败.
     */
    var creationFailure: Int? = null

    /**
     * 读取条目收藏信息前等待, 用于把会话停在 [DownloadRequestState.Preparing].
     */
    var prepareGate: CompletableDeferred<Unit>? = null

    /**
     * 持久化前等待, 用于把会话停在 [DownloadRequestState.Creating].
     */
    var createGate: CompletableDeferred<Unit>? = null

    /**
     * 创建的记录是否放进 [storage] 的列表, 让 [MediaDownloadManager.downloads] 能看到.
     */
    var listCreated = true

    var subject: SubjectCollectionInfo = requestTestSubject()

    private var currentEpisodeId = 0

    val storage = DownloadTestStorage(mediaSourceId = STORAGE_ID)
    val downloadManager = MediaDownloadManager(listOf(storage), applicationScope)
    val factory = DownloadRequestSessionFactory(
        Subjects(), Preferences(), Sources(), Selectors(), downloadManager, AddDownload(),
    )

    data class Creation(
        val subject: SubjectInfo,
        val episode: EpisodeInfo,
        val media: Media,
        val metadata: MediaCacheMetadata,
    ) {
        val episodeId: Int get() = episode.episodeId
    }

    fun create(episodeIds: List<Int>, parentScope: CoroutineScope = sessionScope): DownloadRequestSession =
        factory.create(subject.subjectId, episodeIds, parentScope)

    /**
     * 同步记录 [session] 从当前起的每一次状态变化.
     */
    fun recordStates(session: DownloadRequestSession): List<DownloadRequestState> {
        val states = mutableListOf<DownloadRequestState>()
        testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
            session.state.collect { states += it }
        }
        return states
    }

    suspend fun close() {
        sessionJob.cancelAndJoin()
        applicationJob.cancelAndJoin()
    }

    private inner class Subjects : SubjectCollectionRepository() {
        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> = flow {
            subjectLoads++
            prepareGate?.await()
            subjectFailure?.let { throw it }
            check(subjectId == subject.subjectId) { "Unknown subject $subjectId" }
            emit(subject)
        }

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> =
            throw UnsupportedOperationException()

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

        override suspend fun invalidateCache(subjectIds: List<Int>) = throw UnsupportedOperationException()

        override suspend fun invalidateAllCaches() = throw UnsupportedOperationException()
    }

    private inner class Preferences : EpisodePreferencesRepository {
        override fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference> = flowOf(MediaPreference.Empty)

        override suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference) {
            check(currentEpisodeId != preferenceFailure) { "save failed" }
            savedPreferences += mediaPreference
            log += "save:$currentEpisodeId"
        }

        override suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String) =
            throw UnsupportedOperationException()

        override fun getPreferredWebMediaSource(subjectId: Int): Flow<String?> = throw UnsupportedOperationException()

        override suspend fun removePreferredWebMediaSource(subjectId: Int) = throw UnsupportedOperationException()
    }

    private inner class Sources : MediaSourceManager {
        override val allInstances: Flow<List<MediaSourceInstance>> = flowOf(emptyList())
        override val allFactories: List<MediaSourceFactory> = emptyList()
        override val allFactoryIds: List<FactoryId> = emptyList()
        override val mediaFetcher: Flow<MediaFetcher> = flowOf(Fetcher())
        override val webVideoMatcherLoader = MediaSourceWebVideoMatcherLoader(flowOf(emptyList<MediaSource>()))

        override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)

        override suspend fun addInstance(
            instanceId: String,
            mediaSourceId: String,
            factoryId: FactoryId,
            config: MediaSourceConfig,
        ) = throw UnsupportedOperationException()

        override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> =
            throw UnsupportedOperationException()

        override suspend fun partiallyReorderInstances(instanceIds: List<String>) =
            throw UnsupportedOperationException()

        override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean =
            throw UnsupportedOperationException()

        override suspend fun setEnabled(instanceId: String, enabled: Boolean) = throw UnsupportedOperationException()

        override suspend fun removeInstance(instanceId: String) = throw UnsupportedOperationException()

        override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> = flowOf(MediaSelectorSourceTiers.Empty)
    }

    private inner class Fetcher : MediaFetcher {
        override fun newSession(requestLazy: Flow<MediaFetchRequest>, flowContext: CoroutineContext): MediaFetchSession =
            FetchSession(requestLazy)
    }

    /**
     * 查询立即返回 [mediaListFor] 的结果, 之后保持运行直到被取消, 以便观察查询是否被释放.
     * 结果与真实会话一样在订阅者之间共享: 首个订阅者到来时开始查询, 最后一个订阅者离开时释放.
     */
    private inner class FetchSession(override val request: Flow<MediaFetchRequest>) : MediaFetchSession {
        override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()

        override val cumulativeResults: Flow<List<Media>> = flow {
            val episodeId = request.first().episodeId.toInt()
            queried += episodeId
            try {
                emit(mediaListFor(episodeId))
                awaitCancellation()
            } finally {
                released += episodeId
            }
        }.shareIn(testScope.backgroundScope, SharingStarted.WhileSubscribed(), replay = 1)

        override val hasCompleted: Flow<CompletedConditions> = flowOf(CompletedConditions.AllCompleted)

        override fun setFetchRequest(request: MediaFetchRequest) = Unit
    }

    private inner class Selectors : MediaSelectorFactory {
        override fun create(
            subjectId: Int,
            episodeId: Int,
            mediaList: Flow<List<Media>>,
            flowCoroutineContext: CoroutineContext,
            fetchRequest: Flow<MediaFetchRequest>?,
        ): MediaSelector {
            currentEpisodeId = episodeId
            return DefaultMediaSelector(
                mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
                mediaListNotCached = mediaList,
                savedUserPreference = flowOf(MediaPreference.Empty),
                savedDefaultPreference = flowOf(MediaPreference.Empty),
                mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
                flowCoroutineContext = EmptyCoroutineContext,
                enableCaching = false,
            )
        }
    }

    private inner class AddDownload : AddDownloadUseCase {
        override suspend fun invoke(
            subject: SubjectInfo,
            episode: EpisodeInfo,
            media: Media,
            metadata: MediaCacheMetadata,
        ): MediaCache {
            val episodeId = episode.episodeId
            creationStarted += episodeId
            createGate?.await()
            check(episodeId != creationFailure) { "create failed" }
            val cache = requestTestCache(media, subject.subjectId, episodeId)
            created += Creation(subject, episode, media, metadata)
            log += "create:$episodeId"
            if (listCreated) storage.listFlow.value += cache
            return cache
        }
    }

    companion object {
        const val STORAGE_ID = "request-test-storage"
    }
}
