/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.files.SystemTemporaryDirectory
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaFetchSessionFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestMediaDataProvider
import me.him188.ani.app.domain.media.resolver.TorrentBackedMediaDataProvider
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.legacy.MediaSelectorTestBuilder
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.resolve
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @see CacheOnBtPlayExtension
 */
@OptIn(UnsafeEpisodeSessionApi::class)
class CacheOnBtPlayExtensionTest : AbstractPlayerExtensionTest() {
    private val nullFilePath = SystemTemporaryDirectory.resolve("null.tmp").toString()

    private class FakeTorrentBackedMediaDataProvider(
        override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
        override val engineKey: MediaCacheEngineKey = MediaCacheEngineKey.Anitorrent,
    ) : MediaDataProvider<UriMediaData>, TorrentBackedMediaDataProvider {
        override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData =
            UriMediaData("fake-torrent://")
    }

    private class ConfigurableResolver(
        private val provider: (Media) -> MediaDataProvider<*>,
    ) : MediaResolver {
        override fun supports(media: Media): Boolean = true
        override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> =
            provider(media)
    }

    private val btAsAnitorrentResolver = ConfigurableResolver { media ->
        if (media.kind == MediaSourceKind.BitTorrent) FakeTorrentBackedMediaDataProvider() else TestMediaDataProvider()
    }

    private inner class RecordingStorage(
        engineKey: MediaCacheEngineKey = MediaCacheEngineKey.Anitorrent,
    ) : MediaCacheStorage {
        override val mediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID
        override val cacheMediaSource: MediaSource get() = throw UnsupportedOperationException()
        override val engine = DummyMediaCacheEngine(mediaSourceId, engineKey = engineKey)
        override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())
        override val stats = MutableStateFlow(MediaStats.Unspecified)

        var cacheCalls = 0
        lateinit var lastMetadata: MediaCacheMetadata
        var lastMedia: Media? = null

        override suspend fun restorePersistedCaches() {}
        override suspend fun cache(
            media: Media,
            metadata: MediaCacheMetadata,
            episodeMetadata: EpisodeMetadata,
            resume: Boolean
        ): MediaCache {
            cacheCalls++
            lastMetadata = metadata
            lastMedia = media
            val cache = PromotableMediaCache(
                CachedMedia(
                    media,
                    "local",
                    ResourceLocation.LocalFile(nullFilePath),
                    MediaSourceLocation.Local,
                    MediaSourceKind.LocalCache,
                ),
                metadata,
            )
            listFlow.value += cache
            return cache
        }

