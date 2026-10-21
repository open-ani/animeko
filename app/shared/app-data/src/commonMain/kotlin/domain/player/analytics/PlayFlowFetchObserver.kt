/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.analytics

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isDisabled
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.mediasource.web.BlockedException
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark

/**
 * 观察播放页的条目级查询会话: 一轮查询结束 (所有源到达终态, 或用户提前离开) 时, 先把这一轮里失败、限流、需要验证码,
 * 或成功但耗时达到 [slowThreshold] 的源汇总成一条 `source_failed` (每个源一个 `域名=结果` 记号), 再记录一条 `search_done`;
 * 正常完成的源只计入 `search_done` 的 `count`, `media_count` 与 `cached_count`.
 *
 * 查询会话跨集共享, 因此本观察者是页级的, 事件归到记录时的当前剧集尝试 ([PlayFlowRecorder.currentEpisode]).
 * 一轮 (`round`) 从会话创建, 或上一轮结束后首次有源重启开始, 到所有源都到达终态结束; 每轮只统计这一轮里跑过的源.
 *
 * 同时为 `search_started` 事件提供每个会话在各次剧集尝试里首次出现时的类型 (新建 / 复用) 与复用时重试的源数, 见 [describeSearchStart].
 */
class PlayFlowFetchObserver(
    private val recorder: PlayFlowRecorder,
    private val scope: CoroutineScope,
    private val slowThreshold: Duration = DEFAULT_SLOW_THRESHOLD,
) {
    private val lock = SynchronizedObject()
    private val sessions = HashMap<MediaFetchSession, SessionTrack>()

    /**
     * 新会话创建. 开始观察它; 它替换掉的会话停止观察, 还没结束的查询记为 `search_done outcome=incomplete`.
     */
    fun onSessionCreated(session: MediaFetchSession) {
        val track = SessionTrack(session, recorder.markNow())
        val replaced = synchronized(lock) {
            sessions.values.toList().also {
                sessions.clear()
                sessions[session] = track
            }
        }
        for (old in replaced) finish(old)
        track.job = scope.launch(CoroutineName("PlayFlowFetchObserver")) { observe(track) }
    }

    /**
     * 复用了当前会话, 并重试了 [retriedCount] 个源.
     */
    fun onSessionReused(session: MediaFetchSession, retriedCount: Int) {
        synchronized(lock) {
            sessions[session]?.pendingRetried = retriedCount
        }
    }

    /**
     * 剧集尝试 [episode] 首次拿到 [session] 时, 返回 `search_started` 应带的信息; 本次尝试已经报告过该会话时返回 `null`.
     *
     * 不是经 [onSessionCreated] 创建的会话 (查询会话由别处提供) 在此开始观察.
     */
    fun describeSearchStart(session: MediaFetchSession, episode: PlayFlowEpisode): SearchStart? {
        if (synchronized(lock) { session !in sessions }) onSessionCreated(session)
        return describeKnownSearchStart(session, episode)
    }

    private fun describeKnownSearchStart(session: MediaFetchSession, episode: PlayFlowEpisode): SearchStart? = synchronized(lock) {
        val track = sessions[session] ?: return null
        if (!track.reportedAttempts.add(episode.attempt)) return null
        val reused = track.reportedAttempts.size > 1
        SearchStart(
            reused = reused,
            sourceCount = session.mediaSourceResults.count { it.participates() },
            retried = if (reused) track.pendingRetried else 0,
        ).also { track.pendingRetried = 0 }
    }

    /**
     * 播放页关闭. 还没结束的查询记为 `search_done outcome=incomplete`.
     */
    fun close() {
        val tracks = synchronized(lock) { sessions.values.toList().also { sessions.clear() } }
        for (track in tracks) finish(track)
    }

    /**
     * 停止观察 [track]; 它的查询还没全部结束时, 汇总本轮已出问题的源并记一条 `search_done outcome=incomplete`.
     */
    private fun finish(track: SessionTrack) {
        track.job?.cancel()
        synchronized(lock) {
            if (track.sources.any { !it.state.isFinal }) summarize(track, outcome = "incomplete") else null
        }?.invoke()
    }

    /**
     * 观察会话里每个源的状态与结果, 直到被取消 ([finish]). 会话被替换或页面关闭时取消, 不让被替换的会话因本观察者而一直活着.
     */
    private suspend fun observe(track: SessionTrack) = coroutineScope {
        synchronized(lock) {
            for (result in track.session.mediaSourceResults) {
                if (result.kind == MediaSourceKind.LocalCache) continue
                track.sources += SourceTrack(result)
            }
        }
        for (source in track.sources) {
            launch {
                source.result.state.collect { state ->
                    val events = synchronized(lock) {
                        source.onState(state, recorder.markNow())
                        evaluate(track)
                    }
                    events?.invoke()
                }
            }
            launch {
                source.result.results.collect { list -> synchronized(lock) { source.resultCount = list.size } }
            }
        }
        // 没有参与的源 (例如全部禁用) 时立即结束
        synchronized(lock) { evaluate(track) }?.invoke()
    }

    /**
     * 按各源的最新状态更新本轮统计: 本代还没统计过且已到终态的源计入本轮, 出了问题的 ([SourceTrack.problem]) 记到本轮的问题清单;
     * 所有源都到终态且本轮还没收尾时记录 `source_failed` (有问题的源时) 与 `search_done`. 上一轮结束后又有源在跑则先开新一轮.
     * 终态直接跟着上一代的终态出现 (StateFlow 合并了中间状态) 时, 也能据此开新一轮并结束它.
     *
     * 在锁内调用; 返回的记录动作在锁外执行.
     */
    private fun evaluate(track: SessionTrack): (() -> Unit)? {
        val unreported = track.sources.filter { !it.state.isDisabled && it.generation != it.reportedGeneration }
        if (track.round.closed && unreported.isNotEmpty()) {
            // 上一轮结束后又有源在跑: 开新一轮, 从最早重启的源算起
            val start = unreported.mapNotNull { it.startedAt }.maxByOrNull { it.elapsedNow() } ?: recorder.markNow()
            track.round = Round(track.round.number + 1, start)
        }
        val round = track.round
        for (source in unreported) {
            if (source.state !is MediaSourceFetchState.Completed) continue
            source.reportedGeneration = source.generation
            round.sourceCount++
            if (source.result.searchStats.value?.isCacheOnly == true) round.cachedCount++
            source.problem(slowThreshold)?.let { round.problems += it }
        }
        if (round.closed || !track.sources.all { it.state.isFinal }) return null
        round.closed = true
        return summarize(track, outcome = "complete")
    }

    /**
     * 取本轮的统计快照, 返回记录 `source_failed` (有问题的源时, 装不下时按 `part` 分成多条) 与 `search_done` 的动作.
     * 在锁内调用; 返回的动作在锁外执行.
     */
    private fun summarize(track: SessionTrack, outcome: String): () -> Unit {
        val episode = recorder.currentEpisode
        val round = track.round
        val number = round.number
        val sourceCount = round.sourceCount
        val cachedCount = round.cachedCount
        val mediaCount = track.sources.sumOf { it.resultCount }
        val duration = round.start.elapsedNow()
        // 失败类在前, 慢源在后
        val problems = round.problems.sortedBy { it.outcome == SLOW_OUTCOME }
        return {
            packPlayFlowTokens(problems.map { it.token }).chunked(MAX_SOURCE_CHUNKS).forEachIndexed { partIndex, chunks ->
                recorder.record(PlayFlowStep.SOURCE_FAILED, episode) {
                    put("round", number)
                    put("count", problems.size)
                    put("part", partIndex + 1)
                    chunks.forEachIndexed { index, chunk -> put("sources_${index + 1}", chunk) }
                }
            }
            recorder.record(PlayFlowStep.SEARCH_DONE, episode) {
                put("outcome", outcome)
                put("round", number)
                put("count", sourceCount)
                put("media_count", mediaCount)
                put("cached_count", cachedCount)
                put("duration_ms", duration)
            }
        }
    }

    private fun MediaSourceFetchResult.participates(): Boolean =
        kind != MediaSourceKind.LocalCache && !state.value.isDisabled

    class SearchStart(
        val reused: Boolean,
        val sourceCount: Int,
        val retried: Int,
    )

    private class SessionTrack(
        val session: MediaFetchSession,
        start: TimeMark,
    ) {
        var job: Job? = null
        val sources = ArrayList<SourceTrack>()
        val reportedAttempts = HashSet<Long>()
        var pendingRetried = 0
        var round = Round(1, start)
    }

    /**
     * 一轮查询的统计: 从 [start] 到所有源到达终态 ([closed]).
     */
    private class Round(val number: Int, val start: TimeMark) {
        var closed = false

        /** 已到终态的源数. */
        var sourceCount = 0

        /** 完全由搜索缓存满足的源数. */
        var cachedCount = 0

        /** 出了问题的源, 按到达终态的顺序. */
        val problems = ArrayList<SourceProblem>()
    }

    private class SourceTrack(
        val result: MediaSourceFetchResult,
    ) {
        var state: MediaSourceFetchState = result.state.value
        var generation = 0
        var reportedGeneration = -1
        var startedAt: TimeMark? = null

        /** 本代从开始到终态的时长; 还没到终态, 或开始时就已是终态时为 `null`. */
        var duration: Duration? = null
        var resultCount = 0

        fun onState(new: MediaSourceFetchState, now: TimeMark) {
            val old = state
            state = new
            when (new) {
                MediaSourceFetchState.Idle, MediaSourceFetchState.Working -> {
                    if (old.isFinal) {
                        generation++
                        startedAt = now
                        duration = null
                    } else if (startedAt == null) {
                        startedAt = now
                    }
                }

                is MediaSourceFetchState.Completed -> {
                    // StateFlow 会合并中间状态, 终态可能紧跟上一代的终态出现
                    if (old is MediaSourceFetchState.Completed && old.id != new.id) {
                        generation++
                        startedAt = now
                        duration = null
                    }
                    if (duration == null) duration = startedAt?.elapsedNow()
                }

                MediaSourceFetchState.Disabled -> {}
            }
        }

        /**
         * 这一代出的问题: 失败 (结果为异常简名或阻断原因)、限流、需要验证码, 或成功但耗时达到 [slowThreshold] (结果为 [SLOW_OUTCOME]).
         * 没有问题, 或被取消 (不是源本身的问题) 时为 `null`.
         */
        fun problem(slowThreshold: Duration): SourceProblem? {
            val state = state
            val outcome = when (state) {
                is MediaSourceFetchState.Succeed -> if (duration?.let { it >= slowThreshold } == true) SLOW_OUTCOME else return null
                is MediaSourceFetchState.Failed -> state.cause.reasonName()
                is MediaSourceFetchState.CaptchaRequired -> "captcha_required"
                is MediaSourceFetchState.RateLimited -> "rate_limited"
                is MediaSourceFetchState.Abandoned, MediaSourceFetchState.Idle, MediaSourceFetchState.Working, MediaSourceFetchState.Disabled -> return null
            }
            // 源的标识: 网址的主机名, 没有网址时是 mediaSourceId
            val host = playFlowHost(result.sourceInfo.websiteUrl) ?: result.mediaSourceId
            return SourceProblem(host, outcome)
        }

        private fun Throwable.reasonName(): String = when (this) {
            is BlockedException -> reason::class.simpleName ?: "blocked"
            else -> this::class.simpleName ?: "unknown"
        }
    }

    private class SourceProblem(
        val host: String,
        val outcome: String,
    ) {
        /** `source_failed.sources_*` 里的记号. */
        val token: String get() = "$host=$outcome"
    }

    companion object {
        /** 成功的源耗时达到这个值时也计入 `source_failed`, 结果为 [SLOW_OUTCOME]. */
        val DEFAULT_SLOW_THRESHOLD: Duration = 10.seconds

        /** 成功但慢的源在 `source_failed` 记号里的结果. */
        const val SLOW_OUTCOME = "slow"

        /**
         * 一条 `source_failed` 最多装这么多段 `sources_N`: GA4 的属性上限减去公共属性与 `round` / `count` / `part`. 更多时分成多条.
         */
        val MAX_SOURCE_CHUNKS: Int = PlayFlowRecorder.MAX_PROPERTIES - PlayFlowRecorder.COMMON_KEYS.size - 3
    }
}
