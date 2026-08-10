/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.GetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.childScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MarkAsWatchedExtensionTest : AbstractPlayerExtensionTest() {
    private val extensionFactory = EpisodePlayerExtensionFactory { context, koin ->
        MarkAsWatchedExtension(context, koin, enableSamplingAndDebounce = false)
    }

    /**
     * Creates a test environment with the [MarkAsWatchedExtension].
     *
     * @param videoScaffoldConfigFlow How the mocked [GetVideoScaffoldConfigUseCase] emits config values.
     * @param getEpisodeCollectionType A function controlling the mocked [GetEpisodeCollectionTypeUseCase].
     * @param setEpisodeCollectionType A function controlling the mocked [SetEpisodeCollectionTypeUseCase].
     */
    private fun TestScope.createCase(
        videoScaffoldConfigFlow: Flow<VideoScaffoldConfig> = flowOf(
            VideoScaffoldConfig.AllDisabled.copy(autoMarkDone = true),
        ),
        getEpisodeCollectionType: GetEpisodeCollectionTypeUseCase = GetEpisodeCollectionTypeUseCase { _, _, _ -> null },
        setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase = SetEpisodeCollectionTypeUseCase { _, _, _ -> },
    ): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = createSuite(testScope)

        // Register mocked or stubbed components.
        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                videoScaffoldConfigFlow
            }
        }
        suite.registerComponent<GetEpisodeCollectionTypeUseCase> {
            getEpisodeCollectionType
        }
        suite.registerComponent<SetEpisodeCollectionTypeUseCase> {
            setEpisodeCollectionType
        }

        val state = suite.createState(
            extensions = listOf(extensionFactory),
        )
        state.onUIReady()
        return Triple(testScope, suite, state)
    }

    @Test
    fun `does not mark if autoMarkDone is false`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            videoScaffoldConfigFlow = flowOf(
                VideoScaffoldConfig.AllDisabled.copy(autoMarkDone = false),
            ),
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        // Suppose the total duration is 10 seconds for easy math; playback at 95% (well past 90%).
        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)

        advanceUntilIdle()

        // Because autoMarkDone is false, we expect no marking action.
        assertFalse(setCalled)

        testScope.cancel()
    }

    @Test
    fun `does mark if not playing`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        // Current position is 95% but the media is loaded paused (not playing).
        suite.player.loadMedia(durationMs = 10000L, playWhenReady = false)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)
        advanceUntilIdle()
        assertFalse(setCalled) // paused: strict isPlaying gate does not mark

        suite.player.play()

        advanceUntilIdle()

        assertTrue(setCalled) // TODO: 2025/3/25 This case may be wrong. We should reconsider.

        testScope.cancel()
    }

    @Test
    fun `does not mark if already DONE or DROPPED`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> UnifiedCollectionType.DONE },
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        // Even though we are at 95% and playing, if it's already DONE, we don't mark it again.
        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)

        advanceUntilIdle()

        assertFalse(setCalled)

        testScope.cancel()
    }

    @Test
    fun `marks as watched when playing and above 90 percent`() = runTest {
        var requestedSubjectId: Int? = null
        var requestedEpisodeId: Int? = null
        var requestedType: UnifiedCollectionType? = null

        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null }, // Not already marked
            setEpisodeCollectionType = { subjectId, episodeId, type ->
                requestedSubjectId = subjectId
                requestedEpisodeId = episodeId
                requestedType = type
            },
        )

        // Set up 10-second media playing, move position to 95%.
        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)

        advanceUntilIdle()

        assertEquals(subjectId, requestedSubjectId)
        assertEquals(initialEpisodeId, requestedEpisodeId)
        assertEquals(UnifiedCollectionType.DONE, requestedType)

        testScope.cancel()
    }

    @Test
    fun `marks only once for the same episode`() = runTest {
        var callCount = 0
        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null },
            setEpisodeCollectionType = { _, _, _ ->
                callCount++
            },
        )

        // Move near 90% while playing -> triggers mark once.
        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)

        advanceUntilIdle()
        assertEquals(1, callCount)

        // Advance the position a bit, remain playing, still near the 90% mark.
        // The extension uses `cancelScope()` once it marks, so no double marking.
        suite.player.injectPosition(9999L)
        advanceUntilIdle()
        // Still 1
        assertEquals(1, callCount)

        testScope.cancel()
    }

    @Test
    fun `marks as watched for 3min`() = runTest {
        var requestedSubjectId: Int? = null
        var requestedEpisodeId: Int? = null
        var requestedType: UnifiedCollectionType? = null

        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null }, // Not already marked
            setEpisodeCollectionType = { subjectId, episodeId, type ->
                requestedSubjectId = subjectId
                requestedEpisodeId = episodeId
                requestedType = type
            },
        )

        suite.player.loadMedia(durationMs = 3.minutes.inWholeMilliseconds, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition((3.minutes - 100.seconds).inWholeMilliseconds)

        advanceUntilIdle()

        // Should mark as watched because we're within the last 100 seconds
        assertEquals(subjectId, requestedSubjectId)
        assertEquals(initialEpisodeId, requestedEpisodeId)
        assertEquals(UnifiedCollectionType.DONE, requestedType)

        testScope.cancel()
    }

    @Test
    fun `marks as watched when within last 100 seconds`() = runTest {
        var requestedSubjectId: Int? = null
        var requestedEpisodeId: Int? = null
        var requestedType: UnifiedCollectionType? = null

        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null }, // Not already marked
            setEpisodeCollectionType = { subjectId, episodeId, type ->
                requestedSubjectId = subjectId
                requestedEpisodeId = episodeId
                requestedType = type
            },
        )

        suite.player.loadMedia(durationMs = 1_200_000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition((18.minutes + 21.seconds).inWholeMilliseconds)

        advanceUntilIdle()

        // Should mark as watched because we're within the last 100 seconds
        assertEquals(subjectId, requestedSubjectId)
        assertEquals(initialEpisodeId, requestedEpisodeId)
        assertEquals(UnifiedCollectionType.DONE, requestedType)

        testScope.cancel()
    }

    @Test
    fun `does not mark when neither at 90 percent nor within last 100 seconds`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null }, // Not already marked
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        // Set up a 20-minute video (1,200,000 ms)
        // Position at 80% (960,000 ms) which is less than 90% and more than 100 seconds from the end
        suite.player.loadMedia(durationMs = 1_200_000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(960_000L) // 80% of video, more than 100 seconds from end

        advanceUntilIdle()

        // Should not mark as watched because we're neither at 90% nor within the last 100 seconds
        assertFalse(setCalled)

        testScope.cancel()
    }

    @Test
    fun `does not mark when video is shorter than 10 seconds`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null }, // Not already marked
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        // Set up a video shorter than 10 seconds (9 seconds)
        suite.player.loadMedia(durationMs = 9.seconds.inWholeMilliseconds, playWhenReady = true)
        advanceUntilIdle()

        // Position at 95% which would normally trigger marking
        suite.player.injectPosition((9.seconds.inWholeMilliseconds * 0.95).toLong())

        advanceUntilIdle()

        // Should not mark as watched because the video is shorter than 10 seconds
        assertFalse(setCalled)

        testScope.cancel()
    }

    // TODO: 2025/1/5 This test sometimes fails on desktopMain.
