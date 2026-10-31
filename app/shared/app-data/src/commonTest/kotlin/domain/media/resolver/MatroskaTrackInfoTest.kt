/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class MatroskaTrackInfoTest {
    @Test
    fun `parses HEVC and skips bitmap tracks while retaining FFmpeg subtitle stream indexes`() {
        val video = element(0xae, number(0x83, 1) + string(0x86, "V_MPEGH/ISO/HEVC"))
        val bitmap = element(0xae, number(0x83, 17) + string(0x86, "S_HDMV/PGS"))
        val ass = element(
            0xae,
            number(0x83, 17) + string(0x86, "S_TEXT/ASS") + string(0x22b59d, "chi") + string(0x536e, "简体"),
        )
        val tracks = readMatroskaTrackInfo(Buffer().apply { write(fixture(video + bitmap + ass)) })

        assertEquals("V_MPEGH/ISO/HEVC", tracks.videoCodecId)
        assertEquals(listOf(MatroskaSubtitleTrack(1, "chi", "简体")), tracks.subtitles)
        assertEquals("hvc1", mp4VideoTagFor(tracks.videoCodecId))
        assertEquals(null, mp4VideoTagFor("V_MPEG4/ISO/AVC"))
    }

    private fun fixture(tracks: ByteArray): ByteArray =
        element(0x1a45dfa3, string(0x4282, "matroska")) +
                id(0x18538067) + byteArrayOf(1, -1, -1, -1, -1, -1, -1, -1) +
                element(0x1654ae6b, tracks)

    private fun number(tag: Long, value: Int): ByteArray = element(tag, byteArrayOf(value.toByte()))

    private fun string(tag: Long, value: String): ByteArray = element(tag, value.encodeToByteArray())

    private fun element(tag: Long, value: ByteArray): ByteArray = id(tag) + vint(value.size) + value

    private fun id(value: Long): ByteArray {
        val size = (value.toString(16).length + 1) / 2
        return ByteArray(size) { index -> (value shr ((size - index - 1) * 8)).toByte() }
    }

    private fun vint(value: Int): ByteArray {
        val length = (1..8).first { value < (1 shl (7 * it)) - 1 }
        val encoded = value or (1 shl (7 * length))
        return ByteArray(length) { index ->
            val shift = (length - index - 1) * 8
            ((encoded shr shift) and 0xff).toByte()
        }
    }
}
