/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.download.DownloadTestStorage
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.download.testDownloadEngine
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.torrent.offline.OfflineDownloadAuthException
import me.him188.ani.torrent.offline.OfflineDownloadEngine
import me.him188.ani.torrent.offline.ResolvedMedia
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadOptions
import me.him188.ani.utils.httpdownloader.DownloadProgress
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.httpdownloader.MediaType
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 验证 HTTP 缓存的云端解析边界、剧集任务标识与持久化恢复.
 */
class HttpMediaCacheEngineTest {
    @Test
    fun `season episodes create different HTTP files and deleting one keeps the other`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader)
        val media = TestMediaList.first().copy(
            kind = MediaSourceKind.BitTorrent,
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:season"),
        )
        val firstCache = engine.createCache(media, testMetadata(1), testEpisodeMetadata(1), backgroundScope.coroutineContext)
        val firstId = downloader.states.keys.single()
        val secondCache = engine.createCache(media, testMetadata(2), testEpisodeMetadata(2), backgroundScope.coroutineContext)
        val secondId = downloader.states.keys.single { it != firstId }
        assertEquals(2, downloader.states.size)
        assertEquals(2, downloader.states.values.map { it.relativeOutputPath }.distinct().size)
        assertEquals(2, downloader.states.values.map { it.url }.distinct().size)
        firstCache.closeAndDeleteFiles()
        assertEquals(setOf(secondId), downloader.states.keys)
        secondCache.resume()
        assertEquals(secondId, downloader.resumed.last())
    }

    @Test
    fun `restoration reuses mediaId-derived files and prefers episode identity when present`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader)
        val media = TestMediaList.first().copy(
            mediaId = "source/legacy",
            kind = MediaSourceKind.WEB,
            download = ResourceLocation.HttpStreamingFile("https://example.com/legacy.mp4"),
        )
        val metadata = testMetadata(1)
        val mediaIdDerived = DownloadId("source-legacy")
        downloader.downloadWithId(mediaIdDerived, "https://example.com/legacy.mp4", DownloadOptions())
        engine.restore(media, metadata, backgroundScope.coroutineContext)
        assertEquals(mediaIdDerived, downloader.resumed.last())
        engine.createCache(media, metadata, testEpisodeMetadata(1), backgroundScope.coroutineContext)
        val currentId = downloader.states.keys.single { it != mediaIdDerived }
        engine.restore(media, metadata, backgroundScope.coroutineContext)
        assertEquals(currentId, downloader.resumed.last())
        assertTrue(mediaIdDerived in downloader.states)
    }

    @Test
    fun `restoration recreates task from dao record with episode identity`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader)
        val media = TestMediaList.first().copy(
            mediaId = "source/legacy",
            kind = MediaSourceKind.WEB,
            download = ResourceLocation.HttpStreamingFile("https://example.com/legacy.mp4"),
        )
        val metadata = testMetadata(1)
        engine.createCache(media, metadata, testEpisodeMetadata(1), backgroundScope.coroutineContext)
        val currentId = downloader.states.keys.single()
        // 仅 dao 保留记录, downloader 丢失任务时, 按含剧集信息的标识重建任务.
        downloader.persistedOnly += currentId
        val restored = engine.restore(media, metadata, backgroundScope.coroutineContext)
        assertTrue(restored != null)
        assertEquals(listOf(currentId), downloader.recreated)
        assertEquals(setOf(currentId), downloader.states.keys)
    }

    @Test
    fun `identity is stable filesystem safe and distinguishes episodes subjects and media`() = runTest {
        val media = TestMediaList.first()
        suspend fun createId(media: Media, metadata: MediaCacheMetadata): DownloadId {
            val downloader = FakeDownloader()
            engine(downloader).createCache(media, metadata, testEpisodeMetadata(1), backgroundScope.coroutineContext)
            return downloader.states.keys.single()
        }

        val id = createId(media, testMetadata(1))
        assertEquals(id, createId(media, testMetadata(1).copy(creationTime = 0)))
        assertNotEquals(id, createId(media, testMetadata(2)))
        assertNotEquals(id, createId(media, testMetadata(1).copy(subjectId = "2")))
        assertTrue(id.value.matches(Regex("http-v2-[0-9a-f]{64}")))
        val slash = TestMediaList.first().copy(mediaId = "source/path")
        val colon = slash.copy(mediaId = "source:path")
        assertNotEquals(createId(slash, testMetadata(1)), createId(colon, testMetadata(1)))
    }

    @Test
    fun `BT caching requires an available cloud engine even when playback supports torrents`() {
        val offline = FakeOfflineEngine()
        val engine = engine(FakeDownloader(), offline)
        for (location in listOf(
            ResourceLocation.MagnetLink("magnet:?xt=urn:btih:test"),
            ResourceLocation.HttpTorrentFile("https://example.com/test.torrent"),
        )) {
            val media = TestMediaList.first().copy(kind = MediaSourceKind.BitTorrent, download = location)
            offline.isSupported.value = false
            assertFalse(engine.supports(media))
            offline.isSupported.value = true
            assertTrue(engine.supports(media))
            assertFalse(engine(FakeDownloader(), offlineEngine = null).supports(media))
            assertFalse(engine(FakeDownloader(), offline) { PikPakConfig.Default }.supports(media))
        }
    }

    @Test
    fun `download manager uses local BT when cloud credentials are unavailable`() = runTest {
        val offline = FakeOfflineEngine()
        val http = DownloadTestStorage(engine(FakeDownloader(), offline))
        val torrent = DownloadTestStorage(testDownloadEngine(MediaCacheEngineKey("torrent")))
        val manager = MediaDownloadManager(listOf(torrent, http), backgroundScope)
        for (location in listOf(
            ResourceLocation.MagnetLink("magnet:?xt=urn:btih:test"),
            ResourceLocation.HttpTorrentFile("https://example.com/test.torrent"),
        )) {
            val media = TestMediaList.first().copy(kind = MediaSourceKind.BitTorrent, download = location)
            offline.isSupported.value = false
            assertSame(torrent, manager.defaultStorageFor(media))
            offline.isSupported.value = true
            assertSame(http, manager.defaultStorageFor(media))
        }
    }

    @Test
    fun `cloud failure surfaces its cause without opening local BT playback`() = runTest {
        val failure = OfflineDownloadAuthException("expired credentials")
        val downloader = FakeDownloader()
        val engine = engine(downloader, FakeOfflineEngine(failure))
        val media = TestMediaList.first().copy(
            kind = MediaSourceKind.BitTorrent,
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:test"),
        )
        val thrown = assertFailsWith<MediaResolutionException> {
            engine.createCache(media, testMetadata(1), testEpisodeMetadata(1), backgroundScope.coroutineContext)
        }
        assertEquals(ResolutionFailures.ENGINE_ERROR, thrown.reason)
        assertSame(failure, thrown.cause)
        assertTrue(downloader.states.isEmpty())
    }

    @Test
    fun `HTTP caching does not require a cloud engine`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader, offlineEngine = null) { PikPakConfig.Default }
        val media = TestMediaList.first().copy(
            kind = MediaSourceKind.WEB,
            download = ResourceLocation.HttpStreamingFile("https://example.com/video.mp4"),
        )
        assertTrue(engine.supports(media))
        engine.createCache(media, testMetadata(1), testEpisodeMetadata(1), backgroundScope.coroutineContext)
        assertEquals(1, downloader.states.size)
    }

    @Test
    fun `stored cloud downloads restore without cloud credentials or re-resolution`() = runTest {
        val downloader = FakeDownloader()
        val media = TestMediaList.first().copy(
            kind = MediaSourceKind.BitTorrent,
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:test"),
        )
        val metadata = testMetadata(1)
        engine(downloader).createCache(media, metadata, testEpisodeMetadata(1), backgroundScope.coroutineContext)
        val downloadId = downloader.states.keys.single()
        val unavailable = engine(downloader, offlineEngine = null) { PikPakConfig.Default }
        assertFalse(unavailable.supports(media))
        assertTrue(unavailable.restore(media, metadata, backgroundScope.coroutineContext) != null)
        assertEquals(listOf(downloadId), downloader.resumed)
        downloader.persistedOnly += downloadId
        assertTrue(unavailable.restore(media, metadata, backgroundScope.coroutineContext) != null)
        assertEquals(listOf(downloadId), downloader.recreated)
    }

    private fun testMetadata(episodeId: Int, subjectId: Int = 1) = MediaCacheMetadata(
        subjectId = subjectId.toString(),
        episodeId = episodeId.toString(),
        subjectNames = listOf("Subject"),
        episodeSort = EpisodeSort(episodeId),
        episodeEp = EpisodeSort(episodeId),
        episodeName = "Episode $episodeId",
    )

    private fun testEpisodeMetadata(episodeId: Int) =
        EpisodeMetadata("Episode $episodeId", EpisodeSort(episodeId), EpisodeSort(episodeId))

    private fun engine(
        downloader: FakeDownloader,
        offlineEngine: OfflineDownloadEngine? = FakeOfflineEngine(),
        pikpakConfig: () -> PikPakConfig = { PikPakConfig.Default.copy(enabled = true) },
    ) = HttpMediaCacheEngine(
        downloader,
        Path("/unused-test-downloads"),
        object : MediaResolver by TestUniversalMediaResolver {
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
                check(media.kind != MediaSourceKind.BitTorrent) { "HTTP caching must not call the BT playback resolver" }
                return object : MediaDataProvider<UriMediaData> {
                    override val extraFiles = MediaExtraFiles.EMPTY
                    override suspend fun open(scopeForCleanup: CoroutineScope) = UriMediaData("https://example.com/${episode.sort}.mp4")
                }
            }
        },
        "test-storage",
        object : HttpCacheDownloadStateDao {
            override fun getAll() = flowOf(downloader.persisted.values.toList())
            override suspend fun upsert(state: DownloadState) {
                downloader.states[state.downloadId] = state
            }

            override suspend fun updateStatus(id: DownloadId, status: DownloadStatus) = Unit
            override suspend fun deleteAll() {
                downloader.states.clear()
            }

            override suspend fun deleteById(id: DownloadId) {
                downloader.states.remove(id)
            }

            override suspend fun getById(id: DownloadId) = downloader.persisted[id]
        },
        pikpakConfig = pikpakConfig,
        offlineDownloadEngine = offlineEngine,
    )
}

