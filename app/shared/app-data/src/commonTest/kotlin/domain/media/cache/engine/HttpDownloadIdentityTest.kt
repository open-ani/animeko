/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.download.testDownloadSpec
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadOptions
import me.him188.ani.utils.httpdownloader.DownloadProgress
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.httpdownloader.MediaType
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData

class HttpDownloadIdentityTest {
    @Test
    fun `season episodes create different HTTP files and deleting one keeps the other`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader)
        val media = TestMediaList.first().copy(
            kind = MediaSourceKind.BitTorrent,
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:season"),
        )
        val first = testDownloadSpec(1, media)
        val second = testDownloadSpec(2, media)
        val firstCache = engine.createCache(media, first.metadata, first.episode.toEpisodeMetadata(), backgroundScope.coroutineContext)
        val firstId = downloader.states.keys.single()
        val secondCache = engine.createCache(media, second.metadata, second.episode.toEpisodeMetadata(), backgroundScope.coroutineContext)
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
    fun `restoration reuses legacy files and prefers episode identity when present`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader)
        val media = TestMediaList.first().copy(
            mediaId = "source/legacy",
            kind = MediaSourceKind.WEB,
            download = ResourceLocation.HttpStreamingFile("https://example.com/legacy.mp4"),
        )
        val spec = testDownloadSpec(1, media)
        val legacyId = DownloadId("source-legacy")
        downloader.downloadWithId(legacyId, "https://example.com/legacy.mp4", DownloadOptions())
        engine.restore(media, spec.metadata, backgroundScope.coroutineContext)
        assertEquals(legacyId, downloader.resumed.last())
        engine.createCache(media, spec.metadata, spec.episode.toEpisodeMetadata(), backgroundScope.coroutineContext)
        val currentId = downloader.states.keys.single { it != legacyId }
        engine.restore(media, spec.metadata, backgroundScope.coroutineContext)
        assertEquals(currentId, downloader.resumed.last())
        assertTrue(legacyId in downloader.states)
    }

    @Test
    fun `identity is stable filesystem safe and distinguishes episodes subjects and media`() = runTest {
        val first = testDownloadSpec(1)
        val second = testDownloadSpec(2, first.media)
        suspend fun createId(media: Media, metadata: MediaCacheMetadata): DownloadId {
            val downloader = FakeDownloader()
            engine(downloader).createCache(media, metadata, first.episode.toEpisodeMetadata(), backgroundScope.coroutineContext)
            return downloader.states.keys.single()
        }

        val id = createId(first.media, first.metadata)
        assertEquals(id, createId(first.media, first.metadata.copy(creationTime = 0)))
        assertNotEquals(id, createId(second.media, second.metadata))
        assertNotEquals(id, createId(first.media, first.metadata.copy(subjectId = "2")))
        assertTrue(id.value.matches(Regex("http-v2-[0-9a-f]{64}")))
        val slash = TestMediaList.first().copy(mediaId = "source/path")
        val colon = slash.copy(mediaId = "source:path")
        assertNotEquals(createId(slash, first.metadata), createId(colon, first.metadata))
    }

    private fun engine(downloader: FakeDownloader) = HttpMediaCacheEngine(
        downloader,
        Path("/unused-test-downloads"),
        object : MediaResolver by TestUniversalMediaResolver {
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> =
                object : MediaDataProvider<UriMediaData> {
                    override val extraFiles = MediaExtraFiles.EMPTY
                    override suspend fun open(scopeForCleanup: CoroutineScope) = UriMediaData("https://example.com/${episode.sort}.mp4")
                }
        },
        "test-storage",
        object : HttpCacheDownloadStateDao {
            override fun getAll() = flowOf(downloader.states.values.toList())
            override suspend fun upsert(state: DownloadState) { downloader.states[state.downloadId] = state }
            override suspend fun updateStatus(id: DownloadId, status: DownloadStatus) = Unit
            override suspend fun deleteAll() { downloader.states.clear() }
            override suspend fun deleteById(id: DownloadId) { downloader.states.remove(id) }
            override suspend fun getById(id: DownloadId) = downloader.states[id]
        },
        pikpakConfig = { PikPakConfig.Default.copy(enabled = true) },
    )
}

private class FakeDownloader : HttpDownloader {
    val states = mutableMapOf<DownloadId, DownloadState>()
    val resumed = mutableListOf<DownloadId>()
    override val progressFlow: Flow<DownloadProgress> = flowOf()
    override val downloadStatesFlow: Flow<List<DownloadState>> = flowOf(emptyList())
    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> = flowOf()
    override suspend fun init() = Unit
    override suspend fun download(url: String, options: DownloadOptions): DownloadId = error("unused")
    override suspend fun downloadWithId(downloadId: DownloadId, url: String, options: DownloadOptions): DownloadState =
        states.getOrPut(downloadId) {
            DownloadState(
                downloadId, url, "${downloadId.value}.mp4", emptyList(), 0, 0, 0,
                DownloadStatus.DOWNLOADING, relativeSegmentCacheDir = "segments_${downloadId.value}",
                requestHeaders = options.headers, mediaType = MediaType.MP4,
            )
        }
    override suspend fun resume(downloadId: DownloadId): Boolean { resumed += downloadId; return true }
    override suspend fun getActiveDownloadIds() = states.keys.toList()
    override suspend fun pause(downloadId: DownloadId) = true
    override suspend fun pauseAll() = states.keys.toList()
    override suspend fun cancel(downloadId: DownloadId) = true
    override suspend fun cancelAll() = Unit
    override suspend fun remove(downloadId: DownloadId) = states.remove(downloadId) != null
    override suspend fun getState(downloadId: DownloadId) = states[downloadId]
    override suspend fun getAllStates() = states.values.toList()
    override fun close() = Unit
}
