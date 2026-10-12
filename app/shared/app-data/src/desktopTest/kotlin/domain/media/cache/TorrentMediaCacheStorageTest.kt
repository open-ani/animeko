/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheEpisodeEntity
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.cache.storage.TorrentMediaCacheStorage
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.torrent.api.TorrentHandleState
import me.him188.ani.app.torrent.api.files.AbstractTorrentFileEntry
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.unwrapCached
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @see TorrentMediaCacheStorage
 */
class TorrentMediaCacheStorageTest : AbstractTorrentMediaCacheEngineTest() {

    private val storages = mutableListOf<TorrentMediaCacheStorage>()

    private val metadataFlow = metadataStore.data
        .map { list ->
            list
                .filter { it.engine == CacheEngineKey }
                .sortedBy { it.origin.mediaId } // consistent stable order
        }


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

    @Test
    fun `persisted ownership is available before cache restoration`() = runTest {
        metadataStore.updateData {
            listOf(MediaCacheSave(testMedia, mediaCacheMetadata(), CacheEngineKey))
        }
        val disabled = UnsupportedTorrentEngine(createTestAnitorrentEngine(coroutineContext))
        val storage = createStorage(createEngine(engine = disabled))

        assertTrue(storage.listFlow.value.isEmpty())
        assertTrue(storage.hasRecordForMedia(testMedia.mediaId))
        assertFalse(storage.hasRecordForMedia("unrelated-media"))
    }

    @Test
    fun `resumeByUser turns an auto cache into an explicit one`() = runTest {
        val storage = createStorage(
            createEngine(onDownloadStarted = { it.onTorrentChecked() }),
        )

        val cache = storage.cache(testMedia, mediaCacheMetadata(autoCached = true), resume = true)

        cache.resumeByUser()
        advanceUntilIdle()
        assertEquals(false, cache.metadata.autoCached)
        assertEquals(FilePriority.NORMAL, cache.requestingPriority())

        assertEquals(
            false,
            metadataFlow.first().single { it.origin.mediaId == cache.origin.mediaId }.metadata.autoCached,
        )
    }

    @Test
    fun `downloading a season promotes the record left by playback instead of adding one`() = runTest {
        val storage = createStorage(
            createEngine(onDownloadStarted = { it.onTorrentChecked() }),
        )

        // 播放第 1 集时跟随播放建立的记录.
        val played = storage.cache(testMedia, mediaCacheMetadata(autoCached = true, episodeId = "1"), resume = true)

        // 批量下载整季: 每集一次请求, 共用同一个合集 media, autoCached 为 false.
        val ep1 = storage.cache(testMedia, mediaCacheMetadata(autoCached = false, episodeId = "1"), resume = true)
        val ep2 = storage.cache(testMedia, mediaCacheMetadata(autoCached = false, episodeId = "2"), resume = true)
        advanceUntilIdle()

        assertSame(played, ep1)
        assertEquals(listOf(ep1, ep2), storage.listFlow.first())

        val saves = metadataFlow.first()
        assertEquals(listOf("1", "2"), saves.map { it.metadata.episodeId }.sorted())
        assertEquals(false, saves.single { it.metadata.episodeId == "1" }.metadata.autoCached)
        assertEquals(false, ep1.metadata.autoCached)
        assertEquals(FilePriority.NORMAL, ep1.requestingPriority())
    }

    @Test
    fun `deleting one record of a pack keeps the row for the others`() = runTest {
        val storage = createStorage(
            createEngine(onDownloadStarted = { it.onTorrentChecked() }),
        )

        val ep1 = storage.cache(testMedia, mediaCacheMetadata(episodeId = "1"), resume = false)
        val ep2 = storage.cache(testMedia, mediaCacheMetadata(episodeId = "2"), resume = false)
        assertEquals(2, storage.listFlow.first().size)

        assertEquals(true, storage.delete(ep1))
        assertEquals(listOf(ep2), storage.listFlow.first())
        assertNotNull(torrentInfoDatabase.get(testMedia.mediaId))

        assertEquals(true, storage.delete(ep2))
        assertNull(torrentInfoDatabase.get(testMedia.mediaId))
    }

