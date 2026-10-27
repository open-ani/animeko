/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeEpisodeSessionApi::class)

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.GetSubjectEpisodeInfoBundleFlowUseCase
import me.him188.ani.app.domain.episode.MediaFetchSelectBundle
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.implicit
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.setSourceTier
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.media.selector.ReplayBrowseMemoryUseCase
import me.him188.ani.app.domain.media.selector.ReplayMissReason
import me.him188.ani.app.domain.media.selector.ReplayResult
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.test.DisabledOnNative
import me.him188.ani.utils.coroutines.childScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 实际加载结果、播放进度与自然结束事件共同驱动三集自动选源，测试不直接写入运行记录。 */
@DisabledOnNative // Native 测试运行器不支持此上下文参数夹具。
class AutoPlayNextPlaybackMemoryTest : AbstractPlayerExtensionTest() {
    @Test
    fun `natural episode endings inherit confirmed success and failures across three episodes`() = runFetchMediaSelectorTestSuite {
        initSubject("Example")
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.savedDefaultPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.AllVisible.copy(
            preferKind = MediaSourceKind.WEB, fastSelectWebKind = true, autoEnableLastSelected = false,
            preferSeasons = false, hideSingleEpisodeForCompleted = false,
        )
        val (_, session, sources) = configureFetchSession(startInBackground = false) {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 1 }
                val webC by web { tier = 2 }
                val webD by web { tier = 3 }
            }
        }
        fun episodes(source: String) = (1..3).map { number ->
            val original = media(
                kind = MediaSourceKind.WEB, subjectName = initApi.subjectName,
                episodeRange = EpisodeRange.single(EpisodeSort(number)),
            )
            original.copy(
                mediaId = "$source-$number",
                properties = original.properties.copy(webRoute = WebMediaRoute("https://site/subject/1", "line-1", "config")),
            )
        }
        sources.webA.complete(episodes("A"))
        sources.webB.complete(episodes("B"))
        sources.webC.complete(episodes("C"))
        sources.webD.complete(episodes("D"))

        val fixture = this
        val testScope = implicit<TestScope>()
        val pageScope = testScope.childScope()
        val suite = EpisodePlayerTestSuite(testScope, pageScope)
        val attempted = mutableListOf<Pair<EpisodeSort, String>>()
        val automaticNext = mutableListOf<Int>()
        val bundles = mutableListOf<MediaFetchSelectBundle>()
        suite.registerComponent<MediaResolver> {
            object : MediaResolver {
                override fun supports(media: Media) = true
                override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
                    attempted += episode.sort to media.mediaSourceId
                    if (media.mediaSourceId in setOf("webA", "webB") ||
                        media.mediaSourceId == "webC" && episode.sort == EpisodeSort(2)
                    ) throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
                    return TestUniversalMediaResolver.resolve(media, episode)
                }
            }
        }
        val originalInfo = suite.koin.get<GetSubjectEpisodeInfoBundleFlowUseCase>()
        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { ids ->
                originalInfo(ids).map { info ->
                    val number = EpisodeSort(info.episodeId - initialEpisodeId + 1)
                    info.copy(episodeCollectionInfo = info.episodeCollectionInfo.copy(
                        episodeInfo = info.episodeInfo.copy(sort = number, ep = number),
                    ))
                }
            }
        }
        suite.registerComponent<CreateMediaFetchSelectBundleFlowUseCase> {
            CreateMediaFetchSelectBundleFlowUseCase { infos ->
                infos.map { info ->
                    info?.let {
                        val selector = DefaultMediaSelector(
                            flowOf(fixture.preferenceApi.mediaSelectorContext.value.copy(episodeInfo = info.episodeInfo)),
                            session.cumulativeResults, fixture.preferenceApi.savedUserPreference,
                            fixture.preferenceApi.savedDefaultPreference, fixture.preferenceApi.mediaSelectorSettings,
                            flowCoroutineContext = StandardTestDispatcher(testScope.testScheduler), enableCaching = false,
                        )
                        MediaFetchSelectBundle(session, selector).also(bundles::add)
                    }
                }
            }
        }
        suite.registerComponent<GetMediaSelectorSettingsFlowUseCase> {
            GetMediaSelectorSettingsFlowUseCase { fixture.preferenceApi.mediaSelectorSettings }
        }
        suite.registerComponent<GetMediaSelectorSourceTiersUseCase> {
            GetMediaSelectorSourceTiersUseCase { flowOf(fixture.preferenceApi.sourceTiers!!) }
        }
        suite.registerComponent<GetPreferredWebMediaSourceUseCase> { GetPreferredWebMediaSourceUseCase { flowOf(null) } }
        suite.registerComponent<ReplayBrowseMemoryUseCase> {
            ReplayBrowseMemoryUseCase { _, _, _, _ -> ReplayResult.NotFound(ReplayMissReason.NO_MEMORY) }
        }
        suite.registerComponent<MediaSelectorAutoSelectUseCase> { MediaSelectorAutoSelectUseCaseImpl(koin) }
        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoPlayNext = true, autoSwitchMediaOnPlayerError = true))
            }
        }
        suite.setMediaDuration(100_000)
        val state = EpisodeFetchSelectPlayState(
            subjectId, initialEpisodeId, suite.player, pageScope,
            listOf(AutoSelectExtension, SwitchMediaOnPlayerErrorExtension, SwitchNextEpisodeExtension.Factory { current ->
                automaticNext += current
                (current + 1).takeIf { it <= initialEpisodeId + 2 }
            }),
            suite.koin, mainDispatcher = EmptyCoroutineContext, playbackTimeSource = testScope.testScheduler.timeSource,
        )
        suspend fun selected() = state.mediaSelectorFlow.filterNotNull().first().selected.value!!
        fun failedSources() = state.playbackSourceMemory.snapshot().failures.keys.map { it.sourceId }.toSet()
        fun confirmProgress() {
            repeat(5) { second ->
                testScope.advanceTimeBy(1_000)
                suite.player.injectPosition((second + 1) * 1_000L)
                testScope.runCurrent()
            }
        }
        try {
            state.onUIReady()
            testScope.advanceUntilIdle()
            assertEquals(listOf(EpisodeSort(1) to "webA", EpisodeSort(1) to "webB", EpisodeSort(1) to "webC"), attempted)
            assertEquals(setOf("webA", "webB"), failedSources())
            assertNull(state.playbackSourceMemory.snapshot().recentSuccess)
            confirmProgress()
            assertTrue(state.playbackSourceMemory.snapshot().isRecentSuccess(selected()))

            // D 的静态优先级高于 C 时，下一集仍应先尝试实际成功过的 C。
            setSourceTier("webD", 0)
            suite.player.injectEnded()
            testScope.advanceUntilIdle()
            assertEquals(initialEpisodeId + 1, state.episodeSessionFlow.value.episodeId)
            assertEquals(listOf(EpisodeSort(2) to "webC", EpisodeSort(2) to "webD"), attempted.drop(3))
            assertEquals(setOf("webA", "webB", "webC"), failedSources())
            assertNull(state.playbackSourceMemory.snapshot().recentSuccess)
            confirmProgress()
            assertTrue(state.playbackSourceMemory.snapshot().isRecentSuccess(selected()))

            suite.player.injectEnded()
            testScope.advanceUntilIdle()
            assertEquals(initialEpisodeId + 2, state.episodeSessionFlow.value.episodeId)
            assertEquals(listOf(EpisodeSort(3) to "webD"), attempted.drop(5))
            assertEquals("webD", selected().mediaSourceId)
            assertTrue(state.playbackSourceMemory.snapshot().isRecentSuccess(selected()))
            assertEquals(setOf("webA", "webB", "webC"), failedSources())
            assertEquals(listOf(initialEpisodeId, initialEpisodeId + 1), automaticNext)
            assertEquals(3, bundles.size)
            bundles.forEach { assertSame(session, it.mediaFetchSession) }
            assertEquals(listOf(1, 1, 1, 1), listOf(sources.webA, sources.webB, sources.webC, sources.webD).map { it.fetchCount })
            assertEquals(MediaPreference.Any, preferenceApi.savedUserPreference.value)
            state.onClose()
            assertNull(state.playbackSourceMemory.snapshot().recentSuccess)
            assertTrue(state.playbackSourceMemory.snapshot().failures.isEmpty())
        } finally {
            pageScope.cancel()
        }
    }
}
