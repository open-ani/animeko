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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.him188.ani.client.models.AniWatchTogetherRoomSnapshot
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo

sealed interface WatchTogetherState {
    data object Disabled : WatchTogetherState
    data object Idle : WatchTogetherState
    data class Joining(val roomName: String) : WatchTogetherState
    data class InRoom(val session: RoomSession) : WatchTogetherState
}

sealed interface WatchTogetherConnectionState {
    data object ConnectedSse : WatchTogetherConnectionState
    data object Reconnecting : WatchTogetherConnectionState
    data object DegradedPolling : WatchTogetherConnectionState
}

class RoomSession internal constructor(
    val roomId: String,
    val roomName: String,
    val isHost: Boolean,
    internal val sessionNonce: String,
    initialSnapshot: AniWatchTogetherRoomSnapshot,
    initialFollowing: Boolean,
    val serverClock: ServerClock,
) {
    private val _snapshot = MutableStateFlow(initialSnapshot)
    val snapshot: StateFlow<AniWatchTogetherRoomSnapshot> = _snapshot.asStateFlow()

    private val _connection = MutableStateFlow<WatchTogetherConnectionState>(
        WatchTogetherConnectionState.Reconnecting,
    )
    val connection: StateFlow<WatchTogetherConnectionState> = _connection.asStateFlow()

    private val _following = MutableStateFlow(initialFollowing)
    val following: StateFlow<Boolean> = _following.asStateFlow()

    internal val lastReportAtMillis = atomic(0L)

    /** The `watching` payload of the last successfully delivered report; gates paused-idempotent periodic reports. */
    internal val lastReportedWatching = atomic<AniWatchTogetherWatchingInfo?>(null)
    private val closing = atomic(false)

    /**
     * The latest room version this client is aware of. May run ahead of [snapshot]'s version:
     * a report response carries the post-bump version without a snapshot when the only change
     * was the report itself (see server-side gating).
     */
    private val knownVersionValue = atomic(initialSnapshot.version)
    internal val knownVersion: Long get() = knownVersionValue.value

    internal fun beginClosing(): Boolean = closing.compareAndSet(expect = false, update = true)

    internal val isClosing: Boolean get() = closing.value

    internal fun noteServerVersion(version: Long) {
        while (true) {
            val current = knownVersionValue.value
            if (version <= current) return
            if (knownVersionValue.compareAndSet(current, version)) return
        }
    }

    internal fun updateSnapshot(snapshot: AniWatchTogetherRoomSnapshot): Boolean {
        noteServerVersion(snapshot.version)
        while (true) {
            val current = _snapshot.value
            if (snapshot.version < current.version) return false
            if (_snapshot.compareAndSet(current, snapshot)) return true
        }
    }

    internal fun updateConnection(connection: WatchTogetherConnectionState) {
        _connection.value = connection
    }

    internal fun updateFollowing(following: Boolean) {
        _following.value = following
    }
}

sealed interface WatchTogetherEffect {
    data class Navigate(
        val action: SyncAction,
        /** Display info of what the host is playing, for the guidance toast. */
        val subjectName: String? = null,
        val episodeSort: String? = null,
    ) : WatchTogetherEffect

    data class RoomEnded(val reason: WatchTogetherRoomEndReason) : WatchTogetherEffect
    data object Rejoined : WatchTogetherEffect
    data object RejoinFailed : WatchTogetherEffect

    /**
     * A continuous-correction seek pulled this follower back to the host's position.
     * [deltaMillis] is the applied jump: positive = skipped forward, negative = went back.
     */
    data class ResyncedWithHost(val deltaMillis: Long) : WatchTogetherEffect
}

enum class WatchTogetherRoomEndReason {
    ROOM_CLOSED,
    SESSION_REPLACED,
}