    @Test
    fun `a completed record does not make its pack siblings completed`() = runTest {
        val storage = createStorage(
            createEngine(onDownloadStarted = { it.onTorrentChecked() }),
        )
        val ep1 = storage.cache(testMedia, mediaCacheMetadata(episodeId = "1"), resume = false)
        storage.cache(testMedia, mediaCacheMetadata(episodeId = "2"), resume = false)

        val path = assertNotNull(ep1.fileHandle.entry.first()).pathInTorrent
        val row = assertNotNull(torrentInfoDatabase.get(testMedia.mediaId))

        File(dir, row.relativeDir).resolve(path).apply {
            parentFile.mkdirs()
            writeText("x")
        }

        torrentInfoDatabase.upsertEpisode(
            TorrentCacheEpisodeEntity(testMedia.mediaId, episodeId = "1", completed = true, pathInTorrent = path),
        )
        storage.close()

        val restored = createStorage(createEngine(onDownloadStarted = { it.onTorrentChecked() }))
        restored.restorePersistedCaches()

        val byEpisode = restored.listFlow.first { it.size == 2 }.associateBy { it.metadata.episodeId }
        assertIs<LocalFileMediaCache>(byEpisode.getValue("1"))
        assertIs<TorrentMediaCacheEngine.TorrentMediaCache>(byEpisode.getValue("2"))
    }

    @Test
    fun `a record from before the upgrade takes completion from the torrent row`() = runTest {
        val storage = createStorage(
            createEngine(onDownloadStarted = { it.onTorrentChecked() }),
        )
        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)
        val path = assertNotNull(cache.fileHandle.entry.first()).pathInTorrent
        val row = assertNotNull(torrentInfoDatabase.get(testMedia.mediaId))

        // Before this version only the torrent row knew, and there was no episode record at all.
        torrentInfoDatabase.upsert(row.copy(completed = true, pathInTorrent = path))
        torrentInfoDatabase.deleteEpisode(testMedia.mediaId, "1")
        File(dir, row.relativeDir).resolve(path).apply {
            parentFile.mkdirs()
            writeText("x")
        }
        storage.close()

        val restored = createStorage(createEngine(onDownloadStarted = { it.onTorrentChecked() }))
        restored.restorePersistedCaches()

        assertIs<LocalFileMediaCache>(restored.listFlow.first { it.isNotEmpty() }.single())

