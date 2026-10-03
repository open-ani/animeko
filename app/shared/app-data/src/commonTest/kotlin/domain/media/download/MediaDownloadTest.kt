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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes

class MediaDownloadTest {
    private fun TestScope.download(
        cache: MediaCache,
        storage: DownloadTestStorage = DownloadTestStorage(),
    ): MediaDownload = MediaDownload(cache, storage, backgroundScope)

    /**
     * 推进一个快照采样周期.
     */
    private fun TestScope.tick() {
        advanceTimeBy(1.seconds)
        runCurrent()
    }

    @Test
    fun `pause only pauses downloads in progress`() = runTest {
        val cache = testDownload(1)
        val download = download(cache)

        download.pause()
        assertEquals(1, cache.pauseCalls)
        assertEquals(MediaCacheState.PAUSED, cache.state.value)

        download.pause()
        assertEquals(1, cache.pauseCalls)

        for (state in listOf(MediaCacheState.COMPLETED, MediaCacheState.FAILED)) {
            cache.state.value = state
            download.pause()
            assertEquals(1, cache.pauseCalls)
            assertEquals(state, cache.state.value)
        }
    }

    @Test
    fun `resume only resumes paused downloads`() = runTest {
        val cache = testDownload(1)
        val download = download(cache)

        download.resume()
        assertEquals(0, cache.resumeCalls)
        assertEquals(MediaCacheState.IN_PROGRESS, cache.state.value)

        for (state in listOf(MediaCacheState.COMPLETED, MediaCacheState.FAILED)) {
            cache.state.value = state
            download.resume()
            assertEquals(0, cache.resumeCalls)
            assertEquals(state, cache.state.value)
        }

        cache.state.value = MediaCacheState.PAUSED
        download.resume()
        assertEquals(1, cache.resumeCalls)
        assertEquals(MediaCacheState.IN_PROGRESS, cache.state.value)
    }

    @Test
    fun `claim marks the download busy until released and rejects a second claim`() = runTest {
        val download = download(testDownload(1))
        assertNull(download.operation.value)

        assertTrue(download.claim(DownloadOperation.Pause))
        assertEquals(DownloadOperation.Pause, download.operation.value)
        assertFalse(download.claim(DownloadOperation.Resume))
        assertEquals(DownloadOperation.Pause, download.operation.value)

        download.release()
        assertNull(download.operation.value)
        assertTrue(download.claim(DownloadOperation.Delete))
        assertEquals(DownloadOperation.Delete, download.operation.value)
    }

    @Test
    fun `failing pause propagates and leaves the state unchanged`() = runTest {
        val cache = testDownload(1)
        cache.onPause = { error("engine failure") }
        val download = download(cache)

        val failure = assertFailsWith<IllegalStateException> { download.pause() }
        assertEquals("engine failure", failure.message)
        assertEquals(MediaCacheState.IN_PROGRESS, cache.state.value)

        cache.onPause = {}
        download.pause()
        assertEquals(MediaCacheState.PAUSED, cache.state.value)
    }

