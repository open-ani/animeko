/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoEntity
import me.him188.ani.app.data.persistent.database.dao.createMemoryTorrentCacheInfoDao
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.toSafeDownloadId
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.torrent.pikpak.PikPakSavedFiles
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.utils.httpdownloader.MediaType
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PikPakWebM3uCacheMigrationTest {
    @TempDir
    lateinit var dir: File

    private val magnet = "magnet:?xt=urn:btih:0000000000000000000000000000000000000001"

    private val torrentDao = createMemoryTorrentCacheInfoDao()

    private val media = createTestDefaultMedia(
        mediaId = "pikpak.1",
        mediaSourceId = "pikpak",
        originalTitle = "测试剧集",
        download = ResourceLocation.MagnetLink(magnet),
        originalUrl = "https://example.com/1",
        publishedTime = 0,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    )

    @Test
    fun `a completed entry carries its file identity into the metadata`() = runTest {
        val fileName = "01.mkv"
        val store = MemoryDataStore(listOf(save(metadata(completed =false))))
        val httpDao = FakeHttpCacheDownloadStateDao(
            downloadState(fileName, DownloadStatus.COMPLETED).also { writeLegacyFile(fileName) },
        )

        createMigration(store, httpDao).migrate()

        val saved = store.data.first().single()
        assertEquals(MediaCacheEngineKey.PikPak, saved.engine)
        assertTrue(saved.metadata.completed)
        assertEquals(fileName, saved.metadata.pathInTorrent)
    }

    @Test
    fun `an incomplete entry is not marked completed`() = runTest {
        val fileName = "01.mkv"
        val store = MemoryDataStore(listOf(save(metadata(completed =true))))
        val httpDao = FakeHttpCacheDownloadStateDao(
            downloadState(fileName, DownloadStatus.PAUSED).also { writeLegacyFile(fileName) },
        )

        createMigration(store, httpDao).migrate()

        val saved = store.data.first().single()
        assertEquals(MediaCacheEngineKey.PikPak, saved.engine)
        assertEquals(false, saved.metadata.completed)
        assertNull(saved.metadata.pathInTorrent)
    }

    @Test
    fun `the legacy row is found under the id the HTTP engine derived`() = runTest {
        val fileName = "01.mkv"
        // Path-affecting characters are rewritten before the id is stored, so a migration that
        // used the raw mediaId would miss the row entirely.
        val dirty = media.copy(mediaId = "pikpak:1/2?3")
        val store = MemoryDataStore(listOf(save(metadata(completed = false)).copy(origin = dirty)))
        val httpDao = FakeHttpCacheDownloadStateDao(
            downloadState(fileName, DownloadStatus.COMPLETED)
                .copy(downloadId = dirty.toSafeDownloadId())
                .also { writeLegacyFile(fileName) },
        )

        createMigration(store, httpDao).migrate()

        val saved = store.data.first().single()
        assertEquals(MediaCacheEngineKey.PikPak, saved.engine)
        assertTrue(saved.metadata.completed)
        assertEquals(fileName, saved.metadata.pathInTorrent)
        assertNull(httpDao.getById(dirty.toSafeDownloadId()))
    }

    @Test
    fun `a media anitorrent owns is discarded, leaving its torrent row untouched`() = runTest {
        val fileName = "01.mkv"
        val anitorrent = save(metadata(completed = false, episodeId = "2"))
            .copy(engine = MediaCacheEngineKey.Anitorrent)
        val store = MemoryDataStore(listOf(save(metadata(completed = false)), anitorrent))
        val httpDao = FakeHttpCacheDownloadStateDao(
            downloadState(fileName, DownloadStatus.COMPLETED).also { writeLegacyFile(fileName) },
        )
        val row = TorrentCacheInfoEntity(media.mediaId, byteArrayOf(7), "anitorrent/x", pathInTorrent = "02.mkv")
        torrentDao.upsert(row)

        createMigration(store, httpDao).migrate()

        assertEquals(listOf(anitorrent), store.data.first())
        // The row is shared by both torrent engines; migrating would have replaced it with PikPak's layout.
        assertEquals(row, torrentDao.get(media.mediaId))
        assertFalse(legacyDir().resolve(fileName).exists())
    }

    @Test
    fun `an unfinished record does not adopt a file the engine already holds`() = runTest {
        val fileName = "01.mkv"
        val store = MemoryDataStore(listOf(save(metadata(completed = false))))
        // The legacy download never finished and its output file is gone, but the PikPak engine has since
        // downloaded the same file on its own.
        val httpDao = FakeHttpCacheDownloadStateDao(downloadState(fileName, DownloadStatus.PAUSED))
        File(PikPakSavedFiles.saveDirectoryFor(pikpakSaveDir(), magnet).absolutePath).resolve(fileName).apply {
            parentFile.mkdirs()
            writeText("not mine")
        }

        createMigration(store, httpDao).migrate()

        val saved = store.data.first().single()
        assertEquals(MediaCacheEngineKey.PikPak, saved.engine)
        assertEquals(false, saved.metadata.completed)
        assertNull(saved.metadata.pathInTorrent)
    }

    @Test
    fun `a season pack is discarded with its files instead of migrated`() = runTest {
        val fileName = "season.mkv"
        val store = MemoryDataStore(
            listOf(
                save(metadata(completed = false, episodeId = "1")),
                save(metadata(completed = false, episodeId = "2")),
            ),
        )
        val httpDao = FakeHttpCacheDownloadStateDao(
            downloadState(fileName, DownloadStatus.COMPLETED).also {
                writeLegacyFile(fileName)
                writeLegacySegments("segments")
            },
        )
        // As if a previous build had already migrated this pack.
        torrentDao.upsert(TorrentCacheInfoEntity(media.mediaId, byteArrayOf(1), "pikpak/x"))

        createMigration(store, httpDao).migrate()

        assertEquals(emptyList(), store.data.first())
        assertFalse(legacyDir().resolve(fileName).exists())
        assertFalse(legacyDir().resolve("segments").exists())
        assertNull(httpDao.getById(media.toSafeDownloadId()))
        assertNull(torrentDao.get(media.mediaId))
    }

    @Test
    fun `a pack keeps the torrent row another engine still references`() = runTest {
        val anitorrent = save(metadata(completed = false, episodeId = "3"))
            .copy(engine = MediaCacheEngineKey.Anitorrent)
        val store = MemoryDataStore(
            listOf(
                save(metadata(completed = false, episodeId = "1")),
                save(metadata(completed = false, episodeId = "2")),
                anitorrent,
            ),
        )
        val httpDao = FakeHttpCacheDownloadStateDao(downloadState("season.mkv", DownloadStatus.COMPLETED))
        torrentDao.upsert(TorrentCacheInfoEntity(media.mediaId, byteArrayOf(1), "anitorrent/x"))

        createMigration(store, httpDao).migrate()

        assertEquals(listOf(anitorrent), store.data.first())
        assertNotNull(torrentDao.get(media.mediaId))
    }

    @Test
    fun `a single record is migrated while a pack beside it is discarded`() = runTest {
        val fileName = "01.mkv"
        val packMedia = media.copy(mediaId = "pikpak.pack")
        val store = MemoryDataStore(
            listOf(
                save(metadata(completed = false)),
                save(metadata(completed = false, episodeId = "1")).copy(origin = packMedia),
                save(metadata(completed = false, episodeId = "2")).copy(origin = packMedia),
            ),
        )
        val httpDao = FakeHttpCacheDownloadStateDao(
            downloadState(fileName, DownloadStatus.COMPLETED).also { writeLegacyFile(fileName) },
        )

        createMigration(store, httpDao).migrate()

        val saved = store.data.first().single()
        assertEquals(media.mediaId, saved.origin.mediaId)
        assertEquals(MediaCacheEngineKey.PikPak, saved.engine)
        assertTrue(saved.metadata.completed)
        assertEquals(fileName, saved.metadata.pathInTorrent)
    }

    @Test
    fun `discarding again after an interrupted run converges`() = runTest {
        val fileName = "season.mkv"
        val packSaves = listOf(
            save(metadata(completed = false, episodeId = "1")),
            save(metadata(completed = false, episodeId = "2")),
        )
        val store = MemoryDataStore(packSaves)
        val httpDao = FakeHttpCacheDownloadStateDao(
            downloadState(fileName, DownloadStatus.COMPLETED).also { writeLegacyFile(fileName) },
        )

        createMigration(store, httpDao).migrate()
        // Only the metadata write is durable, so a crash before it replays the discard with the files
        // and the HTTP row already gone.
        store.updateData { packSaves }

        createMigration(store, httpDao).migrate()

        assertEquals(emptyList(), store.data.first())
    }

    private fun createMigration(
        store: MemoryDataStore<List<MediaCacheSave>>,
        httpDao: HttpCacheDownloadStateDao,
    ) = PikPakWebM3uCacheMigration(
        metadataStore = store,
        httpDao = httpDao,
        torrentDao = torrentDao,
        baseSaveDirProvider = object : MediaSaveDirProvider {
            override val saveDir: String = dir.absolutePath
        },
        pikpakSaveDir = pikpakSaveDir(),
    )

    private fun pikpakSaveDir() = File(dir, "pikpak").toKtPath().inSystem

    private fun save(metadata: MediaCacheMetadata) = MediaCacheSave(
        origin = media,
        metadata = metadata,
        engine = MediaCacheEngineKey.WebM3u,
    )

    private fun metadata(completed: Boolean, episodeId: String = "1") = MediaCacheMetadata(
        subjectId = "1",
        episodeId = episodeId,
        subjectNames = emptyList(),
        episodeSort = EpisodeSort(episodeId),
        episodeName = "",
        completed = completed,
    )

    private fun legacyDir() = File(dir, HttpMediaCacheEngine.MEDIA_CACHE_DIR)

    private fun writeLegacyFile(fileName: String) {
        legacyDir().resolve(fileName).apply {
            parentFile.mkdirs()
            writeText("x")
        }
    }

    private fun writeLegacySegments(dirName: String) {
        legacyDir().resolve(dirName).resolve("0.ts").apply {
            parentFile.mkdirs()
            writeText("x")
        }
    }

    private fun downloadState(fileName: String, status: DownloadStatus) = DownloadState(
        downloadId = DownloadId(media.mediaId),
        url = "https://example.com/1.m3u8",
        relativeOutputPath = fileName,
        segments = emptyList(),
        totalSegments = 0,
        downloadedBytes = 1,
        timestamp = 0,
        status = status,
        relativeSegmentCacheDir = "segments",
        requestHeaders = emptyMap(),
        mediaType = MediaType.M3U8,
    )

    private class FakeHttpCacheDownloadStateDao(state: DownloadState) : HttpCacheDownloadStateDao {
        private val states = MutableStateFlow(listOf(state))

        override fun getAll(): Flow<List<DownloadState>> = states

        override suspend fun upsert(state: DownloadState) {
            states.value = states.value.filter { it.downloadId != state.downloadId } + state
        }

        override suspend fun updateStatus(id: DownloadId, status: DownloadStatus) {
            states.value = states.value.map { if (it.downloadId == id) it.copy(status = status) else it }
        }

        override suspend fun deleteAll() {
            states.value = emptyList()
        }

        override suspend fun deleteById(id: DownloadId) {
            states.value = states.value.filter { it.downloadId != id }
        }

        override suspend fun getById(id: DownloadId): DownloadState? = states.value.firstOrNull { it.downloadId == id }
    }
}
