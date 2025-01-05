/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.createExceptionCapturingSupervisorScope
import me.him188.ani.app.domain.episode.getCurrentEpisodeId
import me.him188.ani.app.domain.player.ExtensionException
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.utils.coroutines.childScope
import org.openani.mediamp.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SwitchNextEpisodeExtensionTest : AbstractPlayerExtensionTest() {
    private fun EpisodePlayerTestSuite.enableAutoPlayNext() {
        registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoPlayNext = true))
            }
        }
    }

    private fun TestScope.createCase(getNextEpisode: suspend (currentEpisodeId: Int) -> Int?) = run {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.enableAutoPlayNext()

        val state = suite.createState(
            listOf(
                SwitchNextEpisodeExtension.Factory(getNextEpisode = getNextEpisode),
            ),
        )
        state.onUIReady()
        Triple(testScope, suite, state)
    }

    @Test
    fun `does not switch if player does not finish`() = runTest {
        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了
        suite.player.currentPositionMillis.value = suite.player.mediaProperties.value!!.durationMillis
        suite.player.playbackState.value = PlaybackState.PLAYING

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())

        testScope.cancel()
    }

    @Test
    fun `does not switch if position is not close to the end`() = runTest {
        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了
        suite.player.currentPositionMillis.value = suite.player.mediaProperties.value!!.durationMillis - 5001
        suite.player.playbackState.value = PlaybackState.FINISHED

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())

        testScope.cancel()
    }

    @Test
    fun `can switch to next state normally`() = runTest {
        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了
        suite.player.currentPositionMillis.value = suite.player.mediaProperties.value!!.durationMillis
        suite.player.playbackState.value = PlaybackState.FINISHED

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

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了
        suite.player.currentPositionMillis.value = suite.player.mediaProperties.value!!.durationMillis
        suite.player.playbackState.value = PlaybackState.FINISHED

        advanceUntilIdle()

        assertEquals(1000, state.getCurrentEpisodeId())
        assertEquals(1, getNextEpisodeCalled)

        testScope.cancel()
    }

    @Test
    fun `getNextEpisode exception is caught`() = runTest {
        val (scope, backgroundException) = createExceptionCapturingSupervisorScope(this)
        val suite = EpisodePlayerTestSuite(this, scope)
        suite.enableAutoPlayNext()
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

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        // 播到最尾部了
        suite.player.currentPositionMillis.value = suite.player.mediaProperties.value!!.durationMillis
        suite.player.playbackState.value = PlaybackState.FINISHED

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())
        backgroundException.await().run {
            assertIs<ExtensionException>(this)
            assertIs<RepositoryNetworkException>(cause)
        }
        scope.cancel()
    }
}