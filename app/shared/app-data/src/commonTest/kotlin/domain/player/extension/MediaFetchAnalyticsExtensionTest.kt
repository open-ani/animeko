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
import kotlinx.coroutines.flow.flowOf
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
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TestUniversalMediaResolver
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.SelectOrigin
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceTier
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.MediaFetchResult
import me.him188.ani.utils.coroutines.childScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MediaFetchAnalyticsExtensionTest : AbstractPlayerExtensionTest() {
    private val analytics = RecordingAnalytics()

    private class Case(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
    ) {
        lateinit var state: EpisodeFetchSelectPlayState

        /**
         * 未指定阶级的数据源是 [MediaSourceTier.Fallback].
         */
        var sourceTiers = MediaSelectorSourceTiers(emptyMap())

        /**
         * 标题只包含条目名, 是模糊匹配.
         */
        fun createMedia(mediaSourceId: String, kind: MediaSourceKind = MediaSourceKind.WEB): Media =
            suite.mediaSelectorTestBuilder.createMedia(mediaSourceId, kind)

        /**
         * 条目名与当前条目一致, 是精确匹配.
         */
        fun createExactMedia(
            mediaSourceId: String,
            kind: MediaSourceKind = MediaSourceKind.WEB,
            alliance: String = "XX字幕组",
        ): Media = suite.mediaSelectorTestBuilder.createMedia(mediaSourceId, kind).copy(
            properties = createTestMediaProperties(subjectName = "孤独摇滚！", alliance = alliance),
        )
    }

    /**
     * 数据源要在 [start] 之前用 [EpisodePlayerTestSuite.mediaSelectorTestBuilder] 添加: 每集的查询会话在创建剧集时确定数据源.
     */
    private fun TestScope.createCase(): Case {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, scope)
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }
        return Case(scope, suite)
    }

    private fun TestScope.start(case: Case) {
        case.state = case.suite.createState(
            listOf(
                EpisodePlayerExtensionFactory { context, _ ->
                    MediaFetchAnalyticsExtension(
                        context,
                        GetMediaSelectorSourceTiersUseCase { flowOf(case.sourceTiers) },
                        analytics,
                        testScheduler.timeSource,
                    )
                },
            ),
        )
        case.state.onUIReady()
        runCurrent()
    }

    private suspend fun TestScope.select(case: Case, media: Media) {
        case.state.mediaSelectorFlow.filterNotNull().first().select(media)
        runCurrent()
    }

    private fun events() = analytics.propertiesOf(MediaFetchResult)

    @Test
    fun `records on first selection`() = runTest {
        val case = createCase()
        val sourceA = case.suite.mediaSelectorTestBuilder.delayedMediaSource("a")
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("b")
        start(case)
        val media = case.createMedia("a")

        advanceTimeBy(2_000)
        sourceA.complete(listOf(media))
        runCurrent()
        assertEquals(emptyList(), events())

        advanceTimeBy(1_000)
        select(case, media)
        select(case, case.createMedia("b"))
        case.state.onClose()

        assertEquals(
            mapOf(
                "subject_id" to subjectId.toLong(),
                "episode_id" to initialEpisodeId.toLong(),
                "outcome" to "selected",
                "sources_total" to 2L,
                "sources_succeeded" to 1L,
                "sources_failed" to 0L,
                "media_count" to 1L,
                "web_sources_with_media" to 1L,
                "web_sources_exact" to 0L,
                "web_sources_instant" to 0L,
                "first_media_ms" to 2_000L,
                "select_ms" to 3_000L,
                "selected_by" to "manual",
                "selected_source_id" to "a",
                "selected_tier" to 2L,
                "selected_match" to "fuzzy",
                "is_cached" to 0L,
            ),
            events().single(),
        )
        case.scope.cancel()
    }

    @Test
    fun `records no media when all sources finish without media`() = runTest {
        val case = createCase()
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("a").complete(emptyList())
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("b").complete(emptyList())
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("disabled", enabled = false)
        start(case)

        advanceTimeBy(5_000)
        case.state.onClose()

        assertEquals(
            mapOf(
                "subject_id" to subjectId.toLong(),
                "episode_id" to initialEpisodeId.toLong(),
                "outcome" to "no_media",
                "sources_total" to 2L,
                "sources_succeeded" to 2L,
                "sources_failed" to 0L,
                "media_count" to 0L,
                "web_sources_with_media" to 0L,
                "web_sources_exact" to 0L,
                "web_sources_instant" to 0L,
            ),
            events().single(),
        )
        case.scope.cancel()
    }

    @Test
    fun `records all sources failed`() = runTest {
        val case = createCase()
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("a")
            .completeExceptionally(IllegalStateException("test"))
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("b")
            .completeExceptionally(IllegalStateException("test"))
        // 本地缓存源不计入数据源
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("cache", kind = MediaSourceKind.LocalCache)
            .complete(emptyList())
        start(case)

        case.state.onClose()

        val event = events().single()
        assertEquals("all_sources_failed", event["outcome"])
        assertEquals(2L, event["sources_total"])
        assertEquals(0L, event["sources_succeeded"])
        assertEquals(2L, event["sources_failed"])
        case.scope.cancel()
    }

    @Test
    fun `records leaving before selection`() = runTest {
        val case = createCase()
        val sourceA = case.suite.mediaSelectorTestBuilder.delayedMediaSource("a")
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("b")
        start(case)

        advanceTimeBy(1_000)
        sourceA.complete(listOf(case.createMedia("a")))
        runCurrent()
        advanceTimeBy(1_000)
        case.state.onClose()
        case.state.onClose()

        assertEquals(
            mapOf(
                "subject_id" to subjectId.toLong(),
                "episode_id" to initialEpisodeId.toLong(),
                "outcome" to "left_before_select",
                "sources_total" to 2L,
                "sources_succeeded" to 1L,
                "sources_failed" to 0L,
                "media_count" to 1L,
                "web_sources_with_media" to 1L,
                "web_sources_exact" to 0L,
                "web_sources_instant" to 0L,
                "first_media_ms" to 1_000L,
            ),
            events().single(),
        )
        case.scope.cancel()
    }

    @Test
    fun `records local cache selection`() = runTest {
        val case = createCase()
        val cache = case.createMedia("cache", kind = MediaSourceKind.LocalCache)
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("cache", kind = MediaSourceKind.LocalCache)
            .complete(listOf(cache))
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("a")
        start(case)

        select(case, cache)

        val event = events().single()
        assertEquals("selected", event["outcome"])
        assertEquals(1L, event["sources_total"])
        assertEquals(0L, event["sources_succeeded"])
        assertEquals("cache", event["selected_source_id"])
        assertEquals(1L, event["is_cached"])
        assertEquals(1L, event["media_count"])
        assertEquals(0L, event["web_sources_with_media"])
        assertFalse("selected_tier" in event)
        case.scope.cancel()
    }

    @Test
    fun `counts web sources by match and effective tier`() = runTest {
        val case = createCase()
        case.sourceTiers = MediaSelectorSourceTiers(
            tiers = mapOf("a" to MediaSourceTier(0u), "b" to MediaSourceTier(0u), "c" to MediaSourceTier(2u), "d" to MediaSourceTier(2u)),
            channelTiers = mapOf("d" to mapOf("fast" to MediaSourceTier(0u))),
        )
        val exactA = case.createExactMedia("a")
        val fuzzyB = case.createMedia("b")
        val exactC = case.createExactMedia("c")
        val exactD = case.createExactMedia("d", alliance = "fast")
        val exactBt = case.createExactMedia("bt", kind = MediaSourceKind.BitTorrent)
        val builder = case.suite.mediaSelectorTestBuilder
        builder.delayedMediaSource("a").complete(listOf(exactA))
        builder.delayedMediaSource("b").complete(listOf(fuzzyB))
        builder.delayedMediaSource("c").complete(listOf(exactC))
        builder.delayedMediaSource("d").complete(listOf(exactD))
        builder.delayedMediaSource("bt", kind = MediaSourceKind.BitTorrent).complete(listOf(exactBt))
        start(case)

        select(case, exactC)

        val event = events().single()
        assertEquals(5L, event["media_count"])
        assertEquals(4L, event["web_sources_with_media"])
        // a, c, d
        assertEquals(3L, event["web_sources_exact"])
        // a 的数据源阶级是 0, d 的数据源阶级是 2 但线路阶级是 0
        assertEquals(2L, event["web_sources_instant"])
        assertEquals("c", event["selected_source_id"])
        assertEquals(2L, event["selected_tier"])
        assertEquals("exact", event["selected_match"])
        case.scope.cancel()
    }

    @Test
    fun `records automatic selection`() = runTest {
        val case = createCase()
        case.sourceTiers = MediaSelectorSourceTiers(emptyMap(), channelTiers = mapOf("a" to mapOf("fast" to MediaSourceTier(0u))))
        val media = case.createExactMedia("a", alliance = "fast")
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("a").complete(listOf(media))
        start(case)

        case.state.mediaSelectorFlow.filterNotNull().first().selectAutomatically(media, expectedSelection = null)
        runCurrent()

        val event = events().single()
        assertEquals("auto", event["selected_by"])
        assertEquals(0L, event["selected_tier"])
        assertEquals("exact", event["selected_match"])
        case.scope.cancel()
    }

    @Test
    fun `records browse memory replay`() = runTest {
        val case = createCase()
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("a").complete(emptyList())
        start(case)

        // 回放的资源由数据源现场创建, 不在候选列表中
        case.state.mediaSelectorFlow.filterNotNull().first().select(case.createMedia("a"), SelectOrigin.BROWSE_MEMORY)
        advanceTimeBy(2_001)
        runCurrent()

        val event = events().single()
        assertEquals("browse_memory", event["selected_by"])
        assertEquals(2L, event["selected_tier"])
        assertFalse("selected_match" in event)
        case.scope.cancel()
    }

    @Test
    fun `media outside candidates is recorded after candidates fail to catch up`() = runTest {
        val case = createCase()
        case.suite.mediaSelectorTestBuilder.delayedMediaSource("a").complete(emptyList())
        start(case)

        // 例如手动查找现场创建的资源, 不在候选列表中
        select(case, case.createMedia("manual"))
        assertEquals(emptyList(), events())

        advanceTimeBy(2_001)
        runCurrent()

        val event = events().single()
        assertEquals("selected", event["outcome"])
        assertEquals(0L, event["media_count"])
        assertEquals(0L, event["select_ms"])
        assertEquals("manual", event["selected_source_id"])
        assertEquals("manual", event["selected_by"])
        case.scope.cancel()
    }

    @Test
    fun `records each episode once when switching episodes`() = runTest {
        val case = createCase()
        val sourceA = case.suite.mediaSelectorTestBuilder.delayedMediaSource("a")
        start(case)

        advanceTimeBy(1_000)
        case.state.switchEpisode(1000)
        runCurrent()

        val media = case.createMedia("a")
        sourceA.complete(listOf(media))
        runCurrent()
        select(case, media)
        case.state.onClose()

        val events = events()
        assertEquals(2, events.size)
        assertEquals(initialEpisodeId.toLong(), events[0]["episode_id"])
        assertEquals("left_before_select", events[0]["outcome"])
        assertEquals(0L, events[0]["media_count"])
        assertEquals(1000L, events[1]["episode_id"])
        assertEquals("selected", events[1]["outcome"])
        assertEquals(1L, events[1]["sources_succeeded"])
        case.scope.cancel()
    }
}
