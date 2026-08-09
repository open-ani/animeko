package me.him188.ani.datasources.jellyfin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.MediaChapter
import me.him188.ani.datasources.api.MediaChapterKind
import me.him188.ani.datasources.api.MediaPreviewThumbnails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun testJellyfinTrickplayMetadataDeserializationAndMapping() {
        val itemId = "item1"
        val rawJson = """
            {
              "Trickplay": {
                "$itemId": {
                  "640": {
                    "Width": 640,
                    "Height": 360,
                    "TileWidth": 5,
                    "TileHeight": 5,
                    "ThumbnailCount": 120,
                    "Interval": 20000
                  },
                  "320": {
                    "Width": 320,
                    "Height": 180,
                    "TileWidth": 10,
                    "TileHeight": 10,
                    "ThumbnailCount": 240,
                    "Interval": 10000
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<TrickplayItemDto>(rawJson)
        val thumbnails = assertNotNull(
            createJellyfinPreviewThumbnails("https://example.com", "jellyfin-instance", itemId, parsed.Trickplay),
        )
        assertEquals(320, thumbnails.width)
        assertEquals(180, thumbnails.height)
        assertEquals(10_000L, thumbnails.intervalMillis)
        assertEquals(240, thumbnails.totalCount)
        assertEquals(
            "https://example.com/Videos/item1/Trickplay/320/{tileIndex}.jpg?MediaSourceId=item1",
            (thumbnails.layout as MediaPreviewThumbnails.Layout.SpriteTile).urlPattern,
        )
        assertEquals("jellyfin-instance", thumbnails.requesterMediaSourceId)
        assertEquals(emptyMap(), thumbnails.headers)
    }

    @Test
    fun testJellyfinTrickplayUsesSoleAlternativeMediaSource() {
        val manifest = JellyfinTrickplayManifestDto(
            Width = 320,
            Height = 180,
            TileWidth = 10,
            TileHeight = 10,
            ThumbnailCount = 240,
            Interval = 10_000,
        )
        val thumbnails = assertNotNull(
            createJellyfinPreviewThumbnails(
                "https://example.com",
                "jellyfin-instance",
                "item1",
                mapOf("another-version" to mapOf("320" to manifest)),
            ),
        )
        assertEquals(
            "https://example.com/Videos/item1/Trickplay/320/{tileIndex}.jpg?MediaSourceId=another-version",
            (thumbnails.layout as MediaPreviewThumbnails.Layout.SpriteTile).urlPattern,
        )
    }

    @Test
    fun testJellyfinTrickplayDoesNotGuessBetweenAlternativeMediaSources() {
        val manifest = JellyfinTrickplayManifestDto(
            Width = 320,
            Height = 180,
            TileWidth = 10,
            TileHeight = 10,
            ThumbnailCount = 240,
            Interval = 10_000,
        )
        assertNull(
            createJellyfinPreviewThumbnails(
                "https://example.com",
                "jellyfin-instance",
                "item1",
                mapOf(
                    "version-1" to mapOf("320" to manifest),
                    "version-2" to mapOf("320" to manifest),
                ),
            ),
        )
    }

    @Test
    fun testJellyfinTrickplaySelectsSmallestValidFallback() {
        fun manifest(width: Int, height: Int = width / 2) = JellyfinTrickplayManifestDto(
            Width = width,
            Height = height,
            TileWidth = 10,
            TileHeight = 10,
            ThumbnailCount = 100,
            Interval = 10_000,
        )

        val thumbnails = assertNotNull(
            createJellyfinPreviewThumbnails(
                "https://example.com",
                "jellyfin-instance",
                "item1",
                mapOf(
                    "item1" to mapOf(
                        "1280" to manifest(1280),
                        "160" to manifest(160),
                        "320" to manifest(320, height = 0),
                    ),
                ),
            ),
        )
        assertEquals(160, thumbnails.width)
    }

    @Serializable
    @Suppress("PropertyName")
    private data class TrickplayItemDto(
        val Trickplay: Map<String, Map<String, JellyfinTrickplayManifestDto>> = emptyMap(),
    )
}
