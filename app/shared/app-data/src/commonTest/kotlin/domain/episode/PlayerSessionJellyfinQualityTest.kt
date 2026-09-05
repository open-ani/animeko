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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
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
import org.openani.mediamp.metadata.MediaProperties
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
    fun `non-Jellyfin stop is not serialized behind media opening`() = runTest {
        val openingStarted = CompletableDeferred<Unit>()
        val provider = object : MediaDataProvider<UriMediaData> {
            override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY

            override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData {
                openingStarted.complete(Unit)
                awaitCancellation()
            }
        }
        val resolver = object : MediaResolver {
            override fun supports(media: Media): Boolean = true

            override suspend fun resolve(
                media: Media,
                episode: EpisodeMetadata,
            ): MediaDataProvider<*> = provider
        }
        val player = ResumeBeforeSeekPlayer(TestMediampPlayer(StandardTestDispatcher(testScheduler)))
        val session = PlayerSession(player, koin(resolver), EmptyCoroutineContext)
        val loading = async {
            session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        }

        openingStarted.await()
        val stopping = async { session.stopPlayback() }
        try {
            runCurrent()
            assertTrue(stopping.isCompleted)
        } finally {
            loading.cancelAndJoin()
        }
        stopping.await()
    }

    @Test
    fun `quality change stops and reloads from the requested position`() = runTest {
        var playbackInfoCount = 0
        val startTimeTicks = mutableListOf<Long>()
        val requestedMediaSourceIds = mutableListOf<String?>()
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
                            val body = Json.parseToJsonElement(
                                (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                            ).jsonObject
                            startTimeTicks += body.getValue("StartTimeTicks").jsonPrimitive.content.toLong()
                            requestedMediaSourceIds += body["MediaSourceId"]?.jsonPrimitive?.content
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
        backingPlayer.injectPosition(42_000L)
        runCurrent()

        assertTrue(
            session.reloadJellyfinPlaybackQuality(
                JellyfinPlaybackQuality.fixed(8_000_000),
                startPositionMillis = 42_000L,
            ).isSuccess,
        )
        assertEquals(42_000L, backingPlayer.currentPositionMillis.value)
        assertTrue(backingPlayer.state.value.playWhenReady)
        assertEquals(JellyfinPlaybackQuality.fixed(8_000_000), repository.quality)
        assertEquals(emptyList(), stoppedSessions)

        player.pause()
        assertTrue(
            session.reloadJellyfinPlaybackQuality(
                JellyfinPlaybackQuality.fixed(4_000_000),
                startPositionMillis = 42_000L,
            ).isSuccess,
        )
        assertEquals(42_000L, backingPlayer.currentPositionMillis.value)
        assertTrue(backingPlayer.state.value.playWhenReady)
        assertEquals(listOf("transcode-1"), stoppedSessions)
        assertEquals(JellyfinPlaybackQuality.fixed(4_000_000), repository.quality)

        session.stopPlayback()

        assertEquals(listOf("transcode-1", "transcode-2"), stoppedSessions)
        assertEquals(listOf(0L, 0L, 0L), startTimeTicks)
        assertEquals(listOf(null, "physical-version-1", "physical-version-1"), requestedMediaSourceIds)
    }

    @Test
    fun `failed quality reload stays stopped without changing physical versions`() = runTest {
        var playbackInfoCount = 0
        val stoppedSessions = mutableListOf<String>()
        val provider = provider(
            source = source { request ->
                when (request.url.encodedPath) {
                    "/Items/episode-1/PlaybackInfo" -> {
                        playbackInfoCount++
                        if (playbackInfoCount == 1) {
                            respondJson(
                                playbackInfo(
                                    "old-transcode",
                                    false,
                                    "/Videos/episode-1/old.m3u8?api_key=test-api-key",
                                ),
                            )
                        } else {
                            respondJson(
                                """
                                {
                                  "PlaySessionId": "different-version",
                                  "MediaSources": [{
                                    "Id": "physical-version-2",
                                    "Bitrate": 12000000,
                                    "SupportsDirectPlay": true,
                                    "SupportsDirectStream": true,
                                    "SupportsTranscoding": true
                                  }]
                                }
                                """.trimIndent(),
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
            itemId = "episode-1",
            quality = JellyfinPlaybackQuality.Original,
        )
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(backingPlayer)
        val session = PlayerSession(player, koin(provider.mediaDataProvider), EmptyCoroutineContext)

        session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        val result = session.reloadJellyfinPlaybackQuality(
            JellyfinPlaybackQuality.fixed(8_000_000),
            startPositionMillis = 42_000L,
        )

        assertTrue(result.isFailure)
        assertFalse(backingPlayer.state.value.playWhenReady)
        assertNull(session.jellyfinPlaybackQualityState.value)
        assertEquals(listOf("old-transcode"), stoppedSessions)
        assertEquals(
            listOf("$TEST_BASE_URL/Videos/episode-1/old.m3u8?api_key=test-api-key"),
            player.mediaUris,
        )
    }

    @Test
    fun `player rejection after negotiation stops the replacement transcode`() = runTest {
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
            quality = JellyfinPlaybackQuality.Original,
        )
        val backingPlayer = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        val player = ResumeBeforeSeekPlayer(backingPlayer, failOnMediaNumber = 2)
        val session = PlayerSession(player, koin(provider.mediaDataProvider), EmptyCoroutineContext)

        session.loadMedia(TestMediaList.first(), episodeMetadata(1))
        val result = session.reloadJellyfinPlaybackQuality(
            JellyfinPlaybackQuality.fixed(8_000_000),
            startPositionMillis = 42_000L,
        )

        assertTrue(result.isFailure)
        assertFalse(backingPlayer.state.value.playWhenReady)
        assertNull(session.jellyfinPlaybackQualityState.value)
        assertEquals(listOf("transcode-1", "transcode-2"), stoppedSessions)
    }

    @Test
    fun `a selected media reload waits for the current Jellyfin quality reload and wins`() = runTest {
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
            session.reloadJellyfinPlaybackQuality(
                JellyfinPlaybackQuality.fixed(8_000_000),
                startPositionMillis = 0L,
            )
        }
        oldSwitchStarted.await()

        val loadingNewMedia = async {
            session.loadMedia(newMedia, episodeMetadata(2))
        }
        runCurrent()
        assertFalse(loadingNewMedia.isCompleted)

        releaseOldSwitch.complete(Unit)
        advanceUntilIdle()

        assertTrue(switching.await().isSuccess)
        loadingNewMedia.await()
        assertEquals(
            "$TEST_BASE_URL/Items/new-episode/Download?ApiKey=test-api-key",
            (player.mediaData.first() as UriMediaData).uri,
        )
        assertEquals(12_000_000, session.jellyfinPlaybackQualityState.value?.sourceBitrate)
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
    fun `stopPlayback stops the active Jellyfin transcode`() = runTest {
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
        session.stopPlayback()

        assertEquals(listOf("active-transcode"), stoppedSessions)
    }

    @Test
    fun `stopPlayback bounds a stalled Jellyfin transcode cleanup`() = runTest {
        val deleteStarted = CompletableDeferred<Unit>()
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
                        deleteStarted.complete(Unit)
                        awaitCancellation()
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
        val stopping = async { session.stopPlayback() }
        deleteStarted.await()
        stopping.await()
        assertTrue(stopping.isCompleted)
    }

    @Test
    fun `old proxy close failure does not block stop-then-reload`() = runTest {
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
        val result = session.reloadJellyfinPlaybackQuality(
            JellyfinPlaybackQuality.fixed(4_000_000),
            startPositionMillis = 0L,
        )

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
        private val failOnMediaNumber: Int? = null,
    ) : MediampPlayer by delegate {
        val mediaUris = mutableListOf<String>()
        private var mediaNumber = 0

        init {
            delegate.defaultMediaProperties = MediaProperties(durationMillis = 1_000_000L)
        }

        override suspend fun setMediaData(
            data: MediaData,
            playWhenReady: Boolean,
            startPositionMillis: Long,
        ) {
            mediaNumber++
            (data as? UriMediaData)?.uri?.let(mediaUris::add)
            if (mediaNumber == failOnMediaNumber) {
                error("simulated player rejection")
            }
            delegate.setMediaData(data, playWhenReady, startPositionMillis)
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
    }
}