/**
 * 以内存记录模拟 downloader. [persistedOnly] 中的任务只在 dao 中可见, 用于模拟 downloader 丢失任务而持久化记录仍在的情况.
 */
private class FakeDownloader : HttpDownloader {
    val states = mutableMapOf<DownloadId, DownloadState>()
    val resumed = mutableListOf<DownloadId>()
    val recreated = mutableListOf<DownloadId>()
    val persistedOnly = mutableSetOf<DownloadId>()

    /** dao 视角下的全部记录, 包含 downloader 已丢失但仍持久化的任务. */
    val persisted: Map<DownloadId, DownloadState> get() = states

    override val progressFlow: Flow<DownloadProgress> = flowOf()
    override val downloadStatesFlow: Flow<List<DownloadState>> = flowOf(emptyList())
    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> = flowOf()
    override suspend fun init() = Unit
    override suspend fun download(url: String, options: DownloadOptions): DownloadId = error("unused")
    override suspend fun downloadWithId(downloadId: DownloadId, url: String, options: DownloadOptions): DownloadState {
        if (persistedOnly.remove(downloadId)) recreated += downloadId
        return states.getOrPut(downloadId) {
            DownloadState(
                downloadId, url, "${downloadId.value}.mp4", emptyList(), 0, 0, 0,
                DownloadStatus.DOWNLOADING, relativeSegmentCacheDir = "segments_${downloadId.value}",
                requestHeaders = options.headers, mediaType = MediaType.MP4,
            )
        }
    }

