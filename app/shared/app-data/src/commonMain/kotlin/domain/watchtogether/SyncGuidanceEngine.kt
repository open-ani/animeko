/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.watchtogether

import me.him188.ani.client.models.AniWatchTogetherPlayback
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import kotlin.math.roundToLong

sealed interface LocalPosition {
    data object NotInPlayer : LocalPosition
    data class InPlayer(val subjectId: Int, val episodeId: Int) : LocalPosition
}

sealed interface SyncAction {
    data class PushEpisode(
        val subjectId: Int,
        val episodeId: Int,
        val initialPositionMillis: Long,
    ) : SyncAction

    data class PopThenPushEpisode(
        val subjectId: Int,
        val episodeId: Int,
        val initialPositionMillis: Long,
    ) : SyncAction

    data class SwitchEpisodeInPlace(
        val episodeId: Int,
        val initialPositionMillis: Long,
    ) : SyncAction

    data class SeekOnly(
        val positionMillis: Long,
        val paused: Boolean,
    ) : SyncAction
}

object SyncGuidanceEngine {
    fun compute(
        playback: AniWatchTogetherPlayback?,
        local: LocalPosition,
        nowMillis: Long,
    ): SyncAction? {
        val info = playback?.info ?: return null
        val position = info.positionAt(nowMillis)
        return when (local) {
            LocalPosition.NotInPlayer -> SyncAction.PushEpisode(info.subjectId, info.episodeId, position)
            is LocalPosition.InPlayer -> when {
                local.subjectId != info.subjectId -> {
                    SyncAction.PopThenPushEpisode(info.subjectId, info.episodeId, position)
                }

                local.episodeId != info.episodeId -> SyncAction.SwitchEpisodeInPlace(info.episodeId, position)
                else -> SyncAction.SeekOnly(position, info.paused)
            }
        }
    }
}

fun AniWatchTogetherWatchingInfo.positionAt(nowMillis: Long): Long {
    val rate = playbackRate ?: 1.0f
    val elapsed = (nowMillis - positionAtMillis).coerceAtLeast(0L)
    val extrapolated = if (paused || buffering == true || loading == true) {
        positionMillis
    } else {
        positionMillis + (elapsed * rate).roundToLong()
    }
    return if (durationMillis > 0L) extrapolated.coerceIn(0L, durationMillis) else extrapolated.coerceAtLeast(0L)
}
