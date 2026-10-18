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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchTelemetry
import me.him188.ani.app.domain.media.fetch.MediaSourceSearchStats
import me.him188.ani.app.domain.media.selector.legacy.MediaSelectorTestBuilder
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.domain.player.extension.RecordingAnalytics
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PlayFlowFetchObserverTest {
    private val analytics = RecordingAnalytics()

    private class Observed(val session: MediaFetchSession, val observer: PlayFlowFetchObserver)

    /**
     * 用 [builder] 里的源创建查询会话并开始观察; 后台收集结果以驱动查询.
     */
    private fun TestScope.observe(
        builder: MediaSelectorTestBuilder,
        slowThreshold: Duration = PlayFlowFetchObserver.DEFAULT_SLOW_THRESHOLD,
    ): Observed {
        val session = builder.createMediaFetchSession(builder.createMediaFetcher())
        val recorder = PlayFlowRecorder(1, analytics, testScheduler.timeSource, "sid")
        val observer = PlayFlowFetchObserver(recorder, backgroundScope, slowThreshold)
        recorder.nextEpisodeAttempt(1)
        observer.onSessionCreated(session)
        backgroundScope.launch { session.cumulativeResults.collect {} }
        return Observed(session, observer)
    }

    /**
     * 观察者跑在 backgroundScope: advanceUntilIdle 只保证前台任务跑完, 再 runCurrent 让后台收集器处理最后的状态变化.
     */
    private fun TestScope.settle() {
        advanceUntilIdle()
        runCurrent()
    }

    @Test
    fun `describes a session as new once per attempt and reused afterwards`() = runTest {
        val builder = MediaSelectorTestBuilder(this)
        builder.delayedMediaSource("a").complete(emptyList())
        builder.delayedMediaSource("disabled", enabled = false)
        val session = builder.createMediaFetchSession(builder.createMediaFetcher())
        val recorder = PlayFlowRecorder(1, analytics, testScheduler.timeSource, "sid")
        val observer = PlayFlowFetchObserver(recorder, backgroundScope)
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
    fun `search cache hits reported by a failed source are recorded as is_cached`() = runTest {
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
                                throw IllegalStateException("boom")
                            }
                        }
                    },
                ),
            ),
        )
        val observed = observe(builder)
        settle()

        assertEquals(MediaSourceSearchStats(cacheHits = 1), observed.session.mediaSourceResults.single().searchStats.value)
        val failed = analytics.stepsOf(PlayFlowStep.SOURCE_FAILED).single()
        assertEquals("cached=IllegalStateException", failed["sources_1"])
        assertEquals(1L, analytics.stepsOf(PlayFlowStep.SEARCH_DONE).single()["cached_count"])
    }

    @Test
    fun `only failed sources are reported and restarting one opens a new round with only it`() = runTest {
        val builder = MediaSelectorTestBuilder(this)
        val a = builder.delayedMediaSource("a")
        val b = builder.delayedMediaSource("b")
        val observed = observe(builder)

        advanceTimeBy(1_000)
        a.complete(listOf(builder.createMedia("a")))
        settle()
        // a 正常完成: 不单独记录, 本轮也还没结束
        assertTrue(analytics.stepsOf(PlayFlowStep.SOURCE_FAILED).isEmpty())
        assertTrue(analytics.stepsOf(PlayFlowStep.SEARCH_DONE).isEmpty())

        advanceTimeBy(2_000)
        b.completeExceptionally(IllegalStateException("boom"))
        settle()

        val failed = analytics.stepsOf(PlayFlowStep.SOURCE_FAILED).single()
        assertEquals(1L, failed["round"])
        assertEquals(1L, failed["count"])
        assertEquals("b=IllegalStateException", failed["sources_1"])
        assertNull(failed["sources_2"])
        val done = analytics.stepsOf(PlayFlowStep.SEARCH_DONE).single()
        assertEquals("complete", done["outcome"])
        // 正常完成的 a 只计入汇总
        assertEquals(2L, done["count"])
        assertEquals(1L, done["media_count"])
        assertEquals(0L, done["cached_count"])
        // 汇总在 search_done 之前
        assertTrue((failed["seq"] as Long) < (done["seq"] as Long))

        // 重试失败的源: 新一轮只包含它
        observed.session.mediaSourceResults.single { it.mediaSourceId == "b" }.restart()
        settle()

        val round2 = analytics.stepsOf(PlayFlowStep.SOURCE_FAILED).single { it["round"] == 2L }
        assertEquals("b=IllegalStateException", round2["sources_1"])
        assertEquals(listOf(1L, 2L), analytics.stepsOf(PlayFlowStep.SEARCH_DONE).map { it["round"] })
        assertTrue(analytics.stepsOf(PlayFlowStep.SEARCH_DONE).all { it["outcome"] == "complete" })
        assertEquals(1L, analytics.stepsOf(PlayFlowStep.SEARCH_DONE).last()["count"])
    }

    @Test
    fun `leaving mid-round reports the failures so far as incomplete`() = runTest {
        val builder = MediaSelectorTestBuilder(this)
        val a = builder.delayedMediaSource("a")
        builder.delayedMediaSource("b") // 一直不完成
        val observed = observe(builder)

        advanceTimeBy(1_000)
        a.completeExceptionally(IllegalStateException("boom"))
        settle()
        assertTrue(analytics.stepsOf(PlayFlowStep.SOURCE_FAILED).isEmpty())

        observed.observer.close()
        val failed = analytics.stepsOf(PlayFlowStep.SOURCE_FAILED).single()
        assertEquals("a=IllegalStateException", failed["sources_1"])
        val done = analytics.stepsOf(PlayFlowStep.SEARCH_DONE).single()
        assertEquals("incomplete", done["outcome"])
        assertEquals(1L, done["count"])
    }

    @Test
    fun `a successful source slower than the threshold is reported as slow`() = runTest {
        val builder = MediaSelectorTestBuilder(this)
        val fast = builder.delayedMediaSource("fast")
        val slow = builder.delayedMediaSource("slow")
        observe(builder, slowThreshold = 500.milliseconds)

        advanceTimeBy(100)
        fast.complete(listOf(builder.createMedia("fast")))
        advanceTimeBy(900)
        slow.complete(listOf(builder.createMedia("slow")))
        settle()

        val reported = analytics.stepsOf(PlayFlowStep.SOURCE_FAILED).single()
        assertEquals(1L, reported["count"])
        assertEquals("slow=slow", reported["sources_1"])
        assertEquals(2L, analytics.stepsOf(PlayFlowStep.SEARCH_DONE).single()["count"])
    }

    @Test
    fun `failures that do not fit in one event are split into parts without dropping any`() = runTest {
        val builder = MediaSelectorTestBuilder(this)
        // 每个记号 (id=IllegalStateException) 超过 50 字符, 一段只装得下一个; 比一条事件的段数多一个
        val ids = (1..PlayFlowFetchObserver.MAX_SOURCE_CHUNKS + 1).map { "source-${it.toString().padStart(2, '0')}-" + "x".repeat(40) }
        val sources = ids.map { builder.delayedMediaSource(it) }
        observe(builder)

        for (source in sources) source.completeExceptionally(IllegalStateException("boom"))
        settle()

        val parts = analytics.stepsOf(PlayFlowStep.SOURCE_FAILED)
        assertEquals(listOf(1L, 2L), parts.map { it["part"] })
        assertTrue(parts.all { it["count"] == ids.size.toLong() && it["round"] == 1L })
        val tokens = parts.flatMap { part ->
            (1..PlayFlowFetchObserver.MAX_SOURCE_CHUNKS).mapNotNull { part["sources_$it"] as String? }.flatMap { it.split(',') }
        }
        assertEquals(ids.map { "$it=IllegalStateException" }.toSet(), tokens.toSet())
        assertEquals(ids.size, tokens.size)
        assertEquals(1, analytics.stepsOf(PlayFlowStep.SEARCH_DONE).size)
    }
}
