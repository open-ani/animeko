/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeEpisodeSessionApi::class)

package me.him188.ani.app.domain.media.selector

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.persistent.createTestPreferencesDataStore
import me.him188.ani.app.data.persistent.database.dao.createMemoryPreferredWebMediaSourceDao
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepositoryImpl
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.MediaFetchSelectBundle
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaFetchSessionFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import me.him188.ani.app.domain.player.extension.AbstractPlayerExtensionTest
import me.him188.ani.app.domain.player.extension.SaveMediaPreferenceExtension
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.coroutines.childScope
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaPreferenceCrossSessionE2eTest : AbstractPlayerExtensionTest() {
    private val globalDefault = MutableStateFlow(MediaSelectorTestSuite.DEFAULT_PREFERENCE)

    private fun createRepository(store: DataStore<Preferences>): EpisodePreferencesRepositoryImpl {
        return EpisodePreferencesRepositoryImpl(
            store = store,
            preferredWebMediaSourceDao = createMemoryPreferredWebMediaSourceDao(),
            defaultMediaPreference = globalDefault,
        )
    }

    private fun TestScope.createSessionSuite(
        repository: EpisodePreferencesRepository,
    ): Pair<CoroutineScope, EpisodePlayerTestSuite> {
        val sessionScope = childScope()
        val suite = EpisodePlayerTestSuite(this, sessionScope)
        val testDispatcher = coroutineContext[ContinuationInterceptor]!!
        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }
        suite.registerComponent<MediaSelectorEventSavePreferenceUseCase> {
            MediaSelectorEventSavePreferenceUseCase { mediaSelector, subjectId ->
                mediaSelector.eventHandling.savePreferenceOnSelect { repository.setMediaPreference(subjectId, it) }
            }
        }
        suite.registerComponent<CreateMediaFetchSelectBundleFlowUseCase> {
            CreateMediaFetchSelectBundleFlowUseCase { infoBundleFlow ->
                infoBundleFlow.filterNotNull().distinctUntilChanged().map { bundle ->
                    val fetchSession = suite.mediaSelectorTestBuilder.createMediaFetchSession(
                        suite.mediaSelectorTestBuilder.createMediaFetcher(),
                    )
                    MediaFetchSelectBundle(
                        fetchSession,
                        DefaultMediaSelector(
                            mediaSelectorContextNotCached = fetchSession.request.map { request ->
                                MediaSelectorTestSuite.createMediaSelectorContextFromEmpty(
                                    subjectInfo = SubjectInfo.Empty.copy(
                                        subjectId = request.subjectId.toInt(),
                                        name = request.subjectNames.firstOrNull() ?: "",
                                        nameCn = request.subjectNameCN ?: "",
                                    ),
                                    episodeInfo = EpisodeInfo.Empty.copy(
                                        episodeId = request.episodeId.toInt(),
                                        name = request.episodeName,
                                        sort = request.episodeSort,
                                        ep = request.episodeEp,
                                    ),
                                )
                            },
                            mediaListNotCached = fetchSession.cumulativeResults,
                            savedUserPreference = repository.mediaPreferenceFlow(bundle.subjectId),
                            savedDefaultPreference = globalDefault,
                            mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
                            flowCoroutineContext = testDispatcher,
                            enableCaching = false,
                        ),
                    )
                }
            }
        }
        return sessionScope to suite
    }

    private fun TestScope.startSessionState(suite: EpisodePlayerTestSuite): EpisodeFetchSelectPlayState {
        val state = suite.createState(listOf(SaveMediaPreferenceExtension))
        state.onUIReady()
        advanceUntilIdle()
        return state
    }

    private fun createWebMedia(
        mediaSourceId: String,
        alliance: String,
        resolution: String,
        subtitleLanguageId: String,
    ): DefaultMedia = createTestDefaultMedia(
        mediaId = "$mediaSourceId.1",
        mediaSourceId = mediaSourceId,
        originalTitle = "[$alliance] 孤独摇滚 01",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 1,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(subtitleLanguageId),
            resolution = resolution,
            alliance = alliance,
            size = 122.megaBytes,
        ),
        kind = MediaSourceKind.WEB,
        location = MediaSourceLocation.Online,
    )

    @Test
    fun `A11 端到端 会话A经SaveMediaPreferenceExtension落库 会话B重建后存档驱动偏好过滤与默认选择`() = runTest {
        val store = createTestPreferencesDataStore()
        val target = createWebMedia("web1", "桜都字幕组", "720P", "CHT")
        val competitor = createWebMedia("web2", "北宇治字幕组", "1080P", "CHS")

        val repositoryA = createRepository(store)
        val (scopeA, suiteA) = createSessionSuite(repositoryA)
        suiteA.mediaSelectorTestBuilder.delayedMediaSource("web1").complete(listOf(target))
        suiteA.mediaSelectorTestBuilder.delayedMediaSource("web2").complete(listOf(competitor))
        val stateA = startSessionState(suiteA)

        val selectorA = stateA.mediaSelectorFlow.filterNotNull().first()
        assertTrue(selectorA.select(target))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()

        assertEquals(
            MediaSelectorTestSuite.DEFAULT_PREFERENCE.copy(
                alliance = "桜都字幕组",
                resolution = "720P",
                subtitleLanguageId = "CHT",
                mediaSourceId = "web1",
            ),
            repositoryA.mediaPreferenceFlow(subjectId).first(),
        )
        scopeA.cancel()

        val repositoryB = createRepository(store)
        val (scopeB, suiteB) = createSessionSuite(repositoryB)
        suiteB.mediaSelectorTestBuilder.delayedMediaSource("web1").complete(listOf(target))
        suiteB.mediaSelectorTestBuilder.delayedMediaSource("web2").complete(listOf(competitor))
        val stateB = startSessionState(suiteB)
        stateB.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(scopeB)
        advanceUntilIdle()

        val selectorB = stateB.mediaSelectorFlow.filterNotNull().first()
        assertEquals(setOf(target, competitor), selectorB.filteredCandidatesMedia.first().toSet())
        assertEquals(listOf(target), selectorB.preferredCandidatesMedia.first())
        assertEquals("web1", selectorB.mediaSourceId.finalSelected.first())
        assertEquals(target, selectorB.trySelectDefault())
        scopeB.cancel()
    }
}
