/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.watchtogether

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.watchtogether.SyncAction
import me.him188.ani.app.domain.watchtogether.WatchTogetherEffect
import me.him188.ani.app.ui.watchtogether.WatchTogetherConnectionPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherIntent
import me.him188.ani.app.ui.watchtogether.WatchTogetherJoinError
import me.him188.ani.app.ui.watchtogether.WatchTogetherPhase
import me.him188.ani.app.ui.watchtogether.WatchTogetherViewModel
import org.koin.core.Koin

/** App-scoped so room recovery and following continue across TV navigation entries. */
class TvWatchTogetherViewModel(
    koin: Koin,
) : WatchTogetherViewModel(koin) {
    private val form = MutableStateFlow(TvTogetherState())
    private val roomNameInput = MutableStateFlow<String?>(null)
    private val navigation = Channel<TvTogetherNavigation>(Channel.BUFFERED)
    val navigationEvents = navigation.receiveAsFlow()
    val uiState = combine(uiStateFlow, form, joinFailure, roomNameInput) { shared, form, failure, roomName ->
        TvTogetherState(
            roomName = shared.room?.roomName ?: roomName ?: shared.joinForm.lastRoomName,
            password = form.password,
            joined = shared.phase == WatchTogetherPhase.IN_ROOM,
            joining = shared.phase == WatchTogetherPhase.JOINING,
            isHost = shared.isSelfHost,
            following = shared.following,
            connection = shared.room?.connection ?: WatchTogetherConnectionPresentation.CONNECTED,
            playback = shared.room?.playback,
            members = shared.room?.members.orEmpty(),
            requiresLogin = shared.requiresLogin,
            error = form.error ?: when (failure) {
                WatchTogetherJoinError.EmptyName -> TvTogetherError.EmptyName
                WatchTogetherJoinError.Timeout -> TvTogetherError.Timeout
                is WatchTogetherJoinError.Failure -> TvTogetherError.Join(failure.reason)
                null -> null
            },
        )
    }.stateIn(backgroundScope, SharingStarted.Eagerly, TvTogetherState())

    init {
        backgroundScope.launch {
            uiStateFlow.map { it.phase }.distinctUntilChanged().collect { phase ->
                if (phase == WatchTogetherPhase.IN_ROOM) form.update { it.copy(password = "") }
            }
        }
        backgroundScope.launch {
            effects.collect { effect ->
                when (effect) {
                    is WatchTogetherEffect.Navigate -> when (val action = effect.action) {
                        is SyncAction.PushEpisode -> navigation.send(
                            TvTogetherNavigation(
                                action.subjectId,
                                action.episodeId,
                                false,
                            ),
                        )

                        is SyncAction.PopThenPushEpisode -> navigation.send(
                            TvTogetherNavigation(
                                action.subjectId,
                                action.episodeId,
                                true,
                            ),
                        )

                        else -> Unit // The shared player extension handles in-place switches and seeks.
                    }

                    is WatchTogetherEffect.RoomEnded -> form.update {
                        it.copy(
                            error = TvTogetherError.Ended(effect.reason),
                            password = "",
                        )
                    }

                    WatchTogetherEffect.RejoinFailed -> form.update { it.copy(error = TvTogetherError.RejoinFailed) }
                    WatchTogetherEffect.Rejoined -> form.update { it.copy(error = null) }
                    else -> Unit
                }
            }
        }
    }

    fun onIntent(intent: TvTogetherIntent) {
        when (intent) {
            TvTogetherIntent.Open -> enableFeature()
            is TvTogetherIntent.RoomName -> {
                roomNameInput.value = intent.value
                form.update { it.copy(error = null) }
                clearJoinError()
            }
            is TvTogetherIntent.Password -> {
                form.update { it.copy(password = intent.value, error = null) }
                clearJoinError()
            }
            is TvTogetherIntent.Foreground -> onAppForegroundChanged(intent.value)
            TvTogetherIntent.ToggleFollowing -> onIntent(WatchTogetherIntent.SetFollowing(!uiState.value.following))
            TvTogetherIntent.CancelJoin -> cancelJoin()
            TvTogetherIntent.Leave -> {
                leaveRoom()
                form.update { it.copy(password = "", error = null) }
            }
            TvTogetherIntent.Join -> {
                form.update { it.copy(error = null) }
                joinRoom(uiState.value.roomName, form.value.password)
            }
        }
    }
}
