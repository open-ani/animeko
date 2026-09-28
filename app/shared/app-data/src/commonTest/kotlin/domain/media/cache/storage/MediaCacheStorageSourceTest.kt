/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.storage

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaCacheStorageSourceTest {
    private fun metadata() = MediaCacheMetadata(
        subjectId = "1",
        episodeId = "1",
        subjectNameCN = "Test Subject",
        subjectNames = listOf("Test Subject"),
        episodeSort = EpisodeSort(1),
        episodeEp = EpisodeSort(1),
        episodeName = "Episode 1",
    )

    private fun originMedia(mediaId: String) = createTestDefaultMedia(
        mediaId = mediaId,
        mediaSourceId = "test-source",
        originalUrl = "https://example.com/$mediaId",
        download = ResourceLocation.HttpStreamingFile("https://example.com/$mediaId.m3u8"),
        originalTitle = "Episode 1",
        publishedTime = 1L,
        properties = createTestMediaProperties(
            subjectName = "Test Subject",
            episodeName = "Episode 1",
        ),
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )

    private fun completedCache(mediaId: String): MediaCache {
        val origin = originMedia(mediaId)
        return TestMediaCache(
            CachedMedia(
                origin = origin,
                cacheMediaSourceId = "test-storage",
                download = ResourceLocation.LocalFile("/cache/$mediaId.mp4"),
            ),
            metadata(),
        )
    }

    /**
     * 模拟尚未完成的下载任务: [MediaCache.getCachedMedia] 会抛出异常,
     * 与 [me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine] 对未完成下载的行为一致.
     */
    private fun incompleteTaskCache(mediaId: String): MediaCache {
        val origin = originMedia(mediaId)
        return object : TestMediaCache(
            CachedMedia(
                origin = origin,
                cacheMediaSourceId = "test-storage",
                download = ResourceLocation.LocalFile("/cache/$mediaId.mp4"),
            ),
            metadata(),
        ) {
            override suspend fun getCachedMedia(): CachedMedia {
                throw IllegalStateException("Download not completed, cannot get cached media for $mediaId")
            }
        }
    }

    private fun query() = MediaFetchRequest(
        subjectId = "1",
        episodeId = "1",
        subjectNames = listOf("Test Subject"),
        episodeSort = EpisodeSort(1),
        episodeName = "Episode 1",
    )

    @Test
    fun `fetch returns completed caches`() = runTest {
        val storage = TestMediaCacheStorage().apply {
            listFlow.value = listOf(completedCache("media-1"))
        }
        val source = MediaCacheStorageSource(storage, "Test")

        val results = source.fetch(query()).results.toList()

        assertEquals(1, results.size)
        assertTrue(results[0].media.isLocalCache())
    }

    @Test
    fun `fetch skips caches that fail to provide media instead of failing entirely`() = runTest {
        val storage = TestMediaCacheStorage().apply {
            // 未完成的任务排在前面, 确保单个失败不会毒化整个查询结果
            listFlow.value = listOf(
                incompleteTaskCache("media-task"),
                completedCache("media-1"),
                completedCache("media-2"),
            )
        }
        val source = MediaCacheStorageSource(storage, "Test")

        val results = source.fetch(query()).results.toList()

        assertEquals(
            listOf("test-storage:media-1:1", "test-storage:media-2:1"),
            results.map { it.media.mediaId }.sorted(),
        )
    }

    @Test
    fun `fetch returns empty when all caches fail to provide media`() = runTest {
        val storage = TestMediaCacheStorage().apply {
            listFlow.value = listOf(incompleteTaskCache("media-task"))
        }
        val source = MediaCacheStorageSource(storage, "Test")

        val results = source.fetch(query()).results.toList()

        assertTrue(results.isEmpty())
    }
}
