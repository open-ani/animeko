/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.watchtogether.HostAlignment
import me.him188.ani.app.domain.watchtogether.HostAlignmentRequest
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.WatchTogetherState
import me.him188.ani.app.domain.watchtogether.positionAt
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackEvent
import org.openani.mediamp.isMediaLoaded
import kotlin.math.roundToLong
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Runs under the player extension's playback mutex; playback rate is never changed here. */
internal class HostAlignmentExecutor(
    private val player: MediampPlayer,
    private val bridge: LocalPlaybackBridge,
    private val manager: WatchTogetherManager,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var seekLatencyEstimate = HostAlignment.INITIAL_SEEK_LATENCY_MILLIS

    suspend fun run(request: HostAlignmentRequest, subjectId: Int, episodeId: Int) = coroutineScope {
        val session = request.session
        val initialHost = session.snapshot.value.playback?.info
        if (initialHost == null || initialHost.subjectId != subjectId || initialHost.episodeId != episodeId ||
            HostAlignment.unavailableReason(initialHost, bridge.localWatching.value) != null
        ) {
            manager.notifyAlignmentFinished(false)
            return@coroutineScope
        }
        val media = player.mediaData.value
        if (media == null) {
            manager.notifyAlignmentFinished(false)
            return@coroutineScope
        }
        val execution = currentCoroutineContext().job
        val guard = launch(start = CoroutineStart.UNDISPATCHED) {
            combine(manager.state, session.snapshot, bridge.localWatching, player.mediaData) { state, snapshot, local, data ->
                val host = snapshot.playback?.info
                (state as? WatchTogetherState.InRoom)?.session !== session || session.isClosing ||
                        data !== media || host == null || host.subjectId != subjectId || host.episodeId != episodeId ||
                        host.paused != initialHost.paused || host.playbackRate != initialHost.playbackRate ||
                        host.buffering == true || host.loading == true ||
                        local == null || local.subjectId != subjectId || local.episodeId != episodeId
            }.first { it }
            execution.cancel(CancellationException("Host alignment context changed"))
        }
        var temporarilyPaused = false
        var seekCount = 0
        var residual: Long? = null
        var success = false
        var measuredLatency = 0L
        try {
            success = withTimeoutOrNull(HostAlignment.TIMEOUT_MILLIS) {
                if (!manager.refreshAlignmentClock(session)) return@withTimeoutOrNull false
                while (seekCount < 2) {
                    val host = session.snapshot.value.playback?.info ?: return@withTimeoutOrNull false
                    val target = HostAlignment.seekPosition(host, session.serverClock.now(), seekLatencyEstimate)
                    // Wait for the backend acknowledgement rather than the optimistic position StateFlow.
                    val completed = async(start = CoroutineStart.UNDISPATCHED) {
                        player.events.filterIsInstance<PlaybackEvent.SeekCompleted>().first()
                    }
                    val started = timeSource.markNow()
                    withContext(player.mainDispatcher) {
                        if (host.paused) player.pause()
                        player.seekTo(target)
                        if (!host.paused) player.play()
                    }
                    seekCount++
                    val landing = completed.await()
                    measuredLatency = awaitStablePlayback(host.paused, started)
                    seekLatencyEstimate = measuredLatency
                    if (host.paused) {
                        val finalHost = session.snapshot.value.playback?.info ?: return@withTimeoutOrNull false
                        val finalResidual = landing.positionMillis - finalHost.positionAt(session.serverClock.now())
                        residual = finalResidual
                        return@withTimeoutOrNull HostAlignment.isAcceptableResidual(finalResidual)
                    }

                    val sample = samplePosition() ?: return@withTimeoutOrNull false
                    val (position, now) = sample
                    val latestHost = session.snapshot.value.playback?.info ?: return@withTimeoutOrNull false
                    residual = position - latestHost.positionAt(now)
                    when (HostAlignment.afterSeek(latestHost, now, position, seekCount)) {
                        is HostAlignment.Action.Complete -> return@withTimeoutOrNull true
                        is HostAlignment.Action.GiveUp -> return@withTimeoutOrNull HostAlignment.isAcceptableResidual(position - latestHost.positionAt(now))
                        HostAlignment.Action.Retry -> continue
                        is HostAlignment.Action.Wait -> {
                            val pausedAt = withContext(player.mainDispatcher) {
                                player.pause()
                                temporarilyPaused = true
                                session.serverClock.now()
                            }
                            // pause() changes intent synchronously, but the position flow can still
                            // hold a pre-pause sample. Project the measured position to command time.
                            val localRate = bridge.localWatching.value?.playbackRate ?: 1f
                            val frozenPosition = position + ((pausedAt - now) * localRate).roundToLong()
                            val wait = ((frozenPosition - latestHost.positionAt(session.serverClock.now())) /
                                    latestHost.playbackRate).roundToLong().coerceAtLeast(0L)
                            delay(wait)
                            withContext(player.mainDispatcher) {
                                player.play()
                                temporarilyPaused = false
                            }
                            player.state.first { it.isPlaying }
                            val finalSample = samplePosition() ?: return@withTimeoutOrNull false
                            val finalHost = session.snapshot.value.playback?.info ?: return@withTimeoutOrNull false
                            val finalResidual = finalSample.first - finalHost.positionAt(finalSample.second)
                            residual = finalResidual
                            return@withTimeoutOrNull HostAlignment.isAcceptableResidual(finalResidual)
                        }
                    }
                }
                false
            } ?: false
            manager.notifyAlignmentFinished(success)
        } catch (throwable: Exception) {
            currentCoroutineContext().ensureActive()
            logger.debug { "Host alignment failed: ${throwable.message}" }
            manager.notifyAlignmentFinished(false)
        } finally {
            guard.cancel()
            // Undo only the short corrective pause, and only while the same media is still loaded.
            if (temporarilyPaused) withContext(NonCancellable + player.mainDispatcher) {
                if (player.mediaData.value !== media) return@withContext
                val currentSession = (manager.state.value as? WatchTogetherState.InRoom)?.session
                val host = currentSession?.snapshot?.value?.playback?.info
                if (currentSession !== session || host?.paused != true) player.play()
            }
            logger.debug {
                "Host alignment: success=$success residualMillis=$residual seeks=$seekCount seekLatencyMillis=$measuredLatency"
            }
        }
    }

    /**
     * Media3 acknowledges seeks using a masked discontinuity at command time. Require a stable
     * readiness level so the subsequent buffering fact can settle before accepting that edge.
     */
    private suspend fun awaitStablePlayback(paused: Boolean, started: TimeMark): Long = coroutineScope {
        val ready = CompletableDeferred<Long>()
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            player.state.collectLatest { state ->
                if (!state.isMediaLoaded || state.isBuffering ||
                    (if (paused) state.playWhenReady else !state.isPlaying)
                ) return@collectLatest
                val latency = started.elapsedNow().inWholeMilliseconds
                delay(HostAlignment.STATE_SETTLE_MILLIS)
                if (player.state.value == state) ready.complete(latency)
            }
        }
        try {
            ready.await()
        } finally {
            observer.cancel()
        }
    }

    /** Native positions from one uninterrupted playing window, projected to a common instant. */
    private suspend fun samplePosition(): Pair<Long, Long>? = coroutineScope {
        val result = CompletableDeferred<Pair<Long, Long>?>()
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            player.state.collectLatest { state ->
                if (!state.isPlaying) return@collectLatest
                val samples = mutableListOf<Pair<Long, Long>>()
                withTimeoutOrNull(HostAlignment.SAMPLE_WINDOW_MILLIS) {
                    // Excludes the optimistic position retained by StateFlow after seekTo.
                    player.currentPositionMillis.drop(1).collect {
                        samples += it to manager.serverNowMillis()
                    }
                }
                if (!player.state.value.isPlaying) return@collectLatest
                if (samples.isEmpty()) {
                    result.complete(null)
                } else {
                    val now = manager.serverNowMillis()
                    val localRate = bridge.localWatching.value?.playbackRate ?: 1f
                    val positions = samples.map { (position, at) ->
                        position + ((now - at) * localRate).roundToLong()
                    }.sorted()
                    result.complete(positions[positions.size / 2] to now)
                }
            }
        }
        try {
            val sample = result.await()
            if (player.state.value.isPlaying) sample else null
        } finally {
            observer.cancel()
        }
    }

    private companion object {
        val logger = logger<HostAlignmentExecutor>()
    }
}
