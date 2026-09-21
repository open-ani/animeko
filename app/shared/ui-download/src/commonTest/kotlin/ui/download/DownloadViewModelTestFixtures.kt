/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.models.preference.AnalyticsSettings
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.OneshotActionConfig
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.ProfileSettings
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.createTestSubjectCollection
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.PlaybackHistoryPendingOp
import me.him188.ani.app.data.repository.subject.CollectionsFilterQuery
import me.him188.ani.app.data.repository.subject.OfflineSubjectDisplayInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.download.AddDownloadUseCase
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.datasources.api.CachedMedia
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

internal const val TEST_STORAGE_ID = "test-storage"

/**
 * 一条内存中的下载记录. 默认状态为进行中.
 */
@OptIn(TestOnly::class)
internal fun testDownloadCache(
    episodeId: Int,
    subjectId: Int = 1,
    creationTime: Long = episodeId * 100L,
    state: MediaCacheState = MediaCacheState.IN_PROGRESS,
    range: EpisodeRange? = null,
): TestMediaCache {
    val media = TestMediaList.first().copy(mediaId = "media-$subjectId-$episodeId", episodeRange = range)
    return TestMediaCache(
        CachedMedia(media, TEST_STORAGE_ID, ResourceLocation.LocalFile("/download-$subjectId-$episodeId")),
        MediaCacheMetadata(
            subjectId = subjectId.toString(),
            episodeId = episodeId.toString(),
            subjectNames = listOf("Subject $subjectId"),
            episodeSort = EpisodeSort(episodeId),
            episodeName = "Episode $episodeId",
            creationTime = creationTime,
        ),
    ).apply { this.state.value = state }
}

/**
 * 内存存储: 记录列表与统计都可由测试直接修改.
 */
internal class FakeDownloadStorage(
    initialStats: MediaStats = MediaStats.Zero,
    override val engine: MediaCacheEngine = DummyMediaCacheEngine(TEST_STORAGE_ID),
) : MediaCacheStorage {
    override val mediaSourceId: String = TEST_STORAGE_ID
    override val cacheMediaSource: MediaSource get() = error("Not used")
    override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())
    override val stats = MutableStateFlow(initialStats)

    override suspend fun restorePersistedCaches() = Unit

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean,
    ): MediaCache = error("Not used")

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        val selected = listFlow.value.firstOrNull(predicate) ?: return false
        listFlow.value -= selected
        return true
    }

    override fun close() = Unit
}

/**
 * 删除时直接从下载管理器移除; [failure] 非空时抛出它.
 */
internal class FakeDeleteCacheUseCase(private val downloadManager: MediaDownloadManager) : DeleteCacheUseCase {
    var failure: Throwable? = null

    override suspend fun invoke(cache: MediaCache) {
        failure?.let { throw it }
        downloadManager.deleteDownload(cache)
    }
}

/**
 * 只提供条目收藏流与离线条目信息的仓库.
 */
internal class FakeSubjectCollectionRepository : SubjectCollectionRepository() {
    /**
     * [subjectCollectionFlow] 的数据源; 为 `null` 时该流一直挂起.
     */
    val collection = MutableStateFlow<SubjectCollectionInfo?>(null)

    /**
     * 非空时 [subjectCollectionFlow] 以该异常失败.
     */
    var collectionFailure: Throwable? = null
    val collectionTypes = mutableMapOf<Int, UnifiedCollectionType?>()
    val displayInfos = mutableMapOf<Int, OfflineSubjectDisplayInfo?>()

    override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> = flow {
        collectionFailure?.let { throw it }
        emitAll(collection.filterNotNull())
    }

    override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> =
        flowOf(collectionTypes[subjectId])

    override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> =
        flowOf(displayInfos[subjectId])

    override suspend fun invalidateAllCaches() = throw UnsupportedOperationException()
    override suspend fun invalidateCache(subjectIds: List<Int>) = throw UnsupportedOperationException()
    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> = throw UnsupportedOperationException()
    override fun subjectCollectionsPager(
        query: CollectionsFilterQuery,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<SubjectCollectionInfo>> = throw UnsupportedOperationException()

    override fun cachedValidSubjectIds(): Flow<List<Int>> = throw UnsupportedOperationException()
    override suspend fun updateRecentlyUpdatedSubjectCollections(limit: Int, type: UnifiedCollectionType?, offset: Int) =
        throw UnsupportedOperationException()

    override fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>?,
    ): Flow<List<SubjectCollectionInfo>> = throw UnsupportedOperationException()

    override suspend fun updateRating(subjectId: Int, score: Int?, comment: String?, tags: List<String>?, isPrivate: Boolean?) =
        throw UnsupportedOperationException()

