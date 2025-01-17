/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.player.EpisodeHistories
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.player
import me.him188.ani.utils.coroutines.childScope
import org.openani.mediamp.PlaybackState
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RememberPlayProgressExtensionTest : AbstractPlayerExtensionTest() {
    private val repository = EpisodePlayHistoryRepositoryImpl(MemoryDataStore(EpisodeHistories.Empty))
    private fun TestScope.createCase() = run {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<EpisodePlayHistoryRepository> { repository }

        val state = suite.createState(
            listOf(
                RememberPlayProgressExtension,
            ),
        )
        state.onUIReady()
        Triple(testScope, suite, state)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Normal save cases
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `does nothing initially`() = runTest {
        val (testScope) = createCase()
        advanceUntilIdle()

        assertEquals(emptyList(), repository.flow.first())
        testScope.cancel()
    }

    @Test
    fun `saves play progress when pausing player`() = runTest {
        val (testScope, suite, _) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()

        assertEquals(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000), repository.flow.first()[0])

        testScope.cancel()
    }

    @Test
    fun `when closing - saves play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        state.onClose()
        advanceUntilIdle()

        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `when position is -1 - dont save`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = -1
        advanceUntilIdle()

        assertEquals(
            listOf(),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `when position is 0 - dont save`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 0
        advanceUntilIdle()

        assertEquals(
            listOf(),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `when position is -1 - dont remove`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 1000)

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = -1
        advanceUntilIdle()

        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `when position is 0 - dont remove`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 1000)

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 0
        advanceUntilIdle()

        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `when finish at 1 percent - saves play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        advanceUntilIdle()
        suite.player.playbackState.value = PlaybackState.FINISHED
        advanceUntilIdle()

        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `when finish at end - removes play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 100_000 - 1
        advanceUntilIdle()
        suite.player.playbackState.value = PlaybackState.FINISHED
        advanceUntilIdle()

        assertEquals(
            listOf(),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    @Ignore // TODO: We should enable this test
    fun `when stopPlayback at 1 percent - saves play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        advanceUntilIdle()
        state.player.stopPlayback()
        advanceUntilIdle()

        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    @Ignore // TODO: We should enable this test
    fun `when stopPlayback at end - removes play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 100_000 - 1
        advanceUntilIdle()
        state.player.stopPlayback()
        advanceUntilIdle()

        assertEquals(
            listOf(),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `when closing - does not save play progress if duration is zero`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.setMediaDuration(0)
        state.onClose()
        advanceUntilIdle()

        assertEquals(listOf(), repository.flow.first())

        testScope.cancel()
    }

    @Test
    fun `advancing position when paused does not save`() = runTest {
        val (testScope, suite, _) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()

        suite.player.currentPositionMillis.value = 1001
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()

        assertEquals(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000), repository.flow.first()[0])
        assertEquals(1, repository.flow.first().size)

        testScope.cancel()
    }

    @Test
    fun `pausing twice overrides history`() = runTest {
        val (testScope, suite, _) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()

        suite.player.playbackState.value = PlaybackState.PLAYING
        advanceUntilIdle()

        suite.player.currentPositionMillis.value = 1001
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()

        assertEquals(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1001), repository.flow.first()[0])
        assertEquals(1, repository.flow.first().size)

        testScope.cancel()
    }

    @Test
    fun `removes saved when closing`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()
        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        state.onClose()
        advanceUntilIdle()
        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `removes saved when pausing close to the end`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()
        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        suite.player.playbackState.value = PlaybackState.PLAYING
        advanceUntilIdle()

        suite.player.currentPositionMillis.value = 100_000 - 1
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()
        assertEquals(
            listOf(),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `does not removes saved if paused and skip close to the end`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()
        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        // Did not return to PLAYING state. 

        suite.player.seekTo(100_000 - 1)
        advanceUntilIdle()
        assertEquals(
            // current algorithm does not remove the history in this case
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Player error cases
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `player error does not remove history`() = runTest {
        val (testScope, suite, _) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()
        assertEquals(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000), repository.flow.first()[0])

        assertEquals(
            100_000,
            suite.player.mediaProperties.value!!.durationMillis,
        )
        suite.player.playbackState.value = PlaybackState.ERROR
        advanceUntilIdle()
        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `player finished when duration is zero does not remove history`() = runTest {
        val (testScope, suite, _) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()
        assertEquals(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000), repository.flow.first()[0])

        suite.setMediaDuration(0)
        suite.player.playbackState.value = PlaybackState.FINISHED
        advanceUntilIdle()
        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    fun `player finished when duration is -1 does not remove history`() = runTest {
        val (testScope, suite, _) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 1000
        suite.player.playbackState.value = PlaybackState.PAUSED
        advanceUntilIdle()
        assertEquals(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000), repository.flow.first()[0])

        suite.setMediaDuration(-1)
        suite.player.playbackState.value = PlaybackState.FINISHED
        advanceUntilIdle()
        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 1000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Load
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `loads saved history on READY`() = runTest {
        val (testScope, _, _) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)
        testScope.cancel()

        val (testScope2, suite2, _) = createCase()
        advanceUntilIdle()
        suite2.setMediaDuration(100_000)

        assertNotEquals(500, suite2.player.currentPositionMillis.value) // Not yet loaded.
        suite2.player.playbackState.value = PlaybackState.READY
        advanceUntilIdle()
        assertEquals(500, suite2.player.currentPositionMillis.value) // Load when READY

        testScope2.cancel()
    }

    @Test
    fun `loads saved history on switch episode`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = 1000, 500)
        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 100000

        state.switchEpisode(1000)
        advanceUntilIdle()

        assertEquals(0, suite.player.currentPositionMillis.value) // Not yet loaded.

        // Simulate a new video loaded
        suite.setMediaDuration(100_000)
        suite.player.playbackState.value = PlaybackState.READY

        advanceUntilIdle()
        assertEquals(500, suite.player.currentPositionMillis.value) // Load when READY

        testScope.cancel()
    }

    @Test
    fun `remove saved history on switch episode`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)
        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 100000

        state.switchEpisode(1000)
        advanceUntilIdle()

        assertEquals(emptyList(), repository.flow.first())
        testScope.cancel()
    }

    @Test
    fun `remove saved history on switch episode even if player position greater than video duration`() = runTest {
        // https://github.com/open-ani/animeko/issues/1506
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)
        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 100001

        state.switchEpisode(1000)
        advanceUntilIdle()


        assertEquals(emptyList(), repository.flow.first())
        testScope.cancel()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Edge cases when switching episode
    ///////////////////////////////////////////////////////////////////////////


    @Test
    fun `switch episode`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        suite.setMediaDuration(100_000)
        suite.player.currentPositionMillis.value = 3000

        state.switchEpisode(1000)
        advanceUntilIdle()

        // Should not save for new episode 1000

        assertEquals(
            listOf(EpisodeHistory(episodeId = initialEpisodeId, positionMillis = 3000)),
            repository.flow.first(),
        )

        testScope.cancel()
    }
}
