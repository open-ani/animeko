/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.serialization.toJsonElement

/**
 * 调试用的 `play_flow` 事件日志: 在进程内保留最近 [maxSessions] 个播放会话的全部事件, 供调试悬浮窗实时显示与回看.
 *
 * [PlayFlowRecorder] 每记录一条事件就写入一条. 会话按首条事件出现的顺序排列 (旧在前), 会话内事件按记录顺序即 `seq` 递增.
 * 不持久化, 重启后清空.
 */
class PlayFlowDebugLog(
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
) {
    private val _sessions = MutableStateFlow<List<PlayFlowDebugSession>>(emptyList())

    /**
     * 最近的播放会话, 旧在前.
     */
    val sessions: StateFlow<List<PlayFlowDebugSession>> get() = _sessions

    /**
     * 记录一条属于会话 [playSessionId] 的事件. 任意线程可调.
     */
    fun record(
        playSessionId: String,
        subjectId: Int,
        properties: Map<String, Any?>,
        timestampMillis: Long = currentTimeMillis(),
    ) {
        val event = PlayFlowDebugEvent(properties)
        _sessions.update { sessions ->
            val index = sessions.indexOfLast { it.playSessionId == playSessionId }
            if (index >= 0) {
                sessions.toMutableList().also { list ->
                    list[index] = list[index].copy(events = list[index].events + event)
                }
            } else {
                (sessions + PlayFlowDebugSession(playSessionId, subjectId, timestampMillis, listOf(event)))
                    .takeLast(maxSessions)
            }
        }
    }

    fun clear() {
        _sessions.value = emptyList()
    }

    companion object {
        const val DEFAULT_MAX_SESSIONS = 20

        /**
         * 整个 APP 共用的日志, [PlayFlowRecorder] 默认写到这里.
         */
        val Global = PlayFlowDebugLog()
    }
}

/**
 * 一个播放会话 (一个播放页) 里记录的事件.
 *
 * @property startedAtMillis 首条事件的墙上时钟时间.
 */
data class PlayFlowDebugSession(
    val playSessionId: String,
    val subjectId: Int,
    val startedAtMillis: Long,
    val events: List<PlayFlowDebugEvent>,
) {
    /**
     * 会话的全部事件, 每条就是发往 Analytics 的属性 (公共属性在前), 按 `seq` 顺序排成一个 JSON 数组.
     */
    fun toJsonArray(): JsonArray = buildJsonArray {
        for (event in events) add(event.properties.toJsonElement())
    }

    /**
     * [toJsonArray] 的缩进文本, 供复制.
     */
    fun toJsonString(): String = PrettyJson.encodeToString(JsonArray.serializer(), toJsonArray())

    private companion object {
        val PrettyJson = Json { prettyPrint = true }
    }
}

/**
 * 一条 `play_flow` 事件. 公共属性拆成字段, 其余属性按记录顺序放在 [details].
 */
class PlayFlowDebugEvent(
    /** 发往 Analytics 的全部属性. */
    val properties: Map<String, Any?>,
) {
    val seq: Long = properties["seq"] as? Long ?: -1L
    val tMs: Long = properties["t_ms"] as? Long ?: -1L
    val step: String = properties["step"] as? String ?: "?"
    val episodeAttempt: Long? = properties["episode_attempt"] as? Long
    val episodeId: Long? = properties["episode_id"] as? Long

    /** 公共属性之外的属性, 不含 `null` 值. */
    val details: Map<String, Any?> = buildMap {
        for ((key, value) in properties) {
            if (value != null && key !in PlayFlowRecorder.COMMON_KEYS) put(key, value)
        }
    }
}
