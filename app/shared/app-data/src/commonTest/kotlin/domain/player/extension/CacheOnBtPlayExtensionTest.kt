/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.MediaCacheManagerImpl
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.legacy.MediaSelectorTestBuilder
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetWebMediaSourceInstanceFlowUseCase
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

    private inner class RecordingStorage : MediaCacheStorage {
        override val mediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID
        override val cacheMediaSource: MediaSource get() = throw UnsupportedOperationException()
        override val engine = DummyMediaCacheEngine(mediaSourceId, engineKey = MediaCacheEngineKey.Anitorrent)
        override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())
        override val stats = MutableStateFlow(MediaStats.Unspecified)

        var cacheCalls = 0
        lateinit var lastMetadata: MediaCacheMetadata

        override suspend fun restorePersistedCaches() {}
        override suspend fun cache(
            media: Media,
            metadata: MediaCacheMetadata,
            episodeMetadata: EpisodeMetadata,
            resume: Boolean
        ): MediaCache {
            cacheCalls++
            lastMetadata = metadata
            val cache = TestMediaCache(
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

    private data class Context(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
        val state: EpisodeFetchSelectPlayState,
        val storage: RecordingStorage
    )

    private fun TestScope.createCase(config: (RecordingStorage, MediaSelectorTestBuilder) -> Unit = { _, _ -> }): Context {
        contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        val storage = RecordingStorage()
        val manager = MediaCacheManagerImpl(listOf(storage), testScope)
        suite.registerComponent<MediaCacheManager> { manager }
        suite.registerComponent<GetMediaSelectorSettingsFlowUseCase> {
            GetMediaSelectorSettingsFlowUseCase {
                MutableStateFlow(
                    MediaSelectorSettings.Default,
                )
            }
        }
        suite.registerComponent<GetWebMediaSourceInstanceFlowUseCase> {
            GetWebMediaSourceInstanceFlowUseCase {
                MutableStateFlow(
                    emptyList(),
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
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }
        suite.registerComponent<MediaSelectorAutoSelectUseCaseImpl> { MediaSelectorAutoSelectUseCaseImpl(koin) }
        suite.registerComponent<DeleteCacheUseCase> {
            object : DeleteCacheUseCase {
                override suspend fun invoke(cache: MediaCache) {
                    manager.deleteCache(cache)
                }
            }
        }
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

    @Test
    fun deleteAutoCacheOnSwitch() = runTest {
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
        assertEquals(1, storage.listFlow.value.size)
        scope.cancel()
    }
}
