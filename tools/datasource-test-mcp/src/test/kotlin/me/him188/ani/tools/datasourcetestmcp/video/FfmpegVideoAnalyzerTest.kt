/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FfmpegVideoAnalyzerTest {
    private val analyzer = FfmpegVideoAnalyzer(Json { ignoreUnknownKeys = true })

    @Test
    fun `parses typical ffprobe output`() {
        val output = analyzer.parseFfprobeOutput(
            """
            {
              "streams": [
                {
                  "codec_type": "video",
                  "codec_name": "h264",
                  "width": 1920,
                  "height": 1080,
                  "avg_frame_rate": "24000/1001",
                  "bit_rate": "2500000"
                },
                {
                  "codec_type": "audio",
                  "codec_name": "aac",
                  "sample_rate": "44100",
                  "channels": 2,
                  "bit_rate": "128000"
                }
              ],
              "format": {
                "format_name": "hls",
                "duration": "1421.560000",
                "bit_rate": "2628000"
              }
            }
            """.trimIndent(),
        )

        assertEquals("hls", output.containerFormat)
        assertEquals(1421.56, output.durationSeconds)
        assertEquals(2628000L, output.overallBitrate)
        assertEquals("h264", output.video?.codec)
        assertEquals(1920, output.video?.width)
        assertEquals(1080, output.video?.height)
        assertEquals("24000/1001", output.video?.frameRate)
        assertEquals("aac", output.audio?.codec)
        assertEquals(2, output.audio?.channels)
    }

    @Test
    fun `tolerates missing streams and fields`() {
        val output = analyzer.parseFfprobeOutput("""{"format": {"format_name": "mov,mp4"}}""")
        assertEquals("mov,mp4", output.containerFormat)
        assertNull(output.durationSeconds)
        assertNull(output.video)
        assertNull(output.audio)
    }

    @Test
    fun `zero frame rate is dropped`() {
        val output = analyzer.parseFfprobeOutput(
            """
            {
              "streams": [
                {"codec_type": "video", "codec_name": "h264", "avg_frame_rate": "0/0"}
              ],
              "format": {}
            }
            """.trimIndent(),
        )
        assertNull(output.video?.frameRate)
    }
}
