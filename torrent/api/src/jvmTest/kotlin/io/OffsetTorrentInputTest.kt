/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.io

import me.him188.ani.app.torrent.api.pieces.MutablePieceList
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.api.pieces.asSequence
import me.him188.ani.app.torrent.api.pieces.first
import me.him188.ani.app.torrent.api.pieces.last
import me.him188.ani.app.torrent.readAllBytes
import me.him188.ani.app.torrent.readBytes
import me.him188.ani.app.torrent.readExactBytes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Piece 前后有垃圾
 * @see TorrentInputTest
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "OPT_IN_USAGE_ERROR")
internal class OffsetTorrentInputTest {
    @TempDir
    lateinit var tempDir: File

    // 第一个 piece 前 8 bytes 和最后一个 piece 后 8 bytes 是垃圾
    private val logicalPieces =
        PieceList.create(
            sampleTextByteArray.size.toLong() + 16, // 576 + 16
            16, initialDataOffset = 1000,
        )

    private val tempFile by lazy {
        tempDir.resolve("test.txt").apply {
            parentFile.mkdirs()
            writeText(sampleText)
        }
    }

    private val bufferSize = 20

    private val input: TorrentInput by lazy {
        TorrentInput(
            RandomAccessFile(tempFile, "r"), // 文件长度只有 576, 最后一个 piece 的最后 8 bytes 会是垃圾
            logicalPieces,
            logicalStartOffset = 1008, // 跳过 8 垃圾
            bufferSize = bufferSize,
        )
    }

    @AfterEach
    fun afterTest() {
        input.close()
    }

    private fun runTest(block: suspend MutablePieceList.() -> Unit) {
        kotlinx.coroutines.test.runTest {
            block(logicalPieces)
        }
    }

    @Test
    fun findPiece() {
        assertEquals(0, input.findPieceIndex(0))
        assertEquals(0, input.findPieceIndex(2))
        assertEquals(0, input.findPieceIndex(7))
        assertEquals(1, input.findPieceIndex(8)) // 8+8=16
        assertEquals(1, input.findPieceIndex(16)) // 16+8 = 24
        assertEquals(1, input.findPieceIndex(20)) // 20+8 = 28 > 16
        assertEquals(2, input.findPieceIndex(24)) // 24+8 = 32
        assertEquals(sampleTextByteArray.size / 16 + 1, input.findPieceIndex(sampleTextByteArray.lastIndex.toLong()))
    }

    @Test
    fun readFirstPieceNoSuspend() = runTest {
        logicalPieces.first().state = (PieceState.FINISHED)
        input.readBytes().run {
            assertEquals(8, size)
            assertEquals("Lorem Ip", String(this))
        }
        assertEquals(8L, input.position)
    }

    @Test
    fun seekFirstNoSuspend() = runTest {
        logicalPieces.first().state = (PieceState.FINISHED)
        input.seek(1)
        assertEquals(1L, input.position)
    }

    @Test
    fun seekReadSecondPiece() = runTest {
        logicalPieces.getByPieceIndex(1).state = (PieceState.FINISHED) // logically 16..<32 is finished
        input.seek(16)
        assertEquals(16L, input.position) // logically 24
        input.readBytes().run {
            assertEquals(8, size)
            assertEquals(8L..<24L, input.bufferedOffsetRange) // logically 16..<32
            assertEquals("imply du", String(this))
        }
    }

    @Test
    fun seekReadSecondPieceMiddle() = runTest {
        logicalPieces.getByPieceIndex(1).state = (PieceState.FINISHED)
        input.seek(17)
        assertEquals(17L, input.position)
        input.readBytes().run {
            assertEquals(8L..<24L, input.bufferedOffsetRange)
            assertEquals("mply du", String(this))
        }
    }

    @Test
    fun `seek buffer both direction`() = runTest {
        logicalPieces.getByPieceIndex(0).state = (PieceState.FINISHED)
        logicalPieces.getByPieceIndex(1).state = (PieceState.FINISHED)
        input.seek(17) // 17 18 19 20 21 22 23
        assertEquals(17L, input.position)
        input.readBytes().run {
            assertEquals(0L..<24L, input.bufferedOffsetRange)
            assertEquals("mply du", String(this))
        }
    }

