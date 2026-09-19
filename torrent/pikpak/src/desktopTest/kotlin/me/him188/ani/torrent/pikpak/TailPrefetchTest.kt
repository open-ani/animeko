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
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TailPrefetchTest {
    private val size = 8L * 1024
    private val content = ByteArray(size.toInt()) { (it * 7 % 251).toByte() }

    @Test
    fun `the index at the end of the file is served without touching the stream`() {
        val source = FakeRangeSource(content)
        val tail = TailPrefetch(
            name = "tail.mkv",
            source = source,
            size = size,
            parentCoroutineContext = Dispatchers.IO,
            tailBytes = 2048,
            blockSize = 1024,
        )
        val buffer = ByteArray(256)
        assertTrue(awaitTail(tail, size - 256, buffer), "the prefetch must fill within the timeout")
        assertContentEquals(content.copyOfRange((size - 256).toInt(), size.toInt()), buffer)

        // The whole point: the stream never sees the read, so its position and read-ahead stand.
        val input = HybridSeekableInput(
            name = "tail.mkv",
            dataPath = SystemPaths.createTempDirectory("pikpak-tail").resolve("absent.mkv"),
            pieces = PieceList.create(size, 1024),
            size = size,
            openStream = { error("a read inside the prefetched tail must not open a stream") },
            onDelivered = {},
            onCloudReadStarted = {},
            onCloudReadFinished = {},
            tail = tail,
        )
        input.seekTo(size - 256)
        assertEquals(256, input.read(ByteArray(256), 0, 256))
        input.close()
    }

    @Test
    fun `a read below the tail is left to the stream`() {
        val tail = TailPrefetch(
            name = "tail.mkv",
            source = FakeRangeSource(content),
            size = size,
            parentCoroutineContext = Dispatchers.IO,
            tailBytes = 2048,
            blockSize = 1024,
        )
        assertEquals(0, tail.read(0, ByteArray(256), 0, 256))
        tail.close()
    }

    private fun awaitTail(tail: TailPrefetch, from: Long, buffer: ByteArray): Boolean {
        repeat(100) {
            if (tail.read(from, buffer, 0, buffer.size) == buffer.size) return true
            Thread.sleep(20)
        }
        return false
    }
}