    override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) =
        throw UnsupportedOperationException()

    override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
        throw UnsupportedOperationException()

    override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
        throw UnsupportedOperationException()

    override suspend fun performBangumiFullSync() = throw UnsupportedOperationException()
    override suspend fun getBangumiFullSyncState(): BangumiSyncState? = throw UnsupportedOperationException()
}

/**
 * 条目 [subjectId] 的收藏信息, 包含正片 1..[episodeCount] 集.
 */
@OptIn(TestOnly::class)
internal fun testSubjectCollection(subjectId: Int = 1, episodeCount: Int = 3): SubjectCollectionInfo =
    createTestSubjectCollection(
        subjectId,
        (1..episodeCount).map { id ->
            EpisodeCollectionInfo(
                EpisodeInfo(episodeId = id, type = EpisodeType.MainStory, name = "Episode $id", sort = EpisodeSort(id), ep = EpisodeSort(id)),
                UnifiedCollectionType.NOT_COLLECTED,
            )
        },
        UnifiedCollectionType.DOING,
    )

internal class FakeEpisodePlayHistoryRepository : EpisodePlayHistoryRepository {
    override val flow = MutableStateFlow<List<EpisodeHistory>>(emptyList())
    override val allHistoriesFlow: Flow<List<EpisodeHistory>> get() = flow
    override fun flowByEpisodeIds(episodeIds: Collection<Int>): Flow<List<EpisodeHistory>> =
        flow.map { histories -> histories.filter { it.episodeId in episodeIds } }
    override val pendingOpsFlow: Flow<List<PlaybackHistoryPendingOp>> get() = throw UnsupportedOperationException()
    override val lastSyncAtMillisFlow: Flow<Long> get() = throw UnsupportedOperationException()
    override suspend fun clear() = throw UnsupportedOperationException()
    override suspend fun remove(episodeId: Int) = throw UnsupportedOperationException()
    override suspend fun removeAll(episodeIds: Collection<Int>) = throw UnsupportedOperationException()
    override suspend fun saveOrUpdate(
        episodeId: Int,
        positionMillis: Long,
        subjectId: Int?,
        episodeSort: Float?,
        subjectName: String?,
        subjectImageUrl: String?,
        episodeName: String?,
        durationMillis: Long?,
    ) = throw UnsupportedOperationException()

    override suspend fun applySyncResult(sentPendingOpIds: Collection<Long>, records: List<EpisodeHistory>, nextSyncAtMillis: Long) =
        throw UnsupportedOperationException()

    override suspend fun deletePendingOps(ids: Collection<Long>) = throw UnsupportedOperationException()
    override suspend fun getPositionMillisByEpisodeId(episodeId: Int): Long? = throw UnsupportedOperationException()
}

/**
 * 只提供选源设置, 其余设置访问即报错.
 */
internal class FakeSettingsRepository : SettingsRepository {
    override val mediaSelectorSettings: Settings<MediaSelectorSettings> = object : Settings<MediaSelectorSettings> {
        private val state = MutableStateFlow(MediaSelectorSettings.Default)
        override val flow: Flow<MediaSelectorSettings> = state
        override suspend fun set(value: MediaSelectorSettings) {
            state.value = value
        }
    }

    override val danmakuEnabled: Settings<Boolean> get() = error("Not used")
    override val danmakuConfig: Settings<DanmakuConfig> get() = error("Not used")
    override val danmakuFilterConfig: Settings<DanmakuFilterConfig> get() = error("Not used")
    override val defaultMediaPreference: Settings<MediaPreference> get() = error("Not used")
    override val profileSettings: Settings<ProfileSettings> get() = error("Not used")
    override val proxySettings: Settings<ProxySettings> get() = error("Not used")
    override val mediaCacheSettings: Settings<MediaCacheSettings> get() = error("Not used")
    override val danmakuSettings: Settings<DanmakuSettings> get() = error("Not used")
    override val uiSettings: Settings<UISettings> get() = error("Not used")
    override val themeSettings: Settings<ThemeSettings> get() = error("Not used")
    override val updateSettings: Settings<UpdateSettings> get() = error("Not used")
    override val videoScaffoldConfig: Settings<VideoScaffoldConfig> get() = error("Not used")
    override val playerKernelConfig: Settings<PlayerKernelConfig> get() = error("Not used")
    override val videoResolverSettings: Settings<VideoResolverSettings> get() = error("Not used")
    override val anitorrentConfig: Settings<AnitorrentConfig> get() = error("Not used")
    override val pikpakConfig: Settings<PikPakConfig> get() = error("Not used")
    override val torrentPeerConfig: Settings<TorrentPeerConfig> get() = error("Not used")
    override val oneshotActionConfig: Settings<OneshotActionConfig> get() = error("Not used")
    override val analyticsSettings: Settings<AnalyticsSettings> get() = error("Not used")
    override val debugSettings: Settings<DebugSettings> get() = error("Not used")
    override val watchTogetherSettings: Settings<WatchTogetherSettings> get() = error("Not used")
}

