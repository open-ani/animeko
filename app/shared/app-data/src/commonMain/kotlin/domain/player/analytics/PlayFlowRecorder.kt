/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import me.him188.ani.app.domain.featureflag.FeatureFlagConfig
import me.him188.ani.app.domain.player.extension.toAnalyticsFlag
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.analytics.IAnalytics
import me.him188.ani.utils.platform.Uuid
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 记录一个播放页内加载流程的 [AnalyticsEvent.PlayFlow] 事件.
 *
 * 一个播放页 (一个 `EpisodeViewModel`) 一个实例, 持有本次播放会话的 [playSessionId]. 页内每切一集 (包括 TV 的同集重试)
 * 用 [nextEpisodeAttempt] 开始一次新的剧集尝试 [PlayFlowEpisode]; 事件带上它所属的尝试, 由持有它的任务在开始时捕获,
 * 切集后旧任务补发的事件仍归到旧的尝试.
 *
 * 每条事件带公共属性 `step`, `play_session_id`, `episode_attempt`, `seq`, `t_ms`, `subject_id`, `episode_id`.
 * `seq` 在本页内严格递增, 是重放时唯一可靠的排序键; `t_ms` 是距进入播放页的毫秒数.
 *
 * [record] 不挂起, 任意线程可调. 事件数超过 [maxEvents] 时只再发一条 `step=truncated`, 防止异常循环刷爆配额.
 * 发出的每条事件同时写入 [debugLog], 供调试悬浮窗显示.
 * [enabled] 为 `false` 时 (功能开关 `play_flow_report` 没抽中本次播放会话, 见 [shouldReport]) 不向 [analytics] 发送, 只写入 [debugLog]; 序号照常递增.
 *
 * 事件清单与属性含义见 `docs/contributing/code/analytics-play-flow.md`.
 */
class PlayFlowRecorder(
    val subjectId: Int,
    private val analytics: IAnalytics = Analytics,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    val playSessionId: String = Uuid.randomString(),
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val debugLog: PlayFlowDebugLog? = PlayFlowDebugLog.Global,
    private val enabled: Boolean = true,
) {
    private val start: TimeMark = timeSource.markNow()
    private val seq = atomic(0L)
    private val attempts = atomic(0L)
    private val current = atomic<PlayFlowEpisode?>(null)

    /**
     * 当前的剧集尝试. 只给无法在任务开始时捕获尝试的页级观察者使用 (条目级查询会话); 扩展应使用 `EpisodeSession.playFlowEpisode`.
     */
    val currentEpisode: PlayFlowEpisode? get() = current.value

    /**
     * 距进入播放页的时长.
     */
    fun elapsed(): Duration = start.elapsedNow()

    /**
     * 与本记录器相同时间源的当前时刻, 供各阶段计时.
     */
    fun markNow(): TimeMark = timeSource.markNow()

    /**
     * 开始一次新的剧集尝试, 编号从 1 起.
     */
    fun nextEpisodeAttempt(episodeId: Int): PlayFlowEpisode {
        val episode = PlayFlowEpisode(episodeId, attempts.incrementAndGet(), timeSource.markNow())
        current.value = episode
        return episode
    }

    /**
     * 记录一条 [AnalyticsEvent.PlayFlow] 事件. [episode] 为 `null` 时不带 `episode_attempt` 与 `episode_id`.
     */
    fun record(step: PlayFlowStep, episode: PlayFlowEpisode?, build: PlayFlowProps.() -> Unit = {}) {
        val seq = seq.incrementAndGet()
        if (seq > maxEvents) {
            if (seq == maxEvents + 1L) {
                post(commonProperties(PlayFlowStep.TRUNCATED, episode, seq))
            }
            return
        }
        post(PlayFlowProps(commonProperties(step, episode, seq)).apply(build).build())
    }

    private fun post(properties: Map<String, Any?>) {
        if (enabled) analytics.recordEvent(AnalyticsEvent.PlayFlow, properties)
        debugLog?.record(playSessionId, subjectId, properties)
    }

    /**
     * 供既有事件 (`episode_enter`, `episode_switch`, `media_fetch_result`, `episode_playing`, `episode_exit`) 附加的关联属性.
     */
    fun legacyProperties(episode: PlayFlowEpisode?): Map<String, Any?> = buildMap {
        put("play_session_id", playSessionId)
        put("episode_attempt", episode?.attempt)
    }

    /**
     * 同 [legacyProperties], 按尝试编号 [attempt]; 给还没有 [PlayFlowEpisode] 的调用方 (页面创建时的 `episode_enter`).
     */
    fun legacyProperties(attempt: Long): Map<String, Any?> = buildMap {
        put("play_session_id", playSessionId)
        put("episode_attempt", attempt)
    }

    private fun commonProperties(step: PlayFlowStep, episode: PlayFlowEpisode?, seq: Long): MutableMap<String, Any?> =
        mutableMapOf(
            "step" to step.value,
            "play_session_id" to playSessionId,
            "episode_attempt" to episode?.attempt,
            "seq" to seq,
            "t_ms" to elapsed().inWholeMilliseconds,
            "subject_id" to subjectId.toLong(),
            "episode_id" to episode?.episodeId?.toLong(),
        )

    companion object {
        const val DEFAULT_MAX_EVENTS = 400

        /** 每条事件都带的公共属性, 顺序与 [commonProperties] 一致. */
        val COMMON_KEYS: List<String> = listOf("step", "play_session_id", "episode_attempt", "seq", "t_ms", "subject_id", "episode_id")

        /**
         * GA4 单条事件的参数上限.
         */
        const val MAX_PROPERTIES = 25

        /**
         * GA4 字符串参数值的长度上限.
         */
        const val MAX_STRING_LENGTH = 100

        /**
         * 按功能开关 `play_flow_report` 的配置 [config] 决定一个播放会话是否发送: [random] 是本次抽到的 `[0, 1)` 随机数,
         * 小于配置的 `threshold` (默认 `0`, 即不发送) 才发送. 进入播放页时抽一次, 整个播放会话用同一个结果.
         */
        fun shouldReport(config: FeatureFlagConfig, random: Double = Random.nextDouble()): Boolean =
            random < config.double("threshold", 0.0)
    }
}

