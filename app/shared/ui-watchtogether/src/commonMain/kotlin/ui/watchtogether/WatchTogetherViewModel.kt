/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.network.WatchTogetherJoinException
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.RoomSession
import me.him188.ani.app.domain.watchtogether.WatchTogetherConnectionState
import me.him188.ani.app.domain.watchtogether.WatchTogetherEffect
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.WatchTogetherState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

open class WatchTogetherViewModel(
    private val koin: Koin = GlobalKoin,
    backgroundCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(backgroundCoroutineContext), KoinComponent {
    final override fun getKoin(): Koin = koin
    private var joinJob: Job? = null
    private val joinFailureState = MutableStateFlow<WatchTogetherJoinError?>(null)
    val joinFailure = joinFailureState.asStateFlow()

    init { koin.get<WatchTogetherManager>().start() }

    private val manager: WatchTogetherManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val playbackBridge: LocalPlaybackBridge by inject()
    private val selfInfoProducer by lazy { SelfInfoStateProducer(backgroundScope.coroutineContext, koin) }

    private val joinError = MutableStateFlow<String?>(null)
    private val dialogOpenRequestChannel = Channel<Unit>(Channel.BUFFERED)

    internal val dialogOpenRequests: Flow<Unit> = dialogOpenRequestChannel.receiveAsFlow()

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
            is WatchTogetherIntent.JoinRoom -> joinRoom(intent.roomName, intent.password)
            WatchTogetherIntent.LeaveRoom -> leaveRoom()

            is WatchTogetherIntent.SetFollowing -> launchInBackground {
                manager.setFollowing(intent.following)
            }

            WatchTogetherIntent.DisableFeature -> launchInBackground {
                joinJob?.cancelAndJoin()
                clearJoinError()
                settingsRepository.watchTogetherSettings.update { copy(enabled = false) }
            }
        }
    }

    fun enableFeature() = launchInBackground {
        settingsRepository.watchTogetherSettings.update { copy(enabled = true) }
    }

    fun joinRoom(roomName: String, password: String) {
        if (joinJob?.isActive == true || manager.state.value is WatchTogetherState.Joining) return
        if (roomName.isBlank()) {
            joinFailureState.value = WatchTogetherJoinError.EmptyName
            return
        }
        joinJob = launchInBackground {
            if (sessionStateProvider.stateFlow.first() !is SessionState.Valid) return@launchInBackground
            joinError.value = null
            joinFailureState.value = null
            try {
                val result = withTimeoutOrNull(30_000) {
                    settingsRepository.watchTogetherSettings.update { copy(enabled = true) }
                    manager.state.first { it !is WatchTogetherState.Disabled }
                    manager.join(roomName, password)
                }
                if (result == null) {
                    joinFailureState.value = WatchTogetherJoinError.Timeout
                    joinError.value = WatchTogetherJoinFailure.TEMPORARY.name
                }
                result?.onFailure { error ->
                    val failure = (error as? WatchTogetherJoinException)?.failure ?: WatchTogetherJoinFailure.TEMPORARY
                    joinError.value = failure.name
                    joinFailureState.value = WatchTogetherJoinError.Failure(failure)
                }
            } finally {
                if (manager.state.value is WatchTogetherState.Joining) {
                    withContext(NonCancellable) { withTimeoutOrNull(5_000) { manager.leave() } }
                }
            }
        }
    }

    fun cancelJoin() {
        leaveRoom()
    }

    fun clearJoinError() {
        joinError.value = null
        joinFailureState.value = null
    }

    fun leaveRoom() = launchInBackground {
        joinJob?.cancelAndJoin()
        clearJoinError()
        manager.leave()
    }

    fun onPlayerEntryClick() {
        launchInBackground {
            val settings = settingsRepository.watchTogetherSettings.flow.first()
            if (!settings.enabled) {
                settingsRepository.watchTogetherSettings.update { copy(enabled = true) }
            }
            dialogOpenRequestChannel.send(Unit)
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
                playback = snapshot.playback?.info?.toWatchTogetherPlaybackPresentation(now),
                members = snapshot.members
                    .sortedWith(compareByDescending { it.isHost })
                    .map { member ->
                        member.toWatchTogetherMemberPresentation(now, selfUserId)
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

    private fun WatchTogetherConnectionState.toPresentation(): WatchTogetherConnectionPresentation = when (this) {
        WatchTogetherConnectionState.ConnectedSse -> WatchTogetherConnectionPresentation.CONNECTED
        WatchTogetherConnectionState.Reconnecting -> WatchTogetherConnectionPresentation.RECONNECTING
        WatchTogetherConnectionState.DegradedPolling -> WatchTogetherConnectionPresentation.DEGRADED
    }

    private data class RoomProjection(
        val phase: WatchTogetherPhase = WatchTogetherPhase.NOT_IN_ROOM,
        val room: WatchTogetherRoomCardState? = null,
        val following: Boolean = true,
        val isSelfHost: Boolean = false,
    )
}

sealed interface WatchTogetherJoinError {
    data object EmptyName : WatchTogetherJoinError
    data object Timeout : WatchTogetherJoinError
    data class Failure(val reason: WatchTogetherJoinFailure) : WatchTogetherJoinError
}
