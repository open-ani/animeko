/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoProbeTest {
    @Test
    fun probe_m3u8_master_playlist_checks_first_segment() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                when (request.url.toString()) {
                    "https://cdn.example.com/master.m3u8" -> respond(
                        """
                        #EXTM3U
                        #EXT-X-STREAM-INF:BANDWIDTH=1000
                        media.m3u8
                        """.trimIndent(),
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                    )

                    "https://cdn.example.com/media.m3u8" -> respond(
                        """
                        #EXTM3U
                        #EXTINF:10,
                        segment0001.ts
                        """.trimIndent(),
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                    )

                    "https://cdn.example.com/segment0001.ts" -> respond(
                        content = "segment-bytes",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "video/mp2t"),
                    )

                    else -> error("Unexpected URL: ${request.url}")
                }
            },
        )

        val result = VideoProbe(client).probe("https://cdn.example.com/master.m3u8", emptyMap())

        assertTrue(result.ok)
        assertEquals("m3u8-master", result.kind)
        assertEquals("https://cdn.example.com/media.m3u8", result.nestedPlaylistUrl)
        assertEquals("https://cdn.example.com/segment0001.ts", result.sampledSegmentUrl)
    }
}
