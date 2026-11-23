/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.watchtogether

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import me.him188.ani.client.models.AniWatchTogetherPlayback
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import kotlin.math.abs

class LocalPlaybackBridge {
    private val _alignmentRequest = MutableStateFlow<HostAlignmentRequest?>(null)
    val alignmentRequest: StateFlow<HostAlignmentRequest?> = _alignmentRequest.asStateFlow()
    private val _alignmentCooldownUntil = MutableStateFlow(0L)
    val alignmentCooldownUntil: StateFlow<Long> = _alignmentCooldownUntil.asStateFlow()
    val isAligning: Boolean get() = _alignmentRequest.value != null

    internal fun requestAlignment(session: RoomSession, now: Long): Boolean {
        if (now < _alignmentCooldownUntil.value) return false
        if (!_alignmentRequest.compareAndSet(null, HostAlignmentRequest(session, localOwner.value))) return false
        _alignmentCooldownUntil.value = now + HostAlignment.COOLDOWN_MILLIS
        return true
    }

    internal fun isLocalOwner(owner: Any): Boolean = localOwner.value === owner

    internal fun finishAlignment(request: HostAlignmentRequest) {
        _alignmentRequest.compareAndSet(request, null)
    }

    /** Seek completion supplies a fresh fix even for small host seeks. */
    internal fun notifySeekCompleted(owner: Any, position: Long, now: Long, isHost: Boolean) {
        if (!isHost || localOwner.value !== owner) return
        val previous = _localWatching.value ?: return
        val delta = abs(position - previous.positionAt(now))
        val lastDiscontinuity = lastDiscontinuityWatching.value
        val unreportedPosition = lastDiscontinuity != null &&
                lastDiscontinuity.subjectId == previous.subjectId && lastDiscontinuity.episodeId == previous.episodeId &&
                abs(position - lastDiscontinuity.positionAt(now)) > SEEK_REPORT_TOLERANCE_MILLIS
        val updated = previous.copy(positionMillis = position, positionAtMillis = now)
        _localWatching.value = updated
        // Small seeks can be sampled without crossing the immediate-report threshold.
        if (!isAligning && (delta > SEEK_REPORT_TOLERANCE_MILLIS || unreportedPosition)) {
            lastDiscontinuityWatching.value = updated
            _discontinuities.tryEmit(Unit)
        }
    }

    private val lastDiscontinuityWatching = atomic<AniWatchTogetherWatchingInfo?>(null)
    private val localOwner = atomic<Any?>(null)
    private val _localWatching = MutableStateFlow<AniWatchTogetherWatchingInfo?>(null)
    val localWatching: StateFlow<AniWatchTogetherWatchingInfo?> = _localWatching.asStateFlow()

    private val _targetPlayback = MutableStateFlow<AniWatchTogetherPlayback?>(null)
    val targetPlayback: StateFlow<AniWatchTogetherPlayback?> = _targetPlayback.asStateFlow()

    private val _discontinuities = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val discontinuities: SharedFlow<Unit> = _discontinuities.asSharedFlow()

    private val _directives = MutableSharedFlow<PlaybackDirective>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val directives: SharedFlow<PlaybackDirective> = _directives.asSharedFlow()

    private val _corrections = MutableSharedFlow<Long>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Emitted by the player extension when a continuous-correction seek was actually applied.
     * The value is the applied position jump in millis (positive = forward).
     */
    val corrections: SharedFlow<Long> = _corrections.asSharedFlow()

    fun notifyCorrectionApplied(deltaMillis: Long) {
        _corrections.tryEmit(deltaMillis)
    }

    fun updateLocalWatching(value: AniWatchTogetherWatchingInfo?) {
        localOwner.value = null
        updateLocalWatchingValue(value)
    }

    internal fun updateLocalWatching(owner: Any, value: AniWatchTogetherWatchingInfo) {
        localOwner.value = owner
        updateLocalWatchingValue(value)
    }

    internal fun clearLocalWatching(owner: Any) {
        if (localOwner.compareAndSet(owner, null)) {
            _alignmentRequest.value = null
            updateLocalWatchingValue(null)
        }
    }

    private fun updateLocalWatchingValue(value: AniWatchTogetherWatchingInfo?) {
        val old = _localWatching.value
        _localWatching.value = value
        if (!isAligning && isDiscontinuity(old, value)) {
            lastDiscontinuityWatching.value = value
            _discontinuities.tryEmit(Unit)
        }
    }

    fun emitDirective(directive: PlaybackDirective) {
        _directives.tryEmit(directive)
    }

    internal fun setTargetPlayback(playback: AniWatchTogetherPlayback?) {
        _targetPlayback.value = playback
    }

    fun localPosition(): LocalPosition = _localWatching.value?.let {
        LocalPosition.InPlayer(it.subjectId, it.episodeId)
    } ?: LocalPosition.NotInPlayer

    private fun isDiscontinuity(
        old: AniWatchTogetherWatchingInfo?,
        new: AniWatchTogetherWatchingInfo?,
    ): Boolean {
        if (old == null || new == null) return old != new
        if (old.subjectId != new.subjectId || old.episodeId != new.episodeId) return true
        if (old.paused != new.paused || old.buffering != new.buffering ||
            old.loading != new.loading || old.playbackRate != new.playbackRate
        ) {
            return true
        }
        return abs(new.positionMillis - old.positionAt(new.positionAtMillis)) > POSITION_JUMP_MILLIS
    }

    private companion object {
        const val SEEK_REPORT_TOLERANCE_MILLIS = 50L
        const val POSITION_JUMP_MILLIS = 2_500L
    }
}

sealed interface PlaybackDirective {
    data class Sync(val playback: AniWatchTogetherPlayback, val initial: Boolean = false) : PlaybackDirective
    data class SwitchEpisode(val episodeId: Int, val playback: AniWatchTogetherPlayback) : PlaybackDirective
}

class PlaybackAutomationGate {
    private val _suppressed = MutableStateFlow(false)
    val suppressed: StateFlow<Boolean> = _suppressed.asStateFlow()

    internal fun setSuppressed(value: Boolean) {
        _suppressed.value = value
    }
}
