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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes

class MediaDownloadManagerTest {
    private fun TestScope.manager(vararg storages: MediaCacheStorage): MediaDownloadManager =
        MediaDownloadManager(storages.toList(), backgroundScope)

    /**
     * 推进一个快照采样周期.
     */
    private fun TestScope.tick() {
        advanceTimeBy(1.seconds)
        runCurrent()
    }

    private fun stats(uploaded: Long, downloaded: Long, uploadSpeed: Long, downloadSpeed: Long) = MediaStats(
        uploaded = uploaded.bytes,
        downloaded = downloaded.bytes,
        uploadSpeed = uploadSpeed.bytes,
        downloadSpeed = downloadSpeed.bytes,
    )

    private fun storage(key: MediaCacheEngineKey, supports: Boolean = true) =
        DownloadTestStorage(testDownloadEngine(key, supports))

    // downloads

    @Test
    fun `downloads keep instances for unchanged caches`() = runTest {
        val storage = DownloadTestStorage()
        val first = testDownload(1)
        storage.listFlow.value = listOf(first)
        val manager = manager(storage)
        runCurrent()

        val download = manager.downloads.value.single()
        assertSame(first, download.cache)
        assertSame(storage, download.storage)
        assertSame(download, manager.findDownload(first.cacheId))
        assertSame(download, manager.downloadOf(first))

        val second = testDownload(2)
        storage.listFlow.value = listOf(first, second)
        runCurrent()
        assertEquals(listOf(first, second), manager.downloads.value.map { it.cache })
        assertSame(download, manager.downloads.value.first { it.cache === first })
        assertEquals(listOf(second), manager.findCaches { it === second })

        storage.listFlow.value = listOf(second)
        runCurrent()
        assertNull(manager.findDownload(first.cacheId))
        assertNull(manager.downloadOf(first))
    }

    @Test
    fun `duplicate ids keep the record of the first registered storage`() = runTest {
        val first = DownloadTestStorage().apply { listFlow.value = listOf(testDownload(1)) }
        val second = DownloadTestStorage().apply { listFlow.value = listOf(testDownload(1), testDownload(2)) }
        val manager = manager(first, second)
        runCurrent()

        val downloads = manager.downloads.value
        assertEquals(listOf(testDownload(1).cacheId, testDownload(2).cacheId), downloads.map { it.id })
        assertSame(first, downloads[0].storage)
        assertSame(second, downloads[1].storage)
    }

    @Test
    fun `removed downloads are closed and stop sharing snapshots`() = runTest {
        val storage = DownloadTestStorage()
        val kept = testDownload(1)
        val removed = testDownload(2)
        storage.listFlow.value = listOf(kept, removed)
        val manager = manager(storage)
        runCurrent()
        val keptDownload = assertNotNull(manager.findDownload(kept.cacheId))
        val removedDownload = assertNotNull(manager.findDownload(removed.cacheId))

        storage.listFlow.value = listOf(kept)
        runCurrent()

        // 已关闭的实例不再为订阅者启动上游; 保留的实例照常共享快照.
        val received = mutableListOf<DownloadSnapshot>()
        backgroundScope.launch { removedDownload.snapshot.collect { received += it } }
        backgroundScope.launch { keptDownload.snapshot.collect {} }
        runCurrent()
        assertTrue(received.isEmpty())
        assertEquals(0, removed.fileStats.subscriptionCount.value)
        assertEquals(1, kept.fileStats.subscriptionCount.value)
        assertSame(keptDownload, manager.findDownload(kept.cacheId))
    }

    @Test
    fun `duplicate ids keep the retained instance across updates of the other storage`() = runTest {
        val retainedCache = testDownload(1)
        val first = DownloadTestStorage().apply { listFlow.value = listOf(retainedCache) }
        val second = DownloadTestStorage().apply { listFlow.value = listOf(testDownload(1)) }
        val manager = manager(first, second)
        runCurrent()
        val retained = manager.downloads.value.single()
        assertSame(first, retained.storage)

        second.listFlow.value = listOf(testDownload(1), testDownload(2))
        runCurrent()
        assertSame(retained, manager.findDownload(retainedCache.cacheId))
        assertEquals(listOf(retainedCache.cacheId, testDownload(2).cacheId), manager.downloads.value.map { it.id })

        // 保留的实例没有被关闭, 仍可共享快照.
        backgroundScope.launch { retained.snapshot.collect {} }
        runCurrent()
        assertEquals(1, retainedCache.fileStats.subscriptionCount.value)
    }

