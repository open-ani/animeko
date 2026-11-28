/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvkitFmp4SegmenterTest {
    @Test
    fun `fragment duration uses the video timescale and initialization is separate`() {
        var initialization = byteArrayOf()
        val segments = mutableListOf<AvkitStreamSegment>()
        val segmenter = AvkitFmp4Segmenter({ initialization = it }, segments::add)
        val ftyp = box("ftyp", "iso6".encodeToByteArray())
        val moov = movie()
        val moof = box("moof", box("traf", box("tfhd", ints(0x020018, 1, 1000, 1)) + box("trun", ints(1, 2, 68))))
        val mdat = box("mdat", byteArrayOf(1, 2, 3))
        segmenter.box(ftyp)
        segmenter.box(moov)
        segmenter.box(moof)
        assertTrue(segments.isEmpty())
        segmenter.box(mdat)
        segmenter.finish()
        assertContentEquals(ftyp + moov, initialization)
        assertContentEquals(moof + mdat, segments.single().bytes)
        assertEquals(2.0, segments.single().durationSeconds)
        val playlist = AvkitTsSegmenter.playlist(listOf(2.0), false, fragmentedMp4 = true)
        assertTrue("#EXT-X-VERSION:7" in playlist)
        assertTrue("#EXT-X-MAP:URI=\"init.mp4\"" in playlist)
        assertTrue("segment-0.m4s" in playlist)
    }

    @Test
    fun `incomplete media fragments are rejected`() {
        val segmenter = AvkitFmp4Segmenter({}, {})
        segmenter.box(movie())
        segmenter.box(box("moof", box("traf", box("tfhd", ints(0x020018, 1, 1000, 1)) + box("trun", ints(1, 2, 68)))))
        assertFailsWith<IllegalArgumentException> { segmenter.finish() }
    }

    @Test
    fun `box reader accepts extended sizes and rejects a truncated tail`() = runTest {
        val extended = ints(1) + "mdat".encodeToByteArray() + ints(0, 19) + byteArrayOf(1, 2, 3)
        val input = Buffer().apply { write(extended) }
        assertContentEquals(extended, readGrowingMp4Box(input) { true })
        assertNull(readGrowingMp4Box(input) { true })
        val truncated = Buffer().apply { write(box("mdat", byteArrayOf(1, 2, 3)).dropLast(1).toByteArray()) }
        assertFailsWith<IllegalStateException> { readGrowingMp4Box(truncated) { true } }
    }

    private fun movie(): ByteArray = box("moov",
        box("trak", box("tkhd", ints(0, 0, 0, 1)) +
                box("mdia", box("mdhd", ints(0, 0, 0, 1000)) + box("hdlr", ints(0, 0) + "vide".encodeToByteArray()))) +
                box("mvex", box("trex", ints(0, 1, 1, 1000, 0, 0))))

    private fun ints(vararg values: Int): ByteArray = Buffer().apply { values.forEach(::writeInt) }.readByteArray()

    private fun box(type: String, payload: ByteArray): ByteArray = Buffer().apply {
        writeInt(payload.size + 8)
        writeString(type)
        write(payload)
    }.readByteArray()
}
