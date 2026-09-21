/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.storage

import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.domain.media.cache.LocalFileMediaCache
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.inSystem

class AbstractDataStoreMediaCacheStorageRestoreTest {
    @Test
    fun `completed records of one pack are restored separately and only once`() = runTest {
        val storage = TestStorage(backgroundScope.coroutineContext, listOf("1", "2").map { save(it) })
        assertEquals(listOf("1", "2"), storage.refreshCache().map { it.metadata.episodeId }.sorted())
        assertEquals(listOf("1", "2"), storage.listFlow.first().map { it.metadata.episodeId }.sorted())

        // 已恢复的不再恢复, 也不丢
        assertEquals(emptyList(), storage.refreshCache())
        assertEquals(listOf("1", "2"), storage.listFlow.first().map { it.metadata.episodeId }.sorted())
    }

    private class TestStorage(
        parentCoroutineContext: CoroutineContext,
        saves: List<MediaCacheSave>,
    ) : AbstractDataStoreMediaCacheStorage(
        mediaSourceId = "test-storage",
        datastore = MemoryDataStore(saves),
        engine = LocalFileEngine,
        displayName = "Test Storage",
        parentCoroutineContext = parentCoroutineContext,
    ) {
        override suspend fun restorePersistedCaches() = Unit
    }

    /**
     * 每条记录都恢复为已完成的本地文件.
     */
    private object LocalFileEngine : MediaCacheEngine by DummyMediaCacheEngine("test-storage") {
        override suspend fun restore(origin: Media, metadata: MediaCacheMetadata, parentContext: CoroutineContext): MediaCache =
            LocalFileMediaCache(origin, metadata, Path("/cache/${metadata.episodeId}.mkv").inSystem) {}
    }

    private val pack = createTestDefaultMedia(
        mediaId = "pack",
        mediaSourceId = "test-source",
        originalUrl = "https://example.com/pack",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:pack"),
        originalTitle = "Pack 01-02",
        publishedTime = 1L,
        properties = createTestMediaProperties(subjectName = "Test Subject"),
        episodeRange = EpisodeRange.range(EpisodeSort(1), EpisodeSort(2)),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.BitTorrent,
    )

    private fun save(episodeId: String) = MediaCacheSave(
        origin = pack,
        metadata = MediaCacheMetadata(
            subjectId = "1",
            episodeId = episodeId,
            subjectNameCN = "Test Subject",
            subjectNames = listOf("Test Subject"),
            episodeSort = EpisodeSort(episodeId.toInt()),
            episodeEp = EpisodeSort(episodeId.toInt()),
            episodeName = "Episode $episodeId",
        ),
        engine = LocalFileEngine.engineKey,
    )
}
