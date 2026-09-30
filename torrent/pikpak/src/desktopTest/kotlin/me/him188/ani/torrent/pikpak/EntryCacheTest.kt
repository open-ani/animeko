/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import org.openani.mediamp.io.SeekableInput
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Playback, the seek-bar preview, the index prefetch and a cache download all go through one file
 * cache over one store. What is worth asserting is what reaches the network: a block fetched twice
 * costs a free account its daily downstream, and a preview that reads ahead like a player takes the
 * connections playback needs.
 */
class EntryCacheTest {
    private val block = PikPakFileEntry.PIECE_SIZE

    private fun payload(size: Int) = ByteArray(size) { (it * 31 % 251).toByte() }

    private fun SeekableInput.readAt(position: Long, count: Int): ByteArray {
        seekTo(position)
        val out = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val n = read(out, filled, count - filled)
            assertTrue(n > 0, "the input ended at ${position + filled}")
            filled += n
        }
        return out
    }

    @Test
    fun `a file cached after it was played fetches nothing twice`() = runBlocking {
        val content = payload((block * 40).toInt())
        val source = FakeRangeSource(content)
        val entry = testEntry(SystemPaths.createTempDirectory("entry-shared"), "01.mkv", content.size.toLong(), source)
        try {
            entry.createInput(coroutineContext).use { input ->
                assertContentEquals(content.copyOfRange(0, 4096), input.readAt(0, 4096))
                assertContentEquals(
                    content.copyOfRange((block * 20).toInt(), (block * 20).toInt() + 4096),
                    input.readAt(block * 20, 4096),
                )
            }
            // The cache writes what a stream fetched behind the stream, and a read-ahead the close
            // cut short never lands, so what the download may skip is what the store holds once
            // those writes have settled.
            val held = settledDownloadedBytes(entry)
            val playbackRequests = source.requests.size

            val handle = entry.createHandle()
            handle.resume(FilePriority.NORMAL)
            withTimeout(60.seconds) { entry.fileStats.first { it.isDownloadFinished } }
            handle.close()

            val downloaded = source.requests.drop(playbackRequests)
                .sumOf { minOf(it.length, content.size - it.start) }
            assertEquals(content.size - held, downloaded, "the download fetched what the store already held")
            assertContentEquals(content, entryData(entry))
        } finally {
            entry.close()
        }
    }

    @Test
    fun `a cache download fetches the head and the index before the middle`() = runBlocking {
        val content = payload((PikPakFileEntry.HEADER_SIZE + PikPakFileEntry.FOOTER_SIZE + block * 16).toInt())
        val source = FakeRangeSource(content)
        val entry = testEntry(SystemPaths.createTempDirectory("entry-order"), "01.mkv", content.size.toLong(), source)
        try {
            val handle = entry.createHandle()
            handle.resume(FilePriority.NORMAL)
            withTimeout(60.seconds) { entry.fileStats.first { it.isDownloadFinished } }
            handle.close()

            val starts = source.requests.map { it.start }
            val tailStart = content.size - PikPakFileEntry.FOOTER_SIZE
            val firstTail = starts.indexOfFirst { it >= tailStart }
            val firstMiddle = starts.indexOfFirst { it >= PikPakFileEntry.HEADER_SIZE && it < tailStart }
            // Two workers start at once, so which of the first two requests is recorded first is a race
            assertTrue(0L in starts.take(2), "the download did not start at the head: $starts")
            assertTrue(firstTail in 0 until firstMiddle, "the index came after the middle: $starts")
        } finally {
            entry.close()
        }
    }

    @Test
    fun `a preview input reads ahead one block and no further`() = runBlocking {
        val content = payload((block * 64).toInt())
        val source = FakeRangeSource(content)
        val entry = testEntry(SystemPaths.createTempDirectory("entry-preview"), "01.mkv", content.size.toLong(), source)
        try {
            // The player's input is open but idle, so everything past the preview's block that reaches
            // the network is the preview's doing, the index prefetch at the tail aside.
            entry.createInput(coroutineContext).use {
                entry.createInput(coroutineContext).use { preview ->
                    val at = block * 40
                    assertContentEquals(content.copyOfRange(at.toInt(), at.toInt() + 16), preview.readAt(at, 16))
                    delay(300.milliseconds)
                    val indexStart = content.size - PikPakFileEntry.INDEX_BYTES
                    val beyond = source.requests.filter { it.start >= at + block * 2 && it.start < indexStart }
                    assertTrue(beyond.isEmpty(), "the preview read ahead like a player: $beyond")
                }
            }
        } finally {
            entry.close()
        }
    }

    @Test
    fun `a complete file plays from disk without the cloud`() = runBlocking {
        val directory = SystemPaths.createTempDirectory("entry-complete")
        val content = payload((block * 3 + 7).toInt())
        directory.resolve("01.mkv").writeBytes(content)
        PikPakSavedFiles.markFullyDownloaded(directory.resolve("01.mkv"), content.size.toLong())
        val source = FakeRangeSource(content)
        var prepared = 0
        val entry = testEntry(directory, "01.mkv", content.size.toLong(), source, onPrepare = { prepared++ })
        try {
            entry.createInput(coroutineContext).use { input ->
                assertContentEquals(content.copyOfRange((block * 3).toInt(), content.size), input.readAt(block * 3, 7))
            }
            assertEquals(0, prepared, "a complete file asked the cloud for a link")
            assertTrue(source.requests.isEmpty(), "a complete file reached the network")
        } finally {
            entry.close()
        }
    }

    @Test
    fun `an input outliving a deleted file fails in terms the player handles`() = runBlocking {
        val content = payload((block * 8).toInt())
        val entry = testEntry(SystemPaths.createTempDirectory("entry-deleted"), "01.mkv", content.size.toLong(), FakeRangeSource(content))
        try {
            val handle = entry.createHandle()
            val input = entry.createInput(coroutineContext)
            try {
                input.readAt(0, 16)
                handle.closeAndDelete()
                // Past the window the first read filled, so this has to go back to the cache, and the
                // store it was opened on is gone.
                input.seekTo(block * 4)
                val failure = assertFailsWith<IOException> { input.read(ByteArray(16), 0, 16) }
                assertIs<IOException>(failure)
            } finally {
                input.close()
            }
        } finally {
            entry.close()
        }
    }

    @Test
    fun `what was only played goes with the session and a kept file stays`() = runBlocking {
        val content = payload((block * 8).toInt())
        val played = SystemPaths.createTempDirectory("entry-played")
        val onlyPlayed = testEntry(played, "01.mkv", content.size.toLong(), FakeRangeSource(content))
        onlyPlayed.createInput(coroutineContext).use { it.readAt(0, 16) }
        waitUntil("the played block reaches the disk") { played.resolve("01.mkv").exists() }
        onlyPlayed.close()
        assertFalse(played.resolve("01.mkv").exists(), "what was only played outlived its session")

        val cached = SystemPaths.createTempDirectory("entry-kept")
        val kept = testEntry(cached, "01.mkv", content.size.toLong(), FakeRangeSource(content))
        val handle = kept.createHandle()
        handle.resume(FilePriority.NORMAL)
        withTimeout(60.seconds) { kept.fileStats.first { it.isDownloadFinished } }
        handle.close()
        kept.close()
        assertContentEquals(content, cached.resolve("01.mkv").readBytes(), "a cached file went with its session")
    }

    @Test
    fun `a finished file the session never touched stays although its kept flag was lost`() = runBlocking {
        val content = payload((block * 8).toInt())
        val directory = SystemPaths.createTempDirectory("entry-untouched")
        val downloaded = testEntry(directory, "01.mkv", content.size.toLong(), FakeRangeSource(content))
        val handle = downloaded.createHandle()
        handle.resume(FilePriority.NORMAL)
        withTimeout(60.seconds) { downloaded.fileStats.first { it.isDownloadFinished } }
        handle.close()
        downloaded.close()

        // Another episode of the same torrent is played: this entry is in the session but unread
        testEntry(directory, "01.mkv", content.size.toLong(), FakeRangeSource(content), kept = false).close()
        assertContentEquals(content, directory.resolve("01.mkv").readBytes(), "an untouched download went with the session")
    }

    @Test
    fun `deleting a kept file records it as no longer kept`() = runBlocking {
        val content = payload((block * 8).toInt())
        val persisted = CopyOnWriteArrayList<Boolean>()
        val entry = testEntry(
            SystemPaths.createTempDirectory("entry-delete-kept"), "01.mkv", content.size.toLong(), FakeRangeSource(content),
            persistKept = { persisted += it },
        )
        try {
            // The deletion of a finished download resumes it first, which marks it kept
            val handle = entry.createHandle()
            handle.resume(FilePriority.NORMAL)
            handle.closeAndDelete()
            waitUntil("the kept flag is written") { persisted.isNotEmpty() }
            // A write still queued from the resume must not land after the delete's
            delay(500.milliseconds)
            assertEquals(false, persisted.last(), "a deleted file is still recorded as kept")
        } finally {
            entry.close()
        }
    }

    @Test
    fun `an input keeps playing when its cache is replaced`() = runBlocking {
        val content = payload((block * 16).toInt())
        var cloud: FakeCloudSource? = null
        val entry = testEntry(
            SystemPaths.createTempDirectory("entry-reopen"), "01.mkv", content.size.toLong(), FakeRangeSource(content),
            onCloud = { cloud = it },
        )
        try {
            entry.createInput(coroutineContext).use { input ->
                input.readAt(0, 16)
                // An account switch retires the cache the player was reading; media3 keeps its input
                cloud!!.release()
                val at = block * 10
                assertContentEquals(content.copyOfRange(at.toInt(), at.toInt() + 16), input.readAt(at, 16))
            }
        } finally {
            entry.close()
        }
    }

    private suspend fun waitUntil(what: String, condition: suspend () -> Boolean) {
        withTimeout(20.seconds) { while (!condition()) delay(10.milliseconds) }
        assertTrue(condition(), what)
    }

    @Test
    fun `a prefetch range lands on disk`() = runBlocking {
        val content = payload((block * 32).toInt())
        val source = FakeRangeSource(content)
        val entry = testEntry(SystemPaths.createTempDirectory("entry-prefetch-range"), "01.mkv", content.size.toLong(), source)
        try {
            val handle = entry.createHandle()
            val range = block * 10 + 100..block * 12 + 5
            handle.setPrefetchRange(range)
            withTimeout(10.seconds) {
                while (!(10..12).all { entry.pieceFinished(it) }) delay(50.milliseconds)
            }
            assertTrue(source.requests.all { it.start in block * 10..block * 12 }, "${source.requests}")
            handle.close()
        } finally {
            entry.close()
        }
    }

    private fun PikPakFileEntry.pieceFinished(index: Int): Boolean =
        with(pieces) { pieces.createPieceByListIndexUnsafe(index).state == PieceState.FINISHED }

    private suspend fun settledDownloadedBytes(entry: PikPakFileEntry): Long {
        var last = -1L
        while (true) {
            val now = entry.fileStats.first().downloadedBytes
            if (now == last) return now
            last = now
            delay(300.milliseconds)
        }
    }

    private suspend fun entryData(entry: PikPakFileEntry): ByteArray = entry.resolveFile().readBytes()
}
