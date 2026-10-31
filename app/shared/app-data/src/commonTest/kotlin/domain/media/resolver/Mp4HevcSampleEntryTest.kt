/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Mp4HevcSampleEntryTest {
    @Test
    fun `hev1 is detected inside the video sample description without reading mdat`() = runTest {
        val ftyp = box("ftyp", "isom".encodeToByteArray())
        val mdat = integer(1_000_000) + "mdat".encodeToByteArray()
        val moov = movie("hev1")
        val ranges = mutableListOf<Long>()
        assertTrue(mp4NeedsHevcRemux { offset, size ->
            ranges += offset
            when (offset) {
                0L -> (ftyp + mdat).copyOfRange(0, size)
                ftyp.size.toLong() -> mdat
                ftyp.size + 1_000_000L -> moov.copyOfRange(0, minOf(size, moov.size))
                ftyp.size + 1_000_008L -> moov.copyOfRange(8, moov.size)
                else -> error("Media payload was requested")
            }
        })
        assertEquals(listOf(0L, 12L, 1_000_012L, 1_000_020L), ranges)
    }

    @Test
    fun `hvc1 and avc1 keep direct playback and arbitrary hev1 text is ignored`() = runTest {
        for (codec in listOf("hvc1", "avc1")) {
            val bytes = movie(codec) + box("free", "hev1".encodeToByteArray())
            assertFalse(mp4NeedsHevcRemux { offset, size ->
                bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + size))
            })
        }
    }

    @Test
    fun `invalid and oversized movie boxes do not allocate their advertised sizes`() = runTest {
        for (header in listOf(integer(0), integer(5), integer(5_000_000))) {
            var calls = 0
            assertFalse(mp4NeedsHevcRemux { _, _ -> calls++; header + "moov".encodeToByteArray() })
            assertEquals(1, calls)
        }
    }

    @Test
    fun `deeply nested metadata is bounded`() = runTest {
        var content = box("stsd", ByteArray(4) + integer(1) + box("hev1", ByteArray(78)))
        repeat(100) { content = box("trak", content) }
        val bytes = box("moov", content)
        assertFalse(mp4NeedsHevcRemux { offset, size ->
            bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + size))
        })
    }

    private fun movie(codec: String): ByteArray {
        var content = box("stsd", ByteArray(4) + integer(1) + box(codec, ByteArray(78)))
        for (type in listOf("stbl", "minf", "mdia", "trak", "moov")) content = box(type, content)
        return content
    }

    private fun integer(value: Int) = ByteArray(4) { (value shr ((3 - it) * 8)).toByte() }
    private fun box(type: String, content: ByteArray) = integer(content.size + 8) + type.encodeToByteArray() + content
}
