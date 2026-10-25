/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.watchtogether

import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import kotlin.math.abs
import kotlin.math.roundToLong

/** Decisions for a bounded, one-shot alignment. All times and positions are milliseconds. */
object HostAlignment {
    const val INITIAL_SEEK_LATENCY_MILLIS = 500L
    const val SEEK_MARGIN_MILLIS = 150L
    const val TOLERANCE_MILLIS = 20L
    const val ACCEPTABLE_RESIDUAL_MILLIS = 50L
    const val COOLDOWN_MILLIS = 3_000L
    const val CLOCK_MAX_AGE_MILLIS = 60_000L
    const val TIMEOUT_MILLIS = 30_000L
    const val SAMPLE_WINDOW_MILLIS = 300L
    const val STATE_SETTLE_MILLIS = 100L

    fun unavailableReason(
        host: AniWatchTogetherWatchingInfo?,
        local: AniWatchTogetherWatchingInfo?,
        aligning: Boolean = false,
        cooldownRemaining: Long = 0L,
    ): HostAlignmentUnavailableReason? = when {
        aligning -> HostAlignmentUnavailableReason.ALIGNING
        host == null -> HostAlignmentUnavailableReason.NO_HOST_CONTENT
        local == null || local.subjectId != host.subjectId || local.episodeId != host.episodeId ->
            HostAlignmentUnavailableReason.DIFFERENT_EPISODE
        host.buffering == true || host.loading == true -> HostAlignmentUnavailableReason.HOST_NOT_READY
        local.loading == true -> HostAlignmentUnavailableReason.LOCAL_NOT_READY
        cooldownRemaining > 0L -> HostAlignmentUnavailableReason.COOLDOWN
        else -> null
    }

    fun isAcceptableResidual(residualMillis: Long): Boolean = abs(residualMillis) <= ACCEPTABLE_RESIDUAL_MILLIS

    fun seekPosition(host: AniWatchTogetherWatchingInfo, now: Long, latency: Long): Long {
        val target = host.positionAt(now) + if (host.paused) 0L else
            ((latency + SEEK_MARGIN_MILLIS) * host.playbackRate).roundToLong()
        return if (host.durationMillis > 0L) target.coerceIn(0L, host.durationMillis) else target.coerceAtLeast(0L)
    }

    fun afterSeek(host: AniWatchTogetherWatchingInfo, now: Long, localPosition: Long, seekCount: Int): Action {
        val residual = localPosition - host.positionAt(now)
        return when {
            host.paused || abs(residual) <= TOLERANCE_MILLIS -> Action.Complete(residual)
            residual > TOLERANCE_MILLIS -> Action.Wait((residual / host.playbackRate).roundToLong())
            seekCount < 2 -> Action.Retry
            else -> Action.GiveUp(residual)
        }
    }

    sealed interface Action {
        data class Wait(val millis: Long) : Action
        data object Retry : Action
        data class Complete(val residual: Long) : Action
        data class GiveUp(val residual: Long) : Action
    }
}

enum class HostAlignmentUnavailableReason {
    NO_HOST_CONTENT,
    DIFFERENT_EPISODE,
    HOST_NOT_READY,
    LOCAL_NOT_READY,
    ALIGNING,
    COOLDOWN,
}

/** Identity also prevents a finishing executor from clearing another request. */
class HostAlignmentRequest internal constructor(
    internal val session: RoomSession,
    internal val owner: Any?,
)
