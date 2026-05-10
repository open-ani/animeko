/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeEpisodeSessionApi::class)

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.AnalyticsSettings
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.OneshotActionConfig
import me.him188.ani.app.data.models.preference.ProfileSettings
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaFetchSessionFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestMediaDataProvider
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.SelectFastestPlayableWebMediaUseCase
import me.him188.ani.app.domain.media.selector.SelectFastestPlayableWebMediaUseCaseImpl
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.danmaku.ui.DanmakuConfig
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AutoSelectExtensionTest : AbstractPlayerExtensionTest() {
    private val defaultSettings = MediaSelectorSettings.AllVisible.copy(
        preferKind = null,
        hideSingleEpisodeForCompleted = false,
        preferSeasons = true,
        autoEnableLastSelected = false,
        fastSelectWebKind = false,
    )

    private val mediaSelectorSettings = MutableStateFlow(
        defaultSettings,
    )
    val preferredWebMediaSource = MutableStateFlow<String?>(null)
    private val videoResolverSettings = MutableStateFlow(VideoResolverSettings.Default)

    data class Context(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
        val state: EpisodeFetchSelectPlayState,
    )

    private fun TestScope.createCase(
        mediaResolver: MediaResolver = TestUniversalMediaResolver,
        sampleOpenedMedia: suspend CoroutineScope.(openedMedia: MediaData, timeout: Duration) -> Duration = { _, _ -> ZERO },
        config: (scope: CoroutineScope, suite: EpisodePlayerTestSuite) -> Unit = { _, _ -> },
    ): Context {
        contract {
            callsInPlace(config, InvocationKind.EXACTLY_ONCE)
        }

        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<GetMediaSelectorSettingsFlowUseCase> {
            GetMediaSelectorSettingsFlowUseCase { mediaSelectorSettings }
        }
        suite.registerComponent<MediaSelectorAutoSelectUseCase> {
            MediaSelectorAutoSelectUseCaseImpl(koin)
        }
        suite.registerComponent<SelectFastestPlayableWebMediaUseCase> {
            SelectFastestPlayableWebMediaUseCaseImpl(koin, sampleOpenedMedia)
        }
        suite.registerComponent<MediaResolver> {
            mediaResolver
        }
        suite.registerComponent<GetMediaSelectorSourceTiersUseCase> {
            GetMediaSelectorSourceTiersUseCase {
                flowOf(MediaSelectorSourceTiers.Empty)
            }
        }
        suite.registerComponent<GetPreferredWebMediaSourceUseCase> {
            GetPreferredWebMediaSourceUseCase { preferredWebMediaSource }
        }
        suite.registerComponent<SettingsRepository> {
            object : SettingsRepository {
                override val videoResolverSettings: Settings<VideoResolverSettings> = object : Settings<VideoResolverSettings> {
                    override val flow = this@AutoSelectExtensionTest.videoResolverSettings
                    override suspend fun set(value: VideoResolverSettings) {
                        this@AutoSelectExtensionTest.videoResolverSettings.value = value
                    }
                }

                override val danmakuEnabled: Settings<Boolean> by lazy { error("unused in test") }
                override val danmakuConfig: Settings<DanmakuConfig> by lazy { error("unused in test") }
                override val danmakuFilterConfig: Settings<DanmakuFilterConfig> by lazy { error("unused in test") }
                override val mediaSelectorSettings: Settings<MediaSelectorSettings> by lazy { error("unused in test") }
                override val defaultMediaPreference: Settings<MediaPreference> by lazy { error("unused in test") }
                override val profileSettings: Settings<ProfileSettings> by lazy { error("unused in test") }
                override val proxySettings: Settings<ProxySettings> by lazy { error("unused in test") }
                override val mediaCacheSettings: Settings<MediaCacheSettings> by lazy { error("unused in test") }
                override val danmakuSettings: Settings<DanmakuSettings> by lazy { error("unused in test") }
                override val uiSettings: Settings<UISettings> by lazy { error("unused in test") }
                override val themeSettings: Settings<ThemeSettings> by lazy { error("unused in test") }
                override val updateSettings: Settings<UpdateSettings> by lazy { error("unused in test") }
                override val videoScaffoldConfig: Settings<VideoScaffoldConfig> by lazy { error("unused in test") }
                override val anitorrentConfig: Settings<AnitorrentConfig> by lazy { error("unused in test") }
                override val torrentPeerConfig: Settings<TorrentPeerConfig> by lazy { error("unused in test") }
                override val oneshotActionConfig: Settings<OneshotActionConfig> by lazy { error("unused in test") }
                override val analyticsSettings: Settings<AnalyticsSettings> by lazy { error("unused in test") }
                override val debugSettings: Settings<DebugSettings> by lazy { error("unused in test") }
            }
        }

        // set null by default
        preferredWebMediaSource.value = null
        videoResolverSettings.value = VideoResolverSettings.Default
        config(testScope, suite)


        val state = suite.createState(
            listOf(
                AutoSelectExtension,
            ),
        )
        state.onUIReady()
        advanceUntilIdle()
        return Context(testScope, suite, state)
    }

    @Test
    fun `auto select default`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }
        val (testScope, suite, state) = context

        initializeTest(suite)
        startMediaFetcher(state, testScope)

        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1")
        web1.complete(listOf(myMedia))
        advanceUntilIdle() // Performs auto select

        // Check result.
        state.assertSelected(myMedia, suite)

        testScope.cancel()
    }

    @Test
    fun `auto select cached - control group`() = runTest {
        val cached: CompletableDeferred<List<Media>>
        val web1: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            cached = suite.mediaSelectorTestBuilder.delayedMediaSource("cached")
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }
        val (testScope, suite, state) = context

        initializeTest(suite, preference = MediaPreference.Any.copy(alliance = "alliance2"))
        startMediaFetcher(state, testScope)

        val cachedMedia = suite.mediaSelectorTestBuilder.createMedia(
            "cached",
            kind = MediaSourceKind.WEB,
            alliance = "alliance1",
        )
        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1", alliance = "alliance2")
        cached.complete(listOf(cachedMedia))
        web1.complete(listOf(myMedia))
        advanceUntilIdle() // Performs auto select

        // Check result.
        state.assertSelected(myMedia, suite) // "cached" is WEB

        testScope.cancel()
    }

    @Test
    fun `auto select cached - test group`() = runTest {
        val cached: CompletableDeferred<List<Media>>
        val web1: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            cached = suite.mediaSelectorTestBuilder.delayedMediaSource("cached")
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }
        val (testScope, suite, state) = context

        initializeTest(suite, preference = MediaPreference.Any.copy(alliance = "alliance2"))
        startMediaFetcher(state, testScope)

        val cachedMedia = suite.mediaSelectorTestBuilder.createMedia(
            "cached",
            kind = MediaSourceKind.LocalCache,
            alliance = "alliance1",
        )
        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1", alliance = "alliance2")
        cached.complete(listOf(cachedMedia))
        web1.complete(listOf(myMedia))
        advanceUntilIdle() // Performs auto select

        // Check result.
        state.assertSelected(cachedMedia, suite) // "cached" is LocalCache, must be selected

        testScope.cancel()
    }


    @Test
    fun `fast select web - control group`() = runTest {
        val bt1: CompletableDeferred<List<Media>>
        val web1: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            bt1 = suite.mediaSelectorTestBuilder.delayedMediaSource("bt1")
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }
        val (testScope, suite, state) = context

        initializeTest(
            suite,
            mediaSelectorSettings = defaultSettings.copy(
                fastSelectWebKind = true,
                preferKind = MediaSourceKind.BitTorrent,
            ),
        ) // NOTE: settings disabled
        startMediaFetcher(state, testScope)

        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1")
        web1.complete(listOf(myMedia))
        // bt1 does not complete
        advanceUntilIdle() // Performs auto select

        // Check result.
        state.assertSelected(null, suite)

        testScope.cancel()
    }

    @Test
    fun `fast select web - test group`() = runTest {
        val bt1: CompletableDeferred<List<Media>>
        val web1: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            bt1 = suite.mediaSelectorTestBuilder.delayedMediaSource("bt1", kind = MediaSourceKind.BitTorrent)
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
        }
        val (testScope, suite, state) = context

        initializeTest(
            suite,
            mediaSelectorSettings = defaultSettings.copy(fastSelectWebKind = true, preferKind = MediaSourceKind.WEB),
        ) // NOTE: settings ENABLED
        startMediaFetcher(state, testScope)

        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        web1.complete(listOf(myMedia))
        // bt1 does not complete
        advanceUntilIdle() // Performs auto select

        // Check result.
        state.assertSelected(myMedia, suite)

        testScope.cancel()
    }

    @Test
    fun `select preferred web source - control group`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val web2: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
        }
        val (testScope, suite, state) = context

        // NOTE: No preferred source is set (GetPreferredWebMediaSourceUseCase returns null by default)
        initializeTest(suite)
        startMediaFetcher(state, testScope)

        val media1 = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        val media2 = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web1.complete(listOf(media1))
        web2.complete(listOf(media2))
        advanceUntilIdle() // Performs auto select

        // Check result: should fall back to default selection (first available)
        state.assertSelected(media1, suite)

        testScope.cancel()
    }

    @Test
    fun `select preferred web source - test group`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val web2: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
            preferredWebMediaSource.value = "web2" // Set preferred source
        }
        val (testScope, suite, state) = context

        initializeTest(suite)
        startMediaFetcher(state, testScope)

        val media1 = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        val media2 = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web1.complete(listOf(media1))
        web2.complete(listOf(media2))
        advanceUntilIdle() // Performs auto select

        // Check result: should select from the preferred source "web2"
        state.assertSelected(media2, suite)

        testScope.cancel()
    }

    @Test
    fun `fast select web probe prefers faster source over remembered source`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val web2: CompletableDeferred<List<Media>>
        val context = createCase(
            mediaResolver = DelayBySourceMediaResolver(
                mapOf(
                    "web1" to 4_000L,
                    "web2" to 500L,
                ),
            ),
        ) { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
            preferredWebMediaSource.value = "web1"
            mediaSelectorSettings.value = defaultSettings.copy(
                fastSelectWebKind = true,
                preferKind = MediaSourceKind.WEB,
            )
        }
        val (testScope, suite, state) = context

        initializeTest(
            suite,
            mediaSelectorSettings = defaultSettings.copy(
                fastSelectWebKind = true,
                preferKind = MediaSourceKind.WEB,
            ),
        )
        startMediaFetcher(state, testScope)

        val slowerPreferred = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        val fasterCandidate = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web1.complete(listOf(slowerPreferred))
        web2.complete(listOf(fasterCandidate))
        advanceUntilIdle()

        state.assertSelected(fasterCandidate, suite)

        testScope.cancel()
    }

    @Test
    fun `fast select web probe prefers better combined resolve and download score`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val web2: CompletableDeferred<List<Media>>
        val context = createCase(
            mediaResolver = DelayBySourceMediaResolver(
                mapOf(
                    "web1" to 150L,
                    "web2" to 450L,
                ),
            ),
            sampleOpenedMedia = { openedMedia, _ ->
                val uri = (openedMedia as UriMediaData).uri
                when {
                    uri.contains("web1") -> delay(900L)
                    uri.contains("web2") -> delay(100L)
                }
                ZERO
            },
        ) { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
            mediaSelectorSettings.value = defaultSettings.copy(
                fastSelectWebKind = true,
                preferKind = MediaSourceKind.WEB,
            )
        }
        val (testScope, suite, state) = context

        initializeTest(
            suite,
            mediaSelectorSettings = defaultSettings.copy(
                fastSelectWebKind = true,
                preferKind = MediaSourceKind.WEB,
            ),
        )
        startMediaFetcher(state, testScope)

        val fasterResolveButSlowDownload = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        val slowerResolveButBetterOverall = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web1.complete(listOf(fasterResolveButSlowDownload))
        web2.complete(listOf(slowerResolveButBetterOverall))
        advanceUntilIdle()

        state.assertSelected(slowerResolveButBetterOverall, suite)

        testScope.cancel()
    }

    private suspend fun EpisodeFetchSelectPlayState.assertSelected(
        expected: DefaultMedia?,
        suite: EpisodePlayerTestSuite
    ) {
        val mediaSelector = mediaSelectorFlow.first()!!
        assertEquals(expected, mediaSelector.selected.first())
        if (expected == null) {
            assertEquals(null, suite.player.mediaData.first())
        } else {
            assertIs<UriMediaData>(suite.player.mediaData.filterNotNull().first()) // Player is playing
            assertEquals(0, suite.player.currentPositionMillis.value) // State is reset
        }
    }

    private fun startMediaFetcher(
        state: EpisodeFetchSelectPlayState,
        testScope: CoroutineScope
    ) {
        // MediaFetcher is lazy. We perform fetching in testScope (i.e. foreground). `advanceUntilIdle` will wait for the fetching to complete.
        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(testScope)
    }

    private suspend fun TestScope.initializeTest(
        suite: EpisodePlayerTestSuite,
        mediaSelectorSettings: MediaSelectorSettings =
            defaultSettings.copy(preferKind = null),
        preference: MediaPreference = MediaPreference.Any,
    ) {
        this@AutoSelectExtensionTest.mediaSelectorSettings.value = mediaSelectorSettings
        suite.mediaSelectorTestBuilder.savedUserPreference.value = preference

        // Initialize
        advanceUntilIdle()
        assertEquals(null, suite.player.mediaData.first())
    }

    private class DelayBySourceMediaResolver(
        private val delaysMillisBySourceId: Map<String, Long>,
    ) : MediaResolver {
        override fun supports(media: Media): Boolean = true

        override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
            delay(delaysMillisBySourceId[media.mediaSourceId] ?: 0L)
            return TestMediaDataProvider(uri = "https://example.com/${media.mediaSourceId}.m3u8")
        }
    }
}
