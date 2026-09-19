/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.io.RandomAccessFile
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/**
 * An input can outlive the Stream it was opened on.
 *
 * It is not a TorrentFileHandle, so nothing in the entry counts it, and deleteFiles replaces the
 * whole Stream without asking whether anything is still reading. That is the only remaining path
 * that swaps a Stream under a live input, and the reason the staleness check exists.
 */
class InputInvalidationTest {
    private val length = PikPakFileEntry.PIECE_SIZE
    private val content = ByteArray(length.toInt()) { (it * 31 % 251).toByte() }

    private fun entry(saveDirectory: SystemPath) = PikPakFileEntry(
        index = 0,
        length = length,
        saveDirectory = saveDirectory,
        relativePath = RELATIVE_PATH,
        torrentId = "invalidation-source-key",
        parentCoroutineContext = Dispatchers.IO_,
        meta = PikPakFileMeta(index = 0, pathInTorrent = RELATIVE_PATH, gcid = "GCID-01", length = length),
        source = FakeRangeSource(content),
        concurrency = 1,
        scheduler = DownloadScheduler(),
        onHandleCountChanged = {},
    )

    // Block body on purpose. With an expression body the value of assertFailsWith -- a Throwable --
    // becomes the method's return type, and JUnit skips a test method that does not return void, so
    // the test silently never runs and its green is worth nothing.
    @Test
    fun `an input outliving the cache it was opened on fails in terms the player handles`() {
        runBlocking {
            val saveDirectory = SystemPaths.createTempDirectory("pikpak-invalidation")
            val entry = entry(saveDirectory)

            val handle = entry.createHandle()
            handle.resume(FilePriority.NORMAL)
            withTimeout(60.seconds) { entry.fileStats.first { it.isDownloadFinished } }

            val input = entry.createInput(coroutineContext)
            try {
                val head = ByteArray(256)
                assertEquals(256, input.read(head, 0, 256))
                assertContentEquals(content.copyOfRange(0, 256), head)

                // The user deletes this episode's cache while the player still holds the input.
                // Windows refuses to unlink a file another handle has open, and the input holds one
                // as soon as it has served a read from disk, so the delete itself can fail here.
                // Both outcomes are real; what has to hold either way is that nothing reaches the
                // player as a type it treats as fatal.
                val deleteFailure = runCatching { handle.closeAndDelete() }.exceptionOrNull()
                if (deleteFailure != null) {
                    assertIs<IOException>(deleteFailure, "deleting a playing episode failed as $deleteFailure")
                    return@runBlocking
                }

                assertFalse(saveDirectory.resolve(RELATIVE_PATH).exists(), "the cached file survived the delete")

                // Past the window the first read filled, so this has to go back to a source, and
                // the Stream it was opened on is gone.
                input.seekTo(BEYOND_FIRST_WINDOW)
                assertFailsWith<IOException> { input.read(ByteArray(256), 0, 256) }
            } finally {
                input.close()
                entry.close()
            }
        }
    }

    /**
     * Control: the same post-delete state without the staleness check, which is what the check is
     * buying and the reason it cannot be asserted from the bytes alone.
     *
     * It reads on, correctly. Nothing here is corrupt -- the piece list was reset to READY by the
     * delete, so no disk read is attempted, and the cloud source still holds the same bytes. What
     * the check adds is refusing to serve a file the user asked to remove, and doing it as an
     * IOException rather than whatever a read of a deleted file happens to raise.
     */
    @Test
    fun `without the check the same input keeps streaming the deleted file`() {
        val saveDirectory = SystemPaths.createTempDirectory("pikpak-invalidation-control")
        val absent = saveDirectory.resolve(RELATIVE_PATH)
        val input = HybridSeekableInput(
            name = RELATIVE_PATH,
            dataPath = absent,
            pieces = PieceList.create(length, PikPakFileEntry.PIECE_SIZE),
            size = length,
            openStream = { CountingCloudStream(content) },
            onDelivered = {},
            onCloudReadStarted = {},
            onCloudReadFinished = {},
        )
        try {
            input.seekTo(BEYOND_FIRST_WINDOW)
            val buffer = ByteArray(256)
            assertEquals(256, input.read(buffer, 0, 256))
            assertContentEquals(
                content.copyOfRange(BEYOND_FIRST_WINDOW.toInt(), BEYOND_FIRST_WINDOW.toInt() + 256),
                buffer,
            )
        } finally {
            input.close()
        }
    }

    /**
     * The one window the staleness check does not cover, constructed directly rather than raced.
     *
     * deleteTarget unlinks the data file and only then resets the piece list, and the Stream is not
     * replaced until it returns, so in between a fill can still pick the disk. This drives exactly
     * that state: a file the input has already opened, still-finished pieces, and the file gone.
     */
    @Test
    fun `a fill that picks the disk after the file was unlinked does not fail unexpectedly`() {
        val saveDirectory = SystemPaths.createTempDirectory("pikpak-unlink-window")
        val dataPath = saveDirectory.resolve(RELATIVE_PATH)
        RandomAccessFile(dataPath, "rw").use { it.write(content, 0, content.size) }

        val pieces = PieceList.create(length, PikPakFileEntry.PIECE_SIZE)
        with(pieces) { pieces.getByPieceIndex(0).state = PieceState.FINISHED }
        val input = HybridSeekableInput(
            name = RELATIVE_PATH,
            dataPath = dataPath,
            pieces = pieces,
            size = length,
            openStream = { error("every piece is finished; the disk is the only source here") },
            onDelivered = {},
            onCloudReadStarted = {},
            onCloudReadFinished = {},
        )
        try {
            // Opens the handle and marks the file as seen, which is what lets a later fill keep
            // choosing the disk after the name is gone.
            assertEquals(256, input.read(ByteArray(256), 0, 256))

            // Windows may reject unlinking an open file. Platforms that allow it keep the open
            // handle readable after the directory entry is removed.
            val unlinkFailure = runCatching { dataPath.delete() }.exceptionOrNull()
            if (unlinkFailure != null) {
                assertIs<IOException>(unlinkFailure, "unlinking an open file failed as $unlinkFailure")
                return
            }

            input.seekTo(BEYOND_FIRST_WINDOW)
            val buffer = ByteArray(256)
            val read = try {
                input.read(buffer, 0, 256)
            } catch (e: Throwable) {
                // The type is what decides whether playback survives, so anything else is the bug,
                // whether or not this window is reachable through the entry.
                assertIs<IOException>(e, "a read into the unlink window failed as $e")
                return
            }
            // An open handle remains readable after a successful unlink, so the buffered fill
            // completes with the original bytes.
            assertEquals(256, read)
            assertContentEquals(
                content.copyOfRange(BEYOND_FIRST_WINDOW.toInt(), BEYOND_FIRST_WINDOW.toInt() + 256),
                buffer,
            )
        } finally {
            input.close()
        }
    }

    private companion object {
        const val RELATIVE_PATH = "01.mkv"

        // One buffer window past zero, so the read cannot be answered from what the first fill left.
        const val BEYOND_FIRST_WINDOW = 3L * HybridSeekableInput.BUFFER_PER_DIRECTION
    }
}
