/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.PikPakException
import io.github.nihildigit.pikpak.PikPakStreamReader
import io.github.nihildigit.pikpak.RangeSource
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StreamSeekableInputInterruptTest {
    // ExoPlayer's Loader cancels a load by interrupting the reading thread. runBlocking answers an
    // interrupt with InterruptedException, which media3 does not recognise and turns into a fatal
    // "Unexpected exception loading stream"; an IOException is the cancellation it handles.
    @Test
    fun `an interrupted read reports an IO failure and leaves the flag set`() {
        val stalling = object : RangeSource {
            override suspend fun <T> read(
                start: Long,
                length: Long,
                priority: Int,
                block: suspend (ByteReadChannel) -> T,
            ): T = block(ByteReadChannel(readBytes(start, length, priority)))

            override suspend fun readBytes(start: Long, length: Long, priority: Int): ByteArray =
                awaitCancellation()
        }
        val input = StreamSeekableInput(
            name = "stalled.mkv",
            reader = PikPakStreamReader(
                source = stalling,
                size = 4096,
                concurrency = 1,
                parentCoroutineContext = Dispatchers.IO,
            ),
        )

        val reading = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val flagSurvived = AtomicBoolean(false)
        val reader = thread(name = "interrupted-cloud-reader", isDaemon = true) {
            reading.countDown()
            try {
                input.read(ByteArray(256), 0, 256)
            } catch (e: Throwable) {
                failure.set(e)
                flagSurvived.set(Thread.currentThread().isInterrupted)
            }
        }

        assertTrue(reading.await(5, TimeUnit.SECONDS))
        Thread.sleep(200) // let the read park on the stalled source rather than fail before it starts
        reader.interrupt()
        reader.join(5000)

        assertFalse(reader.isAlive, "the read must not outlive the interrupt")
        assertIs<InterruptedIOException>(failure.get())
        assertTrue(flagSurvived.get(), "the caller decides what the interrupt means; the flag must reach it")

        input.close()
    }

    // The SDK wraps a lost DNS lookup in PikPakException, a RuntimeException, which ExoPlayer's
    // Loader treats as fatal rather than retrying. Screen-on beats the network back by a second,
    // so that difference decides whether playback resumes or dies.
    @Test
    fun `a runtime failure from the sdk reaches the player as an IO failure`() {
        val failing = object : RangeSource {
            override suspend fun <T> read(
                start: Long,
                length: Long,
                priority: Int,
                block: suspend (ByteReadChannel) -> T,
            ): T = block(ByteReadChannel(readBytes(start, length, priority)))

            override suspend fun readBytes(start: Long, length: Long, priority: Int): ByteArray =
                throw PikPakException(-1, "stream failed at offset $start", cause = UnknownHostException("no network"))
        }
        val input = StreamSeekableInput(
            name = "offline.mkv",
            reader = PikPakStreamReader(
                source = failing,
                size = 4096,
                concurrency = 1,
                parentCoroutineContext = Dispatchers.IO,
            ),
            retryDelay = 10.milliseconds,
            retryWindow = 50.milliseconds,
        )

        val failure = assertFailsWith<IOException> { input.read(ByteArray(256), 0, 256) }
        assertIs<PikPakException>(failure.cause)

        input.close()
    }

    // Screen-on beats the network back by a second or two. The player gives up in about three, so
    // without this the same wake-up succeeds or ends playback depending on the timing.
    @Test
    fun `a read retries until the network comes back`() {
        val content = ByteArray(4096) { (it % 251).toByte() }
        val offline = AtomicBoolean(true)
        val attempts = AtomicInteger(0)
        val flaky = object : RangeSource {
            override suspend fun <T> read(
                start: Long,
                length: Long,
                priority: Int,
                block: suspend (ByteReadChannel) -> T,
            ): T = block(ByteReadChannel(readBytes(start, length, priority)))

            override suspend fun readBytes(start: Long, length: Long, priority: Int): ByteArray {
                if (offline.get() && attempts.incrementAndGet() >= 2) offline.set(false)
                if (offline.get()) {
                    throw PikPakException(-1, "stream failed at offset $start", cause = UnknownHostException("no network"))
                }
                val end = minOf(content.size.toLong(), start + length).toInt()
                return content.copyOfRange(start.toInt(), end)
            }
        }
        val input = StreamSeekableInput(
            name = "flaky.mkv",
            reader = PikPakStreamReader(
                source = flaky,
                size = content.size.toLong(),
                concurrency = 1,
                parentCoroutineContext = Dispatchers.IO,
            ),
            retryDelay = 10.milliseconds,
            retryWindow = 5.seconds,
        )

        val buffer = ByteArray(256)
        assertEquals(256, input.read(buffer, 0, 256))
        assertContentEquals(content.copyOfRange(0, 256), buffer)

        input.close()
    }
}
