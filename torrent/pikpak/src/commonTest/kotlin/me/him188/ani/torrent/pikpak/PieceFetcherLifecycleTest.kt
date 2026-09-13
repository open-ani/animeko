/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PieceFetcherLifecycleTest {
    private class Fixture {
        val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())
        val scheduler = DownloadScheduler(limit = 1)
        val content = ByteArray(4096) { (it % 251).toByte() }
        val path = SystemPaths.createTempDirectory("pikpak-lifecycle").resolve("episode.mkv")
        val fetcher = PieceFetcher(
            source = FakeRangeSource(content), file = path,
            pieces = PieceList.create(content.size.toLong(), 1024), totalLength = content.size.toLong(),
            concurrency = 2, logTag = "lifecycle", onPieceDownloaded = {},
            parentCoroutineContext = dispatcher, slot = scheduler.newSlot("download"), maxRequestBytes = 1024,
        )

        fun drain() = dispatcher.scheduler.runCurrent()

        suspend fun awaitBytes(bytes: Long) = withTimeout(10_000) {
            while (fetcher.downloadedBytes != bytes) {
                drain()
                delay(1)
            }
            drain()
        }

        suspend fun close() {
            fetcher.stop()
            drain()
            fetcher.close()
        }
    }

    @Test
    fun `pausing a queued download releases its claimed pieces`() = runBlocking {
        val f = Fixture()
        val playing = f.scheduler.newSlot("playing")
        playing.openStream()
        try {
            f.fetcher.downloadOnly(emptyList(), listOf(0, 1, 2, 3))
            f.fetcher.start()
            f.drain()
            assertEquals(0L, f.fetcher.downloadedBytes)

            f.fetcher.stop()
            f.drain()
            playing.closeStream()
            f.fetcher.start()
            f.awaitBytes(4096)
            assertContentEquals(f.content, f.path.readBytes())
        } finally {
            playing.release()
            f.close()
        }
    }

    @Test
    fun `restarting idle workers preserves completion and frees the next download`() = runBlocking {
        val f = Fixture()
        val next = f.scheduler.newSlot("next")
        try {
            f.fetcher.downloadOnly(emptyList(), listOf(0, 1))
            f.fetcher.start()
            f.awaitBytes(2048)

            f.fetcher.stop()
            val restart = async(start = CoroutineStart.UNDISPATCHED) { f.fetcher.start() }
            assertFalse(restart.isCompleted, "restart must await the old workers' cleanup")
            f.drain()
            restart.await()
            f.fetcher.downloadOnly(emptyList(), listOf(0, 1, 2, 3))
            f.drain()
            val nextTurn = async(f.dispatcher) { next.awaitTurn() }
            f.awaitBytes(4096)
            f.drain()
            withTimeout(10_000) { nextTurn.await() }
            assertContentEquals(f.content, f.path.readBytes())
            assertEquals(4096L, f.fetcher.deliveredBytes, "resume must not redownload finished pieces")
        } finally {
            next.release()
            f.close()
        }
    }
}
