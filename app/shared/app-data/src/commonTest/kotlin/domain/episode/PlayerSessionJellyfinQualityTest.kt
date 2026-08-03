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
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.repository.player.JellyfinPlaybackQualityRepository
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparer
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparerResult
import me.him188.ani.app.domain.media.hls.HlsPlaybackProxySession
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
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.MediaMetadata
import org.openani.mediamp.features.PlayerFeatures
import org.openani.mediamp.features.buildPlayerFeatures
import org.openani.mediamp.metadata.AudioTrack
import org.openani.mediamp.metadata.Chapter
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackGroup
import org.openani.mediamp.metadata.emptyTrackGroup
import org.openani.mediamp.test.TestMediampPlayer
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalMediampApi::class, InternalForInheritanceMediampApi::class)
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
            extraFiles = MediaExtraFiles.EMPTY,
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

    @Test
    fun `stale quality switch cannot replace a newly loaded episode`() = runTest {
        val oldSwitchStarted = CompletableDeferred<Unit>()
        val releaseOldSwitch = CompletableDeferred<Unit>()
        val stoppedSessions = mutableListOf<String>()
        var oldPlaybackInfoCount = 0
        val oldProvider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/old-episode/PlaybackInfo" -> {
                        oldPlaybackInfoCount++
                        if (oldPlaybackInfoCount == 1) {
                            respondJson(playbackInfo("old-original", true, null, bitrate = 24_000_000))
                        } else {
                            oldSwitchStarted.complete(Unit)
                            withContext(NonCancellable) {
                                releaseOldSwitch.await()
                            }
                            respondJson(
                                playbackInfo(
                                    "old-transcode",
                                    false,
                                    "/Videos/old-episode/master.m3u8?api_key=test-api-key",
                                    bitrate = 24_000_000,
                                ),
                            )
                        }
                    }

                    "/Videos/ActiveEncodings" -> {
                        stoppedSessions += checkNotNull(request.url.parameters["playSessionId"])
                        respond("")
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "old-episode",
        )
        val newProvider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/new-episode/PlaybackInfo" -> respondJson(
                        playbackInfo("new-original", true, null, bitrate = 12_000_000),
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "new-episode",
        )
        val oldMedia = TestMediaList[0]
        val newMedia = TestMediaList[1]
        val resolver = resolver(
            oldMedia.mediaId to oldProvider.mediaDataProvider,
            newMedia.mediaId to newProvider.mediaDataProvider,
        )
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(backingPlayer)
        val session = PlayerSession(player, koin(resolver), EmptyCoroutineContext)

        session.loadMedia(oldMedia, episodeMetadata(1))
        val switching = async {
            try {
                withContext(NonCancellable) {
                    session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(8_000_000))
                }
                null
            } catch (e: CancellationException) {
                e
            }
        }
        oldSwitchStarted.await()

        session.loadMedia(newMedia, episodeMetadata(2))
        releaseOldSwitch.complete(Unit)
        advanceUntilIdle()

        assertTrue(switching.await() is CancellationException)
        assertEquals(
            "$TEST_BASE_URL/Items/new-episode/Download?ApiKey=test-api-key",
            (player.mediaData.first() as UriMediaData).uri,
        )
        assertEquals(12_000_000, session.jellyfinPlaybackQualityState.value?.sourceBitrate)
        assertEquals(listOf("old-transcode"), stoppedSessions)
        assertEquals(
            listOf(
                "$TEST_BASE_URL/Items/old-episode/Download?ApiKey=test-api-key",
                "$TEST_BASE_URL/Items/new-episode/Download?ApiKey=test-api-key",
            ),
            player.mediaUris,
        )
    }

    @Test
    fun `cancelling an installed quality switch stops its transcode and rolls back`() = runTest {
        var playbackInfoCount = 0
        val stoppedSessions = mutableListOf<String>()
        val provider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/episode-1/PlaybackInfo" -> {
                        playbackInfoCount++
                        respondJson(
                            if (playbackInfoCount == 1) {
                                playbackInfo("original-session", true, null)
                            } else {
                                playbackInfo(
                                    "cancelled-transcode",
                                    false,
                                    "/Videos/episode-1/master.m3u8?api_key=test-api-key",
                                )
                            },
                        )
                    }

                    "/Videos/ActiveEncodings" -> {
                        stoppedSessions += checkNotNull(request.url.parameters["playSessionId"])
                        throw CancellationException("simulated cleanup cancellation")
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "episode-1",
        )
        val repository = provider.qualityRepository
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(backingPlayer)
        val session = PlayerSession(player, koin(provider.mediaDataProvider), EmptyCoroutineContext)
        session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        backingPlayer.currentPositionMillis.value = 42_000L

        player.holdNextMediaStart()
        val switching = async {
            session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(8_000_000))
        }
        runCurrent()
        backingPlayer.mediaProperties.first { it?.durationMillis == -1L }
        switching.cancelAndJoin()

        assertEquals(listOf("cancelled-transcode"), stoppedSessions)
        assertEquals(
            "$TEST_BASE_URL/Items/episode-1/Download?ApiKey=test-api-key",
            (player.mediaData.first() as UriMediaData).uri,
        )
        assertEquals(42_000L, backingPlayer.currentPositionMillis.value)
        assertEquals(JellyfinPlaybackQuality.Original, repository.quality)
        assertFalse(session.jellyfinPlaybackQualityState.value?.isSwitching ?: true)
        assertNull(session.jellyfinPlaybackProgressSnapshot.value)
    }

    @Test
    fun `episode replacement does not roll stale quality switch back to old media`() = runTest {
        var oldPlaybackInfoCount = 0
        val stoppedSessions = mutableListOf<String>()
        val oldProvider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/old-episode/PlaybackInfo" -> {
                        oldPlaybackInfoCount++
                        respondJson(
                            if (oldPlaybackInfoCount == 1) {
                                playbackInfo("old-original", true, null)
                            } else {
                                playbackInfo(
                                    "old-transcode",
                                    false,
                                    "/Videos/old-episode/master.m3u8?api_key=test-api-key",
                                )
                            },
                        )
                    }

                    "/Videos/ActiveEncodings" -> {
                        stoppedSessions += checkNotNull(request.url.parameters["playSessionId"])
                        respond("")
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "old-episode",
        )
        val newProvider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/new-episode/PlaybackInfo" -> respondJson(playbackInfo("new-original", true, null))
                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "new-episode",
        )
        val oldMedia = TestMediaList[0]
        val newMedia = TestMediaList[1]
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(backingPlayer)
        val session = PlayerSession(
            player,
            koin(
                resolver(
                    oldMedia.mediaId to oldProvider.mediaDataProvider,
                    newMedia.mediaId to newProvider.mediaDataProvider,
                ),
            ),
            EmptyCoroutineContext,
        )

        session.loadMedia(oldMedia, episodeMetadata(1))
        player.holdNextMediaStart()
        val switching = async {
            session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(8_000_000))
        }
        runCurrent()
        backingPlayer.mediaProperties.first { it?.durationMillis == -1L }

        session.loadMedia(newMedia, episodeMetadata(2))
        switching.join()

        assertTrue(switching.isCancelled)
        assertEquals(listOf("old-transcode"), stoppedSessions)
        assertEquals(
            listOf(
                "$TEST_BASE_URL/Items/old-episode/Download?ApiKey=test-api-key",
                "$TEST_BASE_URL/Videos/old-episode/master.m3u8?api_key=test-api-key",
                "$TEST_BASE_URL/Items/new-episode/Download?ApiKey=test-api-key",
            ),
            player.mediaUris,
        )
    }

    @Test
    fun `selected player audio track maps to Jellyfin stream index`() = runTest {
        var playbackInfoCount = 0
        val requestedAudioStreamIndices = mutableListOf<Int?>()
        val provider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/episode-1/PlaybackInfo" -> {
                        playbackInfoCount++
                        val body = Json.parseToJsonElement(
                            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                        ).jsonObject
                        requestedAudioStreamIndices += body["AudioStreamIndex"]?.jsonPrimitive?.content?.toInt()
                        respondJson(
                            audioPlaybackInfo(
                                playSessionId = "session-$playbackInfoCount",
                                supportsDirectPlay = playbackInfoCount == 1,
                                transcodingUrl = if (playbackInfoCount == 1) {
                                    null
                                } else {
                                    "/Videos/episode-1/master.m3u8?api_key=test-api-key"
                                },
                            ),
                        )
                    }

                    "/Videos/ActiveEncodings" -> respond("")
                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "episode-1",
        )
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(
            delegate = backingPlayer,
            audioTracks = listOf(
                AudioTrack("exo-group-0", "exo-group", "English", emptyList()),
                AudioTrack("audio-99", "mpv-aid-99", "Japanese", emptyList()),
            ),
            selectedAudioTrackIndex = 1,
        )
        val session = PlayerSession(player, koin(provider.mediaDataProvider), EmptyCoroutineContext)

        session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        assertTrue(session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(8_000_000)).isSuccess)

        assertEquals(listOf(null, 3), requestedAudioStreamIndices)
    }

    @Test
    fun `selected Jellyfin audio stream is restored when switching from transcode to direct play`() = runTest {
        var playbackInfoCount = 0
        val requestedAudioStreamIndices = mutableListOf<Int?>()
        val provider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/episode-1/PlaybackInfo" -> {
                        playbackInfoCount++
                        val body = Json.parseToJsonElement(
                            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                        ).jsonObject
                        requestedAudioStreamIndices += body["AudioStreamIndex"]?.jsonPrimitive?.content?.toInt()
                        respondJson(
                            audioPlaybackInfo(
                                playSessionId = "session-$playbackInfoCount",
                                supportsDirectPlay = playbackInfoCount != 2,
                                transcodingUrl = if (playbackInfoCount == 2) {
                                    "/Videos/episode-1/master.m3u8?api_key=test-api-key"
                                } else {
                                    null
                                },
                            ),
                        )
                    }

                    "/Videos/ActiveEncodings" -> respond("")
                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "episode-1",
        )
        val initialTracks = listOf(
            AudioTrack("raw-default", "raw-default", "English", emptyList()),
            AudioTrack("raw-selected", "raw-selected", "Japanese", emptyList()),
        )
        val transcodedTrack = AudioTrack("hls-audio", "hls-audio", "Japanese", emptyList())
        val restoredTracks = listOf(
            AudioTrack("new-raw-default", "new-raw-default", "English", emptyList()),
            AudioTrack("new-raw-selected", "new-raw-selected", "Japanese", emptyList()),
        )
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(
            delegate = backingPlayer,
            audioTracks = initialTracks,
            selectedAudioTrackIndex = 1,
        )
        val session = PlayerSession(player, koin(provider.mediaDataProvider), EmptyCoroutineContext)

        session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        player.replaceAudioTracksOnNextMedia(listOf(transcodedTrack), selectedAudioTrackIndex = 0)
        assertTrue(session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(8_000_000)).isSuccess)

        player.replaceAudioTracksOnNextMedia(restoredTracks, selectedAudioTrackIndex = 0)
        assertTrue(session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.Original).isSuccess)

        assertEquals(listOf(null, 3, 3), requestedAudioStreamIndices)
        assertEquals(restoredTracks[1], player.selectedAudioTrack)
    }

    @Test
    fun `audio track count mismatch rejects quality switch without replacing media`() = runTest {
        var playbackInfoCount = 0
        val provider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/episode-1/PlaybackInfo" -> {
                        playbackInfoCount++
                        respondJson(audioPlaybackInfo("session-$playbackInfoCount", true, null))
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "episode-1",
        )
        val onlyPlayerTrack = AudioTrack("player-audio", "player-audio", "Japanese", emptyList())
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(
            delegate = backingPlayer,
            audioTracks = listOf(onlyPlayerTrack),
            selectedAudioTrackIndex = 0,
        )
        val session = PlayerSession(player, koin(provider.mediaDataProvider), EmptyCoroutineContext)

        session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        val originalUri = (player.mediaData.first() as UriMediaData).uri
        val result = session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(8_000_000))

        assertTrue(result.isFailure)
        assertEquals(1, playbackInfoCount)
        assertEquals(originalUri, (player.mediaData.first() as UriMediaData).uri)
    }

    @Test
    fun `close stops the active Jellyfin transcode`() = runTest {
        val stoppedSessions = mutableListOf<String>()
        val provider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/episode-1/PlaybackInfo" -> respondJson(
                        playbackInfo(
                            "active-transcode",
                            false,
                            "/Videos/episode-1/master.m3u8?api_key=test-api-key",
                        ),
                    )

                    "/Videos/ActiveEncodings" -> {
                        stoppedSessions += checkNotNull(request.url.parameters["playSessionId"])
                        respond("")
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "episode-1",
            quality = JellyfinPlaybackQuality.fixed(8_000_000),
        )
        val player = ResumeBeforeSeekPlayer(TestMediampPlayer(StandardTestDispatcher(testScheduler)))
        val session = PlayerSession(player, koin(provider.mediaDataProvider), EmptyCoroutineContext)

        session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        session.close()

        assertEquals(listOf("active-transcode"), stoppedSessions)
    }

    @Test
    fun `proxy close failure after commit does not roll back or skip previous transcode cleanup`() = runTest {
        var playbackInfoCount = 0
        val stoppedSessions = mutableListOf<String>()
        val provider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/episode-1/PlaybackInfo" -> {
                        playbackInfoCount++
                        respondJson(
                            playbackInfo(
                                "transcode-$playbackInfoCount",
                                false,
                                "/Videos/episode-1/stream-$playbackInfoCount.m3u8?api_key=test-api-key",
                            ),
                        )
                    }

                    "/Videos/ActiveEncodings" -> {
                        stoppedSessions += checkNotNull(request.url.parameters["playSessionId"])
                        respond("")
                    }

                    else -> error("Unexpected request: ${request.url}")
                }
            },
            itemId = "episode-1",
            quality = JellyfinPlaybackQuality.fixed(8_000_000),
        )
        val hlsPreparer = ThrowingFirstCloseHlsPlaybackPreparer()
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(backingPlayer)
        val session = PlayerSession(
            player,
            koin(
                provider = provider.mediaDataProvider,
                hlsPlaybackPreparer = hlsPreparer,
                hlsEnabled = true,
            ),
            EmptyCoroutineContext,
        )

        session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        val result = session.switchJellyfinPlaybackQuality(JellyfinPlaybackQuality.fixed(4_000_000))

        assertTrue(result.isSuccess)
        assertEquals(JellyfinPlaybackQuality.fixed(4_000_000), provider.qualityRepository.quality)
        assertEquals(listOf("transcode-1"), stoppedSessions)
        assertEquals(1, hlsPreparer.sessions.first().closeAttempts)
        assertEquals(
            listOf(
                "$TEST_BASE_URL/Videos/episode-1/stream-1.m3u8?api_key=test-api-key",
                "$TEST_BASE_URL/Videos/episode-1/stream-2.m3u8?api_key=test-api-key",
            ),
            player.mediaUris,
        )

        session.stopPlayback()
        assertEquals(listOf("transcode-1", "transcode-2"), stoppedSessions)
    }

    private class ResumeBeforeSeekPlayer(
        private val delegate: TestMediampPlayer,
        audioTracks: List<AudioTrack>? = null,
        selectedAudioTrackIndex: Int? = null,
    ) : MediampPlayer by delegate {
        private var resumedSinceMediaSet = false
        private var holdNextMediaStart = false
        private var currentMediaStartHeld = false
        private val audioTrackGroup = audioTracks?.let { tracks ->
            TestTrackGroup(
                candidates = tracks,
                selected = selectedAudioTrackIndex?.let(tracks::get),
            )
        }
        private var nextAudioTracks: Pair<List<AudioTrack>, Int?>? = null
        val mediaUris = mutableListOf<String>()
        val selectedAudioTrack: AudioTrack? get() = audioTrackGroup?.selected?.value

        override val features: PlayerFeatures = if (audioTrackGroup == null) {
            delegate.features
        } else {
            buildPlayerFeatures {
                add(
                    MediaMetadata.Key,
                    object : MediaMetadata {
                        override val audioTracks: TrackGroup<AudioTrack> = audioTrackGroup
                        override val subtitleTracks: TrackGroup<SubtitleTrack> = emptyTrackGroup()
                        override val chapters: Flow<List<Chapter>> = flowOf(emptyList())
                    },
                )
            }
        }

        fun holdNextMediaStart() {
            holdNextMediaStart = true
        }

        fun releaseCurrentMediaStart() {
            currentMediaStartHeld = false
            exposeMediaTimeline()
        }

        fun replaceAudioTracksOnNextMedia(
            tracks: List<AudioTrack>,
            selectedAudioTrackIndex: Int?,
        ) {
            nextAudioTracks = tracks to selectedAudioTrackIndex
        }

        override suspend fun setMediaData(data: MediaData) {
            (data as? UriMediaData)?.uri?.let(mediaUris::add)
            nextAudioTracks?.let { (tracks, selectedIndex) ->
                checkNotNull(audioTrackGroup).replace(
                    candidates = tracks,
                    selected = selectedIndex?.let(tracks::get),
                )
                nextAudioTracks = null
            }
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

    private class TestTrackGroup<T>(
        candidates: List<T>,
        selected: T?,
    ) : TrackGroup<T> {
        override val selected = MutableStateFlow(selected)
        override val candidates = MutableStateFlow(candidates)

        override fun select(track: T?): Boolean {
            if (track != null && track !in candidates.value) return false
            selected.value = track
            return true
        }

        fun replace(candidates: List<T>, selected: T?) {
            this.candidates.value = candidates
            this.selected.value = selected
        }
    }

    private fun koin(
        provider: JellyfinMediaDataProvider,
        hlsPlaybackPreparer: HlsPlaybackPreparer = NoopHlsPlaybackPreparer,
        hlsEnabled: Boolean = false,
    ): Koin {
        return koin(
            resolver = object : MediaResolver {
                override fun supports(media: Media): Boolean = true

                override suspend fun resolve(
                    media: Media,
                    episode: EpisodeMetadata,
                ): MediaDataProvider<*> = provider
            },
            hlsPlaybackPreparer = hlsPlaybackPreparer,
            hlsEnabled = hlsEnabled,
        )
    }

    private fun koin(
        resolver: MediaResolver,
        hlsPlaybackPreparer: HlsPlaybackPreparer = NoopHlsPlaybackPreparer,
        hlsEnabled: Boolean = false,
    ): Koin {
        return Koin().also { koin ->
            koin.loadModules(
                listOf(
                    module {
                        single<MediaResolver> { resolver }
                        single<GetVideoScaffoldConfigUseCase> {
                            GetVideoScaffoldConfigUseCase {
                                flowOf(
                                    VideoScaffoldConfig.AllDisabled.copy(
                                        enableExperimentalHlsSegmentFiltering = hlsEnabled,
                                    ),
                                )
                            }
                        }
                        single<HlsPlaybackPreparer> { hlsPlaybackPreparer }
                        single<PlaybackAutomationGate> { PlaybackAutomationGate() }
                    },
                ),
            )
        }
    }

    private fun resolver(
        vararg providers: Pair<String, JellyfinMediaDataProvider>,
    ): MediaResolver {
        val providersByMediaId = providers.toMap()
        return object : MediaResolver {
            override fun supports(media: Media): Boolean = true

            override suspend fun resolve(
                media: Media,
                episode: EpisodeMetadata,
            ): MediaDataProvider<*> = checkNotNull(providersByMediaId[media.mediaId])
        }
    }

    private fun source(
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
            instanceId = "jellyfin-instance",
        )
    }

    private fun provider(
        source: JellyfinMediaSource,
        itemId: String,
        quality: JellyfinPlaybackQuality = JellyfinPlaybackQuality.Original,
    ): ProviderFixture {
        val repository = InMemoryQualityRepository(quality)
        return ProviderFixture(
            mediaDataProvider = JellyfinMediaDataProvider(
                source = source,
                itemId = itemId,
                originalTitle = itemId,
                extraFiles = MediaExtraFiles.EMPTY,
                qualityRepository = repository,
            ),
            qualityRepository = repository,
        )
    }

    private fun episodeMetadata(number: Int): EpisodeMetadata {
        return EpisodeMetadata(
            title = "EP$number",
            ep = EpisodeSort(number),
            sort = EpisodeSort(number),
        )
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private data class ProviderFixture(
        val mediaDataProvider: JellyfinMediaDataProvider,
        val qualityRepository: InMemoryQualityRepository,
    )

    private class ThrowingFirstCloseHlsPlaybackPreparer : HlsPlaybackPreparer {
        val sessions = mutableListOf<ThrowingHlsPlaybackProxySession>()

        override suspend fun prepare(data: UriMediaData): HlsPlaybackPreparerResult {
            val session = ThrowingHlsPlaybackProxySession(throwOnClose = sessions.isEmpty())
            sessions += session
            return HlsPlaybackPreparerResult(data, session)
        }
    }

    private class ThrowingHlsPlaybackProxySession(
        private val throwOnClose: Boolean,
    ) : HlsPlaybackProxySession {
        var closeAttempts: Int = 0
            private set

        override fun close() {
            closeAttempts++
            if (throwOnClose) {
                error("simulated proxy close failure")
            }
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
            bitrate: Int = 24_000_000,
        ): String {
            val transcodingProperty = transcodingUrl
                ?.let { ""","TranscodingUrl":"$it"""" }
                .orEmpty()
            return """
                {
                  "PlaySessionId": "$playSessionId",
                  "MediaSources": [{
                    "Id": "physical-version-1",
                    "Bitrate": $bitrate,
                    "SupportsDirectPlay": $supportsDirectPlay,
                    "SupportsDirectStream": false,
                    "SupportsTranscoding": true
                    $transcodingProperty
                  }]
                }
            """.trimIndent()
        }

        fun audioPlaybackInfo(
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
                    "SupportsTranscoding": true,
                    "DefaultAudioStreamIndex": 1,
                    "MediaStreams": [
                      { "Index": 0, "Type": "Video", "Codec": "h264", "BitRate": 22000000 },
                      { "Index": 1, "Type": "Audio", "Codec": "aac", "BitRate": 1000000 },
                      { "Index": 3, "Type": "Audio", "Codec": "aac", "BitRate": 1000000 }
                    ]
                    $transcodingProperty
                  }]
                }
            """.trimIndent()
        }
    }
}
