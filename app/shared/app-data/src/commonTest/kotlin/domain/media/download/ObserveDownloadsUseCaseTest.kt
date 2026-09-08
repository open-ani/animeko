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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.cache.MediaCacheState

class ObserveDownloadsUseCaseTest {
    @Test
    fun `loaded empty list is emitted when there are no storages`() = runTest {
        val manager = MediaDownloadManager(emptyList(), backgroundScope)
        assertEquals(emptyList(), ObserveDownloadsUseCase(manager)().first())
    }

    @Test
    fun `membership and status updates are emitted and unrelated subjects are excluded`() = runTest {
        val storage = DownloadTestStorage()
        val first = testDownload(1)
        val otherSubject = testDownload(2, subjectId = 2)
        storage.listFlow.value = listOf(first, otherSubject)
        val observer = ObserveDownloadsUseCase(MediaDownloadManager(listOf(storage), backgroundScope))
        val received = mutableListOf<List<DownloadSnapshot>>()
        val job = backgroundScope.launch { observer(1).toList(received) }
        runCurrent()
        assertEquals(listOf(first.cacheId), received.last().map { it.id })
        first.state.value = MediaCacheState.PAUSED
        runCurrent()
        assertEquals(MediaCacheState.PAUSED, received.last().single().status)
        val second = testDownload(3)
        storage.listFlow.value += second
        runCurrent()
        assertEquals(setOf(first.cacheId, second.cacheId), received.last().map { it.id }.toSet())
        storage.listFlow.value = listOf(otherSubject)
        runCurrent()
        assertTrue(received.last().isEmpty())
        job.cancel()
        runCurrent()
        assertEquals(0, first.fileStats.subscriptionCount.value)
        assertEquals(0, second.state.subscriptionCount.value)
    }

    @Test
    fun `adding downloads does not restart existing progress collectors`() = runTest {
        val storage = DownloadTestStorage()
        val first = testDownload(1)
        storage.listFlow.value = listOf(first)
        val manager = MediaDownloadManager(listOf(storage), backgroundScope)
        val counts = mutableListOf<Int>()
        backgroundScope.launch { first.fileStats.subscriptionCount.toList(counts) }
        val job = backgroundScope.launch { ObserveDownloadsUseCase(manager)().collect {} }
        runCurrent()
        val boundary = counts.size
        storage.listFlow.value += testDownload(2)
        runCurrent()
        assertFalse(counts.drop(boundary).contains(0))
        job.cancel()
    }
}
