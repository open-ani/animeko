/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.watchtogether

import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.domain.watchtogether.WatchTogetherRoomEndReason
import me.him188.ani.app.ui.watchtogether.WatchTogetherConnectionPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherPlaybackPresentation

data class TvTogetherState(
    val roomName: String = "",
    val password: String = "",
    val joining: Boolean = false,
    val joined: Boolean = false,
    val isHost: Boolean = false,
    val following: Boolean = true,
    val requiresLogin: Boolean = false,
    val connection: WatchTogetherConnectionPresentation = WatchTogetherConnectionPresentation.CONNECTED,
    val playback: WatchTogetherPlaybackPresentation? = null,
    val members: List<WatchTogetherMemberPresentation> = emptyList(),
    val error: TvTogetherError? = null,
)

sealed interface TvTogetherIntent {
    data object Open : TvTogetherIntent
    data class RoomName(val value: String) : TvTogetherIntent
    data class Password(val value: String) : TvTogetherIntent
    data object Join : TvTogetherIntent
    data object CancelJoin : TvTogetherIntent
    data object ToggleFollowing : TvTogetherIntent
    data object Leave : TvTogetherIntent
    data class Foreground(val value: Boolean) : TvTogetherIntent
}

data class TvTogetherNavigation(val subjectId: Int, val episodeId: Int, val replacePlayer: Boolean)

sealed interface TvTogetherError {
    data object EmptyName : TvTogetherError
    data object Timeout : TvTogetherError
    data object RejoinFailed : TvTogetherError
    data class Join(val failure: WatchTogetherJoinFailure?) : TvTogetherError
    data class Ended(val reason: WatchTogetherRoomEndReason) : TvTogetherError
}
