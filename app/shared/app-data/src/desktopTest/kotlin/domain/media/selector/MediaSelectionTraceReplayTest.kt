/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.media.selector.trace.MediaSelectionTrace
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.utils.platform.collections.ImmutableEnumMap
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Replays recorded playback observations through real filtering, preferences and the automatic selection use case. */
class MediaSelectionTraceReplayTest {
    @Serializable
    private data class Case(
        val file: String,
        val recordedMediaId: String?,
        val expectedMediaId: String?,
        val expectedSourceName: String?,
        val earliestMillis: Long,
        val latestMillis: Long,
        val knownEpisodeMismatch: Boolean = false,
        val expectedMatch: String = "EXACT",
        val expectedTier: Int = 0,
    )

    @Test
    fun `live playback episode 01`() = replay("bocchi-01")

    @Test
    fun `live playback episode 06`() = replay("bocchi-06")

    @Test
    fun `live playback episode 12`() = replay("bocchi-12")

    @Test
    fun `live playback SP01 reproduces normal episode 01 mismatch`() = replay("bocchi-sp01")

    @Test
    fun `fresh live playback episode 01`() = replay("fresh-bocchi-01")

    @Test
    fun `fresh live playback SP01 reproduces normal episode 01 mismatch`() = replay("fresh-bocchi-sp01")

    @Test
    fun `summer grand blue S3 waits for Xifan T0 despite earlier sources`() = replay("summer-grand-blue-s3-01")

    @Test
    fun `summer mushoku S3 excludes results belonging to previous seasons`() = replay("summer-mushoku-s3-09")

    @Test
    fun `summer youjo S2 selects the current season and episode`() = replay("summer-youjo-s2-09")

    @Test
    fun `summer opposites S2 sort 13 selects local episode 1`() = replay("summer-opposites-s2-01")

    @Test
    fun `summer opposites S2 sort 21 selects local episode 9`() = replay("summer-opposites-s2-09")

    @Test
    fun `summer smoking matches a real Chinese alias`() = replay("summer-smoking-09")

    @Test
    fun `summer childhood friend handles a long title and late episode`() = replay("summer-childhood-friend-09")

    @Test
    fun `completed Frieren retains exact matching with sequel results present`() = replay("frieren-01")

    @Test
    fun `recorded preferred Xifan blocks earlier exact T0 until completion`() =
        replay("fresh-bocchi-01", Scenario.PreferredXifan)

    @Test
    fun `recorded exact T6 beats fuzzy T1 at the five second boundary`() =
        replay("fresh-bocchi-01", Scenario.ExactBeforeFuzzy)

    @Test
    fun `recorded fuzzy candidates wait until the fifteen second boundary`() =
        replay("fresh-bocchi-01", Scenario.FuzzyOnly)

    @Test
    fun `recorded fast select disabled waits for the slower source`() =
        replay("fresh-bocchi-01", Scenario.FastDisabled)

    private enum class Scenario { Recorded, PreferredXifan, ExactBeforeFuzzy, FuzzyOnly, FastDisabled }