        val record = assertNotNull(torrentInfoDatabase.getEpisode(testMedia.mediaId, "1"))
        assertEquals(true, record.completed)
        assertEquals(path, record.pathInTorrent)
    }

    @Test
    fun `a completed record restores while the engine is disabled`() = runTest {
        val storage = createStorage(
            createEngine(onDownloadStarted = { it.onTorrentChecked() }),
        )
        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)
        val path = assertNotNull(cache.fileHandle.entry.first()).pathInTorrent
        val row = assertNotNull(torrentInfoDatabase.get(testMedia.mediaId))
        File(dir, row.relativeDir).resolve(path).apply {
            parentFile.mkdirs()
            writeText("x")
        }
        torrentInfoDatabase.upsertEpisode(
            TorrentCacheEpisodeEntity(testMedia.mediaId, episodeId = "1", completed = true, pathInTorrent = path),
        )
        storage.close()

        val disabled = UnsupportedTorrentEngine(createTestAnitorrentEngine(coroutineContext))
        val restored = createStorage(createEngine(engine = disabled))
        restored.restorePersistedCaches()

        assertIs<LocalFileMediaCache>(restored.listFlow.first { it.isNotEmpty() }.single())
    }

    private class UnsupportedTorrentEngine(
        private val delegate: TorrentEngine,
    ) : TorrentEngine by delegate {
        override val isSupported: Boolean get() = false
    }

    @Test
    fun `enabling an engine restores missing downloads and retains live handles`() = runTest {
        val original = createStorage(createEngine(onDownloadStarted = { it.onTorrentChecked() }))
        original.cache(testMedia, mediaCacheMetadata(), resume = false)
        original.close()

        val available = MutableStateFlow(false)
        val delegate = createTestAnitorrentEngine(coroutineContext)
        val switchable = object : TorrentEngine by delegate {
            override val isSupported: Boolean get() = available.value
        }
        val restored = createStorage(
            createEngine(engine = switchable, onDownloadStarted = { it.onTorrentChecked() }),
            engineAvailability = available,
        )
        restored.restorePersistedCaches()
        advanceUntilIdle()
        assertTrue(restored.listFlow.value.isEmpty())
        assertTrue(restored.hasRecordForMedia(testMedia.mediaId))

        available.value = true
        val cache = restored.listFlow.first { it.isNotEmpty() }.single()
        assertEquals(testMedia.mediaId, cache.origin.mediaId)
        available.value = false
        runCurrent()
        available.value = true
        runCurrent()
        assertSame(cache, restored.listFlow.value.single())
    }

    private suspend fun TorrentMediaCacheEngine.TorrentMediaCache.requestingPriority(): FilePriority {
        val entry = assertNotNull(fileHandle.entry.first())
        return (entry as AbstractTorrentFileEntry).requestingPriority
    }

    private fun TestScope.createStorage(
        engine: TorrentMediaCacheEngine = createEngine(),
        engineAvailability: Flow<Boolean> = flowOf(true),
    ): TorrentMediaCacheStorage {
        return TorrentMediaCacheStorage(
            CACHE_MEDIA_SOURCE_ID,
            metadataStore,
            engine.also { cacheEngine = it },
            MutableStateFlow(1.2f),
            "本地",
            this.coroutineContext,
            engineAvailability,
        ).also {
            storages.add(it)
        }
    }

    private fun mediaCacheMetadata(autoCached: Boolean = false, episodeId: String = "1") = MediaCacheMetadata(
        subjectId = "1",
        episodeId = episodeId,
        subjectNameCN = "1",
        subjectNames = emptyList(),
        episodeSort = EpisodeSort("02"),
        episodeEp = EpisodeSort("02"),
        episodeName = "测试剧集",
        autoCached = autoCached,
    )

    ///////////////////////////////////////////////////////////////////////////
    // downloader status
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `downloader status reports the engine state and connected peers`() = runTest {
        val storage = createStorage(createEngine(onDownloadStarted = { it.onTorrentChecked() }))
        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)
        assertEquals(
            DownloaderStatus.Torrent(
                serviceConnected = true,
                startup = DownloaderStatus.TorrentStartup.STARTED,
                state = TorrentHandleState.DOWNLOADING,
                connectedPeers = 0,
                seeds = 0,
            ),
            cache.downloaderStatus.first(),
        )
    }

    @Test
    fun `downloader status reports startup timeout when torrent info never arrives`() = runTest {
        // 引擎从不报告种子信息, 启动阶段在虚拟时间中超时.
        val storage = createStorage(createEngine())
        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)
        assertEquals(
            DownloaderStatus.Torrent(
                serviceConnected = true,
                startup = DownloaderStatus.TorrentStartup.TIMED_OUT,
                state = null,
                connectedPeers = 0,
                seeds = 0,
            ),
            cache.downloaderStatus.first(),
        )
        assertEquals(MediaCacheState.FAILED, cache.state.first())
    }

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

        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)
        assertSame(cache, storage.listFlow.first().single())
        assertNotNull(torrentInfoDatabase.get("dmhy.2"))
    }

    private suspend fun TorrentMediaCacheStorage.cache(
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

        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)

        metadataFlow.first().filter { it.origin.mediaId == cache.origin.mediaId }.run {
            assertEquals(1, size)
            assertEquals(cache.origin.mediaId, first().origin.mediaId)
        }

        assertSame(cache, storage.listFlow.first().single())
        assertNotNull(torrentInfoDatabase.get(testMedia.mediaId))
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

        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)
        assertSame(cache, storage.listFlow.first().single())
        assertNotNull(torrentInfoDatabase.get(testMedia.mediaId))
        assertSame(cache, storage.cache(testMedia, mediaCacheMetadata(), resume = false))
        assertSame(cache, storage.listFlow.first().single())
        assertEquals(1, torrentInfoDatabase.getAll().first().size)
    }

    @Test
    fun `concurrent cache calls reuse one persisted record`() = runTest {
        val storage = createStorage(createEngine(onDownloadStarted = { it.onTorrentChecked() }))
        val caches = List(6) {
            async { storage.cache(testMedia, mediaCacheMetadata(), resume = false) }
        }.awaitAll()
        caches.forEach { assertSame(caches.first(), it) }
        assertSame(caches.first(), storage.listFlow.first().single())
        assertEquals(1, metadataFlow.first().size)
        assertEquals(1, torrentInfoDatabase.getAll().first().size)
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

        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)

        metadataFlow.first().filter { it.origin.mediaId == cache.origin.mediaId }.run {
            assertEquals(1, size)
            assertEquals(cache.origin.mediaId, first().origin.mediaId)
        }
        assertNotNull(torrentInfoDatabase.get(testMedia.mediaId))

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
        assertNull(torrentInfoDatabase.get(testMedia.mediaId))
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

    @Test
    fun `restorePersistedCaches restores cache when requested immediately after construction`() = runTest {
        val originalStorage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )
        val metadata = mediaCacheMetadata()
        val originalCache = originalStorage.cache(testMedia, metadata, resume = false)
        originalStorage.close()

        val restoredStorage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        restoredStorage.restorePersistedCaches()

        val restoredCache = restoredStorage.listFlow.first { it.isNotEmpty() }.single()
        assertEquals(originalCache.origin.mediaId, restoredCache.origin.mediaId)
        assertEquals(metadata, restoredCache.metadata)
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
        val cache = storage.cache(testMedia, metadata, resume = false)

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
        assertNotNull(torrentInfoDatabase.get(cache.origin.mediaId))
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

        val cache = storage.cache(testMedia, mediaCacheMetadata(), resume = false)

        assertNotNull(cache.fileHandle.state.first()).run {
            assertNotNull(handle)
        }

        val cachedMedia = cache.getCachedMedia()
        assertEquals("$CACHE_MEDIA_SOURCE_ID:${testMedia.mediaId}", cachedMedia.mediaId)
        assertEquals(CACHE_MEDIA_SOURCE_ID, cachedMedia.mediaSourceId)
        assertEquals(testMedia, cachedMedia.origin)
    }

    @Test
    fun `create two caches with same episode id`() = runTest {
        val storage = createStorage(
            createEngine(
                onDownloadStarted = {
                    it.onTorrentChecked()
                },
            ),
        )

        val metadata = mediaCacheMetadata()
        val testMedia2 = createTestDefaultMedia(
            mediaId = "dmhy.3",
            mediaSourceId = "dmhy",
            originalTitle = "夜晚的水母不会游泳 02 测试剧集2",
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:2"),
            originalUrl = "https://example.com/2",
            publishedTime = 1724493292759,
            episodeRange = EpisodeRange.single(EpisodeSort(2)),
            properties = createTestMediaProperties(),
            kind = MediaSourceKind.BitTorrent,
            location = MediaSourceLocation.Online,
        )

        storage.cache(testMedia, metadata, resume = false)
        storage.cache(testMedia2, metadata, resume = false)

        assertEquals(2, storage.listFlow.first().size)
        assertEquals(2, torrentInfoDatabase.getAll().first().size)
    }
}