    @Test
    fun `seek buffer both direction then seek back`() = runTest {
        logicalPieces.getByPieceIndex(0).state = (PieceState.FINISHED)
        logicalPieces.getByPieceIndex(1).state = (PieceState.FINISHED)
        input.seek(17)
        assertEquals(17L, input.position)
        input.readBytes().run {
            assertEquals(0L..<24L, input.bufferedOffsetRange)
            assertEquals("mply du", String(this))
        }
        input.seek(0)
        input.readBytes().run {
            assertEquals(0L..<24L, input.bufferedOffsetRange)
            assertEquals("Lorem Ipsum is simply du", String(this))
        }
    }

    @Test
    fun `buffer single finished pieces, initial zero`() = runTest {
        logicalPieces.getByPieceIndex(0).state = PieceState.FINISHED
        // other pieces not finished
        assertEquals(8, input.computeMaxBufferSizeForward(0, 100000))
        assertEquals(0, input.computeMaxBufferSizeBackward(0, 100000))
    }

    @Test
    fun `buffer single finished pieces, from intermediate`() = runTest {
        logicalPieces.getByPieceIndex(0).state = PieceState.FINISHED
        // other pieces not finished
        assertEquals(2, input.computeMaxBufferSizeForward(6, 100000))
        assertEquals(6, input.computeMaxBufferSizeBackward(6, 100000))
    }

    @Test
    fun `buffer single finished pieces, over buffer`() = runTest {
        logicalPieces.getByPieceIndex(0).state = PieceState.FINISHED
        // other pieces not finished
        assertEquals(0, input.computeMaxBufferSizeForward(8, 100000)) // 8+8=16, 超过了第一个 buffer
        assertEquals(0, input.computeMaxBufferSizeBackward(8, 100000))
    }

    @Test
    fun `buffer multiple finished pieces, from intermediate`() = runTest {
        logicalPieces.getByPieceIndex(0).state = PieceState.FINISHED
        logicalPieces.getByPieceIndex(1).state = PieceState.FINISHED
        // other pieces not finished
        assertEquals(8 + 16, input.computeMaxBufferSizeForward(0, 100000))
        assertEquals(0, input.computeMaxBufferSizeBackward(0, 100000))
        assertEquals(8 + 16 - 10, input.computeMaxBufferSizeForward(10, 100000))
        assertEquals(10, input.computeMaxBufferSizeBackward(10, 100000))
    }


    @Test
    fun `compute forward ignore trailing garbage`() = runTest {
        logicalPieces.last().state = PieceState.FINISHED
        // other pieces not finished
        assertEquals(1, input.computeMaxBufferSizeForward(sampleText.lastIndex.toLong(), 100000))
    }

    @Test
    fun `buffer zero byte (corner case)`() = runTest {
        logicalPieces.getByPieceIndex(0).state = PieceState.FINISHED
        // other pieces not finished
        assertEquals(0, input.computeMaxBufferSizeForward(logicalPieces.getByPieceIndex(0).size, 100000))
    }

    @Test
    fun `computeMaxBufferSize starting from piece not finished`() = runTest {
        // all pieces not finished
        assertEquals(0, input.computeMaxBufferSizeForward(0, 100000))
    }

    @Test
    fun `compute backward when curr piece not ready`() = runTest {
        assertEquals(0, input.computeMaxBufferSizeBackward(100, 100000))
    }

    @Test
    fun `compute backward when just after first piece while first not ready`() = runTest {
        logicalPieces.getByPieceIndex(1).state = PieceState.FINISHED
        assertEquals(0, input.computeMaxBufferSizeBackward(7, 100000)) // piece 0
        assertEquals(0, input.computeMaxBufferSizeBackward(8, 100000)) // piece 1 first byte
        assertEquals(1, input.computeMaxBufferSizeBackward(9, 100000))
    }

