/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.network.WatchTogetherJoinException
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.RoomSession
import me.him188.ani.app.domain.watchtogether.WatchTogetherConnectionState
import me.him188.ani.app.domain.watchtogether.WatchTogetherEffect
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.WatchTogetherState
import me.him188.ani.app.domain.watchtogether.positionAt
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.client.models.AniWatchTogetherMemberState
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WatchTogetherViewModel : AbstractViewModel(), KoinComponent {
    private val manager: WatchTogetherManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val playbackBridge: LocalPlaybackBridge by inject()
    private val selfInfoProducer = SelfInfoStateProducer(koin = getKoin())

    private val joinError = MutableStateFlow<String?>(null)

    private val roomProjection = manager.state.flatMapLatest { state ->
        when (state) {
            WatchTogetherState.Disabled,
            WatchTogetherState.Idle,
            -> flowOf(RoomProjection())

            is WatchTogetherState.Joining -> flowOf(
                RoomProjection(phase = WatchTogetherPhase.JOINING),
            )

            is WatchTogetherState.InRoom -> state.session.presentationFlow()
        }
    }

    val uiStateFlow = combine(
        settingsRepository.watchTogetherSettings.flow,
        sessionStateProvider.stateFlow,
        roomProjection,
        playbackBridge.localWatching,
        joinError,
    ) { settings, sessionState, projection, localWatching, error ->
        WatchTogetherUiState(
            featureEnabled = settings.enabled,
            phase = projection.phase,
            joinForm = WatchTogetherJoinFormState(
                lastRoomName = settings.lastRoomName,
                errorMessage = error,
            ),
            room = projection.room,
            following = projection.following,
            isSelfHost = projection.isSelfHost,
            requiresLogin = settings.enabled && sessionState !is SessionState.Valid,
            inPlayer = localWatching != null,
        )
    }.stateInBackground(WatchTogetherUiState.Initial)

    val effects: Flow<WatchTogetherEffect> = manager.effects

    fun onIntent(intent: WatchTogetherIntent) {
        when (intent) {
            is WatchTogetherIntent.JoinRoom -> launchInBackground {
                joinError.value = null
                manager.join(intent.roomName, intent.password)
                    .onFailure { throwable ->
                        joinError.value = (throwable as? WatchTogetherJoinException)?.failure?.name
                            ?: WatchTogetherJoinFailure.TEMPORARY.name
                    }
            }

            WatchTogetherIntent.LeaveRoom -> launchInBackground {
                joinError.value = null
                manager.leave()
            }

            is WatchTogetherIntent.SetFollowing -> launchInBackground {
                manager.setFollowing(intent.following)
            }

            WatchTogetherIntent.DisableFeature -> launchInBackground {
                joinError.value = null
                settingsRepository.watchTogetherSettings.update { copy(enabled = false) }
            }
        }
    }

    fun onAppForegroundChanged(foreground: Boolean) {
        manager.setAppForeground(foreground)
    }

    private fun RoomSession.presentationFlow(): Flow<RoomProjection> = combine(
        snapshot,
        connection,
        following,
        selfInfoProducer.flow,
        tickerFlow(),
    ) { snapshot, connection, following, selfInfo, _ ->
        val now = serverClock.now()
        val selfUserId = selfInfo.selfInfo?.id?.toString()
        RoomProjection(
            phase = WatchTogetherPhase.IN_ROOM,
            following = following,
            isSelfHost = isHost,
            room = WatchTogetherRoomCardState(
                roomName = roomName,
                connection = connection.toPresentation(),
                playback = snapshot.playback?.info?.toPresentation(now),
                members = snapshot.members
                    .sortedWith(compareByDescending { it.isHost })
                    .map { member ->
                        val state = member.state.toPresentation()
                        WatchTogetherMemberPresentation(
                            userId = member.userId,
                            nickname = member.nickname,
                            avatarUrl = member.avatarUrl,
                            isHost = member.isHost,
                            isSelf = member.userId == selfUserId,
                            following = member.following,
                            state = state,
                            watching = member.watching?.toPresentation(now),
                            disconnectedMinutes = if (state == WatchTogetherMemberPresence.DISCONNECTED) {
                                ((now - member.lastSeenAt) / 60_000L).coerceAtLeast(0L)
                            } else {
                                null
                            },
                        )
                    },
            ),
        )
    }

    private fun tickerFlow(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    private fun AniWatchTogetherWatchingInfo.toPresentation(nowMillis: Long) =
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

    private fun WatchTogetherConnectionState.toPresentation(): WatchTogetherConnectionPresentation = when (this) {
        WatchTogetherConnectionState.ConnectedSse -> WatchTogetherConnectionPresentation.CONNECTED
        WatchTogetherConnectionState.Reconnecting -> WatchTogetherConnectionPresentation.RECONNECTING
        WatchTogetherConnectionState.DegradedPolling -> WatchTogetherConnectionPresentation.DEGRADED
    }

    private fun AniWatchTogetherMemberState.toPresentation(): WatchTogetherMemberPresence = when (this) {
        AniWatchTogetherMemberState.IDLE -> WatchTogetherMemberPresence.IDLE
        AniWatchTogetherMemberState.WATCHING -> WatchTogetherMemberPresence.WATCHING
        AniWatchTogetherMemberState.DISCONNECTED -> WatchTogetherMemberPresence.DISCONNECTED
    }

    private data class RoomProjection(
        val phase: WatchTogetherPhase = WatchTogetherPhase.NOT_IN_ROOM,
        val room: WatchTogetherRoomCardState? = null,
        val following: Boolean = true,
        val isSelfHost: Boolean = false,
    )
}