    @Test
    fun `findCaches reads storages directly while findDownload follows the aggregated list`() = runTest {
        val storage = DownloadTestStorage()
        val manager = manager(storage)
        runCurrent()
        val cache = testDownload(1)
        storage.listFlow.value = listOf(cache)
        // 未推进调度器: 存储已更新, 聚合列表尚未更新.
        assertEquals(listOf(cache), manager.findCaches { true })
        assertNull(manager.findDownload(cache.cacheId))
        runCurrent()
        assertNotNull(manager.findDownload(cache.cacheId))

        storage.listFlow.value = emptyList()
        assertEquals(emptyList(), manager.findCaches { true })
        assertNotNull(manager.findDownload(cache.cacheId))
    }

    @Test
    fun `createDownload returns the record another storage already holds`() = runTest {
        val existing = testDownload(1)
        val holder = storage(MediaCacheEngineKey.Anitorrent).apply { listFlow.value = listOf(existing) }
        val target = storage(MediaCacheEngineKey.WebM3u).apply { create = { _, _, _ -> error("must not create") } }
        val manager = manager(holder, target)
        runCurrent()

        val episodeMetadata = EpisodeMetadata("Episode 1", EpisodeSort(1), EpisodeSort(1))
        assertSame(existing, manager.createDownload(existing.origin, testMetadata(1), episodeMetadata, target))

        // 其他剧集不受影响, 仍在指定存储中创建.
        val other = testDownload(2)
        target.create = { _, _, _ -> other }
        assertSame(other, manager.createDownload(other.origin, testMetadata(2), episodeMetadata, target))
    }

    @Test
    fun `snapshots emit an empty list without storages`() = runTest {
        val manager = manager()
        assertEquals(emptyList(), manager.snapshots().first())
        assertEquals(emptyList(), manager.snapshots(1).first())
        assertEquals(emptyList(), manager.downloads.value)
    }

    @Test
    fun `snapshots wait for storages to load`() = runTest {
        val list = MutableSharedFlow<List<MediaCache>>(replay = 1)
        val storage = object : MediaCacheStorage by DownloadTestStorage() {
            override val listFlow: Flow<List<MediaCache>> get() = list
        }
        val manager = manager(storage)
        val received = mutableListOf<List<DownloadSnapshot>>()
        backgroundScope.launch { manager.snapshots().collect { received += it } }
        runCurrent()
        assertTrue(received.isEmpty())
        assertEquals(emptyList(), manager.downloads.value)

        assertTrue(list.tryEmit(emptyList()))
        runCurrent()
        assertEquals(listOf(emptyList()), received)
    }

    @Test
    fun `snapshots follow membership and status changes of one subject`() = runTest {
        val storage = DownloadTestStorage()
        val first = testDownload(1)
        val otherSubject = testDownload(2, subjectId = 2)
        storage.listFlow.value = listOf(first, otherSubject)
        val manager = manager(storage)
        val received = mutableListOf<List<DownloadSnapshot>>()
        backgroundScope.launch { manager.snapshots(1).collect { received += it } }
        runCurrent()
        assertEquals(listOf(first.cacheId), received.last().map { it.id })

        first.state.value = MediaCacheState.PAUSED
        tick()
        assertEquals(MediaCacheState.PAUSED, received.last().single().status)

        val second = testDownload(3)
        storage.listFlow.value += second
        runCurrent()
        assertEquals(listOf(first.cacheId, second.cacheId), received.last().map { it.id })

        storage.listFlow.value = listOf(otherSubject)
        runCurrent()
        assertEquals(emptyList(), received.last())

        assertEquals(listOf(otherSubject.cacheId), manager.snapshots().first().map { it.id })
    }

