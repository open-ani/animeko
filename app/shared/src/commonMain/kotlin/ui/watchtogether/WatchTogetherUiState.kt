/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.runtime.Immutable

@Immutable
data class WatchTogetherUiState(
    val featureEnabled: Boolean,
    val phase: WatchTogetherPhase,
    val joinForm: WatchTogetherJoinFormState,
    val room: WatchTogetherRoomCardState?,
    val following: Boolean,
    val isSelfHost: Boolean,
    val requiresLogin: Boolean,
    val inPlayer: Boolean,
) {
    companion object {
        val Initial = WatchTogetherUiState(
            featureEnabled = false,
            phase = WatchTogetherPhase.NOT_IN_ROOM,
            joinForm = WatchTogetherJoinFormState(),
            room = null,
            following = true,
            isSelfHost = false,
            requiresLogin = false,
            inPlayer = false,
        )
    }
}

enum class WatchTogetherPhase {
    NOT_IN_ROOM,
    JOINING,
    IN_ROOM,
}

@Immutable
data class WatchTogetherJoinFormState(
    val lastRoomName: String = "",
    val errorMessage: String? = null,
)

@Immutable
data class WatchTogetherRoomCardState(
    val roomName: String,
    val connection: WatchTogetherConnectionPresentation,
    val playback: WatchTogetherPlaybackPresentation?,
    val members: List<WatchTogetherMemberPresentation>,
)

enum class WatchTogetherConnectionPresentation {
    CONNECTED,
    RECONNECTING,
    DEGRADED,
}

@Immutable
data class WatchTogetherPlaybackPresentation(
    val subjectName: String,
    val episodeSort: String,
    val episodeName: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val paused: Boolean,
    val buffering: Boolean = false,
    /** Initial load (source selection + media loading), until the duration is known. */
    val loading: Boolean = false,
)

@Immutable
data class WatchTogetherMemberPresentation(
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
    val isHost: Boolean,
    val isSelf: Boolean = false,
    val following: Boolean,
    val state: WatchTogetherMemberPresence,
    val watching: WatchTogetherPlaybackPresentation?,
    /** Minutes since last seen; only set when [state] is [WatchTogetherMemberPresence.DISCONNECTED]. */
    val disconnectedMinutes: Long? = null,
)

enum class WatchTogetherMemberPresence {
    IDLE,
    WATCHING,
    DISCONNECTED,
}

sealed interface WatchTogetherIntent {
    data class JoinRoom(val roomName: String, val password: String) : WatchTogetherIntent
    data object LeaveRoom : WatchTogetherIntent
    data class SetFollowing(val following: Boolean) : WatchTogetherIntent
    data object DisableFeature : WatchTogetherIntent
}
