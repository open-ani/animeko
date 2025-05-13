/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.domain.media.cache.engine.AlwaysUseTorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.DataStoreMediaCacheStorage
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.torrent.anitorrent.session.AnitorrentDownloadSession
import me.him188.ani.app.torrent.anitorrent.test.TestAnitorrentTorrentDownloader
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.unwrapCached
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.serialization.putAll
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @see DataStoreMediaCacheStorage
 */
class DirectoryMediaCacheStorageTest {
    companion object {
        private const val CACHE_MEDIA_SOURCE_ID = "local-test"
        private val CacheEngineKey = MediaCacheEngineKey("test-media-cache-engine")
    }

    @TempDir
    private lateinit var dir: File
    private val metadataStore: DataStore<List<MediaCacheSave>> = MemoryDataStore(emptyList())
    private val storages = mutableListOf<DataStoreMediaCacheStorage>()
    private lateinit var cacheEngine: TorrentMediaCacheEngine
    private lateinit var torrentEngine: TorrentEngine
    private suspend fun torrentDownloader(): TestAnitorrentTorrentDownloader =
        torrentEngine.getDownloader() as TestAnitorrentTorrentDownloader

    private val json = Json {
        prettyPrint = true
    }

    private val metadataFlow = metadataStore.data
        .map { list ->
            list
                .filter { it.engine == CacheEngineKey }
                .sortedBy { it.origin.mediaId } // consistent stable order
        }

    private fun TestScope.createEngine(
        onDownloadStarted: suspend (session: AnitorrentDownloadSession) -> Unit = {},
    ): TorrentMediaCacheEngine {
        val client = createDefaultHttpClient()
        this.coroutineContext.job.invokeOnCompletion {
            client.close()
        }
        return TorrentMediaCacheEngine(
            CACHE_MEDIA_SOURCE_ID,
            CacheEngineKey,
            AnitorrentEngine(
                config = flowOf(AnitorrentConfig()),
                client = client.asScopedHttpClient(),
                peerFilterSettings = flowOf(PeerFilterSettings.Empty),
                saveDir = dir.toKtPath().inSystem,
                parentCoroutineContext = coroutineContext,
                anitorrentFactory = TestAnitorrentTorrentDownloader.Factory,
            ).also { torrentEngine = it },
            engineAccess = AlwaysUseTorrentEngineAccess,
            mediaCacheMetadataStore = MemoryDataStore(listOf()),
            shareRatioLimitFlow = flowOf(1.2f),
            flowDispatcher = coroutineContext[ContinuationInterceptor]!!,
            baseSaveDirProvider = object : MediaSaveDirProvider {
                override val saveDir: String = dir.absolutePath
            },
            onDownloadStarted = { onDownloadStarted(it as AnitorrentDownloadSession) },
        )
    }

    private val media = createTestDefaultMedia(
        mediaId = "dmhy.2",
        mediaSourceId = "dmhy",
        originalTitle = "夜晚的水母不会游泳 02 测试剧集",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 1724493292758,
        episodeRange = EpisodeRange.single(EpisodeSort(2)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf("CHT"),
            resolution = "1080P",
            alliance = "北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组",
            size = 233.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    )

    private fun cleanup() {
        storages.forEach { it.close() }
        storages.clear()
    }

    private fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        timeout: Duration = 5.seconds,
        testBody: suspend TestScope.() -> Unit
    ) = kotlinx.coroutines.test.runTest(context, timeout) {
        try {
            testBody()
        } finally {
            cleanup()
        }
    }

    private fun TestScope.createStorage(engine: TorrentMediaCacheEngine = createEngine()): DataStoreMediaCacheStorage {
        return DataStoreMediaCacheStorage(
            CACHE_MEDIA_SOURCE_ID,
            metadataStore,
            engine.also { cacheEngine = it },
            "本地",
            this.coroutineContext,
            clock = object : Clock {
                override fun now(): Instant {
                    return Instant.fromEpochMilliseconds(1725107383853)
                }
            },
        ).also {
            storages.add(it)
        }
    }

    private suspend fun TorrentMediaCacheEngine.TorrentMediaCache.getSession() =
        fileHandle.state.first()!!.session as AnitorrentDownloadSession

