/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.InMemorySessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.io.RandomAccessFile
import me.him188.ani.app.torrent.api.pieces.forEach
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportCompletedFileTest {
    private val magnet =
        "magnet:?xt=urn:btih:157e0a57e1af0e1cfd46258ba6c62938c21b6ee8&dn=01.mp4"

    private fun downloader(
        root: me.him188.ani.utils.io.SystemPath,
        resolve: suspend (uri: String) -> io.github.nihildigit.pikpak.MagnetResource? = {
            throw IOException("no network in this test")
        },
    ) = PikPakTorrentDownloader(
        httpClient = HttpClient(MockEngine { error("the import path must not make any request") }),
        credentials = MutableStateFlow(PikPakCredentials("nobody@example.com", "unused")),
        sessionStore = InMemorySessionStore(),
        rootDataDirectory = root,
        config = MutableStateFlow(PikPakEngineConfig()),
        parentCoroutineContext = Dispatchers.IO,
    ).also { it.magnetResolver = resolve }

    private fun resolved(path: String, length: Long, gcid: String = "GCID-$path") =
        io.github.nihildigit.pikpak.ResolvedFile(path = path, size = length, gcid = gcid)

    private fun torrent(name: String, vararg files: io.github.nihildigit.pikpak.ResolvedFile) =
        io.github.nihildigit.pikpak.MagnetResource(name = name, files = files.toList())

    @Test
    fun `an imported file restores as fully downloaded and offline`() = runBlocking {
        val temp = SystemPaths.createTempDirectory("pikpak-import")
        val root = temp.resolve("pikpak")

        val content = ByteArray(PikPakFileEntry.PIECE_SIZE.toInt() * 2 + 12345) { (it % 251).toByte() }
        val staged = temp.resolve("legacy-download.mp4")
        staged.writeBytes(content)

        val downloader = downloader(root)
        val imported = PikPakSavedFiles.importCompletedFile(root, magnet, staged, "01.mp4")

        assertFalse(staged.exists(), "the source is moved, not copied")
        assertEquals(content.size.toLong(), imported.length())
        assertContentEquals(content, imported.readBytes())

        val session = downloader.startDownload(downloader.fetchTorrent(magnet))
        val entry = session.getFiles().single()

        assertEquals("01.mp4", entry.pathInTorrent)
        assertEquals(content.size.toLong(), entry.length)
        with(entry.pieces) {
            entry.pieces.forEach { piece ->
                assertEquals(PieceState.FINISHED, piece.state, "piece ${piece.pieceIndex}")
            }
        }
        val stats = entry.fileStats.first()
        assertEquals(content.size.toLong(), stats.downloadedBytes)
        assertTrue(stats.isDownloadFinished)

        val handle = entry.createHandle()
        handle.resume()
        handle.close()

        session.close()
        downloader.close()
    }

    @Test
    fun `a partial file restores as partial progress rather than as complete`() = runBlocking {
        val temp = SystemPaths.createTempDirectory("pikpak-partial")
        val root = temp.resolve("pikpak")
        val content = ByteArray(PikPakFileEntry.PIECE_SIZE.toInt() * 3)
        val staged = temp.resolve("legacy.mp4").also { it.writeBytes(content) }

        val imported = PikPakSavedFiles.importCompletedFile(root, magnet, staged, "01.mp4")

        val kept = PikPakFileEntry.PIECE_SIZE
        RandomAccessFile(imported, "rw").use { it.setLength(kept) }

        val downloader = downloader(root)
        val session = downloader.startDownload(downloader.fetchTorrent(magnet))
        val entry = session.getFiles().single()

        assertEquals(content.size.toLong(), entry.length, "the recorded length is unchanged")
        val stats = entry.fileStats.first()
        assertEquals(kept, stats.downloadedBytes)
        assertFalse(stats.isDownloadFinished)
        assertEquals(kept, imported.length(), "building the session must not touch the file")

        session.close()
        downloader.close()
    }

    @Test
    fun `importing a second file of the same torrent keeps the first`() = runBlocking {
        val temp = SystemPaths.createTempDirectory("pikpak-import-pack")
        val root = temp.resolve("pikpak")
        val first = ByteArray(4096) { it.toByte() }
        val second = ByteArray(8192) { (it * 3).toByte() }

        PikPakSavedFiles.importCompletedFile(
            root, magnet, temp.resolve("a.mp4").also { it.writeBytes(first) }, "01.mp4",
        )
        PikPakSavedFiles.importCompletedFile(
            root, magnet, temp.resolve("b.mp4").also { it.writeBytes(second) }, "02.mp4",
        )

        val downloader = downloader(root) {
            torrent("Season Pack", resolved("01.mp4", 4096), resolved("02.mp4", 8192))
        }
        val session = downloader.startDownload(downloader.fetchTorrent(magnet))
        val entries = session.getFiles().sortedBy { it.pathInTorrent }

        assertEquals(listOf("01.mp4", "02.mp4"), entries.map { it.pathInTorrent })
        assertEquals(listOf(first.size.toLong(), second.size.toLong()), entries.map { it.length })
        for (entry in entries) {
            assertTrue(entry.fileStats.first().isDownloadFinished, "${entry.pathInTorrent} is not complete")
        }

        session.close()
        downloader.close()
    }

    @Test
    fun `an imported episode is filled out from the cloud rather than taken for the whole pack`() = runBlocking {
        val temp = SystemPaths.createTempDirectory("pikpak-import-partial-index")
        val root = temp.resolve("pikpak")
        val imported = ByteArray(4096) { it.toByte() }
        PikPakSavedFiles.importCompletedFile(
            root, magnet, temp.resolve("a.mp4").also { it.writeBytes(imported) }, "01.mp4",
        )

        val downloader = downloader(root) {
            torrent("Season Pack", resolved("01.mp4", 999_999), resolved("02.mp4", 8192))
        }

        val session = downloader.startDownload(downloader.fetchTorrent(magnet))
        val entries = session.getFiles().map { it as PikPakFileEntry }.sortedBy { it.pathInTorrent }

        assertEquals(listOf("01.mp4", "02.mp4"), entries.map { it.pathInTorrent })
        val first = entries.first()
        assertEquals(imported.size.toLong(), first.length, "the imported length describes the bytes on disk")
        assertEquals("GCID-01.mp4", first.meta.gcid, "the gcid is what the torrent knows and the disk does not")
        assertTrue(first.fileStats.first().isDownloadFinished, "the imported episode is still complete")
        assertEquals(8192L, entries[1].length)

        val onDisk = PikPakResumeData(root.resolve(sourceKeyFor(magnet))).read()
        assertTrue(onDisk!!.indexed, "a filled-in listing must not be indexed again on the next start")

        session.close()
        downloader.close()
    }

    @Test
    fun `an imported file retains its path after cloud indexing and offline restart`() = runBlocking {
        val temp = SystemPaths.createTempDirectory("pikpak-import-relocate")
        val root = temp.resolve("pikpak")
        val imported = ByteArray(4096) { it.toByte() }
        PikPakSavedFiles.importCompletedFile(
            root, magnet, temp.resolve("a.mp4").also { it.writeBytes(imported) }, "SP01.mp4",
        )
        PikPakSavedFiles.importCompletedFile(
            root, magnet, temp.resolve("b.mp4").also { it.writeBytes(imported) }, "orphan.mp4",
        )

        val downloader = downloader(root) {
            torrent("Season Pack", resolved("01.mp4", 8192), resolved("specials/SP01.mp4", 4096))
        }

        val session = downloader.startDownload(downloader.fetchTorrent(magnet))
        val entries = session.getFiles().map { it as PikPakFileEntry }.associateBy { it.pathInTorrent }

        assertEquals(setOf("01.mp4", "SP01.mp4", "orphan.mp4"), entries.keys)
        val retained = entries.getValue("SP01.mp4")
        assertEquals("GCID-specials/SP01.mp4", retained.meta.gcid)
        assertTrue(retained.fileStats.first().isDownloadFinished)
        val saveDir = root.resolve(sourceKeyFor(magnet))
        assertFalse(saveDir.resolve("specials/SP01.mp4").exists())
        assertContentEquals(imported, saveDir.resolve("SP01.mp4").readBytes())
        assertTrue(entries.getValue("orphan.mp4").fileStats.first().isDownloadFinished, "an unmatched import is kept")

        session.close()
        downloader.close()

        val restarted = downloader(root) { error("the indexed listing must restore offline") }
        val restoredSession = restarted.startDownload(restarted.fetchTorrent(magnet))
        val restored = restoredSession.getFiles().single { it.pathInTorrent == "SP01.mp4" }
        assertTrue(restored.fileStats.first().isDownloadFinished)
        restored.createInput(coroutineContext).use { input ->
            val bytes = ByteArray(imported.size)
            assertEquals(bytes.size, input.read(bytes, 0, bytes.size))
            assertContentEquals(imported, bytes)
        }
        restoredSession.close()
        restarted.close()
    }

    @Test
    fun `ambiguous imported basenames remain independent entries`() {
        fun meta(vararg paths: String) = PikPakTorrentMeta(
            uri = magnet,
            sourceKey = sourceKeyFor(magnet),
            name = "Season Pack",
            files = paths.mapIndexed { index, path ->
                PikPakFileMeta(index = index, pathInTorrent = path, length = 4096)
            },
        )

        val merged = mergeImportedInto(
            meta("cloud/SP01.mp4"),
            meta("a/SP01.mp4", "b/SP01.mp4"),
        )
        assertEquals(
            setOf("cloud/SP01.mp4", "a/SP01.mp4", "b/SP01.mp4"),
            merged.files.map { it.pathInTorrent }.toSet(),
        )
    }

    @Test
    fun `an imported episode stays playable while the cloud is unreachable`() = runBlocking {
        val temp = SystemPaths.createTempDirectory("pikpak-import-offline")
        val root = temp.resolve("pikpak")
        val imported = ByteArray(4096) { it.toByte() }
        PikPakSavedFiles.importCompletedFile(
            root, magnet, temp.resolve("a.mp4").also { it.writeBytes(imported) }, "01.mp4",
        )

        val downloader = downloader(root)
        val session = downloader.startDownload(downloader.fetchTorrent(magnet))
        val entry = session.getFiles().single()

        assertEquals("01.mp4", entry.pathInTorrent)
        assertTrue(entry.fileStats.first().isDownloadFinished)
        assertFalse(assertIs<PartialListing>(session).listingComplete)
        entry.createInput(coroutineContext).use { input ->
            val bytes = ByteArray(imported.size)
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                assertTrue(count > 0, "imported playback ended before the file did")
                offset += count
            }
            assertContentEquals(imported, bytes)
        }

        val onDisk = PikPakResumeData(root.resolve(sourceKeyFor(magnet))).read()
        assertFalse(onDisk!!.indexed, "a failed index must leave the retry in place")

        session.close()
        downloader.close()
    }

    @Test
    fun `a listing filled in from the cloud is complete`() = runBlocking {
        val temp = SystemPaths.createTempDirectory("pikpak-import-complete-listing")
        val root = temp.resolve("pikpak")
        PikPakSavedFiles.importCompletedFile(
            root, magnet, temp.resolve("a.mp4").also { it.writeBytes(ByteArray(4096)) }, "01.mp4",
        )

        val downloader = downloader(root) {
            torrent("Season Pack", resolved("01.mp4", 4096), resolved("02.mp4", 8192))
        }
        val session = downloader.startDownload(downloader.fetchTorrent(magnet))

        assertTrue(assertIs<PartialListing>(session).listingComplete)

        session.close()
        downloader.close()
    }

    @Test
    fun `importing twice in a row is idempotent`() = runBlocking {
        val temp = SystemPaths.createTempDirectory("pikpak-import-twice")
        val root = temp.resolve("pikpak")
        val content = ByteArray(4096) { it.toByte() }
        val downloader = downloader(root)

        val first = temp.resolve("a.mp4").also { it.writeBytes(content) }
        val importedOnce = PikPakSavedFiles.importCompletedFile(root, magnet, first, "01.mp4")

        val importedTwice = PikPakSavedFiles.importCompletedFile(root, magnet, importedOnce, "01.mp4")

        assertContentEquals(content, importedTwice.readBytes())
        val session = downloader.startDownload(downloader.fetchTorrent(magnet))
        assertTrue(session.getFiles().single().fileStats.first().isDownloadFinished)
        session.close()
        downloader.close()
    }
}