/**
 * 每个查询会话立即返回 [mediaListFor] 的结果. 持续订阅结果的查询被取消时, 把剧集 id 记入 [releasedEpisodeIds];
 * 只取首个结果的订阅 (如选择器内部的查询) 不计入.
 */
@OptIn(TestOnly::class)
internal class FakeMediaFetcher : MediaFetcher {
    val releasedEpisodeIds = mutableSetOf<Int>()

    /**
     * 每集查询返回的资源. 默认把 [TestMediaList] 的每个资源都收窄为只含当前集, 使选源后直接创建下载而不进入选集.
     */
    var mediaListFor: (episodeId: Int) -> List<Media> = { episodeId ->
        TestMediaList.map { it.copy(episodeRange = EpisodeRange.single(EpisodeSort(episodeId))) }
    }

    override fun newSession(requestLazy: Flow<MediaFetchRequest>, flowContext: CoroutineContext): MediaFetchSession =
        object : MediaFetchSession {
            override val request: Flow<MediaFetchRequest> = requestLazy
            override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()
            override val cumulativeResults: Flow<List<Media>> = flow {
                val episodeId = requestLazy.first().episodeId.toInt()
                emit(mediaListFor(episodeId))
                try {
                    awaitCancellation()
                } finally {
                    releasedEpisodeIds += episodeId
                }
            }
            override val hasCompleted: Flow<CompletedConditions> = flowOf(CompletedConditions.AllCompleted)
            override fun setFetchRequest(request: MediaFetchRequest) = Unit
        }
}

/**
 * 只提供 [mediaFetcher] 的数据源管理器.
 */
internal class FakeMediaSourceManager(val fetcher: FakeMediaFetcher = FakeMediaFetcher()) : MediaSourceManager {
    override val allInstances: Flow<List<MediaSourceInstance>> = flowOf(emptyList())
    override val allFactories: List<MediaSourceFactory> = emptyList()
    override val allFactoryIds: List<FactoryId> = emptyList()
    override val mediaFetcher: Flow<MediaFetcher> = flowOf(fetcher)
    override val webVideoMatcherLoader = MediaSourceWebVideoMatcherLoader(flowOf(emptyList<MediaSource>()))

    override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)
    override suspend fun addInstance(instanceId: String, mediaSourceId: String, factoryId: FactoryId, config: MediaSourceConfig) =
        error("Not used")

    override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> = error("Not used")
    override suspend fun partiallyReorderInstances(instanceIds: List<String>) = error("Not used")
    override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean = error("Not used")
    override suspend fun setEnabled(instanceId: String, enabled: Boolean) = error("Not used")
    override suspend fun removeInstance(instanceId: String) = error("Not used")
    override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> = flowOf(MediaSelectorSourceTiers.Empty)
}

/**
 * 不读取任何仓库的选择器工厂. 选择器的流不切换调度器, 让测试始终跑在测试调度器上.
 */
internal fun fakeMediaSelectorFactory(): MediaSelectorFactory = object : MediaSelectorFactory {
    override fun create(
        subjectId: Int,
        episodeId: Int,
        mediaList: Flow<List<Media>>,
        flowCoroutineContext: CoroutineContext,
        fetchRequest: Flow<MediaFetchRequest>?,
    ): MediaSelector = DefaultMediaSelector(
        mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
        mediaListNotCached = mediaList,
        savedUserPreference = flowOf(MediaPreference.Empty),
        savedDefaultPreference = flowOf(MediaPreference.Empty),
        mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
        flowCoroutineContext = EmptyCoroutineContext,
        enableCaching = false,
    )
}

internal class FakeEpisodePreferencesRepository : EpisodePreferencesRepository {
    val savedSubjectIds = mutableListOf<Int>()

    override fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference> = flowOf(MediaPreference.Empty)
    override suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference) {
        savedSubjectIds += subjectId
    }

    override suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String) = error("Not used")
    override fun getPreferredWebMediaSource(subjectId: Int): Flow<String?> = error("Not used")
    override suspend fun removePreferredWebMediaSource(subjectId: Int) = error("Not used")
}

/**
 * 把新下载直接放入 [storage]. [gate] 非空时先等待它完成; [failure] 非空时抛出它.
 */
internal class FakeAddDownloadUseCase(private val storage: FakeDownloadStorage) : AddDownloadUseCase {
    val createdEpisodeIds = mutableListOf<Int>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null

    override suspend fun invoke(subject: SubjectInfo, episode: EpisodeInfo, media: Media, metadata: MediaCacheMetadata): MediaCache {
        gate?.await()
        failure?.let { throw it }
        val cache = testDownloadCache(episode.episodeId, subject.subjectId)
        createdEpisodeIds += episode.episodeId
        storage.listFlow.value += cache
        return cache
    }
}
