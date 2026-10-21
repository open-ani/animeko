/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeEpisodeSessionApi::class)

package me.him188.ani.app.domain.player.analytics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.media.resolver.TestMediaDataProvider
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.SelectTrigger
import me.him188.ani.app.domain.player.extension.AbstractPlayerExtensionTest
import me.him188.ani.app.domain.player.extension.RecordingAnalytics
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.childScope
import org.openani.mediamp.PlaybackErrorCode
import org.openani.mediamp.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 从进入播放页到起播的 `play_flow` 事件序列. 自动选择与自动换源的结果由 [MediaAutoSelector][me.him188.ani.app.domain.media.selector.MediaAutoSelector]
 * 的 listener 产生, 在其单元测试中覆盖; 这里走手动选择.
 */
class PlayFlowExtensionsTest : AbstractPlayerExtensionTest() {
    private val analytics = RecordingAnalytics()

    private class Case(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
    ) {
        lateinit var state: EpisodeFetchSelectPlayState
    }

    private fun TestScope.createCase(resolver: MediaResolver = TestUniversalMediaResolver): Case {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, scope)
        suite.registerComponent<MediaResolver> { resolver }
        return Case(scope, suite)
    }

    private fun TestScope.start(case: Case) {
        case.state = case.suite.createState(
            playFlow = PlayFlowRecorder(subjectId, analytics, testScheduler.timeSource, playSessionId = "sid"),
        )
        case.state.onUIReady()
        runCurrent()
    }

    private suspend fun TestScope.select(case: Case, media: Media, trigger: SelectTrigger = SelectTrigger.MANUAL_AUTO_PAGE) {
        case.state.mediaSelectorFlow.filterNotNull().first().select(media, trigger)
        runCurrent()
    }

    private fun steps() = analytics.playFlowSteps()
    private fun step(step: PlayFlowStep) = analytics.stepsOf(step)

    @Test
    fun `records enter info search selection load and playback`() = runTest {
        val case = createCase()
        val sourceA = case.suite.mediaSelectorTestBuilder.delayedMediaSource("a")
        val sourceB = case.suite.mediaSelectorTestBuilder.delayedMediaSource("b")
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("disabled", enabled = false)
        start(case)
        val media = case.suite.mediaSelectorTestBuilder.createMedia("a")

        advanceTimeBy(1_000)
        sourceA.complete(listOf(media))
        runCurrent()
        advanceTimeBy(1_000)
        select(case, media)
        advanceUntilIdle()
        assertTrue(case.suite.player.state.value.isPlaying)

        advanceTimeBy(500)
        sourceB.complete(emptyList())
        advanceUntilIdle()

        val enter = step(PlayFlowStep.ENTER).single()
        assertEquals("open", enter["trigger"])
        assertEquals(1L, enter["episode_attempt"])
        assertEquals("sid", enter["play_session_id"])
        assertEquals(1L, enter["seq"])

        val info = step(PlayFlowStep.INFO_LOADED).single()
        assertEquals("ok", info["outcome"])
        assertEquals("initial", info["trigger"])

        val search = step(PlayFlowStep.SEARCH_STARTED).single()
        assertEquals("new", search["kind"])
        assertEquals(2L, search["count"])

        val select = step(PlayFlowStep.USER_SELECT).single()
        assertEquals(1L, select["sel_no"])
        assertEquals("auto_page", select["entry"])
        assertEquals("a", select["media_source_id"])
        assertEquals("web", select["kind"])
        assertEquals(0L, select["count"])

        val load = step(PlayFlowStep.VIDEO_LOAD_DONE).single()
        assertEquals(1L, load["sel_no"])
        assertEquals("ok", load["outcome"])
        assertEquals("a", load["media_source_id"])
        assertEquals("other", load["engine"])

        val playing = step(PlayFlowStep.PLAYBACK_STARTED).single()
        assertEquals(1L, playing["sel_no"])
        assertEquals(1L, playing["is_first"])
        assertEquals(2_000L, playing["since_enter_ms"])

        // 两个源都正常完成: 不单独记录, 只在 search_done 里汇总
        assertTrue(step(PlayFlowStep.SOURCE_FAILED).isEmpty())

        val done = step(PlayFlowStep.SEARCH_DONE).single()
        assertEquals("complete", done["outcome"])
        assertEquals(2L, done["count"])
        assertEquals(1L, done["media_count"])

        // seq 严格递增
        val seqs = analytics.propertiesOf(AnalyticsEvent.PlayFlow).map { it["seq"] as Long }
        assertEquals(seqs.sorted(), seqs)
        assertEquals(seqs.toSet().size, seqs.size)
        case.scope.cancel()
    }

    @Test
    fun `switching episode starts a new attempt and reuses the search`() = runTest {
        val case = createCase()
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("a").complete(emptyList())
        start(case)
        advanceUntilIdle()

        case.state.switchEpisode(initialEpisodeId + 1)
        advanceUntilIdle()

        val enters = step(PlayFlowStep.ENTER)
        assertEquals(listOf("open", "switch_episode"), enters.map { it["trigger"] })
        assertEquals(listOf(1L, 2L), enters.map { it["episode_attempt"] })
        assertEquals((initialEpisodeId + 1).toLong(), enters[1]["episode_id"])

        // 测试套件为每集单独创建查询会话, 因此两次都是新会话; 复用会话的语义见 PlayFlowFetchObserverTest
        val searches = step(PlayFlowStep.SEARCH_STARTED)
        assertEquals(listOf("new", "new"), searches.map { it["kind"] })
        assertEquals(listOf(1L, 2L), searches.map { it["episode_attempt"] })
        assertEquals(listOf(1L, 2L), step(PlayFlowStep.SEARCH_DONE).map { it["episode_attempt"] })
        case.scope.cancel()
    }

    @Test
    fun `switching source while loading records a cancelled load`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val resolver = object : MediaResolver {
            override fun supports(media: Media): Boolean = true
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
                if (media.mediaSourceId == "slow") gate.await()
                return TestMediaDataProvider()
            }
        }
        val case = createCase(resolver)
        val slow = case.suite.mediaSelectorTestBuilder.createMedia("slow")
        val fast = case.suite.mediaSelectorTestBuilder.createMedia("fast")
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("slow").complete(listOf(slow))
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("fast").complete(listOf(fast))
        start(case)

        select(case, slow)
        advanceTimeBy(1_000)
        select(case, fast, SelectTrigger.MANUAL_BT_PAGE)
        advanceUntilIdle()

        val loads = step(PlayFlowStep.VIDEO_LOAD_DONE)
        assertEquals(listOf("cancelled", "ok"), loads.map { it["outcome"] })
        assertEquals("user_switched", loads[0]["reason"])
        assertEquals("resolve", loads[0]["stage"])
        assertEquals(1L, loads[0]["sel_no"])
        assertEquals(2L, loads[1]["sel_no"])

        val selects = step(PlayFlowStep.USER_SELECT)
        assertEquals(listOf("auto_page", "bt_page"), selects.map { it["entry"] })
        assertEquals(1L, selects[1]["count"])
        assertEquals(listOf(2L), step(PlayFlowStep.PLAYBACK_STARTED).map { it["sel_no"] })
        case.scope.cancel()
    }

    @Test
    fun `switching episode while loading records the cancelled load as episode_switched`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val resolver = object : MediaResolver {
            override fun supports(media: Media): Boolean = true
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
                gate.await()
                return TestMediaDataProvider()
            }
        }
        val case = createCase(resolver)
        val slow = case.suite.mediaSelectorTestBuilder.createMedia("slow")
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("slow").complete(listOf(slow))
        start(case)

        select(case, slow)
        advanceTimeBy(1_000)
        case.state.switchEpisode(initialEpisodeId + 1)
        advanceUntilIdle()

        // 切集在换入新 session 之前就取消了旧 session 的加载, 取消原因要按 scope 判断而不是按当前 session 判断
        val loads = step(PlayFlowStep.VIDEO_LOAD_DONE)
        assertEquals(listOf("cancelled"), loads.map { it["outcome"] })
        assertEquals("episode_switched", loads[0]["reason"])
        assertEquals("resolve", loads[0]["stage"])
        assertEquals(1L, loads[0]["episode_attempt"])
        assertEquals(listOf(1L, 2L), step(PlayFlowStep.ENTER).map { it["episode_attempt"] })
        case.scope.cancel()
    }

    @Test
    fun `resolution failure and player error are recorded`() = runTest {
        val resolver = object : MediaResolver {
            override fun supports(media: Media): Boolean = true
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
                if (media.mediaSourceId == "broken") throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
                return TestMediaDataProvider()
            }
        }
        val case = createCase(resolver)
        val broken = case.suite.mediaSelectorTestBuilder.createMedia("broken")
        val good = case.suite.mediaSelectorTestBuilder.createMedia("good", kind = MediaSourceKind.BitTorrent)
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("broken").complete(listOf(broken))
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("good", kind = MediaSourceKind.BitTorrent).complete(listOf(good))
        start(case)

        select(case, broken)
        advanceUntilIdle()
        select(case, good)
        advanceUntilIdle()
        case.suite.player.injectError(PlaybackException(PlaybackErrorCode.IO, "test"))
        advanceUntilIdle()

        val loads = step(PlayFlowStep.VIDEO_LOAD_DONE)
        assertEquals(listOf("failed", "ok"), loads.map { it["outcome"] })
        assertEquals("resolve", loads[0]["stage"])
        assertEquals("no_playable_link", loads[0]["reason"])
        assertEquals("bt", loads[1]["kind"])

        val error = step(PlayFlowStep.PLAY_ERROR).single()
        assertEquals(2L, error["sel_no"])
        assertEquals("player_io", error["reason"])
        case.scope.cancel()
    }

    @Test
    fun `leaving before the search finishes records an incomplete search`() = runTest {
        val case = createCase()
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("a") // 一直不完成
        start(case)
        advanceTimeBy(3_000)

        case.state.onClose()

        val done = step(PlayFlowStep.SEARCH_DONE).single()
        assertEquals("incomplete", done["outcome"])
        assertEquals(3_000L, done["duration_ms"])
        assertTrue(step(PlayFlowStep.SOURCE_FAILED).isEmpty())
        assertTrue(PlayFlowStep.SEARCH_STARTED.value in steps())
        case.scope.cancel()
    }
}