    @Test
    fun `snapshot reflects state and file stats and playability and operation`() = runTest {
        val cache = testDownload(1)
        cache.fileStats.value = MediaCache.FileStats(totalSize = 100.bytes, downloadedBytes = 25.bytes)
        val storage = DownloadTestStorage()
        val download = download(cache, storage)
        val received = mutableListOf<DownloadSnapshot>()
        backgroundScope.launch { download.snapshot.collect { received += it } }
        runCurrent()

        val initial = received.first()
        assertEquals(download.id, initial.id)
        assertEquals(cache.metadata, initial.metadata)
        assertEquals(MediaCacheState.IN_PROGRESS, initial.status)
        assertEquals(cache.fileStats.value.downloadProgress, initial.progress)
        assertEquals(100.bytes, initial.totalSize)
        assertEquals(0.bytes, initial.downloadSpeed)
        assertTrue(initial.canPlay)
        assertEquals(cache.origin.mediaSourceId, initial.mediaSourceId)
        assertEquals(storage.engine.engineKey, initial.engineKey)
        assertNull(initial.operation)
        assertFalse(initial.isBusy)

        // 可播放性的变化不经采样, 立即反映.
        cache.canPlay.value = false
        runCurrent()
        assertFalse(received.last().canPlay)

        // 进度经采样, 在下一个采样周期反映.
        cache.fileStats.value = MediaCache.FileStats(totalSize = 100.bytes, downloadedBytes = 50.bytes)
        tick()
        assertEquals(cache.fileStats.value.downloadProgress, received.last().progress)

        // 操作与状态的变化立即反映.
        assertTrue(download.claim(DownloadOperation.Pause))
        runCurrent()
        received.last().let {
            assertEquals(DownloadOperation.Pause, it.operation)
            assertTrue(it.isBusy)
            assertEquals(MediaCacheState.IN_PROGRESS, it.status)
        }

        download.pause()
        download.release()
        runCurrent()
        received.last().let {
            assertNull(it.operation)
            assertFalse(it.isBusy)
            assertEquals(MediaCacheState.PAUSED, it.status)
        }
    }

    @Test
    fun `failing file stats report a failed snapshot and retry on the next start`() = runTest {
        val cache = FailingStatsCache(testDownload(1), failures = 1)
        val download = download(cache)
        val received = mutableListOf<DownloadSnapshot>()
        val collector = backgroundScope.launch { download.snapshot.collect { received += it } }
        runCurrent()
        received.last().let {
            assertEquals(MediaCacheState.FAILED, it.status)
            assertFalse(it.canPlay)
            assertTrue(it.progress.isUnspecified)
            assertTrue(it.totalSize.isUnspecified)
        }
        assertEquals(1, cache.attempts)

        // 最后一个订阅者离开 5 秒后共享停止, 再有订阅者时重新收集上游.
        collector.cancel()
        advanceTimeBy(6.seconds)
        runCurrent()
        val recovered = download.snapshot.first { it.status != MediaCacheState.FAILED }
        assertEquals(2, cache.attempts)
        assertEquals(MediaCacheState.IN_PROGRESS, recovered.status)
    }

    @Test
    fun `resubscribing within five seconds replays the latest snapshot without restarting upstream`() = runTest {
        val cache = testDownload(1)
        val download = download(cache)
        val subscriptions = mutableListOf<Int>()
        backgroundScope.launch { cache.fileStats.subscriptionCount.toList(subscriptions) }
        runCurrent()

        val first = download.snapshot.first()
        runCurrent()
        assertEquals(1, cache.fileStats.subscriptionCount.value)

        advanceTimeBy(4.seconds)
        runCurrent()
        assertEquals(1, cache.fileStats.subscriptionCount.value)
        var replayed: DownloadSnapshot? = null
        launch(start = CoroutineStart.UNDISPATCHED) { replayed = download.snapshot.first() }
        // 重放缓存直接提供最近的快照, 无需等待上游.
        assertEquals(first, replayed)
        assertFalse(subscriptions.drop(1).contains(0), "upstream must not restart: $subscriptions")

        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(0, cache.fileStats.subscriptionCount.value)
    }

    @Test
    fun `download speed follows growth of downloaded bytes`() = runTest {
        val cache = testDownload(1)
        cache.fileStats.value = MediaCache.FileStats(totalSize = 100_000.bytes, downloadedBytes = 0.bytes)
        val download = download(cache)
        val received = mutableListOf<DownloadSnapshot>()
        backgroundScope.launch { download.snapshot.collect { received += it } }
        runCurrent()
        assertEquals(0.bytes, received.last().downloadSpeed)

        val bytesPerSecond = 1000L
        repeat(10) { second ->
            cache.fileStats.value = MediaCache.FileStats(
                totalSize = 100_000.bytes,
                downloadedBytes = ((second + 1) * bytesPerSecond).bytes,
            )
            tick()
        }

        val speed = received.last().downloadSpeed.inBytes
        assertTrue(speed in (bytesPerSecond * 7 / 10)..bytesPerSecond, "speed=$speed")
    }
}
