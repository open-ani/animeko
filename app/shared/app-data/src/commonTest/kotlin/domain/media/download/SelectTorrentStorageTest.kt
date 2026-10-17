/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.SOURCE_DMHY
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * @see selectTorrentStorage
 */
class SelectTorrentStorageTest {
    private fun createMedia(mediaId: String) = createTestDefaultMedia(
        mediaId = mediaId,
        mediaSourceId = SOURCE_DMHY,
        originalTitle = "[桜都字幕组] 孤独摇滚 01",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 1,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf("CHS"),
            resolution = "1080P",
            alliance = "桜都字幕组",
            size = 122.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    )

    private fun metadata(episodeId: String) = MediaCacheMetadata(
        subjectId = "1",
        episodeId = episodeId,
        subjectNameCN = "孤独摇滚",
        subjectNames = listOf("孤独摇滚"),
        episodeSort = EpisodeSort(episodeId),
        episodeEp = EpisodeSort(episodeId),
        episodeName = "test",
    )

    private class FakeStorage(engineKey: MediaCacheEngineKey) : MediaCacheStorage {
        var enabled = true
        override val mediaSourceId: String get() = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID
        override val cacheMediaSource: MediaSource get() = throw UnsupportedOperationException()
        override val engine: MediaCacheEngine = object : MediaCacheEngine by DummyMediaCacheEngine(mediaSourceId, engineKey = engineKey) {
            override fun supports(media: Media): Boolean = enabled
        }
        override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())
        override val stats: Flow<MediaStats> = flowOf(MediaStats.Unspecified)

        override suspend fun restorePersistedCaches() = Unit

        override suspend fun cache(
            media: Media,
            metadata: MediaCacheMetadata,
            episodeMetadata: EpisodeMetadata,
            resume: Boolean,
        ): MediaCache = throw UnsupportedOperationException()

        override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean = false

        override fun close() = Unit
    }

    private fun FakeStorage.addRecord(media: Media, episodeId: String) {
        listFlow.value += DummyMediaCache(media, metadata(episodeId), mediaSourceId)
    }

    private val pikPak = FakeStorage(MediaCacheEngineKey.PikPak)
    private val anitorrent = FakeStorage(MediaCacheEngineKey.Anitorrent)
    private val storages = listOf(pikPak, anitorrent)

    @Test
    fun `没有任何记录时用偏好的引擎`() = runTest {
        assertEquals(pikPak, selectTorrentStorage(storages, createMedia("$SOURCE_DMHY.1")))
    }

    @Test
    fun `已有 anitorrent 记录时即使偏好 PikPak 也走 anitorrent`() = runTest {
        val media = createMedia("$SOURCE_DMHY.1")
        anitorrent.addRecord(media, episodeId = "1")

        assertEquals(anitorrent, selectTorrentStorage(storages, media))
    }

    @Test
    fun `同一个种子的另一集也跟随已有记录`() = runTest {
        val media = createMedia("$SOURCE_DMHY.1")
        anitorrent.addRecord(media, episodeId = "1")

        // The second episode has no record yet, but it is the same torrent: caching it with the other
        // engine would produce two CachedMedia with the same id.
        assertEquals(anitorrent, selectTorrentStorage(storages, media, playedWith = MediaCacheEngineKey.PikPak))
    }

    @Test
    fun `另一个种子不受已有记录影响`() = runTest {
        anitorrent.addRecord(createMedia("$SOURCE_DMHY.1"), episodeId = "1")

        assertEquals(pikPak, selectTorrentStorage(storages, createMedia("$SOURCE_DMHY.2")))
    }

    @Test
    fun `没有记录时跟随播放使用的引擎`() = runTest {
        assertEquals(
            anitorrent,
            selectTorrentStorage(storages, createMedia("$SOURCE_DMHY.1"), playedWith = MediaCacheEngineKey.Anitorrent),
        )
    }

    @Test
    fun `没有可用 storage 时返回 null`() = runTest {
        assertNull(selectTorrentStorage(emptyList(), createMedia("$SOURCE_DMHY.1")))
    }

    @Test
    fun `disabled owner blocks another engine even when playback uses it`() = runTest {
        val media = createMedia("$SOURCE_DMHY.1")
        pikPak.addRecord(media, episodeId = "1")
        pikPak.enabled = false

        assertNull(selectTorrentStorage(storages, media, playedWith = MediaCacheEngineKey.Anitorrent))
        assertEquals(anitorrent, selectTorrentStorage(storages, createMedia("$SOURCE_DMHY.2")))

        pikPak.enabled = true
        assertEquals(pikPak, selectTorrentStorage(storages, media))
    }
}
