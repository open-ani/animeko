/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.persistent.database.dao.createMemoryPlaybackHistoryDao
import me.him188.ani.app.data.repository.player.EpisodeHistories
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import me.him188.ani.app.data.repository.player.PlaybackHistoryPendingOp
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.episode.player
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.utils.coroutines.childScope
import org.openani.mediamp.PlaybackErrorCode
import org.openani.mediamp.PlaybackException
import org.openani.mediamp.metadata.MediaProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RememberPlayProgressExtensionTest : AbstractPlayerExtensionTest() {
    private val repository = EpisodePlayHistoryRepositoryImpl(
        MemoryDataStore(EpisodeHistories.Empty),
        createMemoryPlaybackHistoryDao(),
        nowMillis = { 0 },
    )

    /**
     * Loads a media through the fetch-select pipeline: [PlayerSession][me.him188.ani.app.domain.episode.PlayerSession]
     * calls `setMediaData(playWhenReady = true)`, so once settled the player is actually playing
     * (unlike v1 where tests had to write `playbackState.value = PLAYING` afterwards).
     *
     * With [advanceUntilSettled] the initial 5s report timer also fires (virtual time advances);
     * pass `false` (media loads within [runCurrent]) when the test must control timers itself.
     */
    @OptIn(UnsafeEpisodeSessionApi::class)
    private suspend fun TestScope.loadSelectedMedia(
        suite: EpisodePlayerTestSuite,
        state: EpisodeFetchSelectPlayState,
        durationMillis: Long? = 100_000L,
        mediaIndex: Int = 0,
        advanceUntilSettled: Boolean = true,
    ) {
        val media = TestMediaList[mediaIndex]
        val source = suite.mediaSelectorTestBuilder.delayedMediaSource("remember-$mediaIndex")
        source.complete(listOf(media))
        suite.setMediaDuration(durationMillis) // configure the properties reported at the open's Ready point
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        if (advanceUntilSettled) {
            advanceUntilIdle()
        } else {
            runCurrent()
        }
    }

    private fun TestScope.createCase(
        periodicReportInterval: Duration = Duration.INFINITE,
    ) = run {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<EpisodePlayHistoryRepository> { repository }
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }

        val rememberPlayProgress = EpisodePlayerExtensionFactory { context, koin ->
            RememberPlayProgressExtension(context, koin, periodicReportInterval)
        }
        val state = suite.createState(
            listOf(
                rememberPlayProgress,
            ),
        )
        state.onUIReady()
        Triple(testScope, suite, state)
    }

    private suspend fun assertSavedHistory(
        positionMillis: Long,
        episodeId: Int = initialEpisodeId,
    ): EpisodeHistory {
        val history = repository.flow.first().single()
        assertEquals(episodeId, history.episodeId)
        assertEquals(positionMillis, history.positionMillis)
        return history
    }

    private suspend fun assertSingleSavedHistoryList(
        positionMillis: Long,
        episodeId: Int = initialEpisodeId,
    ) {
        assertSavedHistory(positionMillis, episodeId)
        assertEquals(1, repository.flow.first().size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Normal save cases
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `reports after playback remains playing for five seconds`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state, advanceUntilSettled = false)
        runCurrent()
        assertEquals(emptyList(), repository.pendingOpsFlow.first())

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(emptyList(), repository.pendingOpsFlow.first())

        advanceTimeBy(1)
        runCurrent()

        assertSingleSavedHistoryList(0)
        assertEquals(
            listOf(0L),
            repository.pendingOpsFlow.first()
                .filterIsInstance<PlaybackHistoryPendingOp.Upsert>()
                .map { it.positionMillis },
        )

        testScope.cancel()
    }

    @Test
    fun `reports once per minute while playing`() = runTest {
        val (testScope, suite, state) = createCase(periodicReportInterval = 1.minutes)
        advanceUntilIdle()

        loadSelectedMedia(suite, state, advanceUntilSettled = false)
        runCurrent()
        assertEquals(emptyList(), repository.pendingOpsFlow.first())

        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(
            listOf(0L),
            repository.pendingOpsFlow.first()
                .filterIsInstance<PlaybackHistoryPendingOp.Upsert>()
                .map { it.positionMillis },
        )

        suite.player.seekTo(2000)
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(1, repository.pendingOpsFlow.first().size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(2000L),
            repository.pendingOpsFlow.first()
                .filterIsInstance<PlaybackHistoryPendingOp.Upsert>()
                .map { it.positionMillis },
        )

        suite.player.pause()
        runCurrent()
        testScope.cancel()
    }

    @Test
    fun `does nothing initially`() = runTest {
        val (testScope) = createCase()
        advanceUntilIdle()

        assertEquals(emptyList(), repository.flow.first())
        testScope.cancel()
    }

    @Test
    fun `saves play progress when pausing player`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()

        val history = assertSavedHistory(1000)
        assertEquals(1, history.subjectId)
        assertEquals(1f, history.episodeSort)
        assertEquals("中文条目名称", history.subjectName)
        assertEquals("", history.subjectImageUrl)
        assertEquals("Nita O'Donnell", history.episodeName)
        assertEquals(100_000, history.durationMillis)

        testScope.cancel()
    }

    @Test
    fun `does not overwrite progress during a transient media replacement`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)
        suite.player.seekTo(42_000)
        suite.player.pause()
        advanceUntilIdle()
        assertSingleSavedHistoryList(42_000)

        val automationGate = suite.koin.get<PlaybackAutomationGate>()
        automationGate.suppressDuring {
            suite.player.play()
            runCurrent()
            suite.player.seekTo(1_000)
            suite.player.pause()
            advanceUntilIdle()
        }
        assertSingleSavedHistoryList(42_000)

        suite.player.play()
        runCurrent()
        suite.player.seekTo(2_000)
        suite.player.pause()
        advanceUntilIdle()
        assertSingleSavedHistoryList(2_000)

        testScope.cancel()
    }

    @Test
    fun `when closing - saves play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        state.onClose()
        advanceUntilIdle()

        val history = assertSavedHistory(1000)
        assertEquals(1, history.subjectId)
        assertEquals(1f, history.episodeSort)
        assertEquals("中文条目名称", history.subjectName)
        assertEquals("", history.subjectImageUrl)
        assertEquals("Nita O'Donnell", history.episodeName)
        assertEquals(100_000, history.durationMillis)
        assertEquals(1, repository.flow.first().size)

        testScope.cancel()
    }

    @Test
    fun `when position is -1 - dont save`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        // v2: a position fact requires a loaded media; load one without advancing timers.
        loadSelectedMedia(suite, state, advanceUntilSettled = false)

        suite.player.injectPosition(-1)
        runCurrent()
        suite.player.pause() // triggers a save evaluation at position -1
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

        loadSelectedMedia(suite, state, advanceUntilSettled = false)

        suite.player.pause() // triggers a save evaluation at position 0 (not allowed for pause saves)
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

        loadSelectedMedia(suite, state, advanceUntilSettled = false)

        suite.player.injectPosition(-1)
        runCurrent()
        suite.player.pause()
        advanceUntilIdle()

        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    @Test
    fun `when position is 0 - dont remove`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 1000)

        loadSelectedMedia(suite, state, advanceUntilSettled = false)

        suite.player.injectPosition(0)
        runCurrent()
        suite.player.pause()
        advanceUntilIdle()

        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    @Test
    fun `when finish at 1 percent - saves play progress`() = runTest {
        // v2: a natural end with a known duration snaps the position to the duration, so
        // "ended at 1%" only exists when the backend learns the real duration after the end.
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state, durationMillis = null, advanceUntilSettled = false)

        suite.player.injectPosition(1000)
        runCurrent()
        suite.player.injectEnded() // ends at 1000 while the duration is still unknown
        suite.setMediaDuration(100_000) // the duration becomes known right after the end
        advanceUntilIdle()

        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    @Test
    fun `when finish at end - removes play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)

        loadSelectedMedia(suite, state)

        suite.player.seekTo(100_000 - 1)
        advanceUntilIdle()
        suite.player.injectEnded()
        advanceUntilIdle()

        assertEquals(
            listOf(),
            repository.flow.first(),
        )

        testScope.cancel()
    }

    @Test
    @Ignore // TODO: This behavior is currently not implemented. We should implement according to the test.
    fun `when stopPlayback at 1 percent - saves play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state, advanceUntilSettled = false)

        suite.player.injectPosition(1000)
        runCurrent()
        state.player.stopPlayback()
        advanceUntilIdle()

        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    @Test
    @Ignore // TODO: This behavior is currently not implemented. We should implement according to the test.
    fun `when stopPlayback at end - removes play progress`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)

        loadSelectedMedia(suite, state, advanceUntilSettled = false)

        suite.player.injectPosition(100_000 - 1)
        runCurrent()
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

        loadSelectedMedia(suite, state, advanceUntilSettled = false)

        suite.player.injectPosition(1000)
        runCurrent()
        suite.setMediaDuration(0) // duration becomes invalid before closing
        runCurrent()
        state.onClose()
        advanceUntilIdle()

        assertEquals(listOf(), repository.flow.first())

        testScope.cancel()
    }

    @Test
    fun `advancing position when paused does not save`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()

        suite.player.seekTo(1001) // still paused: no save evaluation
        advanceUntilIdle()

        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    @Test
    fun `pausing twice overrides history`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()

        suite.player.play()
        advanceUntilIdle()

        suite.player.seekTo(1001)
        suite.player.pause()
        advanceUntilIdle()

        assertSingleSavedHistoryList(1001)

        testScope.cancel()
    }

    @Test
    fun `removes saved when closing`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()
        assertSingleSavedHistoryList(1000)

        state.onClose()
        advanceUntilIdle()
        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    @Test
    fun `removes saved when pausing close to the end`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()
        assertSingleSavedHistoryList(1000)

        suite.player.play()
        advanceUntilIdle()

        suite.player.seekTo(100_000 - 1)
        advanceUntilIdle()
        suite.player.pause()
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

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()
        assertSingleSavedHistoryList(1000)

        // Did not return to playing.

        suite.player.seekTo(100_000 - 1)
        advanceUntilIdle()
        // current algorithm does not remove the history in this case
        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Player error cases
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `player error does not remove history`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()
        assertSavedHistory(1000)

        assertEquals(
            100_000,
            suite.player.mediaProperties.value!!.durationMillis,
        )
        suite.player.injectError(PlaybackException(PlaybackErrorCode.INTERNAL, "test error"))
        advanceUntilIdle()
        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    @Test
    fun `player finished when duration is zero does not remove history`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()
        assertSavedHistory(1000)

        suite.setMediaDuration(0)
        runCurrent()
        suite.player.injectEnded()
        advanceUntilIdle()
        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    @Test
    fun `player finished when duration is unknown does not remove history`() = runTest {
        // v1 used duration = -1; v2 models an unknown duration as null.
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        suite.player.seekTo(1000)
        advanceUntilIdle()
        suite.player.pause()
        advanceUntilIdle()
        assertSavedHistory(1000)

        suite.setMediaDuration(null)
        runCurrent()
        suite.player.injectEnded()
        advanceUntilIdle()
        assertSingleSavedHistoryList(1000)

        testScope.cancel()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Load
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `loads saved history on first PLAYING`() = runTest {
        val (testScope, _, _) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)
        testScope.cancel()

        val (testScope2, suite2, _) = createCase()
        advanceUntilIdle()
        assertNotEquals(500, suite2.player.currentPositionMillis.value) // Not yet loaded
        suite2.player.loadMedia(durationMs = 100_000L, playWhenReady = false, uri = "file://test")
        advanceUntilIdle()
        assertNotEquals(500, suite2.player.currentPositionMillis.value) // Not loaded while Ready (paused)

        suite2.player.play()
        advanceUntilIdle()
        assertEquals(500, suite2.player.currentPositionMillis.value) // Load when actually playing

        testScope2.cancel()
    }

    @Test
    fun `loads saved history when READY is immediately followed by PLAYING`() = runTest {
        val (testScope, suite, _) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)

        assertNotEquals(500, suite.player.currentPositionMillis.value)
        suite.player.loadMedia(durationMs = 100_000L, playWhenReady = true, uri = "file://test")
        advanceUntilIdle()

        assertEquals(500, suite.player.currentPositionMillis.value)
        testScope.cancel()
    }

    @Test
    fun `waits for video properties before loading saved history`() = runTest {
        val (testScope, suite, _) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)

        // Duration unknown at the open's Ready point; the restore must wait for it.
        suite.player.loadMedia(durationMs = null, playWhenReady = true, uri = "file://test")
        runCurrent()

        assertEquals(0, suite.player.currentPositionMillis.value)

        suite.player.injectProperties(MediaProperties(durationMillis = 100_000L))
        advanceUntilIdle()

        assertEquals(500, suite.player.currentPositionMillis.value)
        testScope.cancel()
    }

    @Test
    fun `loads saved history on switch episode`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = 1000, 500)
        loadSelectedMedia(suite, state, mediaIndex = 0)

        suite.player.seekTo(100_000)
        advanceUntilIdle()

        state.switchEpisode(1000)
        advanceUntilIdle()

        assertEquals(0, suite.player.currentPositionMillis.value) // Not yet loaded.

        // Simulate a new video loaded
        loadSelectedMedia(suite, state, mediaIndex = 1)
        assertEquals(500, suite.player.currentPositionMillis.value) // Load when playback resumes

        testScope.cancel()
    }

    @Test
    fun `remove saved history on switch episode`() = runTest {
        val (testScope, suite, state) = createCase()
        advanceUntilIdle()
        repository.saveOrUpdate(episodeId = initialEpisodeId, 500)
        loadSelectedMedia(suite, state)

        suite.player.seekTo(100_000)
        suite.player.injectEnded()
        advanceUntilIdle()

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
        loadSelectedMedia(suite, state)

        // seekTo clamps to the duration in v2; report the out-of-range position as a native fact.
        suite.player.injectPosition(100_001)
        runCurrent()
        suite.player.pause() // save evaluation with position > duration -> removes
        advanceUntilIdle()

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

        loadSelectedMedia(suite, state)

        suite.player.seekTo(3000)
        advanceUntilIdle()

        state.switchEpisode(1000)
        advanceUntilIdle()

        // Should not save for new episode 1000

        assertSingleSavedHistoryList(3000)

        testScope.cancel()
    }
}
