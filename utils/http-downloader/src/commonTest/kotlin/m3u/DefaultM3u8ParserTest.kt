/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader.m3u

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultM3u8ParserTest {

    private val parser = DefaultM3u8Parser

    @Test
    fun `parse - master playlist with single variant`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            http://example.com/low.m3u8
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MasterPlaylist, "Should parse as MasterPlaylist")

        // Check version
        assertEquals(3, playlist.version)
        // Expect a single variant
        assertEquals(1, playlist.variants.size)
        val variant = playlist.variants.first()
        assertEquals("http://example.com/low.m3u8", variant.uri)
        assertEquals(1280000, variant.bandwidth)
        assertEquals("640x360", variant.resolution)
        assertEquals("avc1.4d401e,mp4a.40.2", variant.codecs)
    }

    @Test
    fun `parse - master playlist with multiple variants`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,AVERAGE-BANDWIDTH=1000000
            http://example.com/low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1280x720,FRAME-RATE=30.0
            http://example.com/high.m3u8
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MasterPlaylist, "Should parse as MasterPlaylist")

        // Check version
        assertEquals(6, playlist.version)
        // Expect two variants
        assertEquals(2, playlist.variants.size)

        val (first, second) = playlist.variants
        assertEquals("http://example.com/low.m3u8", first.uri)
        assertEquals(1280000, first.bandwidth)
        assertEquals(1000000, first.averageBandwidth)
        assertNull(first.resolution)

        assertEquals("http://example.com/high.m3u8", second.uri)
        assertEquals(2560000, second.bandwidth)
        assertNull(second.averageBandwidth)
        assertEquals("1280x720", second.resolution)
        assertEquals(30.0f, second.frameRate)
    }

    @Test
    fun `parse - basic media playlist`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:1
            #EXTINF:9.0,
            http://example.com/segment1.ts
            #EXTINF:9.0,
            http://example.com/segment2.ts
            #EXTINF:9.0,
            http://example.com/segment3.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MediaPlaylist, "Should parse as MediaPlaylist")

        assertEquals(3, playlist.version)
        assertEquals(10, playlist.targetDuration)
        assertEquals(1, playlist.mediaSequence)
        assertTrue(playlist.isEndlist)

        // There are 3 segments
        assertEquals(3, playlist.segments.size)
        playlist.segments.forEach { segment ->
            // Each segment has a duration of 9.0
            assertEquals(9.0f, segment.duration)
            // URIs
            assertTrue(segment.uri.startsWith("http://example.com/segment"))
        }
    }

    @Test
    fun `parse - media playlist with discontinuity and byte range`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:50
            #EXTINF:10.0,Segment 50
            http://example.com/segment50.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.0,Segment 51
            #EXT-X-BYTERANGE:75232@0
            http://example.com/segment51.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MediaPlaylist)

        assertEquals(4, playlist.version)
        assertEquals(10, playlist.targetDuration)
        assertEquals(50, playlist.mediaSequence)
        assertEquals(2, playlist.segments.size)

        // First segment
        val firstSegment = playlist.segments[0]
        assertEquals(10.0f, firstSegment.duration)
        assertEquals("http://example.com/segment50.ts", firstSegment.uri)
        assertEquals("Segment 50", firstSegment.title)
        assertFalse(firstSegment.isDiscontinuity)
        assertNull(firstSegment.byteRange)

        // Second segment
        val secondSegment = playlist.segments[1]
        assertEquals(10.0f, secondSegment.duration)
        assertEquals("http://example.com/segment51.ts", secondSegment.uri)
        assertEquals("Segment 51", secondSegment.title)
        // The second segment follows a discontinuity tag
        assertTrue(secondSegment.isDiscontinuity)
        // Now that byteRange is a structured type:
        assertEquals(75232, secondSegment.byteRange?.length)
        assertEquals(0, secondSegment.byteRange?.offset)

        assertTrue(playlist.isEndlist)
    }

    @Test
    fun `parse - media playlist with encryption key`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:5
            #EXT-X-TARGETDURATION:8
            #EXT-X-KEY:METHOD=AES-128,URI="https://keyserver.example.com/key",IV=0x1A2B3C4D5E6F
            #EXTINF:8.0,
            http://cdn.example.com/fileSequence0.ts
            #EXTINF:8.0,
            http://cdn.example.com/fileSequence1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MediaPlaylist)

        assertEquals(5, playlist.version)
        assertEquals(8, playlist.targetDuration)
        assertTrue(playlist.isEndlist)
        assertEquals(2, playlist.segments.size)

        // Check the encryption key was recorded at the segment level or in the segment's keys map
        val segment0 = playlist.segments[0]
        assertEquals(8.0f, segment0.duration)
        // Key info is in segment0.keys or segment0.tags
        assertTrue(segment0.keys.isNotEmpty() || segment0.tags.isNotEmpty())
    }

    @Test
    fun `parse - master playlist fallback to version default`() {
        // If there's no explicit #EXT-X-VERSION, we might fallback to a default in the implementation
        val content = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000
            http://example.com/only.m3u8
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")
        assertTrue(playlist is M3u8Playlist.MasterPlaylist)

        // Depending on parser default logic (often 3 or 1):
        assertEquals(3, playlist.version)
        assertEquals(1, playlist.variants.size)
    }

    @Test
    fun `parse - media playlist fallback to default fields`() {
        // No target duration or media sequence
        // The parser might fallback to version=3, mediaSequence=0, or omit them
        val content = """
            #EXTM3U
            #EXTINF:6.0,
            fileSequence0.ts
            #EXTINF:6.0,
            fileSequence1.ts
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")
        assertTrue(playlist is M3u8Playlist.MediaPlaylist)

        // The parser default version is 3
        assertEquals(3, playlist.version)
        // If no #EXT-X-TARGETDURATION is found, parser returns null
        assertNull(playlist.targetDuration)
        // If no #EXT-X-MEDIA-SEQUENCE is found, parser defaults to 0
        assertEquals(0, playlist.mediaSequence)
        // The playlist does not end with #EXT-X-ENDLIST, so isEndlist is false
        assertFalse(playlist.isEndlist)

        assertEquals(2, playlist.segments.size)
    }

    @Test
    fun `parse - invalid input throws exception`() {
        assertFailsWith<M3uFormatException> {
            parser.parse("", "http://example.com")
        }
        assertFailsWith<M3uFormatException> {
            // no #EXTM3U
            parser.parse("#EXT-X-STREAM-INF:BANDWIDTH=1000000\nhttp://example.com/video.m3u8", "http://example.com")
        }

        // TODO: This should throw
        parser.parse("#EXTM3U\n#EXTX-STREAM-INF:BANDWIDTH=1000000\nhttp://example.com/video.m3u8", "http://example.com")
        // Typo in the tag #EXTX
    }

    @Test
    fun `parse - media playlist with byte range no offset`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,No offset
            #EXT-X-BYTERANGE:45000
            http://example.com/segmentNoOffset.ts
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")
        assertTrue(playlist is M3u8Playlist.MediaPlaylist)
        assertEquals(1, playlist.segments.size)

        val segment = playlist.segments[0]
        assertEquals(10.0f, segment.duration)
        assertEquals("http://example.com/segmentNoOffset.ts", segment.uri)
        assertEquals(45000, segment.byteRange?.length)
        assertNull(segment.byteRange?.offset)
    }
}
