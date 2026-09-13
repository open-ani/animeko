/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.pieces.MutablePieceList
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The bitmap is the only evidence a piece holds real bytes, and the data file is preallocated, so a
 * bit persisted ahead of its bytes turns into zeros fed to the player after the next start. These
 * tests drive the fetcher against a data file that tells "written" apart from "reached the medium",
 * which a real file cannot be asked about.
 */
class PieceFetcherDurabilityTest {
    private val pieceSize = 1024L
    private val pieceCount = 4
    private val totalLength = pieceSize * pieceCount

    // No zero bytes: a piece that was never written also reads back as the right length of zeros,
    // so zeros in the durable image must not be mistakable for content.
    private val content = ByteArray(totalLength.toInt()) { (it * 7 % 251 + 1).toByte() }

    private val persistInterval = 50.milliseconds

    private fun newFetcher(dataFile: SystemPath, pieces: MutablePieceList, handle: DataFile) = PieceFetcher(
        source = FakeRangeSource(content),
        file = dataFile,
        pieces = pieces,
        totalLength = totalLength,
        concurrency = 2,
        logTag = "durability",
        onPieceDownloaded = {},
        parentCoroutineContext = Dispatchers.IO_,
        retryDelay = 50.milliseconds,
        maxRequestBytes = pieceSize,
        persistInterval = persistInterval,
        openDataFile = { handle },
    )

    @Test
    fun `no bit is persisted while the data file cannot be synced`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-durability-sync-fails")
        val dataFile = dir.resolve("data.bin")
        val handle = FakeDataFile(totalLength.toInt(), syncFailure = IOException("simulated sync failure"))
        val pieces = PieceList.create(totalLength, pieceSize)
        val fetcher = newFetcher(dataFile, pieces, handle)

        fetcher.start()
        fetcher.downloadOnly(emptyList(), (0 until pieceCount).toList())
        awaitAllFinished(pieces)
        // Several persist cycles, so a bitmap written without regard for the sync has every chance
        // to appear.
        delay(persistInterval * 5)

        val bitmapFile = PieceBitmap.pathFor(dataFile)
        assertFalse(
            bitmapFile.exists() && bitmapFile.readBytes().any { it.toInt() != 0 },
            "pieces were recorded as complete although the data file never reached the medium",
        )
        runCatching { fetcher.close() }
        Unit
    }

    @Test
    fun `every persisted bit is backed by bytes that reached the medium`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-durability-sync-order")
        val dataFile = dir.resolve("data.bin")
        val handle = FakeDataFile(totalLength.toInt())
        val pieces = PieceList.create(totalLength, pieceSize)
        val fetcher = newFetcher(dataFile, pieces, handle)

        fetcher.start()
        fetcher.downloadOnly(emptyList(), (0 until pieceCount).toList())
        awaitAllFinished(pieces)
        val persisted = withTimeout(TIMEOUT) {
            var bits = readPersistedBits(dataFile)
            while (bits.none { it }) {
                delay(POLL)
                bits = readPersistedBits(dataFile)
            }
            bits
        }

        // The snapshot is taken after the bitmap was read, so it can only be more durable than the
        // image the bitmap was written against; a violation found here is a real one.
        val durable = handle.durableImage()
        for (index in 0 until pieceCount) {
            if (!persisted[index]) continue
            val start = (index * pieceSize).toInt()
            assertContentEquals(
                content.copyOfRange(start, start + pieceSize.toInt()),
                durable.copyOfRange(start, start + pieceSize.toInt()),
                "piece $index is marked complete on disk but its bytes are still only in the page cache",
            )
        }
        fetcher.close()
    }

    private suspend fun awaitAllFinished(pieces: MutablePieceList) = withTimeout(TIMEOUT) {
        while ((0 until pieceCount).any { with(pieces) { pieces.getByPieceIndex(it).state } != PieceState.FINISHED }) {
            delay(POLL)
        }
    }

    private fun readPersistedBits(dataFile: SystemPath): BooleanArray {
        val bitmapFile = PieceBitmap.pathFor(dataFile)
        val bytes = runCatching { if (bitmapFile.exists()) bitmapFile.readBytes() else null }.getOrNull()
            ?: return BooleanArray(pieceCount)
        return BooleanArray(pieceCount) { bytes[it / 8].toInt() and (1 shl (it % 8)) != 0 }
    }

    private companion object {
        val TIMEOUT = 10.seconds
        val POLL = 10.milliseconds
    }
}

/**
 * A data file whose writes land in memory and only become durable on [sync], which is the
 * distinction the piece bitmap relies on and the one a real file hides.
 */
private class FakeDataFile(
    size: Int,
    private val syncFailure: Throwable? = null,
) : DataFile {
    private val lock = SynchronizedObject()
    private var position = 0L
    private val written = ByteArray(size)
    private var durable = ByteArray(size)

    fun durableImage(): ByteArray = synchronized(lock) { durable.copyOf() }

    override fun seek(position: Long) = synchronized(lock) { this.position = position }

    override fun write(buffer: ByteArray, offset: Int, length: Int) = synchronized(lock) {
        buffer.copyInto(written, position.toInt(), offset, offset + length)
        position += length
    }

    override fun flush() = Unit

    override fun sync() {
        syncFailure?.let { throw it }
        synchronized(lock) { durable = written.copyOf() }
    }

    override fun setLength(newLength: Long) {
        check(newLength == written.size.toLong()) { "the fake is fixed at ${written.size} bytes" }
    }

    override fun length(): Long = written.size.toLong()

    override fun close() = Unit
}
