/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinPlaybackTest {
    @Test
    fun `factory instance id is preserved for per-source playback preferences`() {
        val source = source(handler = { error("No request expected") }, instanceId = "jellyfin-instance-2")

        assertEquals("jellyfin-instance-2", source.mediaSourceId)
    }

    @Test
    fun `fixed bitrate negotiates PlaybackInfo and uses its transcoding url`() = runTest {
        var playbackInfoCount = 0
        val source = source { request ->
            assertEquals("/Items/episode-1/PlaybackInfo", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            playbackInfoCount++
            assertEquals(
                """MediaBrowser Token="test-api-key"""",
                request.headers[HttpHeaders.Authorization],
            )
            val body = request.jsonBody()
            assertEquals("test-user-id", body.getValue("UserId").jsonPrimitive.content)
            assertEquals("8000000", body.getValue("MaxStreamingBitrate").jsonPrimitive.content)
            assertEquals("12500000", body.getValue("StartTimeTicks").jsonPrimitive.content)
            assertEquals("physical-version-1", body.getValue("MediaSourceId").jsonPrimitive.content)
            assertEquals(
                "8000000",
                body.getValue("DeviceProfile").jsonObject
                    .getValue("MaxStreamingBitrate").jsonPrimitive.content,
            )
            assertCompleteVideoDeviceProfile(body.getValue("DeviceProfile").jsonObject)
            respondJson(
                """
                {
                  "PlaySessionId": "play-session-1",
                  "MediaSources": [{
                    "Id": "physical-version-1",
                    "Bitrate": 24000000,
                    "SupportsDirectPlay": false,
                    "SupportsDirectStream": false,
                    "SupportsTranscoding": true,
                    "TranscodingUrl": "/Videos/episode-1/master.m3u8?api_key=test-api-key",
                    "MediaStreams": [
                      { "Type": "Video", "Codec": "hevc", "BitRate": 22000000 },
                      { "Type": "Audio", "Codec": "aac", "BitRate": 2000000 }
                    ]
                  }]
                }
                """.trimIndent(),
            )
        }

        val plan = source.createPlaybackPlan(
            itemId = "episode-1",
            quality = JellyfinPlaybackQuality.fixed(8_000_000),
            mediaSourceId = "physical-version-1",
            startPositionMillis = 1_250,
        )

        assertEquals(1, playbackInfoCount)
        assertEquals(
            "$TEST_BASE_URL/Videos/episode-1/master.m3u8?api_key=test-api-key",
            plan.uri,
        )
        assertEquals(8_000_000, plan.effectiveMaxBitrate)
        assertEquals(24_000_000, plan.sourceBitrate)
        assertEquals("hevc", plan.sourceVideoCodec)
        assertEquals("physical-version-1", plan.mediaSourceId)
        assertEquals("play-session-1", plan.playSessionId)
        assertTrue(plan.isTranscoding)
    }

    @Test
    fun `selected audio stream is retained across PlaybackInfo renegotiation`() = runTest {
        var playbackInfoCount = 0
        val source = source { request ->
            assertEquals("/Items/movie-1/PlaybackInfo", request.url.encodedPath)
            playbackInfoCount++
            val body = request.jsonBody()
            assertEquals("3", body.getValue("AudioStreamIndex").jsonPrimitive.content)

            respondJson(
                """
                {
                  "PlaySessionId": "play-session-$playbackInfoCount",
                  "MediaSources": [{
                    "Id": "physical-version-1",
                    "Bitrate": 24000000,
                    "SupportsDirectPlay": false,
                    "SupportsDirectStream": false,
                    "SupportsTranscoding": true,
                    "DefaultAudioStreamIndex": 1,
                    "TranscodingUrl": "/Videos/movie-1/master.m3u8?AudioStreamIndex=3",
                    "MediaStreams": [
                      { "Index": 0, "Type": "Video", "Codec": "hevc", "BitRate": 22000000 },
                      { "Index": 1, "Type": "Audio", "Codec": "aac", "BitRate": 1000000 },
                      { "Index": 3, "Type": "Audio", "Codec": "aac", "BitRate": 1000000 }
                    ]
                  }]
                }
                """.trimIndent(),
            )
        }

        val plan = source.createPlaybackPlan(
            itemId = "movie-1",
            quality = JellyfinPlaybackQuality.Original,
            audioStreamIndex = 3,
        )

        assertEquals(2, playbackInfoCount)
        assertEquals(listOf(1, 3), plan.audioStreamIndices)
        assertEquals(3, plan.selectedAudioStreamIndex)
    }

    @Test
    fun `original uses Jellyfin compatibility stream instead of forcing direct download`() = runTest {
        var playbackInfoCount = 0
        val source = source { request ->
            assertEquals("/Items/episode-1/PlaybackInfo", request.url.encodedPath)
            playbackInfoCount++
            val body = request.jsonBody()

            if (playbackInfoCount == 1) {
                respondJson(
                    """
                    {
                      "PlaySessionId": "play-session-initial",
                      "MediaSources": [{
                        "Id": "episode-1",
                        "Bitrate": 24000000,
                        "SupportsDirectPlay": false,
                        "SupportsDirectStream": false,
                        "SupportsTranscoding": true,
                        "DefaultAudioStreamIndex": 1,
                        "TranscodingUrl": "/Videos/episode-1/master.m3u8?VideoCodec=h264",
                        "MediaStreams": [
                          { "Index": 0, "Type": "Video", "Codec": "hevc", "BitRate": 23800000 },
                          { "Index": 1, "Type": "Audio", "Codec": "aac", "BitRate": 200000 }
                        ]
                      }]
                    }
                    """.trimIndent(),
                )
            } else {
                assertFalse(body.containsKey("AudioStreamIndex"))
                assertEquals(
                    Int.MAX_VALUE.toString(),
                    body.getValue("MaxStreamingBitrate").jsonPrimitive.content,
                )
                val transcodingProfile = body.getValue("DeviceProfile")
                    .jsonObject
                    .getValue("TranscodingProfiles")
                    .jsonArray
                    .single()
                    .jsonObject
                assertEquals(
                    "hevc,h264",
                    transcodingProfile.getValue("VideoCodec").jsonPrimitive.content,
                )
                respondJson(
                    """
                    {
                      "PlaySessionId": "play-session-original",
                      "MediaSources": [{
                        "Id": "episode-1",
                        "Bitrate": 24000000,
                        "SupportsDirectPlay": false,
                        "SupportsDirectStream": false,
                        "SupportsTranscoding": true,
                        "DefaultAudioStreamIndex": 1,
                        "TranscodingUrl": "/Videos/episode-1/master.m3u8?VideoCodec=hevc,h264",
                        "MediaStreams": [
                          { "Index": 0, "Type": "Video", "Codec": "hevc", "BitRate": 23800000 },
                          { "Index": 1, "Type": "Audio", "Codec": "aac", "BitRate": 200000 }
                        ]
                      }]
                    }
                    """.trimIndent(),
                )
            }
        }

        val plan = source.createPlaybackPlan(
            itemId = "episode-1",
            quality = JellyfinPlaybackQuality.Original,
        )

        assertEquals(2, playbackInfoCount)
        assertEquals(
            "$TEST_BASE_URL/Videos/episode-1/master.m3u8?VideoCodec=hevc,h264",
            plan.uri,
        )
        assertEquals(null, plan.effectiveMaxBitrate)
        assertTrue(plan.isTranscoding)
    }

    @Test
    fun `original keeps direct download when Jellyfin approves it`() = runTest {
        val source = source { request ->
            assertEquals("/Items/episode-1/PlaybackInfo", request.url.encodedPath)
            respondJson(
                """
                {
                  "PlaySessionId": "play-session-original",
                  "MediaSources": [{
                    "Id": "episode-1",
                    "Bitrate": 6000000,
                    "SupportsDirectPlay": true,
                    "SupportsDirectStream": true,
                    "SupportsTranscoding": true,
                    "MediaStreams": [
                      { "Index": 0, "Type": "Video", "Codec": "h264", "BitRate": 5800000 },
                      { "Index": 1, "Type": "Audio", "Codec": "aac", "BitRate": 200000 }
                    ],
                    "DefaultAudioStreamIndex": 1
                  }]
                }
                """.trimIndent(),
            )
        }

        val plan = source.createPlaybackPlan(
            itemId = "episode-1",
            quality = JellyfinPlaybackQuality.Original,
        )

        assertEquals(
            "$TEST_BASE_URL/Items/episode-1/Download?ApiKey=test-api-key",
            plan.uri,
        )
        assertEquals(null, plan.effectiveMaxBitrate)
        assertFalse(plan.isTranscoding)
    }

    @Test
    fun `unsupported default audio is explicitly renegotiated for original video copy`() = runTest {
        var playbackInfoCount = 0
        val source = source { request ->
            assertEquals("/Items/movie-1/PlaybackInfo", request.url.encodedPath)
            playbackInfoCount++
            val body = request.jsonBody()

            if (playbackInfoCount == 1) {
                assertFalse(body.containsKey("AudioStreamIndex"))
                assertEquals(
                    Int.MAX_VALUE.toString(),
                    body.getValue("MaxStreamingBitrate").jsonPrimitive.content,
                )
                respondJson(
                    """
                    {
                      "PlaySessionId": "play-session-direct",
                      "MediaSources": [{
                        "Id": "physical-version-4k",
                        "Bitrate": 61126841,
                        "SupportsDirectPlay": true,
                        "SupportsDirectStream": true,
                        "SupportsTranscoding": true,
                        "DefaultAudioStreamIndex": 1,
                        "MediaStreams": [
                          { "Index": 0, "Type": "Video", "Codec": "hevc", "BitRate": 60742841 },
                          { "Index": 1, "Type": "Audio", "Codec": "dts" },
                          { "Index": 3, "Type": "Audio", "Codec": "ac3", "BitRate": 384000 }
                        ]
                      }]
                    }
                    """.trimIndent(),
                )
            } else {
                assertEquals("1", body.getValue("AudioStreamIndex").jsonPrimitive.content)
                assertEquals(
                    Int.MAX_VALUE.toString(),
                    body.getValue("MaxStreamingBitrate").jsonPrimitive.content,
                )
                val transcodingProfile = body.getValue("DeviceProfile")
                    .jsonObject
                    .getValue("TranscodingProfiles")
                    .jsonArray
                    .single()
                    .jsonObject
                assertEquals(
                    "hevc,h264",
                    transcodingProfile.getValue("VideoCodec").jsonPrimitive.content,
                )
                respondJson(
                    """
                    {
                      "PlaySessionId": "play-session-compatible",
                      "MediaSources": [{
                        "Id": "physical-version-4k",
                        "Bitrate": 61126841,
                        "SupportsDirectPlay": false,
                        "SupportsDirectStream": false,
                        "SupportsTranscoding": true,
                        "DefaultAudioStreamIndex": 1,
                        "TranscodingUrl": "/Videos/movie-1/master.m3u8?VideoCodec=hevc,h264&AudioCodec=aac,mp3",
                        "MediaStreams": [
                          { "Index": 0, "Type": "Video", "Codec": "hevc", "BitRate": 60742841 },
                          { "Index": 1, "Type": "Audio", "Codec": "dts" }
                        ]
                      }]
                    }
                    """.trimIndent(),
                )
            }
        }

        val plan = source.createPlaybackPlan(
            itemId = "movie-1",
            quality = JellyfinPlaybackQuality.Original,
            mediaSourceId = "physical-version-4k",
        )

        assertEquals(2, playbackInfoCount)
        assertEquals(
            "$TEST_BASE_URL/Videos/movie-1/master.m3u8?VideoCodec=hevc,h264&AudioCodec=aac,mp3",
            plan.uri,
        )
        assertEquals(null, plan.effectiveMaxBitrate)
        assertEquals("physical-version-4k", plan.mediaSourceId)
        assertEquals("play-session-compatible", plan.playSessionId)
        assertTrue(plan.isTranscoding)
    }

    @Test
    fun `fixed bitrate does not silently fall back to original when transcoding is unavailable`() = runTest {
        val source = source { request ->
            assertEquals("/Items/episode-1/PlaybackInfo", request.url.encodedPath)
            respondJson(
                """
                {
                  "PlaySessionId": "play-session-unavailable",
                  "MediaSources": [{
                    "Id": "episode-1",
                    "Bitrate": 6000000,
                    "SupportsDirectPlay": false,
                    "SupportsDirectStream": false,
                    "SupportsTranscoding": false
                  }]
                }
                """.trimIndent(),
            )
        }

        val exception = assertFailsWith<JellyfinPlaybackUnavailableException> {
            source.createPlaybackPlan(
                itemId = "episode-1",
                quality = JellyfinPlaybackQuality.fixed(1_500_000),
            )
        }

        assertEquals(JellyfinPlaybackQuality.fixed(1_500_000), exception.quality)
        assertFalse(exception.supportsTranscoding)
    }

    @Test
    fun `auto quality uses Jellyfin bitrate test and LAN floor`() = runTest {
        var bitrateTestCount = 0
        val source = source { request ->
            when (request.url.encodedPath) {
                "/System/Endpoint" -> respondJson("""{"IsInNetwork":true}""")
                "/Playback/BitrateTest" -> {
                    bitrateTestCount++
                    val size = checkNotNull(request.url.parameters["Size"]).toInt()
                    respond(
                        ByteArray(size),
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.OctetStream.toString(),
                        ),
                    )
                }

                "/Items/episode-1/PlaybackInfo" -> {
                    val maxBitrate = request.jsonBody()
                        .getValue("MaxStreamingBitrate").jsonPrimitive.content.toInt()
                    assertTrue(maxBitrate >= 140_000_000)
                    respondJson(
                        """
                        {
                          "MediaSources": [{
                            "Id": "episode-1",
                            "Bitrate": 8000000,
                            "SupportsDirectPlay": true
                          }]
                        }
                        """.trimIndent(),
                    )
                }

                else -> error("Unexpected request: ${request.url}")
            }
        }

        val (first, second) = withContext(Dispatchers.Default) {
            source.createPlaybackPlan("episode-1", JellyfinPlaybackQuality.Auto) to
                    source.createPlaybackPlan("episode-1", JellyfinPlaybackQuality.Auto)
        }

        assertTrue(first.effectiveMaxBitrate!! >= 140_000_000)
        assertEquals(first.effectiveMaxBitrate, second.effectiveMaxBitrate)
        assertEquals(3, bitrateTestCount)
    }

    @Test
    fun `quality presets follow Jellyfin Web bitrate tiers`() {
        val qualities = jellyfinPlaybackQualities(
            sourceBitrate = 7_000_000,
        )

        assertEquals(JellyfinPlaybackQuality.Auto, qualities[0])
        assertEquals(JellyfinPlaybackQuality.Original, qualities[1])
        assertEquals(
            listOf(6_000_000, 4_000_000, 3_000_000, 1_500_000, 720_000, 420_000),
            qualities.drop(2).map { it.maxBitrate },
        )
    }

    @Test
    fun `quality presets are strictly below source bitrate`() {
        val sourceBitrate = 61_126_841
        val fixedBitrates = jellyfinPlaybackQualities(sourceBitrate)
            .filter { it.mode == JellyfinPlaybackQualityMode.FIXED }
            .map { checkNotNull(it.maxBitrate) }

        assertEquals(60_000_000, fixedBitrates.first())
        assertTrue(fixedBitrates.all { it < sourceBitrate })
    }

    private fun source(
        instanceId: String = "test-instance",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): JellyfinMediaSource {
        return JellyfinMediaSource(
            config = MediaSourceConfig(
                arguments = mapOf(
                    "baseUrl" to TEST_BASE_URL,
                    "userId" to "test-user-id",
                    "apikey" to "test-api-key",
                ),
            ),
            client = HttpClient(MockEngine(handler)) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }.asScopedHttpClient(),
            instanceId = instanceId,
        )
    }

    private fun HttpRequestData.jsonBody() = Json.parseToJsonElement(
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
    ).jsonObject

    private fun assertCompleteVideoDeviceProfile(profile: JsonObject) {
        val directPlayProfiles = profile.getValue("DirectPlayProfiles").jsonArray
        assertTrue(directPlayProfiles.isNotEmpty())
        directPlayProfiles.forEach {
            assertEquals("Video", it.jsonObject.getValue("Type").jsonPrimitive.content)
        }

        val transcodingProfile = profile.getValue("TranscodingProfiles")
            .jsonArray
            .single()
            .jsonObject
        assertEquals("ts", transcodingProfile.getValue("Container").jsonPrimitive.content)
        assertEquals("Video", transcodingProfile.getValue("Type").jsonPrimitive.content)
        assertEquals("h264", transcodingProfile.getValue("VideoCodec").jsonPrimitive.content)
        assertEquals("aac,mp3", transcodingProfile.getValue("AudioCodec").jsonPrimitive.content)
        assertEquals("hls", transcodingProfile.getValue("Protocol").jsonPrimitive.content)
        assertEquals("Streaming", transcodingProfile.getValue("Context").jsonPrimitive.content)
        assertEquals("2", transcodingProfile.getValue("MaxAudioChannels").jsonPrimitive.content)
        assertEquals("2", transcodingProfile.getValue("MinSegments").jsonPrimitive.content)

        val subtitleProfiles = profile.getValue("SubtitleProfiles").jsonArray
        assertTrue(subtitleProfiles.isNotEmpty())
        subtitleProfiles.forEach {
            assertEquals("External", it.jsonObject.getValue("Method").jsonPrimitive.content)
        }
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        const val TEST_BASE_URL = "https://jellyfin.example.test"
    }
}
