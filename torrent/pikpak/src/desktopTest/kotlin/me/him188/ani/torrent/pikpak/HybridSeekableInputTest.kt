/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.PikPakStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.api.pieces.forEach
import me.him188.ani.app.torrent.io.RandomAccessFile
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.resolve
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HybridSeekableInputTest {
    // The window between acquiring the handle and reading through it is a few instructions wide, so the
    // race is driven rather than staged: the fix makes an exception impossible, not merely unlikely.
    //
    // fillBuffer cannot report end-of-input, so a close racing a fill must raise IOException.
    // ExoPlayer retries IOException but treats other exception types as fatal load errors.
    @Test
    fun `a close racing a read of finished pieces fails only in terms the player handles`() = runBlocking {
        val pieceSize = 1024L
        val size = 8 * pieceSize
        val data = SystemPaths.createTempDirectory("pikpak-hybrid-disk-close").resolve("video.mkv")
        val content = ByteArray(size.toInt()) { (it * 31 % 251).toByte() }
        RandomAccessFile(data, "rw").use { it.write(content, 0, content.size) }

        repeat(300) { attempt ->
            val pieces = PieceList.create(size, pieceSize)
            pieces.forEach { piece -> piece.state = PieceState.FINISHED }
            val input = HybridSeekableInput(
                name = "disk.mkv",
                dataPath = data,
                pieces = pieces,
                size = size,
                openStream = { error("every piece is on disk; no stream may be opened") },
                onDelivered = {},
                onCloudReadStarted = {},
                onCloudReadFinished = {},
            )
            val start = CountDownLatch(1)
            val failures = CopyOnWriteArrayList<Throwable>()
            val results = CopyOnWriteArrayList<Int>()
            val reader = thread(name = "hybrid-disk-reader", isDaemon = true) {
                start.await()
                runCatching { input.read(ByteArray(256), 0, 256) }
                    .onSuccess { results += it }
                    .onFailure { failures += it }
            }
            val closer = thread(name = "hybrid-disk-closer", isDaemon = true) {
                start.await()
                input.close()
            }
            start.countDown()
            reader.join(5000)
            closer.join(5000)
            assertFalse(reader.isAlive)
            assertFalse(closer.isAlive)
            assertEquals(1, results.size + failures.size, "attempt $attempt: the read neither returned nor threw")
            for (failure in failures) {
                assertIs<IOException>(failure, "attempt $attempt: a close racing a read threw $failure")
            }
            for (result in results) {
                assertTrue(result == -1 || result == 256, "attempt $attempt: read $result")
            }
        }
    }

    @Test
    fun `close racing the first cloud read balances playback registration`() = runBlocking {
        val started = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val events = CopyOnWriteArrayList<String>()
        val scheduler = DownloadScheduler(limit = 1)
        val slot = scheduler.newSlot("playback")
        val bytes = ByteArray(4096)
        val input = HybridSeekableInput(
            name = "absent.mkv",
            dataPath = SystemPaths.createTempDirectory("pikpak-hybrid-close").resolve("absent.mkv"),
            pieces = PieceList.create(4096, 1024), size = 4096,
            openStream = {
                StreamSeekableInput(
                    name = "absent.mkv",
                    reader = PikPakStreamReader(
                        source = FakeRangeSource(bytes), size = 4096, concurrency = 1,
                        parentCoroutineContext = Dispatchers.IO,
                    ),
                )
            },
            onDelivered = {},
            onCloudReadStarted = {
                started.countDown()
                check(releaseStart.await(5, TimeUnit.SECONDS))
                slot.openStream()
                events += "start"
            },
            onCloudReadFinished = { slot.closeStream(); events += "finish" },
        )
        val reader = thread(name = "hybrid-reader", isDaemon = true) {
            // The read may finish or observe closure; registration must balance in either case.
            runCatching { input.read(ByteArray(1), 0, 1) }
        }
        var closer: Thread? = null
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            closer = thread(name = "hybrid-closer", isDaemon = true) {
                closing.countDown()
                input.close()
            }
            assertTrue(closing.await(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (closer.state != Thread.State.BLOCKED && closer.isAlive && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertTrue(closer.state == Thread.State.BLOCKED || !closer.isAlive)
            releaseStart.countDown()
            reader.join(5000)
            closer.join(5000)
            assertFalse(reader.isAlive)
            assertFalse(closer.isAlive)
            assertEquals(listOf("start", "finish"), events)
            withTimeout(5000) { scheduler.newSlot("next download").awaitTurn() }
        } finally {
            releaseStart.countDown()
            reader.join(5000)
            closer?.join(5000)
            input.close()
            slot.release()
        }
    }
}
