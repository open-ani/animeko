/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.RangeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.torrent.api.pieces.MutablePieceList
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.io.RandomAccessFile
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PieceFetcherTest {
    private val pieceSize = 1024L
    private val pieceCount = 8
    private val totalLength = pieceSize * pieceCount
    private val content = ByteArray(totalLength.toInt()) { (it * 31 % 251).toByte() }

    private fun newPieces(): MutablePieceList = PieceList.create(totalLength, pieceSize)

    private fun newFetcher(
        dir: SystemPath,
        source: RangeSource,
        pieces: MutablePieceList,
        concurrency: Int = 1,
        retryDelay: Duration = 50.milliseconds,
        maxRequestBytes: Long = 4 * pieceSize,
    ) = PieceFetcher(
        source = source,
        file = dir.resolve(DATA_NAME),
        pieces = pieces,
        totalLength = totalLength,
        concurrency = concurrency,
        logTag = "test",
        onPieceDownloaded = {},
        parentCoroutineContext = Dispatchers.IO_,
        retryDelay = retryDelay,
        maxRequestBytes = maxRequestBytes,
    )

    private suspend fun awaitFinished(pieces: MutablePieceList, pieceIndices: List<Int>) = withTimeout(TIMEOUT) {
        while (pieceIndices.any { with(pieces) { pieces.getByPieceIndex(it).state } != PieceState.FINISHED }) {
            delay(POLL)
        }
    }

    private fun assertPieceContent(dir: SystemPath, pieceIndices: List<Int>) {
        val onDisk = dir.resolve(DATA_NAME).readBytes()
        assertEquals(totalLength, onDisk.size.toLong(), "the data file was not preallocated to its full length")
        for (index in pieceIndices) {
            val start = (index * pieceSize).toInt()
            val end = start + pieceSize.toInt()
            assertContentEquals(
                content.copyOfRange(start, end),
                onDisk.copyOfRange(start, end),
                "piece $index does not hold the bytes at its own offset",
            )
        }
    }

    @Test
    fun `writes every piece at its own offset`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-fetch-offsets")
        val source = FakeRangeSource(content)
        val pieces = newPieces()
        val fetcher = newFetcher(dir, source, pieces)

        val wanted = listOf(7, 0, 4, 5, 1)
        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)
        awaitFinished(pieces, wanted)
        fetcher.close()

        assertPieceContent(dir, wanted)
    }

    @Test
    fun `contiguous pieces share one request`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-fetch-coalesce")
        val source = FakeRangeSource(content)
        val pieces = newPieces()
        val fetcher = newFetcher(dir, source, pieces)

        val wanted = listOf(0, 1, 2, 3)
        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)
        awaitFinished(pieces, wanted)
        fetcher.close()

        assertEquals(
            1, source.requests.size,
            "contiguous pieces within one request's budget were fetched one by one: ${source.requests}",
        )
        assertEquals(FakeRangeSource.Request(0L, 4 * pieceSize), source.requests.single())
        assertPieceContent(dir, wanted)
    }

    @Test
    fun `pieces recorded in the bitmap are not fetched again`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-fetch-resume")
        val first = FakeRangeSource(content)
        val firstPieces = newPieces()
        val firstFetcher = newFetcher(dir, first, firstPieces)
        val done = listOf(0, 1, 2, 3)
        firstFetcher.start()
        firstFetcher.downloadOnly(emptyList(), done)
        awaitFinished(firstPieces, done)
        firstFetcher.close()

        val second = FakeRangeSource(content)
        val secondPieces = newPieces()
        val secondFetcher = newFetcher(dir, second, secondPieces)
        val rest = listOf(4, 5, 6, 7)
        assertTrue(
            done.all { with(secondPieces) { secondPieces.getByPieceIndex(it).state } == PieceState.FINISHED },
            "the bitmap was not applied to the piece list of the restarted download",
        )

        secondFetcher.start()
        secondFetcher.downloadOnly(emptyList(), done + rest)
        awaitFinished(secondPieces, done + rest)
        secondFetcher.close()

        assertTrue(
            second.requests.all { it.start >= 4 * pieceSize },
            "the restarted download fetched bytes it already had: ${second.requests}",
        )
        assertTrue(secondFetcher.isComplete)
        assertPieceContent(dir, done + rest)
    }

    @Test
    fun `a file inside a subfolder of the torrent creates its directories`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-fetch-nested")
        val source = FakeRangeSource(content)
        val pieces = newPieces()
        val nested = dir.resolve("Season 1").resolve(DATA_NAME)
        val fetcher = PieceFetcher(
            source = source,
            file = nested,
            pieces = pieces,
            totalLength = totalLength,
            concurrency = 2,
            logTag = "test",
            onPieceDownloaded = {},
            parentCoroutineContext = Dispatchers.IO_,
        )

        val wanted = (0 until pieceCount).toList()
        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)
        awaitFinished(pieces, wanted)
        fetcher.close()

        assertContentEquals(content, nested.readBytes())
    }

    @Test
    fun `a truncated data file invalidates the pieces past its end`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-fetch-truncated")
        val source = FakeRangeSource(content)
        val pieces = newPieces()
        val fetcher = newFetcher(dir, source, pieces)
        val wanted = (0 until pieceCount).toList()
        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)
        awaitFinished(pieces, wanted)
        fetcher.close()

        val kept = 3
        RandomAccessFile(dir.resolve(DATA_NAME), "rw").use { it.setLength(kept * pieceSize) }

        val reopened = newFetcher(dir, FakeRangeSource(content), newPieces())
        assertFalse(reopened.isComplete, "a truncated file was reported complete")
        assertEquals(kept * pieceSize, reopened.downloadedBytes, "pieces past the end of the file survived")
        reopened.close()
    }

    @Test
    fun `a failing read is retried instead of killing the worker`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-fetch-retry")
        val source = FakeRangeSource(content, failFirstReads = 2)
        val pieces = newPieces()
        val fetcher = newFetcher(dir, source, pieces)

        val wanted = (0 until pieceCount).toList()
        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)
        awaitFinished(pieces, wanted)

        assertEquals(null, fetcher.error.value, "the error of an attempt that later succeeded was not cleared")
        assertEquals(totalLength, fetcher.downloadedBytes)
        assertTrue(fetcher.deliveredBytes >= totalLength)
        fetcher.close()
        assertPieceContent(dir, wanted)
    }

    @Test
    fun `deleteTarget leaves the fetcher usable`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-fetch-delete")
        val source = FakeRangeSource(content)
        val pieces = newPieces()
        val fetcher = newFetcher(dir, source, pieces)

        val wanted = (0 until pieceCount).toList()
        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)
        awaitFinished(pieces, wanted)
        val deliveredBeforeDelete = fetcher.deliveredBytes

        fetcher.deleteTarget()
        assertFalse(fetcher.isComplete)
        assertFalse(dir.resolve(DATA_NAME).exists(), "the data file survived deleteTarget")
        assertFalse(PieceBitmap.pathFor(dir.resolve(DATA_NAME)).exists(), "the bitmap survived deleteTarget")

        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)
        awaitFinished(pieces, wanted)
        fetcher.close()

        assertTrue(
            fetcher.deliveredBytes > deliveredBeforeDelete,
            "delivered bytes went backwards or the second run fetched nothing",
        )
        assertPieceContent(dir, wanted)
    }

    private companion object {
        const val DATA_NAME = "video.mkv"
        val TIMEOUT = 30.seconds
        val POLL = 10.milliseconds
    }
}
