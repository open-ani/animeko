/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatroskaSubtitleExportTest {
    @Test
    fun `subtitle export uses the bundled Matroska muxer and preserves timestamps`() {
        assertEquals(
            listOf(
                "-nostdin", "-y", "-v", "error", "-copyts", "-i", "input.mkv",
                "-map", "0:s:2", "-c:s", "srt", "-f", "matroska", "-avoid_negative_ts", "disabled", "subtitle.mks",
            ),
            matroskaSubtitleExportArguments("input.mkv", listOf(2 to "subtitle.mks")),
        )
    }

    @Test
    fun `normalized subtitles preserve negative relative timestamps multiline text and overlaps`() {
        val input = Buffer().apply {
            write(element(0x18538067, byteArrayOf()))
            write(element(0x1549a966, element(0x2ad7b1, byteArrayOf(0x1e, 0x84.toByte(), 0x80.toByte())))) // 2 ms
            write(element(0x1f43b675, element(0xe7, byteArrayOf(0x13, 0x88.toByte())) +
                    cue(-1000, 2000, "你好\nWorld") + cue(0, 500, "Overlap")))
        }
        val output = Buffer()
        exportMatroskaSubtitleAsSrt(input, output)
        assertEquals(
            "1\n00:00:08,000 --> 00:00:12,000\n你好\nWorld\n\n" +
                    "2\n00:00:10,000 --> 00:00:11,000\nOverlap\n\n",
            output.readString(),
        )
    }

    @Test
    fun `truncated subtitle elements fail before reading outside their container`() {
        val input = Buffer().apply { write(element(0x1f43b675, byteArrayOf(0xe7.toByte(), 0x82.toByte(), 1))) }
        assertFailsWith<IllegalArgumentException> { exportMatroskaSubtitleAsSrt(input, Buffer()) }
    }

    private fun cue(timestamp: Int, duration: Int, text: String): ByteArray = element(
        0xa0,
        element(0xa1, byteArrayOf(0x81.toByte(), (timestamp shr 8).toByte(), timestamp.toByte(), 0) + text.encodeToByteArray()) +
                element(0x9b, byteArrayOf((duration shr 8).toByte(), duration.toByte())),
    )

    private fun element(id: Long, content: ByteArray): ByteArray {
        val bytes = (id.toString(16).length + 1) / 2
        val header = ByteArray(bytes) { (id shr ((bytes - it - 1) * 8)).toByte() }
        return header + byteArrayOf((0x80 or content.size).toByte()) + content
    }
}
