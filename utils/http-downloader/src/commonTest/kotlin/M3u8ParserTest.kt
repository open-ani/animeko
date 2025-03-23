/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader


import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class M3u8ParserTest {

    @Test
    fun `parse - empty text - returns empty list`() = runTest {
        val emptyM3u8 = ""
        val result = M3u8Parser.parse(emptyM3u8)
        assertTrue(result.isEmpty(), "Expected no segments from empty M3U8")
    }

    @Test
    fun `parse - only comments and empty lines - returns empty list`() = runTest {
        val commentedM3u8 = """
            #EXTM3U

            # This is a comment line
            #EXT-X-VERSION:3
            # Some random comment
        """.trimIndent()

        val result = M3u8Parser.parse(commentedM3u8)
        assertTrue(result.isEmpty(), "Expected no segments when there are no #EXTINF lines")
    }

    @Test
    fun `parse - single segment - extracts one segment info`() = runTest {
        val singleSegmentM3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:9.009,
            https://example.com/segment1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = M3u8Parser.parse(singleSegmentM3u8)
        assertEquals(1, result.size, "Expected exactly one segment")
        with(result[0]) {
            assertEquals(0, index)
            assertEquals("https://example.com/segment1.ts", url)
            assertFalse(isDownloaded)
            assertEquals(-1, byteSize)
            assertNull(tempFilePath)
        }
    }

    @Test
    fun `parse - multiple segments - extracts all segment info`() = runTest {
        val multipleSegmentsM3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:9.009,
            https://example.com/segment1.ts
            #EXTINF:9.009,
            https://example.com/segment2.ts
            #EXTINF:9.009,
            https://example.com/segment3.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = M3u8Parser.parse(multipleSegmentsM3u8)
        assertEquals(3, result.size, "Expected three segments")

        // Verify indexing and URLs
        assertEquals(0, result[0].index)
        assertEquals("https://example.com/segment1.ts", result[0].url)

        assertEquals(1, result[1].index)
        assertEquals("https://example.com/segment2.ts", result[1].url)

        assertEquals(2, result[2].index)
        assertEquals("https://example.com/segment3.ts", result[2].url)
    }

    @Test
    fun `parse - missing line after EXTINF - skip segment`() = runTest {
        // #EXTINF is followed by nothing => parser will skip
        val invalidM3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:9.009,
            #EXTINF:9.009,
            https://example.com/segment2.ts
        """.trimIndent()

        val result = M3u8Parser.parse(invalidM3u8)
        // Only one valid segment (#EXTINF line with a corresponding URL below)
        assertEquals(1, result.size, "Expected only one valid segment")
        assertEquals("https://example.com/segment2.ts", result[0].url)
        assertEquals(0, result[0].index)
    }

    @Test
    fun `parse - random lines between #EXTINF and URL - skip those lines`() = runTest {
        // Some lines that are not empty but also not a valid URL
        val trickyM3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:9.009,
            # Some random line that is not a URL
            # Another random line
            https://example.com/unexpected-1.ts
            #EXTINF:9.009,
            https://example.com/segment2.ts
        """.trimIndent()

        val result = M3u8Parser.parse(trickyM3u8)
        // For the naive parser, the first #EXTINF won't match a next-line URL because next line is comment
        // The second #EXTINF -> next line is the real URL => 1 segment total
        assertEquals(1, result.size, "Expected only one recognized segment based on naive rules")
        assertEquals("https://example.com/segment2.ts", result[0].url)
    }

    @Test
    fun `parse - extinf lines in different case - recognized anyway`() = runTest {
        val mixedCaseM3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #ExtInf:9.009,
            https://example.com/segment1.ts
            #extinf:9.009,
            https://example.com/segment2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = M3u8Parser.parse(mixedCaseM3u8)
        assertEquals(2, result.size, "Expected to parse both segments despite different #EXTINF cases")
        assertEquals("https://example.com/segment1.ts", result[0].url)
        assertEquals("https://example.com/segment2.ts", result[1].url)
    }

    @Test
    fun `parse - content with extra tags - still only parse #EXTINF segments`() = runTest {
        // #EXT-X-KEY, #EXT-X-DISCONTINUITY, etc. are optional for advanced HLS, but our naive parser will ignore them
        val advancedM3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:1
            #EXT-X-KEY:METHOD=AES-128,URI="https://example.com/key"
            #EXTINF:10,
            https://example.com/segment1.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10,
            https://example.com/segment2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = M3u8Parser.parse(advancedM3u8)
        assertEquals(2, result.size, "Expected to parse two segments despite extra advanced tags")
        assertEquals("https://example.com/segment1.ts", result[0].url)
        assertEquals("https://example.com/segment2.ts", result[1].url)
    }
}
