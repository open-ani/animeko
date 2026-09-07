/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import me.him188.ani.app.domain.watchtogether.positionAt
import me.him188.ani.client.models.AniWatchTogetherMember
import me.him188.ani.client.models.AniWatchTogetherMemberState
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo

fun AniWatchTogetherWatchingInfo.toWatchTogetherPlaybackPresentation(nowMillis: Long): WatchTogetherPlaybackPresentation =
    WatchTogetherPlaybackPresentation(
        subjectName = subjectName,
        episodeSort = episodeSort,
        episodeName = episodeName,
        positionMillis = positionAt(nowMillis),
        durationMillis = durationMillis,
        paused = paused,
        buffering = buffering == true,
        loading = loading == true,
    )

fun AniWatchTogetherMember.toWatchTogetherMemberPresentation(
    nowMillis: Long,
    selfUserId: String?,
): WatchTogetherMemberPresentation =
    WatchTogetherMemberPresentation(
        userId = userId,
        nickname = nickname,
        avatarUrl = avatarUrl,
        isHost = isHost,
        isSelf = userId == selfUserId,
        following = following,
        state = when (state) {
            AniWatchTogetherMemberState.IDLE -> WatchTogetherMemberPresence.IDLE
            AniWatchTogetherMemberState.WATCHING -> WatchTogetherMemberPresence.WATCHING
            AniWatchTogetherMemberState.DISCONNECTED -> WatchTogetherMemberPresence.DISCONNECTED
        },
        watching = watching?.toWatchTogetherPlaybackPresentation(nowMillis),
        disconnectedMinutes = if (state == AniWatchTogetherMemberState.DISCONNECTED) {
            ((nowMillis - lastSeenAt) / 60_000L).coerceAtLeast(0L)
        } else null,
    )