    @Test
    fun `compute backward when backward piece not ready`() = runTest {
        logicalPieces.getByPieceIndex(1).state = PieceState.FINISHED // 16..<32
        assertEquals(26 - 16, input.computeMaxBufferSizeBackward(18, 100000)) // logically from 18+8=26
    }


    @Test
    fun `reuse zero byte`() = runTest {
        for (logicalPiece in logicalPieces.asSequence()) {
            logicalPiece.state = PieceState.FINISHED
        }

        input.seek(30)
        assertEquals(0, input.read(ByteArray(0)))
        assertEquals(-1L..-1, input.bufferedOffsetRange)
    }


    ///////////////////////////////////////////////////////////////////////////
    // Reuse buffer
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `double prepareBuffer`() = runTest {
        for (logicalPiece in logicalPieces.asSequence()) {
            logicalPiece.state = PieceState.FINISHED
        }

        // buffer size is 20

        input.seek(30)
        assertEquals(1, input.read(ByteArray(1))) // fill buffer
        assertEquals(30 - bufferSize..<30L + bufferSize, input.bufferedOffsetRange)
        // 10..<50

        input.seek(0) // 超出 buffer 范围
        input.prepareBuffer()
        input.prepareBuffer()
        assertEquals(0L..<bufferSize, input.bufferedOffsetRange)
        // 0..<20, last 10 was reused from previous buffer

        assertEquals("Lorem Ipsum is simpl", input.readExactBytes(20).decodeToString())
    }

    @Test
    fun `reuse buffer from previous start`() = runTest {
        for (logicalPiece in logicalPieces.asSequence()) {
            logicalPiece.state = PieceState.FINISHED
        }

        // buffer size is 20

        input.seek(30)
        assertEquals(1, input.read(ByteArray(1))) // fill buffer
        assertEquals(30 - bufferSize..<30L + bufferSize, input.bufferedOffsetRange)
        // 10..<50

        input.seek(0) // 超出 buffer 范围
        input.prepareBuffer()
        assertEquals(0L..<bufferSize, input.bufferedOffsetRange)
        // 0..<20, last 10 was reused from previous buffer

        assertEquals("Lorem Ipsum is simpl", input.readExactBytes(20).decodeToString())
    }

    @Test
    fun `new buffer includes entire previous as head`() = runTest {
        for (logicalPiece in logicalPieces.asSequence()) {
            logicalPiece.state = PieceState.FINISHED
        }

        // buffer size is 20

        input.seek(0)
        input.prepareBuffer()
        assertEquals(0L..<bufferSize, input.bufferedOffsetRange)

        input.seek(bufferSize.toLong()) // 超出 buffer 范围
        input.prepareBuffer()
        assertEquals(0L..<bufferSize * 2, input.bufferedOffsetRange)
        // 0..<20, last 10 was reused from previous buffer

        assertEquals(sampleText.drop(bufferSize).take(20), input.readExactBytes(20).decodeToString())
        assertEquals(sampleText.drop(bufferSize + 20), input.readAllBytes().decodeToString())
        assertEquals("", input.readAllBytes().decodeToString())
    }

    @Test
    fun `new buffer includes entire previous as tail`() = runTest {
        for (logicalPiece in logicalPieces.asSequence()) {
            logicalPiece.state = PieceState.FINISHED
        }

        // buffer size is 20

        input.seek(sampleText.lastIndex.toLong()) // 576
        input.prepareBuffer()
        assertEquals(sampleText.lastIndex.toLong() - bufferSize..<sampleText.length, input.bufferedOffsetRange)

        input.seek(sampleText.lastIndex.toLong() - bufferSize - 1) // 超出 buffer 范围
        assertEquals(
            sampleText.substring(sampleText.lastIndex - bufferSize - 1),
            input.readAllBytes().decodeToString(),
        )
    }

