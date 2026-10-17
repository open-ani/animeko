/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchTelemetry
import me.him188.ani.app.domain.media.fetch.MediaSourceSearchStats
import me.him188.ani.app.domain.media.selector.legacy.MediaSelectorTestBuilder
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.app.domain.player.extension.RecordingAnalytics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayFlowFetchObserverTest {
    private val analytics = RecordingAnalytics()

    @Test
    fun `describes a session as new once per attempt and reused afterwards`() = runTest {
        val builder = MediaSelectorTestBuilder(this)
        builder.delayedMediaSource("a").complete(emptyList())
        builder.delayedMediaSource("disabled", enabled = false)
        val session = builder.createMediaFetchSession(builder.createMediaFetcher())
        val recorder = PlayFlowRecorder(1, analytics, testScheduler.timeSource, "sid")
        val observer = PlayFlowFetchObserver(recorder, backgroundScope) { null }
        val first = recorder.nextEpisodeAttempt(1)

        val start = assertNotNull(observer.describeSearchStart(session, first))
        assertEquals(false, start.reused)
        assertEquals(1, start.sourceCount)
        assertEquals(0, start.retried)
        assertNull(observer.describeSearchStart(session, first))

        val second = recorder.nextEpisodeAttempt(2)
        observer.onSessionReused(session, retriedCount = 1)
        val reused = assertNotNull(observer.describeSearchStart(session, second))
        assertEquals(true, reused.reused)
        assertEquals(1, reused.retried)
    }

    @Test
    fun `search cache hits reported by the source are recorded as is_cached`() = runTest {
        val builder = MediaSelectorTestBuilder(this)
        builder.mediaSources.add(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    mediaSourceId = "cached",
                    fetch = {
                        SinglePagePagedSource {
                            flow {
                                // Selector 数据源在每个关键词搜索完成后报告是否命中搜索缓存
                                currentCoroutineContext()[MediaSourceFetchTelemetry]?.onSearch("关键词", true)
                                emit(MediaMatch(builder.createMedia("cached"), MatchKind.EXACT))
                            }
                        }
                    },
                ),
            ),
        )
        val session = builder.createMediaFetchSession(builder.createMediaFetcher())
        val recorder = PlayFlowRecorder(1, analytics, testScheduler.timeSource, "sid")
        val observer = PlayFlowFetchObserver(recorder, backgroundScope) { null }
        recorder.nextEpisodeAttempt(1)
        observer.onSessionCreated(session)
        val subscription = backgroundScope.launch { session.cumulativeResults.collect {} }
        advanceUntilIdle()
        runCurrent()

        assertEquals(MediaSourceSearchStats(cacheHits = 1), session.mediaSourceResults.single().searchStats.value)
        val source = analytics.stepsOf(PlayFlowStep.SEARCH_SOURCE_DONE).single()
        assertEquals("cached", source["media_source_id"])
        assertEquals(1L, source["is_cached"])
        subscription.cancel()
    }

    @Test
    fun `restarting a source opens a new round with only that source`() = runTest {
        val builder = MediaSelectorTestBuilder(this)
        val a = builder.delayedMediaSource("a")
        val b = builder.delayedMediaSource("b")
        val session = builder.createMediaFetchSession(builder.createMediaFetcher())
        val recorder = PlayFlowRecorder(1, analytics, testScheduler.timeSource, "sid")
        val observer = PlayFlowFetchObserver(recorder, backgroundScope) { null }
        recorder.nextEpisodeAttempt(1)
        observer.onSessionCreated(session)
        val subscription = backgroundScope.launch { session.cumulativeResults.collect {} }

        advanceTimeBy(1_000)
        a.complete(listOf(builder.createMedia("a")))
        advanceTimeBy(2_000)
        b.completeExceptionally(IllegalStateException("boom"))
        // 观察者跑在 backgroundScope: advanceUntilIdle 只保证前台任务跑完, 再 runCurrent 让后台收集器处理最后的状态变化
        advanceUntilIdle()
        runCurrent()

        val round1 = analytics.stepsOf(PlayFlowStep.SEARCH_SOURCE_DONE)
        assertEquals(setOf("a", "b"), round1.map { it["media_source_id"] }.toSet())
        val sourceA = round1.single { it["media_source_id"] == "a" }
        assertEquals("ok", sourceA["outcome"])
        assertEquals(1L, sourceA["count"])
        assertEquals(1_000L, sourceA["duration_ms"])
        assertEquals(0L, sourceA["restarts"])
        val sourceB = round1.single { it["media_source_id"] == "b" }
        assertEquals("failed", sourceB["outcome"])
        assertEquals("IllegalStateException", sourceB["reason"])
        assertEquals(3_000L, sourceB["duration_ms"])
        assertEquals(1, analytics.stepsOf(PlayFlowStep.SEARCH_DONE).size)
        assertEquals("complete", analytics.stepsOf(PlayFlowStep.SEARCH_DONE).single()["outcome"])

        // 重试失败的源: 新一轮只包含它
        session.mediaSourceResults.single { it.mediaSourceId == "b" }.restart()
        advanceUntilIdle()
        runCurrent()

        val round2 = analytics.stepsOf(PlayFlowStep.SEARCH_SOURCE_DONE).filter { it["round"] == 2L }
        assertEquals(listOf("b"), round2.map { it["media_source_id"] })
        assertEquals(1L, round2.single()["restarts"])
        assertEquals(listOf(1L, 2L), analytics.stepsOf(PlayFlowStep.SEARCH_DONE).map { it["round"] })
        assertTrue(analytics.stepsOf(PlayFlowStep.SEARCH_DONE).all { it["outcome"] == "complete" })

        subscription.cancel()
    }
}
