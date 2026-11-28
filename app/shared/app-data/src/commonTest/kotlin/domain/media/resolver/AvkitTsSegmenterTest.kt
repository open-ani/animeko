/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvkitTsSegmenterTest {
    @Test
    fun `keyframe segments are emitted before EOF and retain complete transport packets`() {
        val segments = mutableListOf<AvkitStreamSegment>()
        val segmenter = AvkitTsSegmenter(segments::add)
        val packets = listOf(pat(), video(0), pat(), video(180_000), pat(), video(360_000))
        packets.forEach(segmenter::packet)
        assertEquals(2, segments.size)
        assertEquals(listOf(2.0, 2.0), segments.map { it.durationSeconds })
        segmenter.finish()
        assertContentEquals(packets.reduce(ByteArray::plus), segments.map { it.bytes }.reduce(ByteArray::plus))
        assertTrue(segments.all { it.bytes.size % 188 == 0 && it.bytes[1] == 0x40.toByte() })
    }

    @Test
    fun `open playlists advertise buffered segments and completed ones include an end marker`() {
        val open = AvkitTsSegmenter.playlist(listOf(2.0, 2.0), false)
        assertTrue("#EXT-X-PLAYLIST-TYPE:EVENT" in open)
        assertTrue("segment-0.ts" in open)
        assertFalse("#EXT-X-ENDLIST" in open)
        assertTrue(AvkitTsSegmenter.playlist(listOf(2.0), true).endsWith("#EXT-X-ENDLIST\n"))
    }

    @Test
    fun `unsupported keyframe gaps fail instead of generating an invalid target duration`() {
        val segmenter = AvkitTsSegmenter {}
        segmenter.packet(pat()); segmenter.packet(video(0)); segmenter.packet(pat())
        assertFailsWith<IllegalArgumentException> { segmenter.packet(video(12 * 90_000)) }
    }

    private fun pat() = ByteArray(188) { 0xff.toByte() }.apply {
        this[0] = 0x47; this[1] = 0x40; this[2] = 0; this[3] = 0x10
    }

    private fun video(pts: Long) = ByteArray(188) { 0xff.toByte() }.apply {
        this[0] = 0x47; this[1] = 0x41; this[2] = 0; this[3] = 0x30; this[4] = 1; this[5] = 0x40
        val pes = byteArrayOf(0, 0, 1, 0xe0.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5)
        pes.copyInto(this, 6)
        this[15] = (0x21 or (((pts shr 30) and 7).toInt() shl 1)).toByte()
        this[16] = (pts shr 22).toByte()
        this[17] = ((((pts shr 15) and 127).toInt() shl 1) or 1).toByte()
        this[18] = (pts shr 7).toByte()
        this[19] = (((pts and 127).toInt() shl 1) or 1).toByte()
    }
}
