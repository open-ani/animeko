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
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.MediaFetchSelectBundle
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaFetchSessionFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.ReplayBrowseMemoryUseCase
import me.him188.ani.app.domain.media.selector.ReplayResult
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.childScope
import org.openani.mediamp.source.UriMediaData
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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
                override suspend fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector) {
                    autoSelectCalls++
                    impl(session, mediaSelector)
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
            ReplayBrowseMemoryUseCase { _, _, _ -> ReplayResult.NOT_FOUND }
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
                    ReplayResult.SELECTED_BY_SORT
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
                    ReplayResult.NOT_FOUND
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
                    ReplayResult.NOT_FOUND
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
