/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.io.IOException
import org.openani.mediamp.io.SeekableInput
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Small player reads are coalesced before they reach the cloud stream, and a deleted file stops being served. */
class CloudSeekableInputTest {
    private val window = 1024

    private fun input(stream: CountingCloudStream, bufferSize: Int = window, isStale: () -> Boolean = { false }) =
        CloudSeekableInput(name = "buffering.mkv", stream = stream, isStale = isStale, bufferSize = bufferSize)

    private fun readOneByOne(input: CloudSeekableInput, count: Int): ByteArray {
        val out = ByteArray(count)
        val one = ByteArray(1)
        for (index in 0 until count) {
            assertEquals(1, input.read(one, 0, 1), "one-byte read $index came back short")
            out[index] = one[0]
        }
        return out
    }

    @Test
    fun `a run of one-byte reads costs one cloud read per window rather than one per byte`() {
        val size = 8 * window
        val content = ByteArray(size) { (it * 31 % 251).toByte() }
        val stream = CountingCloudStream(content)
        val input = input(stream)

        val read = readOneByOne(input, size)

        assertContentEquals(content, read)
        // 8192 player reads, eight 1 KiB windows. Unbuffered this was 8192 crossings; the number
        // is asserted exactly because "fewer" would still pass at one per byte minus one.
        assertEquals(8, stream.reads, "one-byte reads still reach the cloud one by one")
        input.close()
    }

    @Test
    fun `a seek inside the window does not reach the cloud at all`() {
        val size = 4 * window
        val content = ByteArray(size) { (it * 7 % 251).toByte() }
        val stream = CountingCloudStream(content)
        val input = input(stream, bufferSize = size)

        val one = ByteArray(1)
        input.seekTo(0)
        input.read(one, 0, 1)
        val afterFirstFill = stream.reads
        assertEquals(1, afterFirstFill)

        // Reading the header, jumping to the index and jumping back is the pattern this has to be
        // free for; all three land in one window here.
        input.seekTo(size - 1L)
        input.read(one, 0, 1)
        assertEquals(content[size - 1], one[0])
        input.seekTo(0)
        input.read(one, 0, 1)
        assertEquals(content[0], one[0])

        assertEquals(afterFirstFill, stream.reads, "a seek inside the buffered window refilled it")
        input.close()
    }

    @Test
    fun `a stale input refuses to refill in terms the player handles`() {
        val content = ByteArray(4 * window) { it.toByte() }
        var stale = false
        val stream = CountingCloudStream(content)
        val input = input(stream, isStale = { stale })
        try {
            assertEquals(1, input.read(ByteArray(1), 0, 1))
            stale = true
            input.seekTo(2L * window)
            assertFailsWith<IOException> { input.read(ByteArray(1), 0, 1) }
            assertEquals(1, stream.reads, "a stale input went back to the cloud")
        } finally {
            input.close()
        }
    }

    @Test
    fun `closing releases the stream once`() {
        var closed = 0
        val stream = CountingCloudStream(ByteArray(window))
        val input = CloudSeekableInput(name = "close.mkv", stream = stream, onClosed = { closed++ })
        input.close()
        input.close()
        assertEquals(1, closed)
        assertTrue(stream.closed)
    }
}

internal class CountingCloudStream(private val content: ByteArray) : SeekableInput {
    data class Request(val start: Long, val length: Int)

    private var pos = 0L

    val requests = mutableListOf<Request>()

    val reads: Int get() = requests.size

    var seeks: Int = 0
        private set

    override val size: Long get() = content.size.toLong()

    override val position: Long get() = pos

    override val bytesRemaining: Long get() = (size - pos).coerceAtLeast(0)

    var deliveredBytes: Long = 0L
        private set

    override fun seekTo(position: Long) {
        require(position >= 0) { "position must be >= 0, got $position" }
        seeks++
        pos = position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pos >= size) return -1
        val count = minOf(length.toLong(), size - pos).toInt()
        requests += Request(pos, count)
        content.copyInto(buffer, offset, pos.toInt(), pos.toInt() + count)
        pos += count
        deliveredBytes += count
        return count
    }

    var closed = false
        private set

    override fun close() {
        closed = true
    }
}