    @Test
    fun `adding a download keeps speed statistics of existing downloads`() = runTest {
        val storage = DownloadTestStorage()
        val first = testDownload(1)
        first.fileStats.value = MediaCache.FileStats(totalSize = 100_000.bytes, downloadedBytes = 0.bytes)
        storage.listFlow.value = listOf(first)
        val manager = manager(storage)
        val received = mutableListOf<List<DownloadSnapshot>>()
        backgroundScope.launch { manager.snapshots().collect { received += it } }
        runCurrent()

        val bytesPerSecond = 1000L
        repeat(8) { second ->
            first.fileStats.value = MediaCache.FileStats(
                totalSize = 100_000.bytes,
                downloadedBytes = ((second + 1) * bytesPerSecond).bytes,
            )
            tick()
        }
        val stable = received.last().single().downloadSpeed
        assertTrue(stable.inBytes > 0, "speed=$stable")

        val second = testDownload(2)
        storage.listFlow.value += second
        runCurrent()
        received.last().let { snapshots ->
            assertEquals(listOf(first.cacheId, second.cacheId), snapshots.map { it.id })
            assertEquals(stable, snapshots.first { it.id == first.cacheId }.downloadSpeed)
        }

        // 速度统计继续累计, 新一秒的结果与重新开始统计时的零值不同.
        first.fileStats.value = MediaCache.FileStats(totalSize = 100_000.bytes, downloadedBytes = (9 * bytesPerSecond).bytes)
        tick()
        val continued = received.last().first { it.id == first.cacheId }.downloadSpeed
        assertTrue(continued.inBytes >= stable.inBytes * 9 / 10, "speed=$continued, stable=$stable")
    }

    // downloadStatusForEpisode

    @Test
    fun `episode status is cached when a completed download exists`() = runTest {
        val storage = DownloadTestStorage()
        val running = testDownload(1).apply {
            fileStats.value = MediaCache.FileStats(totalSize = 200.bytes, downloadedBytes = 50.bytes)
        }
        val completed = testDownload(2, episodeId = 1).apply {
            state.value = MediaCacheState.COMPLETED
            fileStats.value = MediaCache.FileStats(totalSize = 300.bytes, downloadedBytes = 300.bytes)
        }
        storage.listFlow.value = listOf(running, completed)
        val manager = manager(storage)
        runCurrent()

        assertEquals(EpisodeCacheStatus.Cached(totalSize = 300.bytes), manager.downloadStatusForEpisode(1, 1).first())
    }

    @Test
    fun `episode status is caching for downloads in progress or paused`() = runTest {
        val storage = DownloadTestStorage()
        val paused = testDownload(1).apply {
            state.value = MediaCacheState.PAUSED
            fileStats.value = MediaCache.FileStats(totalSize = 200.bytes, downloadedBytes = 50.bytes)
        }
        val failed = testDownload(2, episodeId = 1).apply { state.value = MediaCacheState.FAILED }
        storage.listFlow.value = listOf(failed, paused)
        val manager = manager(storage)
        runCurrent()

        assertEquals(
            EpisodeCacheStatus.Caching(progress = paused.fileStats.value.downloadProgress, totalSize = 200.bytes),
            manager.downloadStatusForEpisode(1, 1).first(),
        )

        paused.state.value = MediaCacheState.IN_PROGRESS
        assertEquals(
            EpisodeCacheStatus.Caching(progress = paused.fileStats.value.downloadProgress, totalSize = 200.bytes),
            manager.downloadStatusForEpisode(1, 1).first(),
        )
    }

    @Test
    fun `episode status is not cached for failed or missing downloads`() = runTest {
        val storage = DownloadTestStorage()
        val failed = testDownload(1).apply { state.value = MediaCacheState.FAILED }
        val otherEpisode = testDownload(2).apply { state.value = MediaCacheState.COMPLETED }
        val otherSubject = testDownload(3, subjectId = 2, episodeId = 1).apply { state.value = MediaCacheState.COMPLETED }
        storage.listFlow.value = listOf(failed, otherEpisode, otherSubject)
        val manager = manager(storage)
        runCurrent()

        assertEquals(EpisodeCacheStatus.NotCached, manager.downloadStatusForEpisode(1, 1).first())
        assertEquals(EpisodeCacheStatus.NotCached, manager.downloadStatusForEpisode(1, 3).first())
        assertEquals(EpisodeCacheStatus.NotCached, manager(DownloadTestStorage()).downloadStatusForEpisode(1, 1).first())
    }

