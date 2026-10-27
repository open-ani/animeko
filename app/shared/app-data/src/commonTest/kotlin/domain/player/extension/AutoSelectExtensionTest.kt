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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.MediaFetchSelectBundle
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaFetchSessionFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.DroppedFileMedia
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.MediaAutoSelectOutcome
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.PlaybackSourceMemory
import me.him188.ani.app.domain.media.selector.PlaybackAutoSelectionState
import me.him188.ani.app.domain.media.selector.ReplayBrowseMemoryUseCase
import me.him188.ani.app.domain.media.selector.ReplayMissReason
import me.him188.ani.app.domain.media.selector.ReplayResult
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.app.platform.MeteredNetworkDetector
import me.him188.ani.app.platform.NetworkEnvironment
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.inSystem
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlaybackErrorCode
import org.openani.mediamp.PlaybackException
import org.openani.mediamp.source.UriMediaData
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    /**
     * 自动选择被启动的次数. 回放命中后必须为 0: 真实 use case 在已有选择时本来就立即返回, 只看 `selected` 分辨不出扩展有没有跳过它.
     */
    private var autoSelectCalls = 0

    data class Context(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
        val state: EpisodeFetchSelectPlayState,
    )

    private fun TestScope.createCase(
        extensions: List<EpisodePlayerExtensionFactory<*>> = listOf(AutoSelectExtension),
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
        autoSelectCalls = 0
        suite.registerComponent<MediaSelectorAutoSelectUseCase> {
            val impl = MediaSelectorAutoSelectUseCaseImpl(koin)
            object : MediaSelectorAutoSelectUseCase {
                override suspend fun invoke(
                    session: MediaFetchSession,
                    mediaSelector: MediaSelector,
                    playbackMemory: PlaybackSourceMemory.Snapshot,
                ): MediaAutoSelectOutcome {
                    autoSelectCalls++
                    return impl(session, mediaSelector, playbackMemory)
                }
            }
        }
        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }
        suite.registerComponent<GetMediaSelectorSourceTiersUseCase> {
            GetMediaSelectorSourceTiersUseCase {
                flowOf(MediaSelectorSourceTiers.Empty)
            }
        }
        suite.registerComponent<GetPreferredWebMediaSourceUseCase> {
            GetPreferredWebMediaSourceUseCase { preferredWebMediaSource }
        }
        suite.registerComponent<ReplayBrowseMemoryUseCase> {
            ReplayBrowseMemoryUseCase { _, _, _ -> ReplayResult.NotFound(ReplayMissReason.NO_MEMORY) }
        }
        // 与生产的 CreateMediaFetchSelectBundleFlowUseCaseImpl 同形: bundle 由 infoBundleFlow 派生,
        // 因此扩展拿到 bundle 时 infoBundleFlow.replayCache 已有本集信息, 回放才会被调用.
        suite.registerComponent<CreateMediaFetchSelectBundleFlowUseCase> {
            CreateMediaFetchSelectBundleFlowUseCase { infoBundleFlow ->
                val builder = suite.mediaSelectorTestBuilder
                val fetchSession = builder.createMediaFetchSession(builder.createMediaFetcher())
                val bundle = MediaFetchSelectBundle(fetchSession, builder.createMediaSelector(fetchSession))
                infoBundleFlow.map { it?.let { bundle } }.distinctUntilChanged()
            }
        }

        // set null by default
        preferredWebMediaSource.value = null
        config(testScope, suite)


        val state = suite.createState(extensions)
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
    fun `replay hit skips auto select`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val memoryMedia: Media
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
            suite.mediaSelectorTestBuilder.savedUserPreference.value = MediaPreference.Any
            memoryMedia = suite.mediaSelectorTestBuilder.createMedia("web-memory")
            suite.registerComponent<ReplayBrowseMemoryUseCase> {
                ReplayBrowseMemoryUseCase { _, _, mediaSelector ->
                    mediaSelector.select(memoryMedia)
                    ReplayResult.Selected
                }
            }
        }
        val (testScope, suite, state) = context

        // 回放在会话启动时已经完成选择, 不等查询结果
        state.assertSelected(memoryMedia, suite)

        startMediaFetcher(state, testScope)
        web1.complete(listOf(suite.mediaSelectorTestBuilder.createMedia("web1")))
        advanceUntilIdle() // 自动选择没有启动, 查询结果不会覆盖回放的选择

        state.assertSelected(memoryMedia, suite)
        assertEquals(0, autoSelectCalls, "回放命中时不启动自动选择")

        testScope.cancel()
    }

    @Test
    fun `replay miss falls back to auto select`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val replayCalls = mutableListOf<Pair<Int, EpisodeInfo>>()
        var selectedAtReplay: Media? = null
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
            suite.registerComponent<ReplayBrowseMemoryUseCase> {
                ReplayBrowseMemoryUseCase { subjectId, episodeInfo, mediaSelector ->
                    replayCalls += subjectId to episodeInfo
                    selectedAtReplay = mediaSelector.selected.value
                    ReplayResult.NotFound(ReplayMissReason.NO_MEMORY)
                }
            }
        }
        val (testScope, suite, state) = context

        initializeTest(suite)
        startMediaFetcher(state, testScope)

        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1")
        web1.complete(listOf(myMedia))
        advanceUntilIdle() // Performs auto select

        state.assertSelected(myMedia, suite)
        // 回放先于自动选择, 只调用一次, 调用时还没有选择
        val (replayedSubjectId, replayedEpisodeInfo) = replayCalls.single()
        assertEquals(subjectId, replayedSubjectId)
        assertEquals(initialEpisodeId, replayedEpisodeInfo.episodeId)
        assertNull(selectedAtReplay)
        assertEquals(1, autoSelectCalls, "回放未命中才启动自动选择")

        testScope.cancel()
    }

    @Test
    fun `local cache for this episode skips replay`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val cachedMedia: CachedMedia
        var replayCalls = 0
        val context = createCase { _, suite ->
            val cache = suite.mediaSelectorTestBuilder.delayedMediaSource("cache", kind = MediaSourceKind.LocalCache)
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
            suite.mediaSelectorTestBuilder.savedUserPreference.value = MediaPreference.Any
            suite.registerComponent<ReplayBrowseMemoryUseCase> {
                ReplayBrowseMemoryUseCase { _, _, _ ->
                    replayCalls++
                    ReplayResult.NotFound(ReplayMissReason.NO_MEMORY)
                }
            }
            // 本地源是毫秒级完成的, 会话启动前就已有结果; 缓存记录覆盖本集 (夹具的选择器上下文取自查询请求, 即第 1 话).
            val origin = suite.mediaSelectorTestBuilder.createMedia("cache", kind = MediaSourceKind.WEB)
            cachedMedia = CachedMedia(origin, cacheMediaSourceId = "cache", download = origin.download)
            cache.complete(listOf(cachedMedia))
        }
        val (testScope, suite, state) = context

        startMediaFetcher(state, testScope)
        web1.complete(listOf(suite.mediaSelectorTestBuilder.createMedia("web1")))
        advanceUntilIdle() // Performs auto select

        state.assertSelected(cachedMedia, suite) // 本地缓存由自动选择选中
        assertEquals(0, replayCalls, "有本集的本地缓存时不回放")

        testScope.cancel()
    }

    @Test
    fun `exhausted automatic selection is cleared when a manually chosen resource loads`() = runTest {
        val (testScope, suite, state) = createCase { _, suite ->
            suite.mediaSelectorTestBuilder.delayedMediaSource("web", kind = MediaSourceKind.WEB).complete(emptyList())
        }
        initializeTest(suite, defaultSettings.copy(preferKind = MediaSourceKind.WEB))
        val bundle = state.episodeSessionFlow.value.fetchSelectFlow.filterNotNull().first()
        assertIs<PlaybackAutoSelectionState.State.Exhausted>(bundle.autoSelectionState.state.value)

        bundle.mediaSelector.select(suite.mediaSelectorTestBuilder.createMedia("manual", kind = MediaSourceKind.WEB))
        advanceUntilIdle()

        assertSame(PlaybackAutoSelectionState.State.Idle, bundle.autoSelectionState.state.value)
        assertEquals(MediaStatus.Ready, suite.player.state.value.mediaStatus)
        testScope.cancel()
    }

    @Test
    fun `retry failed routes restarts selection after inherited failures exhaust the episode`() = runTest {
        val web: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase { _, suite ->
            web = suite.mediaSelectorTestBuilder.delayedMediaSource("web", kind = MediaSourceKind.WEB)
        }
        initializeTest(suite, defaultSettings.copy(preferKind = MediaSourceKind.WEB))
        val original = suite.mediaSelectorTestBuilder.createMedia("web", kind = MediaSourceKind.WEB)
        val media = original.copy(properties = original.properties.copy(
            webRoute = WebMediaRoute("https://site/subject/1", "line-1", "config"),
        ))
        // 上一集的失败记录先于本集选择器创建，当前集没有发生加载错误。
        state.playbackSourceMemory.recordFailure(
            state.playbackSourceMemory.beginAttempt(initialEpisodeId - 1, media),
            PlaybackSourceMemory.FailureReason.RESOLUTION,
        )
        state.switchEpisode(initialEpisodeId + 1)
        startMediaFetcher(state, testScope)
        web.complete(listOf(media))
        advanceUntilIdle()
        state.assertSelected(null, suite)
        assertIs<PlaybackAutoSelectionState.State.Exhausted>(
            state.episodeSessionFlow.value.fetchSelectFlow.filterNotNull().first().autoSelectionState.state.value,
        )
        val callsBeforeRetry = autoSelectCalls

        state.retryFailedPlaybackSources()
        advanceUntilIdle()

        state.assertSelected(media, suite)
        assertTrue(autoSelectCalls > callsBeforeRetry)
        assertTrue(state.playbackSourceMemory.snapshot().failures.isEmpty())
        testScope.cancel()
    }

    @Test
    fun `exhausted playback retries single episode failures and resources without route identity`() = runTest {
        for (hasRoute in listOf(true, false)) {
            mediaSelectorSettings.value = defaultSettings.copy(preferKind = MediaSourceKind.WEB)
            val media: Media
            val (testScope, suite, state) = createCase(listOf(AutoSelectExtension, SwitchMediaOnPlayerErrorExtension)) { _, suite ->
                suite.mediaSelectorTestBuilder.savedUserPreference.value = MediaPreference.Any
                suite.registerComponent<GetVideoScaffoldConfigUseCase> {
                    GetVideoScaffoldConfigUseCase { flowOf(VideoScaffoldConfig.AllDisabled.copy(autoSwitchMediaOnPlayerError = true)) }
                }
                val original = suite.mediaSelectorTestBuilder.createMedia("web", kind = MediaSourceKind.WEB)
                media = original.copy(properties = original.properties.copy(
                    webRoute = if (hasRoute) WebMediaRoute("https://site/subject/1", "line-1", "config") else null,
                ))
                suite.mediaSelectorTestBuilder.delayedMediaSource("web", kind = MediaSourceKind.WEB).complete(listOf(media))
            }
            val oldSession = state.episodeSessionFlow.value
            val oldMediaData = suite.player.mediaData.value
            suite.player.injectError(PlaybackException(if (hasRoute) PlaybackErrorCode.DECODING else PlaybackErrorCode.IO, "test"))
            advanceUntilIdle()
            // 只有本集资源黑名单，没有可用于显示按钮的跨集失败记录。
            assertTrue(state.playbackSourceMemory.snapshot().failures.isEmpty())
            assertIs<PlaybackAutoSelectionState.State.Exhausted>(
                oldSession.fetchSelectFlow.filterNotNull().first().autoSelectionState.state.value,
            )

            state.retryFailedPlaybackSources()
            advanceUntilIdle()

            assertNotSame(oldSession, state.episodeSessionFlow.value)
            assertNotSame(oldMediaData, suite.player.mediaData.value)
            assertEquals(MediaStatus.Ready, suite.player.state.value.mediaStatus)
            assertEquals(media, state.mediaSelectorFlow.filterNotNull().first().selected.value)
            assertSame(
                PlaybackAutoSelectionState.State.Idle,
                state.episodeSessionFlow.value.fetchSelectFlow.filterNotNull().first().autoSelectionState.state.value,
            )
            testScope.cancel()
        }
    }

    @Test
    fun `network change retries the current failed route and preserves healthy paused playback`() = runTest {
        mediaSelectorSettings.value = defaultSettings.copy(preferKind = MediaSourceKind.WEB)
        val network = MutableStateFlow<NetworkEnvironment?>(NetworkEnvironment("wifi", true))
        val media: Media
        val (testScope, suite, state) = createCase(listOf(AutoSelectExtension, SwitchMediaOnPlayerErrorExtension)) { _, suite ->
            suite.mediaSelectorTestBuilder.savedUserPreference.value = MediaPreference.Any
            suite.configureNetworkRecovery(network, autoSwitch = true)
            val source = suite.mediaSelectorTestBuilder.delayedMediaSource("web", kind = MediaSourceKind.WEB)
            val original = suite.mediaSelectorTestBuilder.createMedia("web", kind = MediaSourceKind.WEB)
            media = original.copy(properties = original.properties.copy(
                webRoute = WebMediaRoute("https://site/subject/1", "line-1", "config"),
            ))
            source.complete(listOf(media))
        }
        state.assertSelected(media, suite)
        val healthySession = state.episodeSessionFlow.value
        val opened = suite.player.mediaData.value
        suite.player.seekTo(42_000)
        suite.player.pause()
        network.value = NetworkEnvironment("mobile", true)
        advanceUntilIdle()
        assertSame(healthySession, state.episodeSessionFlow.value)
        assertSame(opened, suite.player.mediaData.value)
        assertEquals(42_000, suite.player.currentPositionMillis.value)

        // 当前线路已在旧网络失败且候选耗尽，新网络必须允许同一资源重新加载。
        suite.player.injectError(PlaybackException(PlaybackErrorCode.IO, "old network"))
        advanceUntilIdle()
        assertEquals(1, state.playbackSourceMemory.snapshot().failures.size)
        assertIs<PlaybackAutoSelectionState.State.Exhausted>(
            state.episodeSessionFlow.value.fetchSelectFlow.filterNotNull().first().autoSelectionState.state.value,
        )
        network.value = NetworkEnvironment("wifi-2", true)
        advanceUntilIdle()

        assertNotSame(healthySession, state.episodeSessionFlow.value)
        assertEquals(initialEpisodeId, state.episodeSessionFlow.value.episodeId)
        assertEquals(MediaStatus.Ready, suite.player.state.value.mediaStatus)
        assertEquals(media, state.mediaSelectorFlow.filterNotNull().first().selected.value)
        assertTrue(state.playbackSourceMemory.snapshot().failures.isEmpty())

        // 同一个网络短暂断开时的播放器错误也允许重载，断网期间不记录线路失败。
        val beforeDisconnect = state.episodeSessionFlow.value
        network.value = NetworkEnvironment(null, false)
        runCurrent()
        suite.player.injectError(PlaybackException(PlaybackErrorCode.IO, "offline"))
        runCurrent()
        assertTrue(state.playbackSourceMemory.snapshot().failures.isEmpty())
        network.value = NetworkEnvironment("wifi-2", true)
        advanceUntilIdle()
        assertNotSame(beforeDisconnect, state.episodeSessionFlow.value)
        assertEquals(MediaStatus.Ready, suite.player.state.value.mediaStatus)
        assertTrue(state.playbackSourceMemory.snapshot().failures.isEmpty())
        // 新环境里再次发生的真实失败仍需记录，不能因恢复逻辑反复重启当前集。
        val recoveredSession = state.episodeSessionFlow.value
        suite.player.injectError(PlaybackException(PlaybackErrorCode.IO, "new network"))
        advanceUntilIdle()
        assertSame(recoveredSession, state.episodeSessionFlow.value)
        assertEquals(1, state.playbackSourceMemory.snapshot().failures.size)
        testScope.cancel()
    }

    @Test
    fun `network change preserves a playback error when automatic switching is disabled`() = runTest {
        mediaSelectorSettings.value = defaultSettings.copy(preferKind = MediaSourceKind.WEB)
        val network = MutableStateFlow<NetworkEnvironment?>(NetworkEnvironment("wifi", true))
        val (testScope, suite, state) = createCase(listOf(AutoSelectExtension, SwitchMediaOnPlayerErrorExtension)) { _, suite ->
            suite.mediaSelectorTestBuilder.savedUserPreference.value = MediaPreference.Any
            suite.configureNetworkRecovery(network, autoSwitch = false)
            suite.mediaSelectorTestBuilder.delayedMediaSource("web", kind = MediaSourceKind.WEB)
                .complete(listOf(suite.mediaSelectorTestBuilder.createMedia("web", kind = MediaSourceKind.WEB)))
        }
        val session = state.episodeSessionFlow.value
        suite.player.injectError(PlaybackException(PlaybackErrorCode.IO, "old network"))
        advanceUntilIdle()
        network.value = NetworkEnvironment("mobile", true)
        advanceUntilIdle()

        assertSame(session, state.episodeSessionFlow.value)
        assertIs<MediaStatus.Error>(suite.player.state.value.mediaStatus)
        testScope.cancel()
    }

    private fun EpisodePlayerTestSuite.configureNetworkRecovery(
        network: MutableStateFlow<NetworkEnvironment?>,
        autoSwitch: Boolean,
    ) {
        registerComponent<MeteredNetworkDetector> {
            object : MeteredNetworkDetector {
                override val isMeteredNetworkFlow = flowOf(false)
                override val networkEnvironmentFlow = network
                override fun dispose() {}
            }
        }
        registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase { flowOf(VideoScaffoldConfig.AllDisabled.copy(autoSwitchMediaOnPlayerError = autoSwitch)) }
        }
    }

    @Test
    fun `network change preserves an error on a manually dropped file`() = runTest {
        mediaSelectorSettings.value = defaultSettings.copy(preferKind = MediaSourceKind.WEB)
        val network = MutableStateFlow<NetworkEnvironment?>(NetworkEnvironment("wifi", true))
        val (testScope, suite, state) = createCase(listOf(AutoSelectExtension, SwitchMediaOnPlayerErrorExtension)) { _, suite ->
            suite.configureNetworkRecovery(network, autoSwitch = true)
        }
        val dropped = DroppedFileMedia.create(Path("/videos/episode-01.mkv").inSystem)
        state.mediaSelectorFlow.filterNotNull().first().selectTemporarily(dropped)
        advanceUntilIdle()
        val session = state.episodeSessionFlow.value
        suite.player.injectError(PlaybackException(PlaybackErrorCode.IO, "file error"))
        advanceUntilIdle()
        network.value = NetworkEnvironment("mobile", true)
        advanceUntilIdle()

        assertSame(session, state.episodeSessionFlow.value)
        assertEquals(dropped, state.mediaSelectorFlow.filterNotNull().first().selected.value)
        assertIs<MediaStatus.Error>(suite.player.state.value.mediaStatus)
        testScope.cancel()
    }

    private suspend fun EpisodeFetchSelectPlayState.assertSelected(
        expected: Media?,
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
}
