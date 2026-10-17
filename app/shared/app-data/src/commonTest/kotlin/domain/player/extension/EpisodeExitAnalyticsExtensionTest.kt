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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.EpisodeExit
import me.him188.ani.utils.coroutines.childScope
import org.openani.mediamp.PlaybackErrorCode
import org.openani.mediamp.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpisodeExitAnalyticsExtensionTest : AbstractPlayerExtensionTest() {
    private val analytics = RecordingAnalytics()

    private class Case(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
        val state: EpisodeFetchSelectPlayState,
    )

    private fun TestScope.createCase(): Case {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, scope)
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }
        val state = suite.createState(
            listOf(
                EpisodePlayerExtensionFactory { context, _ ->
                    EpisodeExitAnalyticsExtension(context, analytics, testScheduler.timeSource)
                },
            ),
        )
        state.onUIReady()
        runCurrent()
        return Case(scope, suite, state)
    }

    /**
     * 选择一个资源, 播放器立即打开并开始播放 (未设置 [org.openani.mediamp.test.TestMediampPlayer.openInitiallyStalled] 时).
     */
    private suspend fun TestScope.select(
        case: Case,
        mediaSourceId: String,
        kind: MediaSourceKind = MediaSourceKind.WEB,
    ) {
        val media = case.suite.mediaSelectorTestBuilder.createMedia(mediaSourceId, kind)
        case.state.mediaSelectorFlow.filterNotNull().first().select(media)
        runCurrent()
    }

    private fun exitEvents() = analytics.propertiesOf(EpisodeExit)

    @Test
    fun `records playback stats on close`() = runTest {
        val case = createCase()
        val player = case.suite.player

        advanceTimeBy(1_000)
        select(case, "a")
        assertTrue(player.state.value.isPlaying)

        advanceTimeBy(10_000)
        player.injectStall(true)
        runCurrent()
        advanceTimeBy(2_000)
        player.injectStall(false)
        player.injectPosition(12_000)
        runCurrent()
        advanceTimeBy(3_000)

        case.state.onClose()

        assertEquals(
            mapOf(
                "subject_id" to subjectId.toLong(),
                "episode_id" to initialEpisodeId.toLong(),
                "exit_reason" to "close",
                "reached_playing" to 1L,
                "first_frame_ms" to 1_000L,
                "watched_ms" to 13_000L,
                "position_ms" to 12_000L,
                "duration_ms" to 100_000L,
                "buffering_count" to 1L,
                "buffering_ms" to 2_000L,
                "had_error" to 0L,
                "media_source_id" to "a",
                "is_cached" to 0L,
                "source_switch_count" to 0L,
            ),
            exitEvents().single(),
        )
        case.scope.cancel()
    }

    @Test
    fun `records leaving before playback`() = runTest {
        val case = createCase()

        advanceTimeBy(5_000)
        case.state.onClose()

        assertEquals(
            mapOf(
                "subject_id" to subjectId.toLong(),
                "episode_id" to initialEpisodeId.toLong(),
                "exit_reason" to "close",
                "reached_playing" to 0L,
                "watched_ms" to 0L,
                "buffering_count" to 0L,
                "buffering_ms" to 0L,
                "had_error" to 0L,
                "source_switch_count" to 0L,
            ),
            exitEvents().single(),
        )
        case.scope.cancel()
    }

    @Test
    fun `loading before each media starts is not buffering`() = runTest {
        val case = createCase()
        val player = case.suite.player
        player.openInitiallyStalled = true

        select(case, "a")
        advanceTimeBy(3_000)
        player.injectStall(false)
        runCurrent()
        advanceTimeBy(1_000)

        // 换源, 新资源打开后同样先加载
        select(case, "b", kind = MediaSourceKind.LocalCache)
        advanceTimeBy(2_000)
        player.injectStall(false)
        runCurrent()
        advanceTimeBy(1_000)

        case.state.onClose()

        val event = exitEvents().single()
        assertEquals(3_000L, event["first_frame_ms"])
        assertEquals(2_000L, event["watched_ms"])
        assertEquals(0L, event["buffering_count"])
        assertEquals(0L, event["buffering_ms"])
        assertEquals("b", event["media_source_id"])
        assertEquals(1L, event["is_cached"])
        assertEquals(1L, event["source_switch_count"])
        case.scope.cancel()
    }

    @Test
    fun `buffering while paused is not counted`() = runTest {
        val case = createCase()
        val player = case.suite.player

        select(case, "a")
        advanceTimeBy(1_000)
        player.pause()
        player.injectStall(true)
        runCurrent()
        advanceTimeBy(1_000)

        case.state.onClose()

        val event = exitEvents().single()
        assertEquals(1_000L, event["watched_ms"])
        assertEquals(0L, event["buffering_count"])
        case.scope.cancel()
    }

    @Test
    fun `ongoing buffering is counted until exit`() = runTest {
        val case = createCase()
        val player = case.suite.player

        select(case, "a")
        advanceTimeBy(1_000)
        player.injectStall(true)
        runCurrent()
        advanceTimeBy(4_000)

        case.state.onClose()

        val event = exitEvents().single()
        assertEquals(1_000L, event["watched_ms"])
        assertEquals(1L, event["buffering_count"])
        assertEquals(4_000L, event["buffering_ms"])
        case.scope.cancel()
    }

    @Test
    fun `records player error`() = runTest {
        val case = createCase()

        select(case, "a")
        case.suite.player.injectError(PlaybackException(PlaybackErrorCode.INTERNAL, "test error"))
        runCurrent()

        case.state.onClose()

        val event = exitEvents().single()
        assertEquals(1L, event["had_error"])
        assertEquals(null, event["position_ms"])
        case.scope.cancel()
    }

    @Test
    fun `records once per episode when switching and then closing`() = runTest {
        val case = createCase()

        select(case, "a")
        advanceTimeBy(2_000)
        case.state.switchEpisode(1000)
        runCurrent()

        advanceTimeBy(3_000)
        case.state.onClose()
        case.state.onClose()

        val events = exitEvents()
        assertEquals(2, events.size)
        assertEquals(initialEpisodeId.toLong(), events[0]["episode_id"])
        assertEquals("switch_episode", events[0]["exit_reason"])
        assertEquals(1L, events[0]["reached_playing"])
        assertEquals(2_000L, events[0]["watched_ms"])

        assertEquals(1000L, events[1]["episode_id"])
        assertEquals("close", events[1]["exit_reason"])
        assertEquals(0L, events[1]["reached_playing"])
        assertEquals(0L, events[1]["watched_ms"])
        assertEquals(null, events[1]["media_source_id"])
        case.scope.cancel()
    }
}