    @Test
    fun `episode status follows state changes`() = runTest {
        val storage = DownloadTestStorage()
        val running = testDownload(1).apply {
            fileStats.value = MediaCache.FileStats(totalSize = 200.bytes, downloadedBytes = 50.bytes)
        }
        storage.listFlow.value = listOf(running)
        val manager = manager(storage)
        runCurrent()
        val status = manager.downloadStatusForEpisode(1, 1)
        assertEquals(
            EpisodeCacheStatus.Caching(progress = running.fileStats.value.downloadProgress, totalSize = 200.bytes),
            status.first(),
        )

        running.fileStats.value = MediaCache.FileStats(totalSize = 200.bytes, downloadedBytes = 200.bytes)
        running.state.value = MediaCacheState.COMPLETED
        assertEquals(EpisodeCacheStatus.Cached(totalSize = 200.bytes), status.first { it is EpisodeCacheStatus.Cached })

        storage.listFlow.value = emptyList()
        assertEquals(EpisodeCacheStatus.NotCached, status.first { it is EpisodeCacheStatus.NotCached })
    }

    // overallStats

    @Test
    fun `overall stats sum storage stats and follow their updates`() = runTest {
        val first = DownloadTestStorage().apply { stats.value = stats(1, 2, 3, 4) }
        val second = DownloadTestStorage().apply { stats.value = stats(10, 20, 30, 40) }
        val manager = manager(first, second)
        val received = mutableListOf<MediaStats>()
        backgroundScope.launch { manager.overallStats.collect { received += it } }
        runCurrent()
        assertEquals(stats(11, 22, 33, 44), received.last())

        first.stats.value = stats(100, 200, 300, 400)
        runCurrent()
        assertEquals(stats(110, 220, 330, 440), received.last())
    }

    @Test
    fun `overall stats are zero without storages`() = runTest {
        assertEquals(MediaStats.Zero, manager().overallStats.first())
    }

    // defaultStorageFor

    @Test
    fun `default storage prefers WebM3u engine for BT media`() = runTest {
        val torrent = storage(MediaCacheEngineKey.Anitorrent)
        val unsupported = storage(MediaCacheEngineKey.WebM3u, supports = false)
        val web = storage(MediaCacheEngineKey.WebM3u)
        val otherWeb = storage(MediaCacheEngineKey.WebM3u)
        val manager = manager(torrent, unsupported, web, otherWeb)

        assertSame(web, manager.defaultStorageFor(TestMediaList.first().copy(kind = MediaSourceKind.BitTorrent)))
    }

    @Test
    fun `default storage uses torrent engine when WebM3u cannot handle BT media`() = runTest {
        val web = storage(MediaCacheEngineKey.WebM3u, supports = false)
        val torrent = storage(MediaCacheEngineKey.Anitorrent)
        val manager = manager(web, torrent)

        assertSame(torrent, manager.defaultStorageFor(TestMediaList.first().copy(kind = MediaSourceKind.BitTorrent)))
    }

    @Test
    fun `default storage takes the first registered compatible storage`() = runTest {
        val unsupported = storage(MediaCacheEngineKey.Anitorrent, supports = false)
        val first = storage(MediaCacheEngineKey.WebM3u)
        val second = storage(MediaCacheEngineKey.WebM3u)
        val manager = manager(unsupported, first, second)

        assertSame(first, manager.defaultStorageFor(TestMediaList.first().copy(kind = MediaSourceKind.WEB)))
    }

    @Test
    fun `default storage fails without compatible storage`() = runTest {
        val media = TestMediaList.first()
        val unsupported = storage(MediaCacheEngineKey.WebM3u, supports = false)

        assertFailsWith<UnsupportedOperationException> { manager(unsupported).defaultStorageFor(media) }
        assertFailsWith<UnsupportedOperationException> { manager().defaultStorageFor(media) }
    }

