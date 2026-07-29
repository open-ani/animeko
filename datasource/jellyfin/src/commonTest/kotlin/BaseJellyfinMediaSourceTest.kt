package me.him188.ani.datasources.jellyfin

import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.MediaChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseJellyfinMediaSourceTest {
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
}