        override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
            val list = listFlow.value
            val idx = list.indexOfFirst(predicate)
            if (idx != -1) {
                listFlow.value = list.toMutableList().apply { removeAt(idx) }
                return true
            }
            return false
        }

        override fun close() {}
    }

    /**
     * 用户在播放期间点下载时, [me.him188.ani.app.domain.media.cache.storage.TorrentMediaCacheStorage.cache]
     * 复用同一个对象并调 [MediaCache.resumeByUser] 把 [MediaCacheMetadata.autoCached] 清掉.
     */
    private class PromotableMediaCache(
        media: CachedMedia,
        initialMetadata: MediaCacheMetadata,
    ) : TestMediaCache(media, initialMetadata) {
        override var metadata: MediaCacheMetadata = initialMetadata
            private set

        override suspend fun resumeByUser() {
            metadata = metadata.copy(autoCached = false)
            resume()
        }
    }

    private data class Context(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
        val state: EpisodeFetchSelectPlayState,
        val storage: RecordingStorage
    )

    private fun TestScope.createCase(
        resolver: MediaResolver = btAsAnitorrentResolver,
        engineKey: MediaCacheEngineKey = MediaCacheEngineKey.Anitorrent,
        deleteCache: (MediaDownloadManager) -> DeleteCacheUseCase = { manager ->
            object : DeleteCacheUseCase {
                override suspend fun invoke(cache: MediaCache) {
                    manager.deleteDownload(cache)
                }
            }
        },
        config: (RecordingStorage, MediaSelectorTestBuilder) -> Unit = { _, _ -> },
    ): Context {
        contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        val storage = RecordingStorage(engineKey)
        val manager = MediaDownloadManager(listOf(storage), testScope)
        suite.registerComponent<MediaDownloadManager> { manager }
        suite.registerComponent<GetMediaSelectorSettingsFlowUseCase> {
            GetMediaSelectorSettingsFlowUseCase {
                MutableStateFlow(
                    MediaSelectorSettings.Default,
                )
            }
        }
        suite.registerComponent<GetMediaSelectorSourceTiersUseCase> {
            GetMediaSelectorSourceTiersUseCase {
                MutableStateFlow(
                    MediaSelectorSourceTiers.Empty,
                )
            }
        }
        suite.registerComponent<MediaResolver> { resolver }
        suite.registerComponent<MediaSelectorAutoSelectUseCaseImpl> { MediaSelectorAutoSelectUseCaseImpl(koin) }
        suite.registerComponent<DeleteCacheUseCase> { deleteCache(manager) }
        config(storage, suite.mediaSelectorTestBuilder)
        val state = suite.createState(listOf(CacheOnBtPlayExtension))
        state.onUIReady()
        return Context(testScope, suite, state, storage)
    }

    private fun startFetcher(state: EpisodeFetchSelectPlayState, scope: CoroutineScope) {
        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(scope)
    }

    @Test
    fun autoCacheBtMedia() = runTest {
        val deferred = CompletableDeferred<List<Media>>()
        val context = createCase { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        mediaSourceId = "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                deferred.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val media = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        deferred.complete(listOf(media))
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()
        assertEquals(1, storage.cacheCalls)
        assertEquals(storage.lastMetadata.autoCached, true)
        scope.cancel()
    }

    /**
     * 跟随播放建立的记录一个字节都没取过时, 离开本集就删掉它, 否则它会以「暂停」堆在下载页:
     * 云盘引擎的自动记录不主动下载, 进度恒为零.
     */
    @Test
    fun autoCacheDeletedOnEpisodeSwitchWhenNotStarted() = runTest {
        val deferred = CompletableDeferred<List<Media>>()
        val context = createCase { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        mediaSourceId = "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                deferred.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val media = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        deferred.complete(listOf(media))
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()
        assertEquals(1, storage.listFlow.value.size)

        state.switchEpisode(1000)
        advanceUntilIdle()
        assertEquals(0, storage.listFlow.value.size, "记录没有传输过任何字节, 切集时应当删除")
        scope.cancel()
    }

    /**
     * 用户点下载把跟随播放的记录转正之后, 第一个分片还没下完就切集, 这条记录是用户要的下载, 不能删.
     */
    @Test
    fun autoCacheKeptOnEpisodeSwitchAfterUserPromotedIt() = runTest {
        val deferred = CompletableDeferred<List<Media>>()
        val context = createCase { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        mediaSourceId = "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                deferred.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val media = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        deferred.complete(listOf(media))
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()
        val cache = storage.listFlow.value.single() as PromotableMediaCache
        assertEquals(true, cache.metadata.autoCached)

        cache.resumeByUser()
        state.switchEpisode(1000)
        advanceUntilIdle()

        assertEquals(1, storage.listFlow.value.size, "用户添加的下载一个字节都没取到时也必须留下")
        scope.cancel()
    }

    /** 取过字节的记录是用户真正在下的东西, 切集不能动它. */
    @Test
    fun autoCacheKeptOnEpisodeSwitchWhenStarted() = runTest {
        val deferred = CompletableDeferred<List<Media>>()
        val context = createCase { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        mediaSourceId = "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                deferred.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val media = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        deferred.complete(listOf(media))
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()
        val cache = storage.listFlow.value.first() as TestMediaCache
        cache.fileStats.value = MediaCache.FileStats(10.bytes, 1.bytes)

        state.switchEpisode(1000)
        advanceUntilIdle()
        assertEquals(1, storage.listFlow.value.size, "已经下过字节的记录必须留下")
        scope.cancel()
    }

    @Test
    fun autoCacheSurvivesSwitch() = runTest {
        val bt = CompletableDeferred<List<Media>>()
        val web = CompletableDeferred<List<Media>>()
        val context = createCase { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                bt.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        "web",
                        kind = MediaSourceKind.WEB,
                        fetch = {
                            SinglePagePagedSource {
                                web.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val btMedia = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        val webMedia = suite.mediaSelectorTestBuilder.createMedia("web", kind = MediaSourceKind.WEB)
        bt.complete(listOf(btMedia))
        web.complete(listOf(webMedia))
        state.mediaSelectorFlow.filterNotNull().first().select(btMedia)
        advanceUntilIdle()
        assertEquals(1, storage.listFlow.value.size)
        state.mediaSelectorFlow.filterNotNull().first().select(webMedia)
        advanceUntilIdle()
        assertEquals(1, storage.listFlow.value.size)
        scope.cancel()
    }

    @Test
    fun allUnstartedAutoCachesAreDeletedAfterSwitchingSources() = runTest {
        val results = CompletableDeferred<List<Media>>()
        val context = createCase(
            resolver = ConfigurableResolver { FakeTorrentBackedMediaDataProvider() },
        ) { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource("bt", kind = MediaSourceKind.BitTorrent, fetch = {
                        SinglePagePagedSource { results.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow() }
                    }),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val first = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        val second = first.copy(mediaId = "bt.2", download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:2"))
        results.complete(listOf(first, second))
        val selector = state.mediaSelectorFlow.filterNotNull().first()
        selector.select(first)
        advanceUntilIdle()
        selector.select(second)
        advanceUntilIdle()
        assertEquals(2, storage.listFlow.value.size)

        state.switchEpisode(1000)
        advanceUntilIdle()
        assertEquals(0, storage.listFlow.value.size)
        scope.cancel()
    }

    @Test
    fun keepCacheWhenProgress() = runTest {
        val bt = CompletableDeferred<List<Media>>()
        val web = CompletableDeferred<List<Media>>()
        val context = createCase { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                bt.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        "web",
                        kind = MediaSourceKind.WEB,
                        fetch = {
                            SinglePagePagedSource {
                                web.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val btMedia = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        val webMedia = suite.mediaSelectorTestBuilder.createMedia("web", kind = MediaSourceKind.WEB)
        bt.complete(listOf(btMedia))
        web.complete(listOf(webMedia))
        state.mediaSelectorFlow.filterNotNull().first().select(btMedia)
        advanceUntilIdle()
        val cache = storage.listFlow.value.first() as TestMediaCache
        cache.fileStats.value = MediaCache.FileStats(10.bytes, 1.bytes)
        state.mediaSelectorFlow.filterNotNull().first().select(webMedia)
        advanceUntilIdle()
        assertEquals(1, storage.listFlow.value.size)
        scope.cancel()
    }

    @Test
    fun userCacheNotDeleted() = runTest {
        val bt = CompletableDeferred<List<Media>>()
        val web = CompletableDeferred<List<Media>>()
        lateinit var manualCache: TestMediaCache
        val context = createCase { storage, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                bt.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        "web",
                        kind = MediaSourceKind.WEB,
                        fetch = {
                            SinglePagePagedSource {
                                web.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
            val manual = TestMediaCache(
                CachedMedia(
                    TestMediaList[0],
                    "local",
                    ResourceLocation.LocalFile(nullFilePath),
                    MediaSourceLocation.Local,
                    MediaSourceKind.LocalCache,
                ),
                MediaCacheMetadata("1", "1", "test", listOf("test"), EpisodeSort(1), EpisodeSort(1), "test"),
            )
            storage.listFlow.value += manual
            manualCache = manual
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val btMedia = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        val webMedia = suite.mediaSelectorTestBuilder.createMedia("web", kind = MediaSourceKind.WEB)
        bt.complete(listOf(btMedia))
        web.complete(listOf(webMedia))
        state.mediaSelectorFlow.filterNotNull().first().select(btMedia)
        advanceUntilIdle()
        state.mediaSelectorFlow.filterNotNull().first().select(webMedia)
        advanceUntilIdle()

        assertEquals(true, storage.listFlow.value.contains(manualCache))
        scope.cancel()
    }

    @Test
    fun autoCachePackSiblingHit() = runTest {
        val deferred = CompletableDeferred<List<Media>>()
        val context = createCase(resolver = ConfigurableResolver { FakeTorrentBackedMediaDataProvider() }) { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        mediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
                        kind = MediaSourceKind.LocalCache,
                        fetch = {
                            SinglePagePagedSource {
                                deferred.await().map { MediaMatch(it, MatchKind.FUZZY) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val origin = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        val siblingHit = CachedMedia(origin, MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID, origin.download)
        deferred.complete(listOf(siblingHit))
        state.mediaSelectorFlow.filterNotNull().first().select(siblingHit)
        advanceUntilIdle()
        assertEquals(1, storage.cacheCalls)
        assertEquals(origin.mediaId, storage.lastMedia?.mediaId)
        scope.cancel()
    }

    @Test
    fun skipAutoCacheWhenCachedMediaAlreadyHasRecord() = runTest {
        val deferred = CompletableDeferred<List<Media>>()
        val origin = TestMediaList[0]
        val context = createCase(resolver = ConfigurableResolver { FakeTorrentBackedMediaDataProvider() }) { storage, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        mediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
                        kind = MediaSourceKind.LocalCache,
                        fetch = {
                            SinglePagePagedSource {
                                deferred.await().map { MediaMatch(it, MatchKind.FUZZY) }.asFlow()
                            }
                        },
                    ),
                ),
            )
            storage.listFlow.value += TestMediaCache(
                CachedMedia(
                    origin,
                    "local",
                    ResourceLocation.LocalFile(nullFilePath),
                    MediaSourceLocation.Local,
                    MediaSourceKind.LocalCache,
                ),
                MediaCacheMetadata("1", "1", "test", listOf("test"), EpisodeSort(1), EpisodeSort(1), "test"),
            )
        }
        val (scope, _, state, storage) = context
        startFetcher(state, scope)
        val cached = CachedMedia(origin, MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID, origin.download)
        deferred.complete(listOf(cached))
        state.mediaSelectorFlow.filterNotNull().first().select(cached)
        advanceUntilIdle()
        assertEquals(0, storage.cacheCalls)
        scope.cancel()
    }

    /**
     * 云盘引擎按需取流, 记录不会取任何数据; 留下它只会让它以「本地缓存」的身份赢下选源.
     */
    @Test
    fun noAutoCacheOnPikPak() = runTest {
        val (scope, storage) = runPikPakPlayback()

        assertEquals(0, storage.cacheCalls)
        assertEquals(0, storage.listFlow.value.size)
        scope.cancel()
    }

    private suspend fun TestScope.runPikPakPlayback(
        engineKey: MediaCacheEngineKey = MediaCacheEngineKey.PikPak,
    ): Pair<CoroutineScope, RecordingStorage> {
        val deferred = CompletableDeferred<List<Media>>()
        val context = createCase(engineKey = engineKey) { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        mediaSourceId = "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                deferred.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val media = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        deferred.complete(listOf(media))
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()
        return scope to storage
    }

    @Test
    fun skipAutoCacheWhenResolvedToHttp() = runTest {
        // A cloud offline backend (e.g. PikPak) that intercepts a BT magnet
        // resolves it into an HTTP stream — the post-resolve MediaDataProvider
        // is not TorrentBackedMediaDataProvider, so starting an anitorrent
        // download alongside the HTTP playback is pure waste.
        val deferred = CompletableDeferred<List<Media>>()
        val httpOnlyResolver = ConfigurableResolver { TestMediaDataProvider() }
        val context = createCase(resolver = httpOnlyResolver) { _, builder ->
            builder.mediaSources.add(
                createTestMediaSourceInstance(
                    TestHttpMediaSource(
                        mediaSourceId = "bt",
                        kind = MediaSourceKind.BitTorrent,
                        fetch = {
                            SinglePagePagedSource {
                                deferred.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                            }
                        },
                    ),
                ),
            )
        }
        val (scope, suite, state, storage) = context
        startFetcher(state, scope)
        val media = suite.mediaSelectorTestBuilder.createMedia("bt", kind = MediaSourceKind.BitTorrent)
        deferred.complete(listOf(media))
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()
        assertEquals(0, storage.cacheCalls)
        assertEquals(0, storage.listFlow.value.size)
        scope.cancel()
    }
}