    private fun amendJsonString(
        @Language("json") string: String,
        block: JsonObjectBuilder.(origin: JsonObject) -> Unit
    ): String {
        json.decodeFromString(JsonObject.serializer(), string).let {
            return json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    putAll(it)
                    block(it)
                },
            )
        }
    }


    private fun mediaCacheMetadata() = MediaCacheMetadata(
        subjectId = "1",
        episodeId = "1",
        subjectNameCN = "1",
        subjectNames = emptyList(),
        episodeSort = EpisodeSort("02"),
        episodeEp = EpisodeSort("02"),
        episodeName = "测试剧集",
    )

    ///////////////////////////////////////////////////////////////////////////
    // simple create, restore, find
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `create cache then get from listFlow`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache
        assertSame(cache, storage.listFlow.first().single())
    }

    private suspend fun DataStoreMediaCacheStorage.cache(
        media: DefaultMedia,
        metadata: MediaCacheMetadata,
        resume: Boolean
    ) = cache(
        media,
        metadata,
        EpisodeMetadata("Test", null, EpisodeSort(1)), // doesn't matter, as we only test BT engine.
        resume,
    )

    @Test
    fun `create cache saves metadata`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache

        metadataFlow.first().filter { it.origin.mediaId == cache.origin.mediaId }.run {
            assertEquals(1, size)
            assertEquals(cache.origin.mediaId, first().origin.mediaId)
        }

        assertSame(cache, storage.listFlow.first().single())
    }

    @Test
    fun `create same cache twice`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache
        assertSame(cache, storage.listFlow.first().single())
        assertSame(cache, storage.cache(media, mediaCacheMetadata(), resume = false))
        assertSame(cache, storage.listFlow.first().single())
    }

    @Test
    fun `create and delete`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache = storage.cache(media, mediaCacheMetadata(), resume = false)
                as TorrentMediaCacheEngine.TorrentMediaCache

        metadataFlow.first().filter { it.origin.mediaId == cache.origin.mediaId }.run {
            assertEquals(1, size)
            assertEquals(cache.origin.mediaId, first().origin.mediaId)
        }

        assertNotNull(cache.fileHandle.state.first()).run {
            assertNotNull(handle)
            assertNotNull(entry)
        }

        assertEquals(cache, storage.listFlow.first().single())
        assertEquals(true, storage.delete(cache))

        metadataFlow.first().filter { it.origin.mediaId == cache.origin.mediaId }.run {
            assertEquals(0, size)
        }
        assertEquals(null, storage.listFlow.first().firstOrNull())
    }

    ///////////////////////////////////////////////////////////////////////////
    // restore
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `restorePersistedCaches - nothing`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )
        storage.restorePersistedCaches()
        assertEquals(0, storage.listFlow.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // cacheMediaSource
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `query cacheMediaSource`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val metadata = mediaCacheMetadata()
        val cache =
            storage.cache(media, metadata, resume = false) as TorrentMediaCacheEngine.TorrentMediaCache

        assertEquals(
            cache.getCachedMedia().unwrapCached(),
            storage.cacheMediaSource.fetch(
                MediaFetchRequest(
                    subjectId = "1",
                    episodeId = "1",
                    subjectNames = metadata.subjectNames,
                    episodeSort = metadata.episodeSort,
                    episodeName = metadata.episodeName,
                ),
            ).results.toList().single().media.unwrapCached(),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // metadata
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `cached media id`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val cache =
            storage.cache(media, mediaCacheMetadata(), resume = false) as TorrentMediaCacheEngine.TorrentMediaCache

        assertNotNull(cache.fileHandle.state.first()).run {
            assertNotNull(handle)
        }

        val cachedMedia = cache.getCachedMedia()
        assertEquals("$CACHE_MEDIA_SOURCE_ID:${media.mediaId}", cachedMedia.mediaId)
        assertEquals(CACHE_MEDIA_SOURCE_ID, cachedMedia.mediaSourceId)
        assertEquals(media, cachedMedia.origin)
    }

    ///////////////////////////////////////////////////////////////////////////
    // others
    ///////////////////////////////////////////////////////////////////////////

    private suspend fun DataStoreMediaCacheStorage.getUploaded() =
        stats.map { it.uploaded }.first()
}

private suspend fun <T> MutableSharedFlow<T>.emit(
    value: (T & Any).() -> T
) = emit(value(replayCache.first()!!))
