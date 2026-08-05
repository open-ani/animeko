package me.him188.ani.datasources.jellyfin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.MediaChapter
import me.him188.ani.datasources.api.MediaChapterKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaseJellyfinMediaSourceTest {
    @Test
    fun fallbackRequestPropagatesCancellation() = runTest {
        assertFailsWith<CancellationException> {
            fallbackOnFailure<Unit> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun fallbackRequestIgnoresRegularFailure() = runTest {
        assertEquals(null, fallbackOnFailure<Unit> { error("not supported") })
    }

    @Test
    fun testMediaChapterMapping() {
        val chapter = MediaChapter(name = "OP", durationMillis = 90_000L, offsetMillis = 10_000L)
        assertEquals("OP", chapter.name)
        assertEquals(90_000L, chapter.durationMillis)
        assertEquals(10_000L, chapter.offsetMillis)
    }

    @Test
    fun testMediaSegmentQueryResultDeserialization() {
        val rawJson = """
            {
              "Items": [
                {
                  "Id": "seg1",
                  "ItemId": "item1",
                  "Type": "Intro",
                  "StartTicks": 554802116,
                  "EndTicks": 1460208374
                },
                {
                  "Id": "seg2",
                  "ItemId": "item1",
                  "Type": "Outro",
                  "StartTicks": 13290383643,
                  "EndTicks": 14146631118
                }
              ],
              "TotalRecordCount": 2,
              "StartIndex": 0
            }
        """.trimIndent()

        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<MediaSegmentQueryResult>(rawJson)
        assertEquals(2, parsed.Items.size)
        assertEquals("Intro", parsed.Items[0].Type)
        assertEquals(554802116L, parsed.Items[0].StartTicks)
        assertEquals(1460208374L, parsed.Items[0].EndTicks)
        assertEquals("Outro", parsed.Items[1].Type)
    }

    @Test
    fun testIntroTimestampsDtoDeserialization() {
        val rawJson = """
            {
              "IntroStart": 55.48,
              "IntroEnd": 146.02,
              "Valid": true
            }
        """.trimIndent()

        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<IntroTimestampsDto>(rawJson)
        assertTrue(parsed.Valid)
        assertEquals(55.48, parsed.IntroStart)
        assertEquals(146.02, parsed.IntroEnd)
    }

    @Test
    fun testCurrentIntroSkipperSegmentsDeserialization() {
        val rawJson = """
            {
              "Introduction": {
                "Start": 55.48,
                "End": 146.02,
                "Valid": true
              },
              "Credits": {
                "Start": 1329.04,
                "End": 1414.66,
                "Valid": true
              }
            }
        """.trimIndent()

        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<IntroSkipperSegmentsDto>(rawJson)
        val chapters = parsed.toMediaChapters()

        assertEquals(2, chapters.size)
        assertEquals(MediaChapter("OP", 90_540L, 55_480L, MediaChapterKind.OPENING), chapters[0])
        assertEquals(MediaChapter("ED", 85_620L, 1_329_040L, MediaChapterKind.ENDING), chapters[1])
    }

    @Test
    fun testLegacyIntroSkipperSegmentsDeserialization() {
        val rawJson = """
            {
              "Introduction": {
                "IntroStart": 55.48,
                "IntroEnd": 146.02,
                "Valid": true
              },
              "Credits": {
                "IntroStart": 1329.04,
                "IntroEnd": 1414.66,
                "Valid": true
              }
            }
        """.trimIndent()

        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<IntroSkipperSegmentsDto>(rawJson)
        val chapters = parsed.toMediaChapters()

        assertEquals(2, chapters.size)
        assertEquals("OP", chapters[0].name)
        assertEquals(MediaChapterKind.OPENING, chapters[0].kind)
        assertEquals("ED", chapters[1].name)
        assertEquals(MediaChapterKind.ENDING, chapters[1].kind)
        assertEquals(1_329_040L, chapters[1].offsetMillis)
    }
}
