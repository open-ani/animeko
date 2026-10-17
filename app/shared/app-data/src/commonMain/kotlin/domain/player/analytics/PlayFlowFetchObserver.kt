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
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.web.BlockedException
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.time.Duration
import kotlin.time.TimeMark

/**
 * 观察播放页的条目级查询会话, 在每轮查询全部结束时为每个参与的数据源记录一条 `search_source_done`, 再记录一条 `search_done`.
 *
 * 查询会话跨集共享, 因此本观察者是页级的, 事件归到记录时的当前剧集尝试 ([PlayFlowRecorder.currentEpisode]).
 * 一轮 (`round`) 从会话创建, 或上一轮结束后首次有源重启开始, 到所有源都到达终态结束; 每轮只记录这一轮里跑过的源.
 *
 * 同时为 `search_started` 事件提供每个会话在各次剧集尝试里首次出现时的类型 (新建 / 复用) 与复用时重试的源数, 见 [describeSearchStart].
 */
class PlayFlowFetchObserver(
    private val recorder: PlayFlowRecorder,
    private val scope: CoroutineScope,
    private val sourceTiers: () -> MediaSelectorSourceTiers?,
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
     * 停止观察 [track]; 它的查询还没全部结束时记一条 `search_done outcome=incomplete`.
     */
    private fun finish(track: SessionTrack) {
        track.job?.cancel()
        val incomplete = synchronized(lock) { track.sources.any { !it.state.isFinal } }
        if (incomplete) {
            recorder.record(PlayFlowStep.SEARCH_DONE, recorder.currentEpisode) {
                put("outcome", "incomplete")
                put("round", track.round)
                put("count", track.sources.size)
                put("duration_ms", track.roundStart.elapsedNow())
            }
        }
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
     * 按各源的最新状态判断本轮是否结束. 终态直接跟着上一代的终态出现 (StateFlow 合并了中间状态) 时, 也能据此开新一轮并结束它.
     * 在锁内调用; 返回的记录动作在锁外执行.
     */
    private fun evaluate(track: SessionTrack): (() -> Unit)? {
        val allFinal = track.sources.all { it.state.isFinal }
        val pending = track.sources.filter { !it.state.isDisabled && it.generation != it.reportedGeneration }
        if (track.roundClosed && pending.isNotEmpty()) {
            // 上一轮结束后又有源在跑: 开新一轮, 从最早重启的源算起
            track.round++
            track.roundStart = pending.mapNotNull { it.startedAt }.maxByOrNull { it.elapsedNow() } ?: recorder.markNow()
            track.roundClosed = false
        }
        if (!allFinal || track.roundClosed) return null
        return completeRound(track, pending)
    }

    /**
     * 本轮结束: 收集需要记录的源. 在锁内计算, 锁外记录.
     */
    private fun completeRound(track: SessionTrack, pending: List<SourceTrack>): () -> Unit {
        track.roundClosed = true
        val episode = recorder.currentEpisode
        val tiers = sourceTiers()
        for (source in pending) source.reportedGeneration = source.generation
        val round = track.round
        val roundDuration = track.roundStart.elapsedNow()
        val snapshots = pending.map { it.snapshot() }
        val totalResults = track.sources.sumOf { it.resultCount }
        return {
            for (snapshot in snapshots) {
                recorder.record(PlayFlowStep.SEARCH_SOURCE_DONE, episode) {
                    put("media_source_id", snapshot.mediaSourceId)
                    put("kind", snapshot.kind.toPlayFlowKind())
                    put("outcome", snapshot.outcome)
                    put("reason", snapshot.reason)
                    put("count", snapshot.resultCount)
                    put("tier", tiers?.get(snapshot.mediaSourceId)?.value?.toLong())
                    put("restarts", snapshot.restarts)
                    put("duration_ms", snapshot.duration)
                    put("is_cached", snapshot.cacheHit)
                    put("round", round)
                }
            }
            recorder.record(PlayFlowStep.SEARCH_DONE, episode) {
                put("outcome", "complete")
                put("round", round)
                put("count", snapshots.size)
                put("media_count", totalResults)
                put("duration_ms", roundDuration)
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
        var roundStart: TimeMark,
    ) {
        var job: Job? = null
        val sources = ArrayList<SourceTrack>()
        val reportedAttempts = HashSet<Long>()
        var pendingRetried = 0
        var round = 1
        var roundClosed = false
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

        fun snapshot(): SourceSnapshot {
            val state = state
            val (outcome, reason) = when (state) {
                is MediaSourceFetchState.Succeed -> "ok" to null
                is MediaSourceFetchState.Failed -> "failed" to state.cause.reasonName()
                is MediaSourceFetchState.CaptchaRequired -> "captcha_required" to null
                is MediaSourceFetchState.RateLimited -> "rate_limited" to null
                is MediaSourceFetchState.Abandoned -> "cancelled" to null
                MediaSourceFetchState.Idle -> "not_started" to null
                MediaSourceFetchState.Working -> "working" to null
                MediaSourceFetchState.Disabled -> "disabled" to null
            }
            return SourceSnapshot(
                mediaSourceId = result.mediaSourceId,
                kind = result.kind,
                outcome = outcome,
                reason = reason,
                resultCount = resultCount,
                restarts = generation,
                duration = duration,
                cacheHit = result.searchStats.value?.isCacheOnly,
            )
        }

        private fun Throwable.reasonName(): String = when (this) {
            is BlockedException -> reason::class.simpleName ?: "blocked"
            else -> this::class.simpleName ?: "unknown"
        }
    }

    private class SourceSnapshot(
        val mediaSourceId: String,
        val kind: MediaSourceKind,
        val outcome: String,
        val reason: String?,
        val resultCount: Int,
        val restarts: Int,
        val duration: Duration?,
        val cacheHit: Boolean?,
    )
}
