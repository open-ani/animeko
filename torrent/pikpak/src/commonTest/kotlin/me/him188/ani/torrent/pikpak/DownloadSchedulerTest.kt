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
import io.ktor.utils.io.ByteReadChannel
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.atomicfu.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.torrent.api.pieces.MutablePieceList
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DownloadSchedulerTest {
    private val pieceSize = 1024L
    private val pieceCount = 24
    private val totalLength = pieceSize * pieceCount
    private val content = ByteArray(totalLength.toInt()) { (it * 31 % 251).toByte() }

    private class ReadLog {
        private val lock = SynchronizedObject()
        private val entries = mutableListOf<String>()

        fun record(name: String) = synchronized(lock) { entries += name }
        fun snapshot(): List<String> = synchronized(lock) { entries.toList() }
    }

    private class LoggingSource(
        private val content: ByteArray,
        private val name: String,
        private val log: ReadLog,
    ) : RangeSource {
        override suspend fun <T> read(
            start: Long,
            length: Long,
            priority: Int,
            block: suspend (ByteReadChannel) -> T,
        ): T = block(ByteReadChannel(readBytes(start, length, priority)))

        override suspend fun readBytes(start: Long, length: Long, priority: Int): ByteArray {
            log.record(name)
            val from = start.toInt()
            return content.copyOfRange(from, minOf(content.size, from + length.toInt()))
        }
    }

    private fun newPieces(): MutablePieceList = PieceList.create(totalSize = totalLength, pieceSize = pieceSize)

    private fun newFetcher(
        dir: SystemPath,
        name: String,
        pieces: MutablePieceList,
        scheduler: DownloadScheduler,
        log: ReadLog,
    ) = PieceFetcher(
        source = LoggingSource(content, name, log),
        file = dir.resolve("$name.mkv"),
        pieces = pieces,
        totalLength = totalLength,
        concurrency = 2,
        logTag = name,
        onPieceDownloaded = {},
        parentCoroutineContext = Dispatchers.IO_,
        slot = scheduler.newSlot(name),
        maxRequestBytes = pieceSize,
    )

    private suspend fun awaitComplete(fetcher: PieceFetcher) = withTimeout(30.seconds) {
        while (!fetcher.isComplete) delay(10.milliseconds)
    }

    @Test
    fun `downloads beyond the limit do not fetch`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-scheduler-limit")
        val scheduler = DownloadScheduler(limit = 1)
        val log = ReadLog()
        val wanted = (0 until pieceCount).toList()

        val firstPieces = newPieces()
        val secondPieces = newPieces()
        val first = newFetcher(dir, "first", firstPieces, scheduler, log)
        val second = newFetcher(dir, "second", secondPieces, scheduler, log)

        first.start()
        first.downloadOnly(emptyList(), wanted)
        second.start()
        second.downloadOnly(emptyList(), wanted)

        awaitComplete(first)
        awaitComplete(second)
        first.close()
        second.close()

        val order = log.snapshot()
        val switches = order.zipWithNext().count { (a, b) -> a != b }
        assertEquals(1, switches, "the two downloads interleaved: $order")
    }

    @Test
    fun `a file being played spends one of the slots`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-scheduler-playing")

        val scheduler = DownloadScheduler(limit = 1)
        val log = ReadLog()
        val wanted = (0 until pieceCount).toList()

        val playingPieces = newPieces()
        val otherPieces = newPieces()
        val playingSlot = scheduler.newSlot("playing")
        val playing = PieceFetcher(
            source = LoggingSource(content, "playing", log),
            file = dir.resolve("playing.mkv"),
            pieces = playingPieces,
            totalLength = totalLength,
            concurrency = 2,
            logTag = "playing",
            onPieceDownloaded = {},
            parentCoroutineContext = Dispatchers.IO_,
            slot = playingSlot,
            maxRequestBytes = pieceSize,
        )
        val other = newFetcher(dir, "other", otherPieces, scheduler, log)

        playingSlot.openStream()
        playing.start()
        playing.downloadOnly(emptyList(), wanted)
        other.start()
        other.downloadOnly(emptyList(), wanted)

        awaitComplete(playing)
        val otherProgress = other.downloadedBytes

        playing.close()

        assertEquals(0L, otherProgress, "a download of another episode ran while the only slot was played")

        playingSlot.closeStream()
        awaitComplete(other)
        other.close()
        assertTrue(other.isComplete)
    }

    @Test
    fun `a download backing off after a failure does not hold the slot`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-scheduler-backoff")
        val scheduler = DownloadScheduler(limit = 1)
        val log = ReadLog()
        val wanted = (0 until pieceCount).toList()

        val failingPieces = newPieces()
        val failing = PieceFetcher(
            source = FakeRangeSource(content, failFirstReads = Int.MAX_VALUE),
            file = dir.resolve("failing.mkv"),
            pieces = failingPieces,
            totalLength = totalLength,
            concurrency = 2,
            logTag = "failing",
            onPieceDownloaded = {},
            parentCoroutineContext = Dispatchers.IO_,
            slot = scheduler.newSlot("failing"),
            // Longer than the completion timeout below: the second file must not wait this out.
            retryDelay = 5.minutes,
            maxRequestBytes = pieceSize,
        )
        val otherPieces = newPieces()
        val other = newFetcher(dir, "other", otherPieces, scheduler, log)

        failing.start()
        failing.downloadOnly(emptyList(), wanted)
        withTimeout(30.seconds) { while (failing.error.value == null) delay(10.milliseconds) }

        other.start()
        other.downloadOnly(emptyList(), wanted)
        awaitComplete(other)

        failing.close()
        other.close()
        assertTrue(other.isComplete)
    }

    @Test
    fun `one other episode keeps downloading while a file is played`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-scheduler-second")
        val scheduler = DownloadScheduler(limit = 2)
        val log = ReadLog()
        val wanted = (0 until pieceCount).toList()

        val playingSlot = scheduler.newSlot("playing")
        playingSlot.openStream()

        val otherPieces = newPieces()
        val other = newFetcher(dir, "other", otherPieces, scheduler, log)
        other.start()
        other.downloadOnly(emptyList(), wanted)

        awaitComplete(other)
        other.close()
        playingSlot.closeStream()
        assertTrue(other.isComplete)
    }

    // Holds each read long enough for the workers to pile up, and remembers how high the pile got.
    private class ConcurrencyProbe(private val content: ByteArray) : RangeSource {
        private val inFlight = atomic(0)
        val peak = atomic(0)

        override suspend fun <T> read(
            start: Long,
            length: Long,
            priority: Int,
            block: suspend (ByteReadChannel) -> T,
        ): T = block(ByteReadChannel(readBytes(start, length, priority)))

        override suspend fun readBytes(start: Long, length: Long, priority: Int): ByteArray {
            val now = inFlight.incrementAndGet()
            peak.update { maxOf(it, now) }
            try {
                delay(20.milliseconds)
                val from = start.toInt()
                return content.copyOfRange(from, minOf(content.size, from + length.toInt()))
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private fun newProbedFetcher(dir: SystemPath, pieces: MutablePieceList, scheduler: DownloadScheduler, probe: ConcurrencyProbe) =
        PieceFetcher(
            source = probe,
            file = dir.resolve("probed.mkv"),
            pieces = pieces,
            totalLength = totalLength,
            concurrency = 8,
            logTag = "probed",
            onPieceDownloaded = {},
            parentCoroutineContext = Dispatchers.IO_,
            slot = scheduler.newSlot("probed"),
            streaming = scheduler.streaming,
            maxRequestBytes = pieceSize,
        )

    @Test
    fun `a download keeps few workers while a stream is open`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-scheduler-throttle")
        val scheduler = DownloadScheduler(limit = 2)
        val wanted = (0 until pieceCount).toList()

        val playingSlot = scheduler.newSlot("playing")
        playingSlot.openStream()

        val probe = ConcurrencyProbe(content)
        val fetcher = newProbedFetcher(dir, newPieces(), scheduler, probe)
        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)

        awaitComplete(fetcher)
        fetcher.close()
        playingSlot.closeStream()
        assertTrue(
            probe.peak.value <= PieceFetcher.WORKERS_WHILE_STREAMING,
            "peak in-flight ${probe.peak.value} while a stream was open",
        )
    }

    @Test
    fun `a download brings its workers back once the stream closes`() = runBlocking {
        val dir = SystemPaths.createTempDirectory("pikpak-scheduler-unthrottle")
        val scheduler = DownloadScheduler(limit = 2)
        val wanted = (0 until pieceCount).toList()

        val playingSlot = scheduler.newSlot("playing")
        playingSlot.openStream()

        val probe = ConcurrencyProbe(content)
        val fetcher = newProbedFetcher(dir, newPieces(), scheduler, probe)
        fetcher.start()
        fetcher.downloadOnly(emptyList(), wanted)

        withTimeout(30.seconds) {
            while (fetcher.downloadedBytes < 4 * pieceSize) delay(10.milliseconds)
        }
        assertTrue(probe.peak.value <= PieceFetcher.WORKERS_WHILE_STREAMING)
        playingSlot.closeStream()

        awaitComplete(fetcher)
        fetcher.close()
        assertTrue(
            probe.peak.value > PieceFetcher.WORKERS_WHILE_STREAMING,
            "peak in-flight stayed at ${probe.peak.value} after the stream closed",
        )
    }
}