/**
 * 播放页内的一次剧集尝试: 进入播放页是第 1 次, 之后每次切集 (含 TV 的同集重试) 加一.
 */
class PlayFlowEpisode(
    val episodeId: Int,
    val attempt: Long,
    /** 本次尝试开始的时刻. */
    val startedAt: TimeMark,
) {
    private val lock = SynchronizedObject()
    private val selections = HashMap<String, Long>()

    /**
     * 资源 [mediaId] 在本次尝试内的选择编号 `sel_no`, 从 1 起按首次询问的顺序分配; 同一资源再次被选中沿用同一编号.
     * 它把一次选择与之后的加载、起播、换源事件串起来. 选择事件、自动选择结果与加载任务在不同扩展里, 谁先问到谁分配.
     */
    fun selectionNoOf(mediaId: String): Long = synchronized(lock) {
        selections.getOrPut(mediaId) { selections.size + 1L }
    }

    override fun toString(): String = "PlayFlowEpisode(episodeId=$episodeId, attempt=$attempt)"
}

/**
 * [AnalyticsEvent.PlayFlow] 的 `step` 取值.
 */
enum class PlayFlowStep(val value: String) {
    /** 进入播放页 / 页内切集 / 自动连播 / TV 重试. */
    ENTER("enter"),

    /** 剧集信息加载完成或失败. */
    INFO_LOADED("info_loaded"),

    /** 开始查找资源. */
    SEARCH_STARTED("search_started"),

    /** 自动选源有了结果. */
    AUTO_SELECT_DONE("auto_select_done"),

    /** 用户手动选了一个资源. */
    USER_SELECT("user_select"),

    /** 手动查找的一步完成. */
    MANUAL_SEARCH("manual_search"),

    /** 用户为推进播放做的其他操作. */
    USER_ACTION("user_action"),

    /** 选中的资源加载完毕: 可以播放, 失败, 或被打断. */
    VIDEO_LOAD_DONE("video_load_done"),

    /** 失败后系统自动换源的结果. */
    AUTO_SWITCH("auto_switch"),

    /** 画面开始播放. */
    PLAYBACK_STARTED("playback_started"),

    /** 已在播放中出错. */
    PLAY_ERROR("play_error"),

    /** 一轮查询结束时, 这一轮里失败、限流、需要验证码或成功但慢的源的汇总; 一条装不下时分成多条 (`part`). 正常完成的源只计入 `search_done`. */
    SOURCE_FAILED("source_failed"),

    /** 查询全部结束, 或用户在结束前离开. */
    SEARCH_DONE("search_done"),

    /** 事件数超过上限, 之后的事件被丢弃. */
    TRUNCATED("truncated"),
}

/**
 * 构造事件属性, 统一转换成 GA4 接受的类型: 整数为 `Long`, 布尔为 `1L` / `0L`, 时长为毫秒, 枚举为小写名称,
 * 字符串截断到 [PlayFlowRecorder.MAX_STRING_LENGTH]; `null` 丢弃.
 */
class PlayFlowProps internal constructor(
    private val map: MutableMap<String, Any?>,
) {
    fun put(key: String, value: String?) {
        map[key] = value?.take(PlayFlowRecorder.MAX_STRING_LENGTH)
    }

    fun put(key: String, value: Long?) {
        map[key] = value
    }

    fun put(key: String, value: Int?) {
        map[key] = value?.toLong()
    }

    fun put(key: String, value: Boolean?) {
        map[key] = value?.toAnalyticsFlag()
    }

    fun put(key: String, value: Duration?) {
        map[key] = value?.inWholeMilliseconds
    }

    fun put(key: String, value: Enum<*>?) {
        map[key] = value?.name?.lowercase()
    }

    internal fun build(): Map<String, Any?> {
        check(map.size <= PlayFlowRecorder.MAX_PROPERTIES) {
            "play_flow event has ${map.size} properties, exceeding ${PlayFlowRecorder.MAX_PROPERTIES}: ${map.keys}"
        }
        return map
    }
}
