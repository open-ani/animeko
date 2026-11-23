/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
class InflateSubtitleTest {
    @Test
    fun preservesUtf8AndAssFieldsWithinSampleSlice() {
        val dialogue = "0,0,Default,,0,0,0,,你好，世界！\\N繁體字幕".encodeToByteArray()
        val compressed = compress(dialogue)
        assertTrue(0.toByte() in compressed)
        val sample = byteArrayOf(12, 34) + compressed + byteArrayOf(56, 78)
        assertContentEquals(dialogue, inflateSubtitle(sample, 2, compressed.size))
    }

    @Test
    fun rejectsTruncatedAndCorruptStreams() {
        val compressed = compress("0,0,Default,,0,0,0,,字幕".encodeToByteArray())
        assertFailsWith<ParserException> { inflateSubtitle(compressed, 0, compressed.size - 1) }
        compressed[compressed.lastIndex] = (compressed.last().toInt() xor 1).toByte()
        assertFailsWith<ParserException> { inflateSubtitle(compressed, 0, compressed.size) }
    }

    @Test
    fun boundsDecompressedSize() {
        val compressed = compress(ByteArray(16 * 1024 * 1024 + 1) { 'A'.code.toByte() })
        assertFailsWith<ParserException> { inflateSubtitle(compressed, 0, compressed.size) }
    }

    @Test
    fun acceptsEmptyZlibStream() {
        val compressed = compress(byteArrayOf())
        assertContentEquals(byteArrayOf(), inflateSubtitle(compressed, 0, compressed.size))
    }

    @Test
    fun preservesPayloadAcrossOutputBufferBoundaries() {
        for (size in listOf(4095, 4096, 4097, 16385)) {
            val payload = ByteArray(size) { (it % 251).toByte() }
            val compressed = compress(payload)
            assertContentEquals(payload, inflateSubtitle(compressed, 0, compressed.size), "Size $size")
        }
    }

    @Test
    fun acceptsExactlyTheDecompressedSizeLimit() {
        val payload = ByteArray(16 * 1024 * 1024) { 'A'.code.toByte() }
        val compressed = compress(payload)
        assertContentEquals(payload, inflateSubtitle(compressed, 0, compressed.size))
    }

    @Test
    fun rejectsEveryTruncatedPrefixIncludingMissingChecksum() {
        val compressed = compress("字幕不能只解压一部分".encodeToByteArray())
        for (length in 0 until compressed.size) {
            val error = assertFailsWith<ParserException>("Length $length") {
                inflateSubtitle(compressed, 0, length)
            }
            assertTrue(error.contentIsMalformed)
        }
    }

    @Test
    fun rejectsStreamsRequiringAPresetDictionary() {
        val dictionary = "shared subtitle dictionary".encodeToByteArray()
        val deflater = Deflater()
        val compressed = try {
            deflater.setDictionary(dictionary)
            ByteArrayOutputStream().also { output ->
                DeflaterOutputStream(output, deflater).use { it.write(dictionary) }
            }.toByteArray()
        } finally {
            deflater.end()
        }
        val error = assertFailsWith<ParserException> {
            inflateSubtitle(compressed, 0, compressed.size)
        }
        assertTrue(error.contentIsMalformed)
    }

    private fun compress(data: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        DeflaterOutputStream(output).use { it.write(data) }
    }.toByteArray()
}
