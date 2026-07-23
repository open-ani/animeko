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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class M3u8AdAnalyzerTest {

    @Test
    fun sandwiched_short_ad_group_is_filtered_with_correct_offsets() = runTest {
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
        assertTrue("sandwiched_short" in removed.reasons)
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

    private fun analyzerServing(vararg pages: Pair<String, String>): M3u8AdAnalyzer {
        val byUrl = pages.toMap()
        val client = HttpClient(
            MockEngine { request ->
                val body = byUrl[request.url.toString()]
                    ?: error("Unexpected URL: ${request.url}")
                respond(
                    body,
                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl"),
                )
            },
        )
        return M3u8AdAnalyzer(client)
    }
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
