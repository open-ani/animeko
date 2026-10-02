/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes

class AddDownloadUseCaseTest {
    private val subject = requestTestSubject().subjectInfo
    private val episode = requestTestEpisode(1)
    private val media = requestTestMedia(1)
    private val metadata = MediaCacheMetadata(MediaFetchRequest.create(subject, episode))

    @Test
    fun `mismatched metadata is rejected before touching the storage`() = runTest {
        val storage = DownloadTestStorage().apply { create = { _, _, _ -> fail("must not create") } }
        val requests = mutableListOf<DanmakuFetchRequest>()
        val useCase = AddDownloadUseCaseImpl(MediaDownloadManager(listOf(storage), backgroundScope)) { requests += it }

        assertFailsWith<IllegalArgumentException> { useCase(subject, episode, media, metadata.copy(subjectId = "2")) }
        assertFailsWith<IllegalArgumentException> { useCase(subject, episode, media, metadata.copy(episodeId = "2")) }
        runCurrent()
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `successful creation returns the storage record and caches danmaku in the background`() = runTest {
        val cache = requestTestCache(media, subjectId = subject.subjectId, episodeId = episode.episodeId).apply {
            fileStats.value = MediaCache.FileStats(totalSize = 1234.bytes, downloadedBytes = 0.bytes)
        }
        var persisted = false
        val storage = DownloadTestStorage().apply {
            create = { actualMedia, actualMetadata, episodeMetadata ->
                assertSame(media, actualMedia)
                assertEquals(metadata, actualMetadata)
                assertEquals(episode.toEpisodeMetadata(), episodeMetadata)
                persisted = true
                cache
            }
        }
        val finishDanmaku = CompletableDeferred<Unit>()
        val requests = mutableListOf<DanmakuFetchRequest>()
        val useCase = AddDownloadUseCaseImpl(MediaDownloadManager(listOf(storage), backgroundScope)) {
            assertTrue(persisted)
            finishDanmaku.await()
            requests += it
        }

        assertSame(cache, useCase(subject, episode, media, metadata))
        runCurrent()
        assertTrue(requests.isEmpty())
        finishDanmaku.complete(Unit)
        runCurrent()

        val request = requests.single()
        assertEquals(subject.subjectId, request.subjectId)
        assertEquals(subject.displayName, request.subjectPrimaryName)
        assertEquals(episode.episodeId, request.episodeId)
        assertEquals(episode.sort, request.episodeSort)
        assertEquals(episode.ep, request.episodeEp)
        assertEquals(media.originalTitle, request.filename)
        assertEquals(1234L, request.fileSize)
    }

    @Test
    fun `unspecified total size is reported as unknown file size`() = runTest {
        val cache = requestTestCache(media, subjectId = subject.subjectId, episodeId = episode.episodeId)
        val storage = DownloadTestStorage().apply { create = { _, _, _ -> cache } }
        val requests = mutableListOf<DanmakuFetchRequest>()
        val useCase = AddDownloadUseCaseImpl(MediaDownloadManager(listOf(storage), backgroundScope)) { requests += it }

        assertSame(cache, useCase(subject, episode, media, metadata))
        runCurrent()
        assertNull(requests.single().fileSize)
    }

    @Test
    fun `danmaku caching failure is logged and does not affect the result`() = runTest {
        val cache = requestTestCache(media, subjectId = subject.subjectId, episodeId = episode.episodeId)
        val storage = DownloadTestStorage().apply { create = { _, _, _ -> cache } }
        var attempts = 0
        val useCase = AddDownloadUseCaseImpl(MediaDownloadManager(listOf(storage), backgroundScope)) {
            attempts++
            error("danmaku unavailable")
        }

        assertSame(cache, useCase(subject, episode, media, metadata))
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(backgroundScope.isActive)
    }

    @Test
    fun `storage failure propagates and skips danmaku`() = runTest {
        val failure = IllegalStateException("storage failed")
        val storage = DownloadTestStorage().apply { create = { _, _, _ -> throw failure } }
        val useCase = AddDownloadUseCaseImpl(MediaDownloadManager(listOf(storage), backgroundScope)) {
            fail("must not cache danmaku")
        }

        assertSame(failure, assertFailsWith<IllegalStateException> { useCase(subject, episode, media, metadata) })
        runCurrent()
    }
}
