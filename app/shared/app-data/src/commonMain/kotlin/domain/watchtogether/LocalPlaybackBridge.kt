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
        if (localOwner.compareAndSet(owner, null)) updateLocalWatchingValue(null)
    }

    private fun updateLocalWatchingValue(value: AniWatchTogetherWatchingInfo?) {
        val old = _localWatching.value
        _localWatching.value = value
        if (isDiscontinuity(old, value)) _discontinuities.tryEmit(Unit)
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
