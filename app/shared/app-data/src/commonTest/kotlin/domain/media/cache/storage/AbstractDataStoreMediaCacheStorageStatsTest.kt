/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.tools.toProgress
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbstractDataStoreMediaCacheStorageStatsTest {
    @Test
    fun `stats should sum current cache session stats`() = runTest {
        val storage = TestStorage(backgroundScope.coroutineContext)
        val cache1 = testCache(
            mediaId = "media-1",
            episodeId = "1",
            stats = MutableStateFlow(
                sessionStats(
                    downloadedBytes = 1200L,
                    uploadedBytes = 300L,
                    downloadSpeed = 50L,
                    uploadSpeed = 10L,
                ),
            ),
        )
        val cache2 = testCache(
            mediaId = "media-2",
            episodeId = "2",
            stats = MutableStateFlow(
                sessionStats(
                    downloadedBytes = 800L,
                    uploadedBytes = 100L,
                    downloadSpeed = 20L,
                    uploadSpeed = 5L,
                ),
            ),
        )

        storage.listFlow.value = listOf(cache1, cache2)

        assertEquals(
            MediaStats(
                uploaded = 400L.bytes,
                downloaded = 2000L.bytes,
                uploadSpeed = 15L.bytes,
                downloadSpeed = 70L.bytes,
            ),
            storage.stats.first(),
        )
    }

    @Test
    fun `stats should update when cache session stats change or cache is deleted`() = runTest {
        val storage = TestStorage(backgroundScope.coroutineContext)
        val cache1Stats = MutableStateFlow(
            sessionStats(
                downloadedBytes = 1200L,
                uploadedBytes = 300L,
                downloadSpeed = 50L,
                uploadSpeed = 10L,
            ),
        )
        val cache2Stats = MutableStateFlow(
            sessionStats(
                downloadedBytes = 800L,
                uploadedBytes = 100L,
                downloadSpeed = 20L,
                uploadSpeed = 5L,
            ),
        )
        val cache1 = testCache("media-1", "1", cache1Stats)
        val cache2 = testCache("media-2", "2", cache2Stats)
        storage.listFlow.value = listOf(cache1, cache2)

        val emissions = mutableListOf<MediaStats>()
        val job = launch {
            storage.stats.take(3).toList(emissions)
        }

        runCurrent()
        cache1Stats.value = sessionStats(
            downloadedBytes = 1500L,
            uploadedBytes = 500L,
            downloadSpeed = 60L,
            uploadSpeed = 12L,
        )
        runCurrent()
        storage.delete(cache2)
        runCurrent()
        job.join()

        assertEquals(
            MediaStats(
                uploaded = 400L.bytes,
                downloaded = 2000L.bytes,
                uploadSpeed = 15L.bytes,
                downloadSpeed = 70L.bytes,
            ),
            emissions[0],
        )
        assertEquals(
            MediaStats(
                uploaded = 600L.bytes,
                downloaded = 2300L.bytes,
                uploadSpeed = 17L.bytes,
                downloadSpeed = 80L.bytes,
            ),
            emissions[1],
        )
        assertEquals(
            MediaStats(
                uploaded = 500L.bytes,
                downloaded = 1500L.bytes,
                uploadSpeed = 12L.bytes,
                downloadSpeed = 60L.bytes,
            ),
            emissions[2],
        )
        assertTrue(cache2.isDeleted.value)
    }
}

private class TestStorage(
    parentCoroutineContext: CoroutineContext,
) : AbstractDataStoreMediaCacheStorage(
    mediaSourceId = "test-storage",
    datastore = MemoryDataStore(emptyList()),
    engine = DummyMediaCacheEngine("test-storage"),
    displayName = "Test Storage",
    parentCoroutineContext = parentCoroutineContext,
) {
    override suspend fun restorePersistedCaches() = Unit
}

private fun testCache(
    mediaId: String,
    episodeId: String,
    stats: MutableStateFlow<MediaCache.SessionStats>,
): TestMediaCache {
    val episodeSort = EpisodeSort(episodeId.toInt())
    val origin = createTestDefaultMedia(
        mediaId = mediaId,
        mediaSourceId = "test-source",
        originalUrl = "https://example.com/$mediaId",
        download = ResourceLocation.HttpStreamingFile("https://example.com/$mediaId.m3u8"),
        originalTitle = "Episode $episodeId",
        publishedTime = 1L,
        properties = createTestMediaProperties(
            subjectName = "Test Subject",
            episodeName = "Episode $episodeId",
        ),
        episodeRange = EpisodeRange.single(episodeSort),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )
    val cachedMedia = CachedMedia(
        origin = origin,
        cacheMediaSourceId = "test-cache",
        download = ResourceLocation.LocalFile("C:/cache/$mediaId.mp4"),
    )

    return TestMediaCache(
        media = cachedMedia,
        metadata = MediaCacheMetadata(
            subjectId = "1",
            episodeId = episodeId,
            subjectNameCN = "Test Subject",
            subjectNames = listOf("Test Subject"),
            episodeSort = episodeSort,
            episodeEp = episodeSort,
            episodeName = "Episode $episodeId",
        ),
        sessionStats = stats,
    )
}

private fun sessionStats(
    downloadedBytes: Long,
    uploadedBytes: Long,
    downloadSpeed: Long,
    uploadSpeed: Long,
): MediaCache.SessionStats {
    return MediaCache.SessionStats(
        totalSize = 2048L.bytes,
        downloadedBytes = downloadedBytes.bytes,
        downloadSpeed = downloadSpeed.bytes,
        uploadedBytes = uploadedBytes.bytes,
        uploadSpeed = uploadSpeed.bytes,
        downloadProgress = 0f.toProgress(),
    )
}
