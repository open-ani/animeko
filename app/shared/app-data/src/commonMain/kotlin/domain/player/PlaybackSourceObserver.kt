/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import me.him188.ani.app.domain.episode.MediaLoadListener
import me.him188.ani.app.domain.episode.MediaLoadPhase
import me.him188.ani.app.domain.media.selector.PlaybackSourceMemory
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackErrorCode
import org.openani.mediamp.PlaybackEvent
import org.openani.mediamp.PlaybackException
import kotlin.time.Duration
import kotlin.time.TimeSource

/** 只在本次加载已 Ready 后观察播放。选择或剧集改变时，调用方取消观察并结束对应尝试。 */
internal class PlaybackSourceObserver(
    private val memory: PlaybackSourceMemory,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    fun loadListener(
        delegate: MediaLoadListener,
        attempt: PlaybackSourceMemory.Attempt,
        ready: CompletableDeferred<Boolean>,
    ): MediaLoadListener = object : MediaLoadListener by delegate {
        override fun onReady(elapsed: Duration) {
            delegate.onReady(elapsed)
            ready.complete(true)
        }

        override fun onFailed(phase: MediaLoadPhase, state: VideoLoadingState.Failed, elapsed: Duration) {
            onLoadFailed(attempt, state)
            delegate.onFailed(phase, state, elapsed)
            ready.complete(false)
        }

        override fun onCancelled(phase: MediaLoadPhase, elapsed: Duration) {
            delegate.onCancelled(phase, elapsed)
            ready.complete(false)
        }
    }

    fun onLoadFailed(attempt: PlaybackSourceMemory.Attempt, state: VideoLoadingState.Failed) {
        when (state) {
            VideoLoadingState.NoMatchingFile, VideoLoadingState.ResolutionTimedOut ->
                memory.recordFailure(attempt, PlaybackSourceMemory.FailureReason.RESOLUTION)
            VideoLoadingState.NetworkError -> if (memory.snapshot().networkAvailable == true) {
                memory.recordFailure(attempt, PlaybackSourceMemory.FailureReason.RESOLUTION)
            }
            is VideoLoadingState.UnknownError -> if (state.cause is PlaybackException &&
                state.cause.code != PlaybackErrorCode.DECODING && state.cause.code != PlaybackErrorCode.UNSUPPORTED_FORMAT
            ) memory.recordFailure(attempt, PlaybackSourceMemory.FailureReason.PLAYBACK)
            // 取消、网络环境及不支持的播放器配置不能证明某条线路不可用。
            else -> Unit
        }
    }

    suspend fun observe(player: MediampPlayer, attempt: PlaybackSourceMemory.Attempt) {
        if (attempt.route == null) return
        val start = timeSource.markNow()
        var evidence = PlaybackProgressEvidence()
        var activeAttempt = attempt
        var confirmed = false
        try {
            merge(
                combine(player.state, player.currentPositionMillis) { state, position ->
                    Sample(state.isPlaying, position, (state.mediaStatus as? MediaStatus.Error)?.error)
                },
                player.events.filterIsInstance<PlaybackEvent.SeekCompleted>().map { Seek },
                memory.snapshots.map { Generation(it.generation) }.distinctUntilChanged(),
            ).first { input ->
                when (input) {
                    Seek -> evidence.onSeek()
                    is Generation -> if (input.value != activeAttempt.generation) {
                        // 播放器保留的报错属于旧尝试；新环境只有重新加载后的尝试可以记失败。
                        if (player.state.value.mediaStatus is MediaStatus.Error) return@first true
                        activeAttempt = memory.renewAttempt(activeAttempt) ?: return@first true
                        evidence = PlaybackProgressEvidence()
                        confirmed = false
                    }

                    is Sample -> {
                        if (input.error != null) {
                            if (input.error.code != PlaybackErrorCode.DECODING && input.error.code != PlaybackErrorCode.UNSUPPORTED_FORMAT) {
                                memory.recordFailure(activeAttempt, PlaybackSourceMemory.FailureReason.PLAYBACK)
                            }
                            return@first true
                        }
                        if (!confirmed && evidence.update(start.elapsedNow().inWholeMilliseconds, input.position, input.playing)) {
                            memory.recordSuccess(activeAttempt)
                            confirmed = true
                        }
                    }
                }
                false
            }
        } finally {
            memory.finishAttempt(activeAttempt)
        }
    }

    private sealed interface Input
    private data class Sample(val playing: Boolean, val position: Long, val error: PlaybackException?) : Input
    private data object Seek : Input
    private data class Generation(val value: Long) : Input
}

/** 累计同时有时间经过与进度前进的播放区间。暂停、缓冲、停滞和跳跃不计入五秒门槛。 */
internal class PlaybackProgressEvidence {
    private var previousTime: Long? = null
    private var previousPosition = 0L
    private var previouslyPlaying = false
    private var playedMillis = 0L

    fun onSeek() {
        previousTime = null
    }

    fun update(timeMillis: Long, positionMillis: Long, playing: Boolean): Boolean {
        val elapsed = previousTime?.let { timeMillis - it } ?: 0L
        val advanced = positionMillis - previousPosition
        if (playing && previouslyPlaying && elapsed > 0 && advanced in 1..(elapsed * 4 + 250)) {
            playedMillis += minOf(elapsed, advanced)
        }
        previousTime = timeMillis
        previousPosition = positionMillis
        previouslyPlaying = playing
        return playedMillis >= 5_000
    }
}
