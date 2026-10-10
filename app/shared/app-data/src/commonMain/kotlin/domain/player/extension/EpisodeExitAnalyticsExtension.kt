/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.EpisodeExit
import me.him188.ani.utils.analytics.IAnalytics
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlayerState
import org.openani.mediamp.isMediaLoaded
import org.openani.mediamp.source.MediaData
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 离开剧集时记录本集的播放情况 ([EpisodeExit]): 是否起播, 起播耗时, 播放与卡顿时长, 换源次数等,
 * 用来区分用户很快离开是因为没能起播, 卡顿, 还是内容本身.
 *
 * 切换剧集 (手动或自动连播) 或关闭播放页时记录, 每个 [EpisodeSession] 只记录一次. 进程被杀时不会记录.
 *
 * 时长都是墙上时间, 从 [onStart] (进入本集) 起算. 卡顿只统计当前资源起播之后的缓冲, 包括拖动进度 (含恢复播放进度) 后的缓冲;
 * 打开资源后到首次起播之间的加载不算卡顿, 换源后同样重新等起播.
 */
class EpisodeExitAnalyticsExtension(
    private val context: PlayerExtensionContext,
    private val analytics: IAnalytics = Analytics,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : PlayerExtension("EpisodeExitAnalytics") {
    private val lock = Mutex()

    /**
     * 当前剧集的统计. 记录时取出并置为 `null`, 因此 [onBeforeSwitchEpisode] 与 [onClose] 都被调用时也只记录一次.
     */
    private val currentStats = MutableStateFlow<EpisodePlaybackStats?>(null)

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val stats = EpisodePlaybackStats(episodeSession.episodeId, timeSource.markNow())
        currentStats.value = stats

        backgroundTaskScope.launch("PlayerState") {
            val player = context.player
            player.state.collect { state ->
                lock.withLock { stats.onPlayerState(state, player.mediaData.value) }
            }
        }
        backgroundTaskScope.launch("SelectedMedia") {
            episodeSession.fetchSelectFlow.filterNotNull().collectLatest { fetchSelect ->
                fetchSelect.mediaSelector.selected.filterNotNull().collect { media ->
                    lock.withLock { stats.onSelect(media) }
                }
            }
        }
    }

    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        record(EXIT_REASON_SWITCH_EPISODE)
    }

    override suspend fun onClose() {
        record(EXIT_REASON_CLOSE)
    }

    private suspend fun record(exitReason: String) {
        val stats = currentStats.getAndUpdate { null } ?: return
        // 两种离开都发生在停止播放器之前 (切集时已暂停), 位置和时长仍属于本集.
        // 后台任务可能已经停止, 没有观察到暂停, 仍在进行的播放和卡顿算到现在为止.
        val player = context.player
        val positionMillis = player.currentPositionMillis.value.takeIf { player.state.value.isMediaLoaded }
        val durationMillis = player.mediaProperties.value?.durationMillis?.takeIf { it > 0 }
        val properties = lock.withLock {
            val now = stats.elapsed()
            val selected = stats.selected
            buildMap<String, Any?> {
                put("subject_id", context.subjectId.toLong())
                put("episode_id", stats.episodeId.toLong())
                put("exit_reason", exitReason)
                put("reached_playing", (stats.firstPlaying != null).toAnalyticsFlag())
                put("first_frame_ms", stats.firstPlaying?.inWholeMilliseconds)
                put("watched_ms", stats.playing.total(now).inWholeMilliseconds)
                put("position_ms", positionMillis)
                put("duration_ms", durationMillis)
                put("buffering_count", stats.buffering.count.toLong())
                put("buffering_ms", stats.buffering.total(now).inWholeMilliseconds)
                put("had_error", stats.hadError.toAnalyticsFlag())
                put("media_source_id", selected?.mediaSourceId?.take(100))
                put("is_cached", selected?.isLocalCache()?.toAnalyticsFlag())
                put("source_switch_count", stats.sourceSwitchCount.toLong())
            }
        }
        analytics.recordEvent(EpisodeExit, properties)
    }

    companion object : EpisodePlayerExtensionFactory<EpisodeExitAnalyticsExtension> {
        private const val EXIT_REASON_CLOSE = "close"
        private const val EXIT_REASON_SWITCH_EPISODE = "switch_episode"

        override fun create(context: PlayerExtensionContext, koin: Koin): EpisodeExitAnalyticsExtension {
            return EpisodeExitAnalyticsExtension(context)
        }
    }
}

/**
 * 一个 [EpisodeSession] 内的播放统计. 时间都是相对 [start] 的时长.
 */
private class EpisodePlaybackStats(
    val episodeId: Int,
    private val start: TimeMark,
) {
    var firstPlaying: Duration? = null
        private set
    val playing = StateTimer()
    val buffering = StateTimer()
    var hadError = false
        private set
    var selected: Media? = null
        private set
    var sourceSwitchCount = 0
        private set

    /**
     * 最近起播过的资源. 当前资源 (同一个 [MediaData]) 起播之后的缓冲才算卡顿.
     */
    private var startedMediaData: MediaData? = null

    fun elapsed(): Duration = start.elapsedNow()

    fun onPlayerState(state: PlayerState, mediaData: MediaData?) {
        val now = elapsed()
        if (state.mediaStatus is MediaStatus.Error) {
            hadError = true
        }
        if (state.isPlaying) {
            if (firstPlaying == null) firstPlaying = now
            startedMediaData = mediaData
        }
        val currentMediaStarted = mediaData != null && mediaData === startedMediaData
        playing.update(state.isPlaying, now)
        // 暂停时的缓冲用户不在等待, 不算卡顿
        buffering.update(currentMediaStarted && state.playWhenReady && state.isBuffering, now)
    }

    fun onSelect(media: Media) {
        val previous = selected
        if (previous != null && previous.mediaId != media.mediaId) {
            sourceSwitchCount++
        }
        selected = media
    }
}

/**
 * 统计一个状态进入的次数和累计时长.
 */
private class StateTimer {
    var count = 0
        private set
    private var total = Duration.ZERO
    private var since: Duration? = null

    fun update(active: Boolean, now: Duration) {
        val start = since
        if (active && start == null) {
            count++
            since = now
        } else if (!active && start != null) {
            total += now - start
            since = null
        }
    }

    /**
     * 截至 [now] 的累计时长, 包括仍在进行的一段.
     */
    fun total(now: Duration): Duration = total + (since?.let { now - it } ?: Duration.ZERO)
}
