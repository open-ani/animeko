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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.cache.storage.TorrentMediaCacheStorage
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @see TorrentMediaCacheStorage
 */
class TorrentMediaCacheStorageTest : AbstractTorrentMediaCacheEngineTest() {

    private val metadataStore: DataStore<List<MediaCacheSave>> = MemoryDataStore(emptyList())
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

    private fun TestScope.createStorage(engine: TorrentMediaCacheEngine = createEngine()): TorrentMediaCacheStorage {
        return TorrentMediaCacheStorage(
            CACHE_MEDIA_SOURCE_ID,
            metadataStore,
            engine.also { cacheEngine = it },
            MutableStateFlow(1.2f),
            "本地",
            this.coroutineContext,
        ).also {
            storages.add(it)
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