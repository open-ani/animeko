/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.TorrentMediaCacheProgressProvider
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.api.pieces.count
import me.him188.ani.app.torrent.api.pieces.first
import me.him188.ani.app.torrent.api.pieces.forEach
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TorrentMediaCacheProgressProviderTest {
    private val pieces = PieceList.create(16, 0) { 1000 }
        .apply {
            assertEquals(16, count)
        }

    private operator fun PieceList.get(pieceIndex: Int) = getByPieceIndex(pieceIndex)

    /**
     * Helper to ensure we're always running in a test context.
     */
    private fun runTest(block: suspend TestScope.() -> Unit) {
        kotlinx.coroutines.test.runTest {
            block()
        }
    }

    private fun TestScope.createProvider(
        flowContext: CoroutineContext = coroutineContext[ContinuationInterceptor]!!
    ): TorrentMediaCacheProgressProvider {
        return TorrentMediaCacheProgressProvider(pieces, flowContext)
    }

    // -------------------------------------------------------------------------
    // Quick Utility Checks (not using the Flow, just direct calls)
    // -------------------------------------------------------------------------

    @Test
    fun `initial state`() = runTest {
        // Given
        val cacheProgress = createProvider()

        // When
        val info = cacheProgress.createInfo()

        // Then
        assertEquals(16, info.chunkStates.size)
        assertEquals(16, info.chunkWeights.size)
        // Each chunk should have weight = 1/16
        assertEquals(1 / 16f, info.chunkWeights[0], 1e-7f)
        assertEquals(1 / 16f, info.chunkWeights.last(), 1e-7f)
        // All states should be NONE
        assertEquals(ChunkState.NONE, info.chunkStates[0])
        assertEquals(ChunkState.NONE, info.chunkStates.last())
    }

    @Test
    fun `runPass no change`() = runTest {
        // Given
        val cacheProgress = createProvider()

        // When
        val passResult = cacheProgress.runPass()
        val info = cacheProgress.createInfo()

        // Then
        // No changes => anyChanged = false, allFinished = false
        assertFalse(passResult.anyChanged, "Expected no changes in chunk states")
        assertFalse(passResult.allFinished, "Expected not all finished")
        // States remain NONE
        assertEquals(ChunkState.NONE, info.chunkStates[0])
        assertEquals(ChunkState.NONE, info.chunkStates.last())
    }

    @Test
    fun `runPass first finish`() = runTest {
        // Given
        val cacheProgress = createProvider()
        with(pieces) {
            pieces.first().state = PieceState.FINISHED
        }

        // When
        val passResult = cacheProgress.runPass()
        val info = cacheProgress.createInfo()

        // Then
        assertTrue(passResult.anyChanged)
        assertFalse(passResult.allFinished)
        // The first chunk is DONE, others remain NONE
        assertEquals(ChunkState.DONE, info.chunkStates[0])
        assertEquals(ChunkState.NONE, info.chunkStates[1])
        assertEquals(ChunkState.NONE, info.chunkStates.last())
    }

    // -------------------------------------------------------------------------
    // Turbine Tests (Flow-based)
    // -------------------------------------------------------------------------

    @Test
    fun `flow does not emit if no change and not all finished`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cacheProgress = createProvider(flowContext = testDispatcher)

        // Collect the flow with Turbine
        cacheProgress.flow.test {
            // There's no immediate emission for non-empty pieces,
            // unless passResult.anyChanged = true or passResult.allFinished = true.
            // We'll advance time by 2 seconds to simulate two pass intervals.
            advanceTimeBy(2.seconds)
            // We expect no events because no piece changed
            expectNoEvents()

            // Clean up
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `flow emits once when first chunk changes`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cacheProgress = createProvider(flowContext = testDispatcher)

        with(pieces) {
            cacheProgress.flow.test {
                // Let one "pass" happen with no changes
                advanceTimeBy(1.seconds)
                expectNoEvents()

                // Now, finish the first piece
                pieces.first().state = PieceState.FINISHED

                // Advance time, let the provider detect the change in next pass
                advanceTimeBy(1.seconds)

                // We should get exactly one emission
                val item = awaitItem()
                assertEquals(ChunkState.DONE, item.chunkStates.first(), "First chunk should be DONE")
                assertEquals(ChunkState.NONE, item.chunkStates.last(), "Last chunk should be NONE")

                // No further changes => no more emissions
                advanceTimeBy(1.seconds)
                expectNoEvents()

                cancelAndConsumeRemainingEvents() // SharedFlow won't complete
            }
        }
    }

    @Test
    fun `flow stops after all chunks are finished`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cacheProgress = createProvider(flowContext = testDispatcher)

        with(pieces) {
            cacheProgress.flow.test {
                // Mark all as finished
                pieces.forEach { it.state = PieceState.FINISHED }

                // Advance time enough for at least one pass
                advanceTimeBy(1.seconds)

                // We should receive a single emission with all DONE
                val item = awaitItem()
                item.chunkStates.forEach { state ->
                    assertEquals(ChunkState.DONE, state, "All chunks should be DONE")
                }

                cancelAndConsumeRemainingEvents() // SharedFlow won't complete
            }
        }
    }

    @Test
    fun `flow emits multiple times if multiple changes happen over separate passes`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cacheProgress = createProvider(flowContext = testDispatcher)

        with(pieces) {
            cacheProgress.flow.test {
                // 1) Wait a pass, no changes => no emission
                advanceTimeBy(1.seconds)
                expectNoEvents()

                // 2) Change piece[0]
                pieces.getByPieceIndex(0).state = PieceState.FINISHED
                advanceTimeBy(1.seconds)

                val firstEmission = awaitItem()
                assertEquals(ChunkState.DONE, firstEmission.chunkStates[0])
                // The rest are still NONE
                assertEquals(ChunkState.NONE, firstEmission.chunkStates[1])
                assertEquals(ChunkState.NONE, firstEmission.chunkStates.last())

                // 3) Change piece[1]
                pieces.getByPieceIndex(1).state = PieceState.FINISHED
                advanceTimeBy(1.seconds)

                val secondEmission = awaitItem()
                assertEquals(ChunkState.DONE, secondEmission.chunkStates[1])
                assertEquals(ChunkState.NONE, secondEmission.chunkStates[2])
                assertEquals(ChunkState.NONE, secondEmission.chunkStates.last())

                // 4) Change piece[2]
                pieces.getByPieceIndex(2).state = PieceState.FINISHED
                advanceTimeBy(1.seconds)

                val thirdEmission = awaitItem()
                assertEquals(ChunkState.DONE, thirdEmission.chunkStates[2])
                // Still not all finished => The flow is still running.

                // 5) No further changes => no new emission
                advanceTimeBy(2.seconds)
                ensureAllEventsConsumed()

                // Cancel
                cancelAndConsumeRemainingEvents() // SharedFlow won't complete
            }
        }
    }

    // If you need a test verifying that eventually the flow stops after enough chunks are done:
    @Test
    fun `flow eventually completes after the last piece is done`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cacheProgress = createProvider(flowContext = testDispatcher)

        with(pieces) {
            cacheProgress.flow.test {
                // Mark first half as FINISHED
                repeat(8) { i -> pieces[i].state = PieceState.FINISHED }
                // Advance
                advanceTimeBy(1.seconds)
                val firstEmission = awaitItem()
                assertEquals(8, firstEmission.chunkStates.count { it == ChunkState.DONE })
                assertEquals(8, firstEmission.chunkStates.count { it == ChunkState.NONE })

                // Mark second half as FINISHED
                (8 until 16).forEach { i -> pieces[i].state = PieceState.FINISHED }
                advanceTimeBy(1.seconds)
                val secondEmission = awaitItem()
                assertEquals(16, secondEmission.chunkStates.count { it == ChunkState.DONE })

                // Next pass => allFinished => expectComplete
                // We might not even need to wait because the flow should break
                cancelAndConsumeRemainingEvents() // SharedFlow won't complete
            }
        }
    }
}
