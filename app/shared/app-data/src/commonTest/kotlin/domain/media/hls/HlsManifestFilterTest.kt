/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HlsManifestFilterTest {
    @Test
    fun `filters repeated sandwiched short groups in low density playlist`() {
        val result = HlsManifestFilter.filter(
            mediaPlaylist(
                group(30, duration = 3.0, uriPrefix = "https://cdn.example/main/seg0"),
                group(3, duration = 6.0, uriPrefix = "https://cdn.example/ad/ad", startNumber = 900),
                group(30, duration = 3.0, uriPrefix = "https://cdn.example/main/seg1"),
                group(3, duration = 6.0, uriPrefix = "https://cdn.example/ad/ad", startNumber = 900),
                group(30, duration = 3.0, uriPrefix = "https://cdn.example/main/seg2"),
            ),
        )

        assertEquals(HlsManifestFilterStatus.Filtered, result.status)
        assertEquals(2, result.removedGroups.size)
        assertTrue(result.removedGroups.all { "strong_path" in it.reasons })
        assertTrue(result.removedGroups.all { "repeat_short" in it.reasons })
        assertTrue(result.removedGroups.all { "sandwiched_short" in it.reasons })
        assertTrue(result.removedGroups.all { "low_density_short" in it.reasons })
        assertFalse("/ad/" in result.content)
        assertEquals(90, result.content.mediaSegmentUriCount())
    }

    @Test
    fun `detects relative ad directory as strong path`() {
        val result = HlsManifestFilter.filter(
            mediaPlaylist(
                group(30, duration = 3.0, uriPrefix = "main/seg0"),
                group(3, duration = 6.0, uriPrefix = "ads/ad", startNumber = 900),
                group(30, duration = 3.0, uriPrefix = "main/seg1"),
            ),
        )

        assertEquals(HlsManifestFilterStatus.Filtered, result.status)
        assertEquals(1, result.removedGroups.size)
        assertTrue("strong_path" in result.removedGroups.single().reasons)
        assertFalse("ads/ad900.ts" in result.content)
    }

    @Test
    fun `does not treat discontinuity sequence as segment boundary`() {
        val content = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-DISCONTINUITY-SEQUENCE:7")
            appendLine("#EXT-X-TARGETDURATION:10")
            mediaPlaylistBody(
                group(2, duration = 6.0, uriPrefix = "ads/ad", startNumber = 900),
                group(30, duration = 3.0, uriPrefix = "main/seg0"),
            )
            append("#EXT-X-ENDLIST")
        }

        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Filtered, result.status)
        assertTrue("#EXT-X-DISCONTINUITY-SEQUENCE:7" in result.content)
        assertFalse("ads/ad900.ts" in result.content)
    }

    @Test
    fun `does not filter dense short groups without structural signals`() {
        val groups = List(21) { index ->
            group(4, duration = 10.0, uriPrefix = "https://cdn.example/main/g$index-", startNumber = index * 10)
        }

        val content = mediaPlaylist(*groups.toTypedArray())
        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Unchanged, result.status)
        assertEquals("no_candidate", result.reason)
        assertEquals(content, result.content)
    }

    @Test
    fun `detects dense tiny group`() {
        val groups = buildList {
            repeat(10) { add(group(5, duration = 15.0, uriPrefix = "https://cdn.example/main/a$it-")) }
            add(group(1, duration = 8.0, uriPrefix = "https://cdn.example/insert/tiny"))
            repeat(10) { add(group(5, duration = 15.0, uriPrefix = "https://cdn.example/main/b$it-")) }
        }

        val result = HlsManifestFilter.filter(mediaPlaylist(*groups.toTypedArray()))

        assertEquals(HlsManifestFilterStatus.Filtered, result.status)
        assertEquals(1, result.removedGroups.size)
        assertEquals(listOf("dense_tiny"), result.removedGroups.single().reasons)
        assertFalse("insert/tiny" in result.content)
    }

    @Test
    fun `detects sequence island in dense playlist`() {
        val groups = buildList {
            repeat(9) { index ->
                add(group(4, duration = 12.5, uriPrefix = "https://cdn.example/main/seg", startNumber = index * 4))
            }
            add(group(2, duration = 6.0, uriPrefix = "https://cdn.example/main/seg", startNumber = 9000))
            repeat(12) { index ->
                add(group(4, duration = 12.5, uriPrefix = "https://cdn.example/main/seg", startNumber = 36 + index * 4))
            }
        }

        val result = HlsManifestFilter.filter(mediaPlaylist(*groups.toTypedArray()))

        assertEquals(HlsManifestFilterStatus.Filtered, result.status)
        assertEquals(1, result.removedGroups.size)
        assertTrue("sequence_island" in result.removedGroups.single().reasons)
        assertFalse("seg9000.ts" in result.content)
        assertTrue("seg9.ts" in result.content)
    }

    @Test
    fun `keeps playlist without discontinuity unchanged`() {
        val content = mediaPlaylist(
            group(4, duration = 10.0, uriPrefix = "https://cdn.example/main/seg"),
            discontinuity = false,
        )

        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Unchanged, result.status)
        assertEquals("no_discontinuity", result.reason)
        assertEquals(content, result.content)
    }

    @Test
    fun `keeps encrypted single group playlist unchanged when there is no manifest boundary`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-KEY:METHOD=AES-128,URI="enc.key",IV=0x00000000000000000000000000000000
            #EXTINF:10,
            plist0.ts
            #EXTINF:10,
            plist1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Unchanged, result.status)
        assertEquals("no_discontinuity", result.reason)
        assertEquals(content, result.content)
    }

    @Test
    fun `keeps encrypted playlist with implicit iv unchanged when candidate exists`() {
        val content = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:10")
            appendLine("#EXT-X-MEDIA-SEQUENCE:10")
            appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"enc.key\"")
            mediaPlaylistBody(
                group(30, duration = 3.0, uriPrefix = "main/seg0"),
                group(3, duration = 6.0, uriPrefix = "ads/ad", startNumber = 900),
                group(30, duration = 3.0, uriPrefix = "main/seg1"),
            )
            append("#EXT-X-ENDLIST")
        }

        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Unchanged, result.status)
        assertEquals("encrypted_implicit_iv", result.reason)
        assertEquals(content, result.content)
    }

    @Test
    fun `filters encrypted playlist with explicit iv`() {
        val content = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:10")
            appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"enc.key\",IV=0x00000000000000000000000000000000")
            mediaPlaylistBody(
                group(30, duration = 3.0, uriPrefix = "main/seg0"),
                group(3, duration = 6.0, uriPrefix = "ads/ad", startNumber = 900),
                group(30, duration = 3.0, uriPrefix = "main/seg1"),
            )
            append("#EXT-X-ENDLIST")
        }

        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Filtered, result.status)
        assertFalse("ads/ad900.ts" in result.content)
    }

    @Test
    fun `keeps byte range playlist with implicit offset unchanged when candidate exists`() {
        val content = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:4")
            appendLine("#EXT-X-TARGETDURATION:10")
            appendLine("#EXT-X-BYTERANGE:3000")
            mediaPlaylistBody(
                group(30, duration = 3.0, uriPrefix = "main/seg0"),
                group(3, duration = 6.0, uriPrefix = "ads/ad", startNumber = 900),
                group(30, duration = 3.0, uriPrefix = "main/seg1"),
            )
            append("#EXT-X-ENDLIST")
        }

        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Unchanged, result.status)
        assertEquals("byterange_implicit_offset", result.reason)
        assertEquals(content, result.content)
    }

    @Test
    fun `does not rewrite master playlist`() {
        val content = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000
            high/index.m3u8
        """.trimIndent()

        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Unsupported, result.status)
        assertEquals("master_playlist", result.reason)
        assertEquals(content, result.content)
    }

    @Test
    fun `does not rewrite live playlist`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:10,
            seg0.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10,
            seg1.ts
        """.trimIndent()

        val result = HlsManifestFilter.filter(content)

        assertEquals(HlsManifestFilterStatus.Unsupported, result.status)
        assertEquals("live_or_incomplete_playlist", result.reason)
        assertEquals(content, result.content)
    }
}

private fun mediaPlaylist(
    vararg groups: List<TestSegment>,
    discontinuity: Boolean = true,
): String {
    return buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:15")
        mediaPlaylistBody(*groups, discontinuity = discontinuity)
        append("#EXT-X-ENDLIST")
    }
}

private fun StringBuilder.mediaPlaylistBody(
    vararg groups: List<TestSegment>,
    discontinuity: Boolean = true,
) {
    groups.forEachIndexed { groupIndex, group ->
        if (discontinuity && groupIndex > 0) {
            appendLine("#EXT-X-DISCONTINUITY")
        }
        group.forEach { segment ->
            appendLine("#EXTINF:${segment.duration},")
            appendLine(segment.uri)
        }
    }
}

private fun group(
    count: Int,
    duration: Double,
    uriPrefix: String,
    startNumber: Int = 0,
): List<TestSegment> {
    return List(count) { index ->
        TestSegment(
            duration = duration,
            uri = "$uriPrefix${startNumber + index}.ts",
        )
    }
}

private data class TestSegment(
    val duration: Double,
    val uri: String,
)

private fun String.mediaSegmentUriCount(): Int {
    return lines().count { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#")
    }
}