    private fun replay(name: String, mode: Scenario = Scenario.Recorded) = runTest {
        val originalCase = MediaSelectionTrace.json.decodeFromString<Map<String, Case>>(resource("index.json")).getValue(name)
        val fixture = MediaSelectionTrace.json.decodeFromString<MediaSelectionFixture>(resource(originalCase.file))
        val recorded = fixture.restore()
        assertEquals(originalCase.recordedMediaId, recorded.frames.firstNotNullOfOrNull { it.selectedMediaId }, "Recorded first choice")
        val (case, trace) = withScenario(originalCase, recorded, mode)
        assertEquals(1, trace.formatVersion)
        assertEquals("desktop-playback", trace.acquisition)
        assertTrue(trace.frames.isNotEmpty())
        assertTrue(trace.frames.zipWithNext().all { (a, b) -> a.elapsedMillis <= b.elapsedMillis })
        assertTrue(trace.durationMillis >= trace.frames.last().elapsedMillis)
        val initial = trace.frames.first()
        val context = MutableStateFlow(initial.context.restore())
        val preference = MutableStateFlow(initial.preference)
        val defaults = MutableStateFlow(initial.defaultPreference)
        val settings = MutableStateFlow(initial.settings)
        val session = ReplaySession(trace)
        val selector = DefaultMediaSelector(
            mediaSelectorContextNotCached = context,
            mediaListNotCached = session.cumulativeResults,
            savedUserPreference = preference,
            savedDefaultPreference = defaults,
            mediaSelectorSettings = settings,
            enableCaching = true,
            flowCoroutineContext = StandardTestDispatcher(testScheduler),
            cachingScope = backgroundScope,
        )
        val koin = Koin().apply {
            loadModules(listOf(module {
                single<GetMediaSelectorSettingsFlowUseCase> { GetMediaSelectorSettingsFlowUseCase { settings } }
                single<GetMediaSelectorSourceTiersUseCase> {
                    GetMediaSelectorSourceTiersUseCase { context.mapNotNull { it.mediaSourceTiers } }
                }
                single<GetPreferredWebMediaSourceUseCase> {
                    GetPreferredWebMediaSourceUseCase { flowOf(trace.preferredSourceId) }
                }
            }))
        }
        val startedAt = testScheduler.currentTime
        var selectedAt: Long? = null
        val selection = async(start = CoroutineStart.UNDISPATCHED) {
            MediaSelectorAutoSelectUseCaseImpl(koin)(session, selector)
        }
        val selectionTime = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            selector.selected.first { it != null }
            selectedAt = testScheduler.currentTime - startedAt
        }
        try {
            for (frame in trace.frames) {
                val target = startedAt + frame.elapsedMillis
                advanceTimeBy((target - testScheduler.currentTime).coerceAtLeast(0))
                context.value = frame.context.restore()
                preference.value = frame.preference
                defaults.value = frame.defaultPreference
                settings.value = frame.settings
                session.apply(frame)
                runCurrent()
            }
            advanceTimeBy(startedAt + trace.durationMillis - testScheduler.currentTime)
            runCurrent()
            assertEquals(case.expectedMediaId, selector.selected.value?.mediaId, case.file)
            if (case.expectedMediaId != null) {
                val sourceName = trace.sources.single { it.id == selector.selected.value!!.mediaSourceId }.name
                assertEquals(case.expectedSourceName, sourceName, case.file)
                assertTrue(assertNotNull(selectedAt) in case.earliestMillis..case.latestMillis, "${case.file}: selected at $selectedAt")
                assertTrue(selection.isCompleted, case.file)
                assertFalse(selection.isCancelled, case.file)
                val matchesEpisode = selector.selected.value!!.episodeRange?.knownSorts
                    ?.any { it == (trace.request.episodeEp ?: trace.request.episodeSort) } == true
                assertEquals(!case.knownEpisodeMismatch, matchesEpisode, "${case.file}: requested episode match")
                val snapshot = selector.autoSelectSnapshots(flowOf(session.mediaSourceResults.map { source ->
                    MediaSourceSelectionSnapshot(source.mediaSourceId, source.kind, source.state.value, source.results.value)
                })).first()
                val candidate = snapshot.candidates.single { it.result.mediaId == case.expectedMediaId }
                assertEquals(case.expectedMatch, candidate.metadata.subjectMatchKind.name, case.file)
                assertEquals(
                    case.expectedTier,
                    snapshot.context.mediaSourceTiers!!.get(candidate.result.mediaSourceId, candidate.result.properties.alliance).value.toInt(),
                    case.file,
                )
            }
        } finally {
            selection.cancel()
            selectionTime.cancel()
            koin.close()
        }
    }

    /** Controlled policy regressions retain real candidate attributes and real completion times.
     * The only changes are explicit source/candidate subsets or the preference being exercised.
     */
    private fun withScenario(case: Case, trace: MediaSelectionTrace, scenario: Scenario): Pair<Case, MediaSelectionTrace> {
        if (scenario == Scenario.Recorded) return case to trace
        val xifan = trace.sources.single { it.name == "稀饭动漫" }.id
        val haixing = trace.sources.single { it.name == "海星动漫" }.id
        val dida = trace.sources.single { it.name == "嘀嗒影视" }.id
        val sourceIds = when (scenario) {
            Scenario.PreferredXifan -> setOf(xifan, dida)
            Scenario.ExactBeforeFuzzy, Scenario.FuzzyOnly -> setOf(haixing)
            Scenario.FastDisabled -> setOf(xifan, dida)
            Scenario.Recorded -> error("Handled above")
        }
        val indexes = trace.sources.indices.filter { trace.sources[it].id in sourceIds }
        val frames = trace.frames.map { frame ->
            frame.copy(
                sources = indexes.map { index ->
                    val source = frame.sources[index]
                    source.copy(results = source.results.filter { media ->
                        when (scenario) {
                            Scenario.FuzzyOnly -> media.properties.subjectName!!.startsWith("剧场总集篇")
                            Scenario.ExactBeforeFuzzy -> media.properties.subjectName!!.startsWith("剧场总集篇") ||
                                    (media.properties.subjectName == "孤独摇滚！" && media.properties.alliance == "播放Ⅰ")
                            else -> true
                        }
                    })
                },
                settings = if (scenario == Scenario.FastDisabled) frame.settings.copy(fastSelectWebKind = false) else frame.settings,
            )
        }
        val expected = frames.last().sources.flatMap { it.results }.single { media ->
            when (scenario) {
                Scenario.PreferredXifan -> media.mediaSourceId == xifan && media.properties.subjectName == "孤独摇滚！"
                Scenario.ExactBeforeFuzzy -> media.properties.subjectName == "孤独摇滚！"
                Scenario.FuzzyOnly -> media.properties.subjectName == "剧场总集篇孤独摇滚！ Re:" && media.properties.alliance == "播放Ⅱ"
                Scenario.FastDisabled -> media.mediaId == case.expectedMediaId
                Scenario.Recorded -> error("Handled above")
            }
        }
        val expectedAt = when (scenario) {
            Scenario.ExactBeforeFuzzy -> 5_000L
            Scenario.FuzzyOnly -> 15_000L
            else -> frames.first { frame -> frame.sources.any { it.id == xifan && it.state == "Succeed" } }.elapsedMillis
        }
        val expectedName = trace.sources.single { it.id == expected.mediaSourceId }.name
        val expectedTier = when (scenario) {
            Scenario.ExactBeforeFuzzy -> 6
            Scenario.PreferredXifan, Scenario.FuzzyOnly -> 1
            else -> 0
        }
        return case.copy(
            expectedMediaId = expected.mediaId, expectedSourceName = expectedName,
            earliestMillis = expectedAt, latestMillis = expectedAt,
            expectedMatch = if (scenario == Scenario.FuzzyOnly) "FUZZY" else "EXACT", expectedTier = expectedTier,
        ) to MediaSelectionTrace(
            capturedAt = trace.capturedAt, applicationVersion = trace.applicationVersion,
            acquisition = trace.acquisition, durationMillis = trace.durationMillis, searchCacheCleared = trace.searchCacheCleared,
            request = trace.request,
            preferredSourceId = if (scenario == Scenario.PreferredXifan) xifan else trace.preferredSourceId,
            sources = indexes.map { trace.sources[it] }, frames = frames,
        )
    }

    private fun resource(name: String): String = checkNotNull(javaClass.getResourceAsStream("/media-selector-traces/$name")) {
        "Missing captured fixture $name"
    }.bufferedReader().use { it.readText() }

    private class ReplaySession(trace: MediaSelectionTrace) : MediaFetchSession {
        override val request = flowOf(trace.request)
        override val mediaSourceResults = trace.sources.map { ReplaySource(it) }
        override val cumulativeResults = combine(mediaSourceResults.map { it.results }) { lists ->
            lists.flatMap { it }.distinctBy { it.mediaId }
        }
        override val hasCompleted: Flow<CompletedConditions> = combine(mediaSourceResults.map { it.state }) {
            CompletedConditions(ImmutableEnumMap { kind ->
                val states = mediaSourceResults.filter { it.kind == kind }.map { it.state.value }
                if (states.all { state -> state is MediaSourceFetchState.Disabled }) null
                else states.all { state -> state.isFinal }
            })
        }

        fun apply(frame: MediaSelectionTrace.Frame) {
            // LocalTorrent and LocalWebM3u share a mediaSourceId. Preserve their separate subscriptions by position.
            assertEquals(mediaSourceResults.size, frame.sources.size)
            mediaSourceResults.zip(frame.sources).forEach { (source, update) ->
                assertEquals(source.mediaSourceId, update.id)
                source.apply(update)
            }
        }

        override fun setFetchRequest(request: MediaFetchRequest) = error("A recorded session has a fixed request")
    }

    private class ReplaySource(source: MediaSelectionTrace.Source) : MediaSourceFetchResult {
        override val instanceId = source.id
        override val mediaSourceId = source.id
        override val kind = source.kind
        override val sourceInfo = MediaSourceInfo(source.name)
        override val state = MutableStateFlow<MediaSourceFetchState>(MediaSourceFetchState.Idle)
        override val results = MutableStateFlow<List<Media>>(emptyList())

        fun apply(update: MediaSelectionTrace.SourceUpdate) {
            results.value = update.results
            state.value = when (update.state) {
                "Idle" -> MediaSourceFetchState.Idle
                "Working" -> MediaSourceFetchState.Working
                "Disabled" -> MediaSourceFetchState.Disabled
                "Succeed" -> MediaSourceFetchState.Succeed(update.generation ?: 0)
                // Automatic selection treats these as terminal non-success states; retain the recorded name in the cause.
                "Failed", "Abandoned", "CaptchaRequired", "RateLimited" ->
                    MediaSourceFetchState.Failed(IllegalStateException(update.state), update.generation ?: 0)
                else -> error("Unknown recorded state ${update.state}")
            }
        }

        override fun enable() {
            if (state.value is MediaSourceFetchState.Disabled) state.value = MediaSourceFetchState.Idle
        }
        override fun restart() = error("Source retries are driven exclusively by the recorded timeline")
    }
}
