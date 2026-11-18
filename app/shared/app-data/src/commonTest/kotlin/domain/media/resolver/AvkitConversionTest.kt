/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvkitConversionTest {
    @Test
    fun `HEVC and AAC retain their encoded streams with the hvc1 sample entry`() {
        val args = avkitConversionArguments("source", "output", MediaContainerTrackInfo("V_MPEGH/ISO/HEVC", emptyList(), listOf("A_AAC")))
        assertEquals("copy", args.valueOf("-c:v"))
        assertEquals("copy", args.valueOf("-c:a"))
        assertEquals("hvc1", args.valueOf("-tag:v"))
    }

    @Test
    fun `unsupported audio is converted independently from compatible video`() {
        for (codec in listOf("A_OPUS", "A_DTS", "A_TRUEHD", "A_VORBIS", "A_FLAC", "A_PCM/INT/LIT")) {
            val args = avkitConversionArguments("source", "output", MediaContainerTrackInfo("V_MPEG4/ISO/AVC", emptyList(), listOf(codec)))
            assertEquals("copy", args.valueOf("-c:v"))
            assertEquals("aac", args.valueOf("-c:a"))
            assertEquals("2", args.valueOf("-ac"))
        }
    }

    @Test
    fun `unsupported video and failed native decoding use compatible software codecs`() {
        for (codec in listOf("V_VP9", "V_AV1", "V_MPEG2", "V_MS/VFW/FOURCC", "V_REAL/RV40")) {
            val args = avkitConversionArguments("source", "output", MediaContainerTrackInfo(codec, emptyList(), listOf("A_AAC")))
            assertEquals("mpeg4", args.valueOf("-c:v"))
            assertEquals("yuv420p", args.valueOf("-pix_fmt"))
            assertFalse("-tag:v" in args)
        }
        val forced = avkitConversionArguments("source", "output", MediaContainerTrackInfo("V_MPEGH/ISO/HEVC", emptyList()), true)
        assertEquals("mpeg4", forced.valueOf("-c:v"))
        assertEquals("aac", forced.valueOf("-c:a"))
    }

    @Test
    fun `MP4 playback compatibility includes video tags and audio codecs`() {
        assertEquals(null, mp4CompatibilityTracks(listOf("hvc1", "mp4a")))
        assertEquals("V_MPEGH/ISO/HEVC", mp4CompatibilityTracks(listOf("hev1", "mp4a"))?.videoCodecId)
        assertEquals(listOf("Opus"), mp4CompatibilityTracks(listOf("hvc1", "Opus"))?.audioCodecIds)
        assertTrue(mp4CompatibilityTracks(listOf("vp09", "mp4a")) != null)
    }

    private fun List<String>.valueOf(option: String): String = get(indexOf(option) + 1)
}
