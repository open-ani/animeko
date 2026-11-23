/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.videoplayer.media

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

/** Small text-only Matroska files with independently configurable tracks and BlockGroups. */
internal object MatroskaSubtitleFixture {
    data class File(val bytes: ByteArray, val clusterOffset: Int)

    fun file(tracks: List<ByteArray>, blocks: List<ByteArray>): File {
        val header = element(
            0x1A45DFA3,
            uint(0x4286, 1) + uint(0x42F7, 1) + uint(0x42F2, 4) + uint(0x42F3, 8) +
                element(0x4282, "matroska".encodeToByteArray()) + uint(0x4287, 4) + uint(0x4285, 2),
        )
        val info = element(0x1549A966, uint(0x2AD7B1, 1_000_000))
        val trackElements = element(0x1654AE6B, tracks.fold(byteArrayOf(), ByteArray::plus))
        val cluster = element(0x1F43B675, uint(0xE7, 0) + blocks.fold(byteArrayOf(), ByteArray::plus))
        val bytes = header + element(0x18538067, info + trackElements + cluster)
        return File(bytes, bytes.size - cluster.size)
    }

    fun track(
        number: Int,
        compression: ByteArray? = null,
        codec: String = "S_TEXT/UTF8",
        type: Int = 17,
    ): ByteArray = element(
        0xAE,
        uint(0xD7, number) + uint(0x73C5, number) + uint(0x83, type) +
            element(0x86, codec.encodeToByteArray()) +
            (compression?.let { element(0x6D80, element(0x6240, element(0x5034, it))) } ?: byteArrayOf()),
    )

    // A present but empty ContentCompression selects the Matroska default algorithm, zlib.
    fun compression(algorithm: Int? = null, strippedHeader: String = ""): ByteArray =
        (algorithm?.let { uint(0x4254, it) } ?: byteArrayOf()) +
            (if (strippedHeader.isEmpty()) byteArrayOf() else element(0x4255, strippedHeader.encodeToByteArray()))

    fun block(number: Int, timeMillis: Int, payload: ByteArray, flags: Int = 0): ByteArray = element(
        0xA0,
        element(0xA1, vint(number) + byteArrayOf((timeMillis shr 8).toByte(), timeMillis.toByte(), flags.toByte()) + payload) +
            uint(0x9B, 3000),
    )

    fun zlib(text: String): ByteArray = ByteArrayOutputStream().also { output ->
        DeflaterOutputStream(output).use { it.write(text.encodeToByteArray()) }
    }.toByteArray()

    private fun element(id: Int, data: ByteArray): ByteArray = unsigned(id) + vint(data.size) + data

    private fun uint(id: Int, value: Int): ByteArray = element(id, unsigned(value))

    private fun unsigned(value: Int): ByteArray {
        val size = (1..4).first { value.toLong() < (1L shl (8 * it)) }
        return ByteArray(size) { (value ushr (8 * (size - it - 1))).toByte() }
    }

    private fun vint(value: Int): ByteArray {
        val size = (1..4).first { value.toLong() < (1L shl (7 * it)) - 1 }
        val encoded = value.toLong() or (1L shl (7 * size))
        return ByteArray(size) { (encoded shr (8 * (size - it - 1))).toByte() }
    }
}