//    @Test
//    fun `handles exceptions from setEpisodeCollectionTypeUseCase`(): TestResult = runTest {
//        val (scope, backgroundException) = createExceptionCapturingSupervisorScope(this)
//        val suite = createSuite(scope)
//
//        // Register mock components. We'll throw an exception from setEpisodeCollectionTypeUseCase
//        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
//            GetVideoScaffoldConfigUseCase {
//                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoMarkDone = true))
//            }
//        }
//        suite.registerComponent<GetEpisodeCollectionTypeUseCase> {
//            GetEpisodeCollectionTypeUseCase { _, _ ->
//                null // Not already marked
//            }
//        }
//        suite.registerComponent<SetEpisodeCollectionTypeUseCase> {
//            SetEpisodeCollectionTypeUseCase { _, _, _ -> throw RepositoryNetworkException("Simulated network error") }
//        }
//
//        val state = suite.createState(
//            listOf(
//                extensionFactory,
//            ),
//        )
//        state.onUIReady()
//
//        // Move near 90% while playing -> triggers mark once, but we'll throw from setEpisodeCollectionTypeUseCase
//        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
//        suite.player.injectPosition(9500L)
//        advanceUntilIdle()
//
//        // The exception thrown in setEpisodeCollectionTypeUseCase should propagate as ExtensionException
//        backgroundException.await().let { ex ->
//            assertIs<ExtensionException>(ex)
//            assertIs<RepositoryNetworkException>(ex.cause)
//        }
//
//        scope.cancel()
//    }

    private fun TestScope.createSuite(scope: CoroutineScope): EpisodePlayerTestSuite {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return EpisodePlayerTestSuite(this, scope)
    }
}
