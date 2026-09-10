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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.datasources.api.MediaCacheMetadata

class MediaDownloadManagerCreateDownloadTest {
    @Test
    fun `creation persists the selected media and starts danmaku saving in the background`() = runTest {
        val request = testDownloadRequest(1)
        val cache = testDownload(1)
        val metadata = MediaCacheMetadata(testDownloadSelection(1).fetchSession.request.first())
        val finishDanmaku = CompletableDeferred<Unit>()
        val requests = mutableListOf<DanmakuFetchRequest>()
        var persisted = false
        val storage = DownloadTestStorage().apply {
            create = { media, actualMetadata, episodeMetadata ->
                assertSame(cache.origin, media)
                assertEquals(metadata, actualMetadata)
                assertEquals(request.episode.toEpisodeMetadata(), episodeMetadata)
                persisted = true
                cache
            }
        }
        val manager = MediaDownloadManager(listOf(storage), backgroundScope) {
            assertTrue(persisted)
            finishDanmaku.await()
            requests += it
        }
        assertSame(cache, manager.createDownload(request.subject, request.episode, cache.origin, metadata))
        runCurrent()
        assertTrue(requests.isEmpty())
        finishDanmaku.complete(Unit)
        runCurrent()
        assertEquals(request.episode.episodeId, requests.single().episodeId)
        assertEquals(cache.origin.originalTitle, requests.single().filename)
    }

    @Test
    fun `storage failure propagates without starting ancillary work`() = runTest {
        val request = testDownloadRequest(1)
        val cache = testDownload(1)
        val failure = IllegalStateException("storage failed")
        val storage = DownloadTestStorage().apply { create = { _, _, _ -> throw failure } }
        val manager = MediaDownloadManager(listOf(storage), backgroundScope) { error("must not save danmaku") }
        assertSame(failure, assertFailsWith<IllegalStateException> {
            manager.createDownload(request.subject, request.episode, cache.origin, cache.metadata)
        })
        runCurrent()
    }

    @Test
    fun `construction starts no jobs and completed creations release their coroutines`() = runTest {
        val applicationJob = SupervisorJob(backgroundScope.coroutineContext[Job])
        val application = CoroutineScope(backgroundScope.coroutineContext + applicationJob)
        val storage = DownloadTestStorage().apply {
            create = { _, metadata, _ -> testDownload(metadata.episodeId.toInt()) }
        }
        val manager = MediaDownloadManager(listOf(storage), application, cacheDanmaku = {})
        runCurrent()
        assertEquals(0, applicationJob.children.count())
        repeat(5) { index ->
            val request = testDownloadRequest(index + 1)
            val cache = testDownload(index + 1)
            application.async {
                manager.createDownload(request.subject, request.episode, cache.origin, cache.metadata)
            }.await()
            runCurrent()
            assertEquals(0, applicationJob.children.count())
        }
        application.cancel()
    }
}
