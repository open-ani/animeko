/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.domain.featureflag.FeatureFlagConfig
import me.him188.ani.app.domain.player.extension.RecordingAnalytics
import me.him188.ani.utils.analytics.AnalyticsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class PlayFlowRecorderTest {
    private val analytics = RecordingAnalytics()
    private val timeSource = TestTimeSource()

    private fun events() = analytics.propertiesOf(AnalyticsEvent.PlayFlow)

    private fun recorder(maxEvents: Int = PlayFlowRecorder.DEFAULT_MAX_EVENTS) =
        PlayFlowRecorder(subjectId = 1, analytics = analytics, timeSource = timeSource, playSessionId = "sid", maxEvents = maxEvents)

    @Test
    fun `common properties are typed for GA4`() {
        val recorder = recorder()
        val episode = recorder.nextEpisodeAttempt(2)
        timeSource += 1500.milliseconds

        recorder.record(PlayFlowStep.ENTER, episode) {
            put("trigger", "open")
            put("is_fullscreen", true)
            put("count", 3)
            put("duration_ms", 20.milliseconds)
            put("kind", PlayFlowStep.ENTER)
            put("media_source_id", "x".repeat(200))
            put("reason", null as String?)
        }

        val event = events().single()
        assertEquals(PlayFlowRecorder.COMMON_KEYS, event.keys.take(PlayFlowRecorder.COMMON_KEYS.size))
        assertEquals("enter", event["step"])
        assertEquals("sid", event["play_session_id"])
        assertEquals(1L, event["episode_attempt"])
        assertEquals(1L, event["seq"])
        assertEquals(1500L, event["t_ms"])
        assertEquals(1L, event["subject_id"])
        assertEquals(2L, event["episode_id"])
        assertEquals(1L, event["is_fullscreen"])
        assertEquals(3L, event["count"])
        assertEquals(20L, event["duration_ms"])
        assertEquals("enter", event["kind"])
        assertEquals(100, (event["media_source_id"] as String).length)
        assertFalse("reason" in event)
        assertTrue(event.values.all { it is String || it is Long })
    }

    @Test
    fun `episode attempts and selection numbers increase`() {
        val recorder = recorder()
        val first = recorder.nextEpisodeAttempt(10)
        val second = recorder.nextEpisodeAttempt(11)
        assertEquals(1L, first.attempt)
        assertEquals(2L, second.attempt)
        assertEquals(second, recorder.currentEpisode)

        assertEquals(1L, second.selectionNoOf("a"))
        assertEquals(2L, second.selectionNoOf("b"))
        assertEquals(1L, second.selectionNoOf("a"))
        assertEquals(1L, first.selectionNoOf("b"))
    }

    @Test
    fun `seq is strictly increasing under concurrent recording`() = runTest {
        val recorder = recorder()
        val episode = recorder.nextEpisodeAttempt(1)
        withContext(Dispatchers.Default) {
            repeat(200) {
                launch { recorder.record(PlayFlowStep.USER_ACTION, episode) { put("action", "x") } }
            }
        }
        val seqs = events().map { it["seq"] as Long }.sorted()
        assertEquals((1L..200L).toList(), seqs)
    }

    @Test
    fun `events beyond the limit are replaced by a single truncated marker`() {
        val recorder = recorder(maxEvents = 3)
        val episode = recorder.nextEpisodeAttempt(1)
        repeat(10) { recorder.record(PlayFlowStep.USER_ACTION, episode) }

        assertEquals(
            listOf("user_action", "user_action", "user_action", "truncated"),
            events().map { it["step"] },
        )
    }

    @Test
    fun `legacy properties carry the session and attempt`() {
        val recorder = recorder()
        val episode = recorder.nextEpisodeAttempt(1)
        assertEquals(mapOf("play_session_id" to "sid", "episode_attempt" to 1L), recorder.legacyProperties(episode))
        assertEquals(mapOf("play_session_id" to "sid", "episode_attempt" to null), recorder.legacyProperties(null))
    }

    @Test
    fun `too many properties fail fast`() {
        val recorder = recorder()
        val episode = recorder.nextEpisodeAttempt(1)
        assertFailsWith<IllegalStateException> {
            recorder.record(PlayFlowStep.ENTER, episode) {
                repeat(PlayFlowRecorder.MAX_PROPERTIES) { put("p$it", it) }
            }
        }
    }

    @Test
    fun `disabled recorder keeps the debug log but sends nothing`() {
        val debugLog = PlayFlowDebugLog()
        val recorder = PlayFlowRecorder(
            subjectId = 1, analytics = analytics, timeSource = timeSource, playSessionId = "sid",
            debugLog = debugLog, enabled = false,
        )
        val episode = recorder.nextEpisodeAttempt(2)
        recorder.record(PlayFlowStep.ENTER, episode) { put("trigger", "open") }
        recorder.record(PlayFlowStep.SEARCH_STARTED, episode)

        assertTrue(events().isEmpty())
        val session = debugLog.sessions.value.single()
        assertEquals("sid", session.playSessionId)
        assertEquals(listOf(1L, 2L), session.events.map { it.properties["seq"] })
        assertEquals(listOf("enter", "search_started"), session.events.map { it.properties["step"] })
    }

    @Test
    fun `shouldReport compares the draw with the configured threshold`() {
        val config = FeatureFlagConfig(buildJsonObject { put("threshold", 0.3) })
        assertTrue(PlayFlowRecorder.shouldReport(config, random = 0.0))
        assertTrue(PlayFlowRecorder.shouldReport(config, random = 0.29))
        assertFalse(PlayFlowRecorder.shouldReport(config, random = 0.3))
        assertFalse(PlayFlowRecorder.shouldReport(config, random = 0.99))
    }

    @Test
    fun `shouldReport never sends without a numeric threshold and always sends at one`() {
        assertFalse(PlayFlowRecorder.shouldReport(FeatureFlagConfig.Empty, random = 0.0))
        assertFalse(PlayFlowRecorder.shouldReport(FeatureFlagConfig(buildJsonObject { put("threshold", "0.3") }), random = 0.0))
        assertFalse(PlayFlowRecorder.shouldReport(FeatureFlagConfig(buildJsonObject { put("enabled", true) }), random = 0.0))
        assertTrue(PlayFlowRecorder.shouldReport(FeatureFlagConfig(buildJsonObject { put("threshold", 1) }), random = 0.999))
    }

    @Test
    fun `shouldReport draws its own random number by default`() {
        val always = FeatureFlagConfig(buildJsonObject { put("threshold", 1) })
        val never = FeatureFlagConfig(buildJsonObject { put("threshold", 0) })
        repeat(20) {
            assertTrue(PlayFlowRecorder.shouldReport(always))
            assertFalse(PlayFlowRecorder.shouldReport(never))
        }
    }
}
