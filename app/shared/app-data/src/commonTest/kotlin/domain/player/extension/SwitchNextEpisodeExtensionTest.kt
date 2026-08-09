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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.createExceptionCapturingSupervisorScope
import me.him188.ani.app.domain.episode.getCurrentEpisodeId
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.resolver.UnsupportedMediaException
import me.him188.ani.app.domain.player.ExtensionException
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.coroutines.childScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SwitchNextEpisodeExtensionTest : AbstractPlayerExtensionTest() {
    private suspend fun TestScope.loadSelectedMedia(
        suite: EpisodePlayerTestSuite,
        state: EpisodeFetchSelectPlayState,
        durationMillis: Long? = 100_000L,
        mediaIndex: Int = 0,
    ) {
        val media = TestMediaList[mediaIndex]
        val source = suite.mediaSelectorTestBuilder.delayedMediaSource("switch-$mediaIndex")
        source.complete(listOf(media))
        suite.setMediaDuration(durationMillis)
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()
    }

    private fun EpisodePlayerTestSuite.enableAutoPlayNext() {
        registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoPlayNext = true))
            }
        }
    }

    private fun TestScope.createCase(
        getNextEpisode: suspend (currentEpisodeId: Int) -> Int?,
        resolver: MediaResolver = TestUniversalMediaResolver,
    ) = run {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.enableAutoPlayNext()
        suite.registerComponent<MediaResolver> { resolver }

        val state = suite.createState(
            listOf(
                SwitchNextEpisodeExtension.Factory(getNextEpisode = getNextEpisode),
            ),
        )
        state.onUIReady()
        advanceUntilIdle()
        Triple(testScope, suite, state)
    }

    @Test
    fun `does not switch if player does not finish`() = runTest {
        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了, 但没有自然结束 (无 MediaEnded 事件)
        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())

        testScope.cancel()
    }

    @Test
    fun `does not switch if position is not close to the end`() = runTest {
        // v2: with a known duration, a natural end always snaps the final position to the
        // duration, so the "far from end" guard can only trigger for unknown durations.
        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        loadSelectedMedia(suite, state, durationMillis = null)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        suite.player.injectPosition(95_000)
        advanceUntilIdle()
        suite.player.injectEnded()

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())

        testScope.cancel()
    }

    @Test
    fun `can switch to next state normally`() = runTest {
        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了
        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
        advanceUntilIdle()
        suite.player.injectEnded()

        advanceUntilIdle()

        assertEquals(1000, state.getCurrentEpisodeId())

        testScope.cancel()
    }

    @Test
    fun `switches only once`() = runTest {
        var getNextEpisodeCalled = 0
        val (testScope, suite, state) =
            createCase(
                getNextEpisode = {
                    getNextEpisodeCalled++
                    1000
                },
            )

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了
        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
        advanceUntilIdle()
        suite.player.injectEnded()

        advanceUntilIdle()

        assertEquals(1000, state.getCurrentEpisodeId())
        assertEquals(1, getNextEpisodeCalled)

        testScope.cancel()
    }

    @Test
    fun `getNextEpisode exception is caught`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (scope, backgroundException) = createExceptionCapturingSupervisorScope(this)
        val suite = EpisodePlayerTestSuite(this, scope)
        suite.enableAutoPlayNext()
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }
        val state = suite.createState(
            listOf(
                SwitchNextEpisodeExtension.Factory(
                    getNextEpisode = {
                        throw RepositoryNetworkException()
                    },
                ),
            ),
        )
        state.onUIReady()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了
        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
        advanceUntilIdle()
        suite.player.injectEnded()

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())
        backgroundException.await().run {
            assertIs<ExtensionException>(this)
            assertIs<RepositoryNetworkException>(cause)
        }
        scope.cancel()
    }

    @Test
    fun `does not switch next episode when playback never started after switching`() = runTest {
        var getNextEpisodeCalled = 0
        var resolveCalls = 0
        // The first resolve (initial episode) succeeds; later resolves (the new episode) fail,
        // so the new episode's media never loads into the player.
        val failingAfterFirstResolver = object : MediaResolver {
            override fun supports(media: Media): Boolean = true
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
                if (resolveCalls++ == 0) return TestUniversalMediaResolver.resolve(media, episode)
                throw UnsupportedMediaException(media)
            }
        }
        val (testScope, suite, state) =
            createCase(
                getNextEpisode = {
                    getNextEpisodeCalled++
                    1000
                },
                resolver = failingAfterFirstResolver,
            )

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())
        assertEquals(0, getNextEpisodeCalled)

        // 播到最尾部了，触发自动切集
        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
        advanceUntilIdle()
        suite.player.injectEnded()
        advanceUntilIdle()

        // Verify switched to next episode (1000)
        assertEquals(1000, state.getCurrentEpisodeId())
        assertEquals(1, getNextEpisodeCalled)

        // Trigger media selection for the new episode; its load fails (the player stays Idle,
        // production keeps mediaProperties/state reset via stopPlayback).
        state.mediaSelectorFlow.filterNotNull().first().select(TestMediaList[0])
        advanceUntilIdle()

        // v2: without a loaded media there is no playback session, so a natural-end signal
        // cannot even be produced — injectEnded is a no-op. The extension must not switch again.
        suite.player.injectEnded()
        advanceUntilIdle()

        // Verify does NOT switch again
        assertEquals(1000, state.getCurrentEpisodeId())
        assertEquals(1, getNextEpisodeCalled)

        testScope.cancel()
    }
}
