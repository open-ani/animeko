/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.him188.ani.app.domain.player.extension.RecordingAnalytics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TestTimeSource

class PlayFlowDebugLogTest {
    @Test
    fun `groups events by play session in arrival order`() {
        val log = PlayFlowDebugLog()
        log.record("a", 1, props(step = "enter", seq = 1, tMs = 0, attempt = 1, episodeId = 10), timestampMillis = 100)
        log.record("b", 2, props(step = "enter", playSessionId = "b", seq = 1, tMs = 0, attempt = 1, episodeId = 20), timestampMillis = 200)
        log.record("a", 1, props(step = "info_loaded", seq = 2, tMs = 50, attempt = 1, episodeId = 10, "outcome" to "ok"))

        val sessions = log.sessions.value
        assertEquals(listOf("a", "b"), sessions.map { it.playSessionId })
        val a = sessions[0]
        assertEquals(1, a.subjectId)
        assertEquals(100L, a.startedAtMillis)
        assertEquals(listOf(1L, 2L), a.events.map { it.seq })
        val info = a.events[1]
        assertEquals("info_loaded", info.step)
        assertEquals(50L, info.tMs)
        assertEquals(1L, info.episodeAttempt)
        assertEquals(10L, info.episodeId)
        // 公共属性不进 details, null 值丢弃
        assertEquals(mapOf<String, Any?>("outcome" to "ok"), info.details)
    }

    @Test
    fun `keeps only the most recent sessions`() {
        val log = PlayFlowDebugLog(maxSessions = 2)
        for (id in listOf("a", "b", "c")) {
            log.record(id, 1, props(step = "enter", seq = 1, tMs = 0, attempt = 1, episodeId = 1))
        }
        assertEquals(listOf("b", "c"), log.sessions.value.map { it.playSessionId })
    }

    @Test
    fun `recorder forwards every posted event including truncation`() {
        val log = PlayFlowDebugLog()
        val recorder = PlayFlowRecorder(7, RecordingAnalytics(), TestTimeSource(), "sid", maxEvents = 2, debugLog = log)
        val episode = recorder.nextEpisodeAttempt(42)
        repeat(4) { recorder.record(PlayFlowStep.ENTER, episode) }

        val session = log.sessions.value.single()
        assertEquals("sid", session.playSessionId)
        assertEquals(7, session.subjectId)
        assertEquals(listOf("enter", "enter", "truncated"), session.events.map { it.step })
        assertEquals(listOf(1L, 2L, 3L), session.events.map { it.seq })
    }

    @Test
    fun `session exports its events as a json array with the analytics property names`() {
        val log = PlayFlowDebugLog()
        log.record("a", 1, props(step = "enter", seq = 1, tMs = 0, attempt = 1, episodeId = 10, "trigger" to "open", "is_fullscreen" to 1L))
        log.record("a", 1, props(step = "info_loaded", seq = 2, tMs = 50, attempt = 1, episodeId = 10, "outcome" to "ok"))

        val array = log.sessions.value.single().toJsonArray()
        assertEquals(2, array.size)
        val enter = array[0].jsonObject
        assertEquals("enter", enter["step"]?.jsonPrimitive?.content)
        assertEquals("a", enter["play_session_id"]?.jsonPrimitive?.content)
        assertEquals(1L, enter["episode_attempt"]?.jsonPrimitive?.long)
        assertEquals(1L, enter["seq"]?.jsonPrimitive?.long)
        assertEquals(0L, enter["t_ms"]?.jsonPrimitive?.long)
        assertEquals(1L, enter["subject_id"]?.jsonPrimitive?.long)
        assertEquals(10L, enter["episode_id"]?.jsonPrimitive?.long)
        assertEquals("open", enter["trigger"]?.jsonPrimitive?.content)
        assertEquals(1L, enter["is_fullscreen"]?.jsonPrimitive?.long)
        assertEquals("ok", array[1].jsonObject["outcome"]?.jsonPrimitive?.content)

        val text = log.sessions.value.single().toJsonString()
        assertEquals(array, Json.parseToJsonElement(text))
    }

    private fun props(
        step: String,
        seq: Long,
        tMs: Long,
        attempt: Long,
        episodeId: Long,
        vararg details: Pair<String, Any?>,
        playSessionId: String = "a",
    ): Map<String, Any?> = buildMap {
        put("step", step)
        put("play_session_id", playSessionId)
        put("episode_attempt", attempt)
        put("seq", seq)
        put("t_ms", tMs)
        put("subject_id", 1L)
        put("episode_id", episodeId)
        put("nothing", null)
        putAll(details)
    }
}