    @TestFactory
    fun `reuse buffer from previous end`() = (20L..60L step 4).map { index ->
        DynamicTest.dynamicTest("$index") {
            with(logicalPieces) {
                for (logicalPiece in logicalPieces.asSequence()) {
                    logicalPiece.state = PieceState.FINISHED
                }
            }

            // buffer size is 20

            input.seek(30)
            assertEquals(1, input.read(ByteArray(1))) // fill buffer
            assertEquals(30 - bufferSize..<30L + bufferSize, input.bufferedOffsetRange)
            // 10..<50

            input.seek(index)
            input.prepareBuffer()
            assertEquals(index - bufferSize..<index + bufferSize, input.bufferedOffsetRange)
            // 40..<80, first 10 was reused from previous buffer

            assertEquals(sampleText.substring(index.toInt()).take(10), input.readExactBytes(10).decodeToString())
            assertEquals(sampleText.substring(index.toInt() + 10), input.readAllBytes().decodeToString())

            input.seek(0)
            assertEquals(sampleText, input.readAllBytes().decodeToString())
        }
    }

    @Test
    fun `buffer when piece not ready, then ready and re-buffer`() = runTest {
        for (logicalPiece in logicalPieces.asSequence()) {
            logicalPiece.state = PieceState.FINISHED
        }
        logicalPieces.getByPieceIndex(2).state = PieceState.DOWNLOADING // 48..<64

        // buffer size is 20

        input.seek(23) // logically 23+8=31, piece index 1
        input.prepareBuffer()
        assertEquals(3..<24L, input.bufferedOffsetRange) // piece 2 (logically 32..<48) is not ready, so we cap at 24
        // logically buffered ..<32 which is end of piece 1

        logicalPieces.getByPieceIndex(2).state = PieceState.FINISHED // 现在 2 号 piece 好了

        input.seek(32) // logically 32+8=40, piece index 2
        input.prepareBuffer()
        assertEquals(32L - bufferSize..<32L + bufferSize, input.bufferedOffsetRange)

        assertEquals(sampleText.substring(32), input.readAllBytes().decodeToString())
    }

    @Test
    fun `seek no reuse`() = runTest {
        for (logicalPiece in logicalPieces.asSequence()) {
            logicalPiece.state = PieceState.FINISHED
        }

        // buffer size is 20

        input.seek(30)
        assertEquals(1, input.read(ByteArray(1))) // fill buffer
        assertEquals(30 - bufferSize..<30L + bufferSize, input.bufferedOffsetRange)
        // 10..<50

        input.seek(100) // 远超出 buffer 范围
        input.prepareBuffer()
        assertEquals(100L - bufferSize..<100L + bufferSize, input.bufferedOffsetRange)
        // 40..<80, first 10 was reused from previous buffer

        assertEquals(sampleText.substring(100..<110), input.readExactBytes(10).decodeToString())
    }

    @Test
    fun `random seek and read`() = runTest {
        for (logicalPiece in logicalPieces.asSequence()) {
            logicalPiece.state = PieceState.FINISHED
        }

        val random = Random(2352151)
        repeat(1000) {
            val pos = random.nextLong(0L..<sampleText.length).absoluteValue
            input.seek(pos)
//                val length = Random.nextInt()
            assertEquals(sampleText.substring(pos.toInt()), input.readAllBytes().decodeToString())
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Error cases
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `seek negative offset`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            input.seek(-1)
        }
    }

    @Test
    fun `read negative length`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            input.read(ByteArray(1), 1, -1)
        }
    }

    @Test
    fun `read negative offset`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            input.read(ByteArray(1), -1, 1)
        }
    }

    @Test
    fun `seek over size then read`() = runTest {
        input.seek(Long.MAX_VALUE)
        assertEquals(-1, input.read(ByteArray(10)))
    }

    @Test
    fun `read closed`() = runTest {
        input.close()
        assertFailsWith<IllegalStateException> {
            input.read(ByteArray(2))
        }
    }

    @Test
    fun `seek closed`() = runTest {
        input.close()
        assertFailsWith<IllegalStateException> {
            input.seek(10)
        }
    }

}