/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.repository.player.JellyfinPlaybackQualityRepository
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparer
import me.him188.ani.app.domain.media.hls.NoopHlsPlaybackPreparer
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.JellyfinMediaDataProvider
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.jellyfin.JellyfinMediaSource
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality
import me.him188.ani.utils.ktor.asScopedHttpClient
import org.koin.core.Koin
import org.koin.dsl.module
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.test.TestMediampPlayer
import org.openani.mediamp.source.MediaData
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerSessionJellyfinQualityTest {
    @Test
    fun `preserves playback intent while switching bitrate`() = runTest {
        var playbackInfoCount = 0
        val startTimeTicks = mutableListOf<Long>()
        val stoppedSessions = mutableListOf<String>()
        val source = JellyfinMediaSource(
            config = MediaSourceConfig(
                arguments = mapOf(
                    "baseUrl" to TEST_BASE_URL,
                    "userId" to "test-user-id",
                    "apikey" to "test-api-key",
                ),
            ),
            client = HttpClient(
                MockEngine { request ->
                    when {
                        request.url.encodedPath == "/Items/episode-1/PlaybackInfo" -> {
                            playbackInfoCount++
                            startTimeTicks += Json.parseToJsonElement(
                                (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                            ).jsonObject.getValue("StartTimeTicks").jsonPrimitive.content.toLong()
                            val isOriginal = playbackInfoCount == 1
                            respond(
                                content = if (isOriginal) {
                                    playbackInfo(
                                        playSessionId = "original-session",
                                        supportsDirectPlay = true,
                                        transcodingUrl = null,
                                    )
                                } else {
                                    playbackInfo(
                                        playSessionId = "transcode-${playbackInfoCount - 1}",
                                        supportsDirectPlay = false,
                                        transcodingUrl = "/Videos/episode-1/master.m3u8?api_key=test-api-key",
                                    )
                                },
                                headers = headersOf(
                                    HttpHeaders.ContentType,
                                    ContentType.Application.Json.toString(),
                                ),
                            )
                        }

                        request.url.encodedPath == "/Videos/ActiveEncodings" -> {
                            assertEquals(HttpMethod.Delete, request.method)
                            stoppedSessions += checkNotNull(request.url.parameters["playSessionId"])
                            respond("")
                        }

                        else -> error("Unexpected request: ${request.url}")
                    }
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }.asScopedHttpClient(),
            instanceId = "jellyfin-instance",
        )
        val repository = InMemoryQualityRepository(JellyfinPlaybackQuality.Original)
        val provider = JellyfinMediaDataProvider(
            source = source,
            itemId = "episode-1",
            originalTitle = "Episode 1",
            extraFiles = org.openani.mediamp.source.MediaExtraFiles.EMPTY,
            qualityRepository = repository,
        )
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(backingPlayer)
        val session = PlayerSession(
            player = player,
            koin = koin(provider),
            mainDispatcher = EmptyCoroutineContext,
        )

        session.loadMedia(
            TestMediaList.first(),
            EpisodeMetadata(title = "EP1", ep = EpisodeSort(1), sort = EpisodeSort(1)),
        )
        backingPlayer.currentPositionMillis.value = 42_000L

        assertTrue(session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(8_000_000)).isSuccess)
        assertEquals(42_000L, backingPlayer.currentPositionMillis.value)
        assertEquals(JellyfinPlaybackQuality.fixed(8_000_000), repository.quality)
        assertEquals(emptyList(), stoppedSessions)

        backingPlayer.playbackState.value = PlaybackState.PAUSED
        assertTrue(session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(4_000_000)).isSuccess)
        assertEquals(42_000L, backingPlayer.currentPositionMillis.value)
        assertEquals(PlaybackState.PAUSED, backingPlayer.playbackState.value)
        assertEquals(listOf("transcode-1"), stoppedSessions)

        backingPlayer.playbackState.value = PlaybackState.PAUSED_BUFFERING
        player.holdNextMediaStart()
        val switching = async {
            session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(2_000_000))
        }
        runCurrent()
        backingPlayer.mediaProperties.first { it?.durationMillis == -1L }

        assertEquals(
            JellyfinPlaybackProgressSnapshot(
                positionMillis = 42_000L,
                durationMillis = 1_000_000L,
            ),
            session.jellyfinPlaybackProgressSnapshot.value,
        )
        assertEquals(0L, backingPlayer.currentPositionMillis.value)
        assertEquals(-1L, backingPlayer.mediaProperties.value?.durationMillis)

        player.releaseCurrentMediaStart()
        advanceUntilIdle()

        assertTrue(switching.await().isSuccess)
        assertNull(session.jellyfinPlaybackProgressSnapshot.value)
        assertEquals(42_000L, backingPlayer.currentPositionMillis.value)
        assertEquals(PlaybackState.PLAYING, backingPlayer.playbackState.value)
        assertEquals(listOf("transcode-1", "transcode-2"), stoppedSessions)
        assertEquals(JellyfinPlaybackQuality.fixed(2_000_000), repository.quality)

        player.holdNextMediaStart()
        val failedSwitch = async {
            session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(1_000_000))
        }
        runCurrent()
        backingPlayer.mediaProperties.first { it?.durationMillis == -1L }

        assertEquals(
            JellyfinPlaybackProgressSnapshot(
                positionMillis = 42_000L,
                durationMillis = 1_000_000L,
            ),
            session.jellyfinPlaybackProgressSnapshot.value,
        )
        assertEquals(0L, backingPlayer.currentPositionMillis.value)
        assertEquals(-1L, backingPlayer.mediaProperties.value?.durationMillis)

        advanceUntilIdle()

        assertTrue(failedSwitch.await().isFailure)
        assertNull(session.jellyfinPlaybackProgressSnapshot.value)
        assertEquals(42_000L, backingPlayer.currentPositionMillis.value)
        assertEquals(PlaybackState.PLAYING, backingPlayer.playbackState.value)
        assertEquals(JellyfinPlaybackQuality.fixed(2_000_000), repository.quality)
        assertEquals(listOf("transcode-1", "transcode-2", "transcode-4"), stoppedSessions)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), startTimeTicks)

        session.stopPlayback()

        assertEquals(listOf("transcode-1", "transcode-2", "transcode-4", "transcode-3"), stoppedSessions)
    }

    @OptIn(InternalForInheritanceMediampApi::class)
    private class ResumeBeforeSeekPlayer(
        private val delegate: TestMediampPlayer,
    ) : MediampPlayer by delegate {
        private var resumedSinceMediaSet = false
        private var holdNextMediaStart = false
        private var currentMediaStartHeld = false

        fun holdNextMediaStart() {
            holdNextMediaStart = true
        }

        fun releaseCurrentMediaStart() {
            currentMediaStartHeld = false
            exposeMediaTimeline()
        }

        override suspend fun setMediaData(data: MediaData) {
            resumedSinceMediaSet = false
            currentMediaStartHeld = holdNextMediaStart
            holdNextMediaStart = false
            delegate.setMediaData(data)
            delegate.currentPositionMillis.value = 0L
            delegate.mediaProperties.value = MediaProperties(durationMillis = -1L)
        }

        override fun resume() {
            delegate.resume()
            if (!currentMediaStartHeld) {
                exposeMediaTimeline()
            }
        }

        private fun exposeMediaTimeline() {
            delegate.mediaProperties.value = MediaProperties(durationMillis = 1_000_000L)
            delegate.currentPositionMillis.value = 100L
            resumedSinceMediaSet = true
        }

        override fun seekTo(positionMillis: Long) {
            if (resumedSinceMediaSet) {
                delegate.seekTo(positionMillis)
            }
        }
    }

    private fun koin(provider: JellyfinMediaDataProvider): Koin {
        return Koin().also { koin ->
            koin.loadModules(
                listOf(
                    module {
                        single<MediaResolver> {
                            object : MediaResolver {
                                override fun supports(media: Media): Boolean = true

                                override suspend fun resolve(
                                    media: Media,
                                    episode: EpisodeMetadata,
                                ): MediaDataProvider<*> = provider
                            }
                        }
                        single<GetVideoScaffoldConfigUseCase> {
                            GetVideoScaffoldConfigUseCase { flowOf(VideoScaffoldConfig.AllDisabled) }
                        }
                        single<HlsPlaybackPreparer> { NoopHlsPlaybackPreparer }
                        single<PlaybackAutomationGate> { PlaybackAutomationGate() }
                    },
                ),
            )
        }
    }

    private class InMemoryQualityRepository(
        var quality: JellyfinPlaybackQuality,
    ) : JellyfinPlaybackQualityRepository {
        override fun preferenceFlow(mediaSourceId: String): Flow<JellyfinPlaybackQuality> = flowOf(quality)

        override suspend fun get(mediaSourceId: String): JellyfinPlaybackQuality = quality

        override suspend fun set(mediaSourceId: String, quality: JellyfinPlaybackQuality) {
            this.quality = quality
        }
    }

    private companion object {
        const val TEST_BASE_URL = "https://jellyfin.example.test"

        fun playbackInfo(
            playSessionId: String,
            supportsDirectPlay: Boolean,
            transcodingUrl: String?,
        ): String {
            val transcodingProperty = transcodingUrl
                ?.let { ""","TranscodingUrl":"$it"""" }
                .orEmpty()
            return """
                {
                  "PlaySessionId": "$playSessionId",
                  "MediaSources": [{
                    "Id": "physical-version-1",
                    "Bitrate": 24000000,
                    "SupportsDirectPlay": $supportsDirectPlay,
                    "SupportsDirectStream": false,
                    "SupportsTranscoding": true
                    $transcodingProperty
                  }]
                }
            """.trimIndent()
        }
    }
}