    // createDownload

    @Test
    fun `createDownload forwards arguments to the given storage and returns its result`() = runTest {
        val cache = testDownload(1)
        val media: Media = cache.origin
        val metadata: MediaCacheMetadata = cache.metadata
        val episodeMetadata = EpisodeMetadata(title = "Episode 1", ep = EpisodeSort(1), sort = EpisodeSort(1))
        var seen: Triple<Media, MediaCacheMetadata, EpisodeMetadata>? = null
        val other = DownloadTestStorage().apply { create = { _, _, _ -> error("wrong storage") } }
        val target = DownloadTestStorage().apply {
            create = { m, md, em ->
                seen = Triple(m, md, em)
                cache
            }
        }
        val manager = manager(other, target)

        assertSame(cache, manager.createDownload(media, metadata, episodeMetadata, target))
        val arguments = assertNotNull(seen)
        assertSame(media, arguments.first)
        assertSame(metadata, arguments.second)
        assertSame(episodeMetadata, arguments.third)
    }

    @Test
    fun `createDownload uses the default storage when none is given`() = runTest {
        val cache = testDownload(1)
        val unsupported = storage(MediaCacheEngineKey.Anitorrent, supports = false).apply {
            create = { _, _, _ -> error("unsupported storage") }
        }
        val supported = storage(MediaCacheEngineKey.WebM3u).apply { create = { _, _, _ -> cache } }
        val manager = manager(unsupported, supported)

        val episodeMetadata = EpisodeMetadata(title = "Episode 1", ep = EpisodeSort(1), sort = EpisodeSort(1))
        assertSame(cache, manager.createDownload(cache.origin, cache.metadata, episodeMetadata))
    }

    // delete

    @Test
    fun `delete occupies the delete slot until the storage finishes`() = runTest {
        val storage = DownloadTestStorage()
        val cache = testDownload(1)
        storage.listFlow.value = listOf(cache)
        val gate = CompletableDeferred<Unit>()
        storage.onDelete = { gate.await() }
        val manager = manager(storage)
        runCurrent()
        val download = assertNotNull(manager.findDownload(cache.cacheId))

        val deletion = async { manager.delete(download) }
        runCurrent()
        assertEquals(DownloadOperation.Delete, download.operation.value)
        assertFailsWith<DownloadBusyException> { download.pause() }
        assertFailsWith<DownloadBusyException> { manager.deleteDownload(cache) }
        assertEquals(0, cache.pauseCalls)
        assertFalse(deletion.isCompleted)

        gate.complete(Unit)
        runCurrent()
        assertTrue(deletion.await())
        assertNull(download.operation.value)
        assertEquals(emptyList(), storage.listFlow.value)

        assertFalse(manager.delete(download))
    }

    @Test
    fun `deleteDownload falls back to storages for caches not yet listed`() = runTest {
        val storage = DownloadTestStorage()
        val cache = testDownload(1)
        storage.listFlow.value = listOf(cache)
        val manager = manager(storage)
        assertNull(manager.downloadOf(cache))

        assertTrue(manager.deleteDownload(cache))
        assertEquals(emptyList(), storage.listFlow.value)
        assertFalse(manager.deleteDownload(testDownload(2)))
    }

    @Test
    fun `deleteDownload routes listed caches through their download`() = runTest {
        val storage = DownloadTestStorage()
        val cache = testDownload(1)
        storage.listFlow.value = listOf(cache)
        val manager = manager(storage)
        runCurrent()
        val download = assertNotNull(manager.downloadOf(cache))
        // 记录存储执行删除时该下载正在执行的操作.
        val operations = mutableListOf<DownloadOperation?>()
        storage.onDelete = { operations += download.operation.value }

        assertTrue(manager.deleteDownload(cache))
        assertEquals<List<DownloadOperation?>>(listOf(DownloadOperation.Delete), operations)
        assertNull(download.operation.value)
        assertEquals(emptyList(), storage.listFlow.value)
    }
}
