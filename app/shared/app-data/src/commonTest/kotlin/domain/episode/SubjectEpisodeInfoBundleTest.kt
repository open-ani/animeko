/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.utils.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class SubjectEpisodeInfoBundleTest {
    private val subjectId = 1

    private fun EpisodePlayerTestSuite.createState(episodeIdFlow: Flow<Int>): SubjectEpisodeInfoBundleLoader {
        return SubjectEpisodeInfoBundleLoader(subjectId, episodeIdFlow, koin)
    }

    private fun TestScope.createSuite(
        backgroundScopeForState: CoroutineScope = this.backgroundScope,
    ) = EpisodePlayerTestSuite(this, backgroundScopeForState)

    @Test
    fun `infoLoadErrorState initially null`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = createSuite()
        val state = suite.createState(episodeIdFlow)
        assertEquals(null, state.infoLoadErrorState.value)
    }

    ///////////////////////////////////////////////////////////////////////////
    // infoBundleFlow
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `infoBundleFlow emits null first`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = createSuite()
        val state = suite.createState(episodeIdFlow)
        assertEquals(null, state.infoBundleFlowFlatten.first()) // refreshes UI
    }

    @Test
    fun `infoBundleFlow load success`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = createSuite()
        val state = suite.createState(episodeIdFlow)
        assertNotEquals(null, state.infoBundleFlowFlatten.drop(1).first())
        assertEquals(null, state.infoLoadErrorState.value)
    }

    @Test
    fun `infoBundleFlow completes when episodeId completes`() = runTest {
        val episodeIdFlow = flowOf(2)
        val suite = createSuite()
        val state = suite.createState(episodeIdFlow)
        state.infoBundleFlowFlatten.drop(1).toList().run {
            assertEquals(1, size)
            assertNotEquals(null, first())
        }
    }

    @Test
    fun `infoBundleFlow load failure is captured in the background amd exposed via infoLoadErrorState`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val (scope, backgroundException) = createExceptionCapturingSupervisorScope()
        val suite = createSuite(scope)
        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                idsFlow.map {
                    throw RepositoryNetworkException()
                }
            }
        }
        val state = suite.createState(episodeIdFlow)
        val job = state.infoBundleFlowFlatten.drop(1).launchIn(scope) // will hang forever
        assertEquals(LoadError.NetworkError, state.infoLoadErrorState.onEach { println(it) }.filterNotNull().first())
        job.cancel()
        scope.cancel()
        assertIs<RepositoryNetworkException>(backgroundException.await(), "should be a network error")
    }

    @Test
    fun `infoBundleFlow does NOT update infoLoadErrorState on CancellationException`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val (scope, backgroundException) = createExceptionCapturingSupervisorScope()
        val suite = createSuite(scope)

        // Override GetSubjectEpisodeInfoBundleFlowUseCase to throw CancellationException
        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                idsFlow.map {
                    throw CancellationException("Simulated cancellation")
                }
            }
        }

        val state = suite.createState(episodeIdFlow)
        val job = state.infoBundleFlowFlatten.drop(1).launchIn(scope) // Start collecting

        // infoLoadErrorState should remain null because we explicitly skip CancellationException
        // in 'onCompletion'.
        val firstError = state.infoLoadErrorState.first()
        // We expect that the flow completes (or is canceled) without setting a LoadError
        // Because .onCompletion() checks for `e != null && e !is CancellationException`
        // => a CancellationException should not set state.infoLoadErrorState.
        assertEquals(null, firstError, "infoLoadErrorState should remain null on CancellationException")

        // Cancel everything
        job.cancel()
        scope.cancel()

        // Not asserting this, because CancellationException will just be ignored by the SupervisorJob.
//        assertIs<CancellationException>(backgroundException.await())
    }

    @Test
    fun `infoBundleFlow collector cancellation does NOT set infoLoadErrorState`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = createSuite()

        // Provide normal successful flow so that no error is thrown
        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                // Normally return a valid bundle
                idsFlow.map {
                    // Simplified: just build a fake bundle
                    createTestSubjectEpisodeInfoBundle(
                        subjectId = it.subjectId,
                        episodeId = it.episodeId,
                    )
                }
            }
        }

        val state = suite.createState(episodeIdFlow)

        // Collect in a child job
        val collectorJob = state.infoBundleFlowFlatten.drop(1).onEach {
            // Once we get the first success, we manually cancel
            this.cancel()
        }.launchIn(backgroundScope)

        // Wait briefly to ensure the collection started
        // (You can also explicitly wait for the drop(1).first() if you want.)
        advanceUntilIdle()

        // Once canceled by the collector, there should be no new error state
        assertEquals(
            null, state.infoLoadErrorState.value,
            "Collector cancellation should not produce a LoadError",
        )

        collectorJob.cancel() // Just ensure it's fully canceled
    }

}