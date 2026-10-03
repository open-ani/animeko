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
import io.ktor.http.headersOf
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class M3u8AdAnalyzerTest {

    @Test
    fun spliced_ad_group_is_filtered_with_correct_offsets() = runTest {
        // 正片 30 段 x 6s (180s) + 广告 3 段 x 5s (15s) + 正片 30 段 x 6s (180s)
        val playlist = mediaPlaylist(
            group(30, duration = 6.0, uriPrefix = "main/a"),
            group(3, duration = 5.0, uriPrefix = "extra/e", startNumber = 900),
            group(30, duration = 6.0, uriPrefix = "main/b"),
        )
        val analyzer = analyzerServing("https://cdn.example.com/media.m3u8" to playlist)

        val result = analyzer.analyze("https://cdn.example.com/media.m3u8", emptyMap())

        val hlsFilter = assertNotNull(result.hlsFilter)
        assertEquals("filtered", hlsFilter.status)
        assertTrue(hlsFilter.filterable)
        assertEquals("https://cdn.example.com/media.m3u8", hlsFilter.mediaPlaylistUrl)

        assertEquals(1, hlsFilter.removedGroups.size)
        val removed = hlsFilter.removedGroups.single()
        assertEquals(3, removed.segmentCount)
        assertEquals(30, removed.startSegmentIndex)
        assertEquals(32, removed.endSegmentIndex)
        assertEquals(15.0, removed.durationSeconds)
        assertEquals(180.0, removed.startOffsetSeconds)
        assertEquals(195.0, removed.endOffsetSeconds)

        assertTrue(
            result.suspicion in setOf("suspected_medium", "suspected_high"),
            "expected suspicion >= medium, got ${result.suspicion}",
        )
        assertTrue(result.reasons.any { "Ani HLS 广告过滤器" in it })
    }

    @Test
    fun clean_playlist_without_discontinuity_is_unchanged() = runTest {
        val playlist = mediaPlaylist(
            group(30, duration = 6.0, uriPrefix = "main/a"),
            discontinuity = false,
        )
        val analyzer = analyzerServing("https://cdn.example.com/media.m3u8" to playlist)

        val result = analyzer.analyze("https://cdn.example.com/media.m3u8", emptyMap())

        val hlsFilter = assertNotNull(result.hlsFilter)
        assertEquals("unchanged", hlsFilter.status)
        assertEquals("no_discontinuity", hlsFilter.reason)
        assertFalse(hlsFilter.filterable)
        assertTrue(hlsFilter.removedGroups.isEmpty())
        assertEquals("none", result.suspicion)
    }

    @Test
    fun master_playlist_is_followed_to_media_playlist() = runTest {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1000000
            media.m3u8
        """.trimIndent()
        val media = mediaPlaylist(
            group(30, duration = 6.0, uriPrefix = "main/a"),
            group(3, duration = 5.0, uriPrefix = "extra/e", startNumber = 900),
            group(30, duration = 6.0, uriPrefix = "main/b"),
        )
        val analyzer = analyzerServing(
            "https://cdn.example.com/master.m3u8" to master,
            "https://cdn.example.com/media.m3u8" to media,
        )

        val result = analyzer.analyze("https://cdn.example.com/master.m3u8", emptyMap())

        val hlsFilter = assertNotNull(result.hlsFilter)
        assertEquals("https://cdn.example.com/media.m3u8", hlsFilter.mediaPlaylistUrl)
        assertEquals("filtered", hlsFilter.status)
        assertTrue(hlsFilter.filterable)
        assertEquals(1, hlsFilter.removedGroups.size)
    }

    @Test
    fun cancellation_is_propagated() = runTest {
        val analyzer = M3u8AdAnalyzer(HttpClient(MockEngine { awaitCancellation() }))

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1) {
                analyzer.analyze("https://cdn.example.com/media.m3u8", emptyMap())
            }
        }
    }

    /**
     * 除了给出的播放列表, 还按 [SEGMENT_FIRST_PTS_MILLIS] 提供各组首片的开头: 过滤器读它判断时间轴.
     */
    private fun analyzerServing(vararg pages: Pair<String, String>): M3u8AdAnalyzer {
        val byUrl = pages.toMap()
        val client = HttpClient(
            MockEngine { request ->
                val url = request.url.toString()
                byUrl[url]?.let { body ->
                    return@MockEngine respond(body, headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"))
                }
                val ptsMillis = SEGMENT_FIRST_PTS_MILLIS[url.substringAfterLast('/')]
                    ?: error("Unexpected URL: $url")
                respond(tsPacketWithVideoPts(ptsMillis * 90), headers = headersOf(HttpHeaders.ContentType, "video/mp2t"))
            },
        )
        return M3u8AdAnalyzer(client)
    }

    private companion object {
        /** 正片两段首尾相接 (1.4 秒起, 第二段接在 180 秒之后); 广告独立转码, 从 1.47 秒起. */
        val SEGMENT_FIRST_PTS_MILLIS = mapOf(
            "a0.ts" to 1_400L,
            "e900.ts" to 1_470L,
            "b0.ts" to 181_400L,
        )
    }
}

/** 一个载有视频 PES 头 (只带 PTS) 的 TS 包. */
private fun tsPacketWithVideoPts(pts: Long): ByteArray {
    val packet = ByteArray(188) { 0xFF.toByte() }
    packet[0] = 0x47
    packet[1] = 0x41 // payload_unit_start, PID 0x100
    packet[2] = 0x00
    packet[3] = 0x10 // 只有 payload
    val pes = byteArrayOf(0, 0, 1, 0xE0.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5)
    pes.copyInto(packet, 4)
    val at = 4 + pes.size
    packet[at] = ((0x2 shl 4) or (((pts ushr 30).toInt() and 0x07) shl 1) or 0x01).toByte()
    packet[at + 1] = ((pts ushr 22).toInt() and 0xFF).toByte()
    packet[at + 2] = ((((pts ushr 15).toInt() and 0x7F) shl 1) or 0x01).toByte()
    packet[at + 3] = ((pts ushr 7).toInt() and 0xFF).toByte()
    packet[at + 4] = (((pts.toInt() and 0x7F) shl 1) or 0x01).toByte()
    return packet
}

// region fixtures (style adapted from HlsManifestFilterTest)

private fun mediaPlaylist(
    vararg groups: List<TestSegment>,
    discontinuity: Boolean = true,
): String = buildString {
    appendLine("#EXTM3U")
    appendLine("#EXT-X-VERSION:3")
    appendLine("#EXT-X-TARGETDURATION:10")
    groups.forEachIndexed { groupIndex, group ->
        if (discontinuity && groupIndex > 0) {
            appendLine("#EXT-X-DISCONTINUITY")
        }
        group.forEach { segment ->
            appendLine("#EXTINF:${segment.duration},")
            appendLine(segment.uri)
        }
    }
    append("#EXT-X-ENDLIST")
}

private fun group(
    count: Int,
    duration: Double,
    uriPrefix: String,
    startNumber: Int = 0,
): List<TestSegment> = List(count) { index ->
    TestSegment(duration = duration, uri = "$uriPrefix${startNumber + index}.ts")
}

private data class TestSegment(
    val duration: Double,
    val uri: String,
)

// endregion