    override suspend fun resume(downloadId: DownloadId): Boolean {
        resumed += downloadId
        return true
    }

    override suspend fun getActiveDownloadIds() = states.keys.toList()
    override suspend fun pause(downloadId: DownloadId) = true
    override suspend fun pauseAll() = states.keys.toList()
    override suspend fun cancel(downloadId: DownloadId) = true
    override suspend fun cancelAll() = Unit
    override suspend fun remove(downloadId: DownloadId) = states.remove(downloadId) != null
    override suspend fun getState(downloadId: DownloadId) = states[downloadId]?.takeUnless { downloadId in persistedOnly }
    override suspend fun getAllStates() = states.values.toList()
    override fun close() = Unit
}

private class FakeOfflineEngine(private val failure: Throwable? = null) : OfflineDownloadEngine {
    override val id = "test"
    override val displayName = "Test"
    override val isSupported = MutableStateFlow(true)

    override suspend fun resolve(uri: String, pickVideoFile: (List<String>) -> String?): ResolvedMedia {
        failure?.let { throw it }
        val files = listOf("Subject - 01.mp4", "Subject - 02.mp4")
        val file = checkNotNull(pickVideoFile(files))
        return ResolvedMedia("https://example.com/${files.indexOf(file) + 1}.mp4", fileName = file)
    }
}
