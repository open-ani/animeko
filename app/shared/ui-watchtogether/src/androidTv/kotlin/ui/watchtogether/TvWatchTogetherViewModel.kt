/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.watchtogether

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.network.WatchTogetherJoinException
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.watchtogether.SyncAction
import me.him188.ani.app.domain.watchtogether.WatchTogetherEffect
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.WatchTogetherState
import me.him188.ani.app.ui.watchtogether.WatchTogetherConnectionPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherIntent
import me.him188.ani.app.ui.watchtogether.WatchTogetherPhase
import me.him188.ani.app.ui.watchtogether.WatchTogetherViewModel

/** App-scoped so room recovery and following continue across TV navigation entries. */
class TvWatchTogetherViewModel(
    private val manager: WatchTogetherManager,
    private val settings: SettingsRepository,
    sessionStateProvider: SessionStateProvider,
) : WatchTogetherViewModel() {
    private val form = MutableStateFlow(TvTogetherState())
    private var joinJob: Job? = null
    private val navigation = Channel<TvTogetherNavigation>(Channel.BUFFERED)
    val navigationEvents = navigation.receiveAsFlow()
    val uiState = combine(uiStateFlow, form, sessionStateProvider.stateFlow) { shared, form, session ->
        TvTogetherState(
            roomName = shared.room?.roomName ?: form.roomName,
            password = form.password,
            joined = shared.phase == WatchTogetherPhase.IN_ROOM,
            joining = shared.phase == WatchTogetherPhase.JOINING,
            isHost = shared.isSelfHost,
            following = shared.following,
            connection = shared.room?.connection ?: WatchTogetherConnectionPresentation.CONNECTED,
            playback = shared.room?.playback,
            members = shared.room?.members.orEmpty(),
            requiresLogin = session !is SessionState.Valid,
            error = form.error,
        )
    }.stateIn(backgroundScope, SharingStarted.Eagerly, TvTogetherState())

    init {
        manager.start()
        backgroundScope.launch {
            val lastRoomName = settings.watchTogetherSettings.flow.first().lastRoomName
            form.update { if (it.roomName.isEmpty()) it.copy(roomName = lastRoomName) else it }
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
            TvTogetherIntent.Open -> backgroundScope.launch { settings.watchTogetherSettings.update { copy(enabled = true) } }
            is TvTogetherIntent.RoomName -> form.update { it.copy(roomName = intent.value, error = null) }
            is TvTogetherIntent.Password -> form.update { it.copy(password = intent.value, error = null) }
            is TvTogetherIntent.Foreground -> onAppForegroundChanged(intent.value)
            TvTogetherIntent.ToggleFollowing -> onIntent(WatchTogetherIntent.SetFollowing(!uiState.value.following))
            TvTogetherIntent.CancelJoin -> {
                val job = joinJob
                if (job?.isActive == true) job.cancel()
                else backgroundScope.launch { manager.leave() }
            }

            TvTogetherIntent.Leave -> backgroundScope.launch {
                manager.leave()
                form.update { it.copy(password = "", error = null) }
            }

            TvTogetherIntent.Join -> {
                if (joinJob?.isActive == true || uiState.value.joining || uiState.value.requiresLogin) return
                if (form.value.roomName.isBlank()) {
                    form.update { it.copy(error = TvTogetherError.EmptyName) }
                    return
                }
                val roomName = form.value.roomName
                val password = form.value.password
                joinJob = backgroundScope.launch {
                    form.update { it.copy(error = null) }
                    try {
                        val result = withTimeoutOrNull(30_000) {
                            settings.watchTogetherSettings.update { copy(enabled = true) }
                            manager.state.first { it !is WatchTogetherState.Disabled }
                            manager.join(roomName, password)
                        }
                        if (result == null) form.update { it.copy(error = TvTogetherError.Timeout) }
                        result?.onFailure { error ->
                            form.update { it.copy(error = TvTogetherError.Join((error as? WatchTogetherJoinException)?.failure)) }
                        }
                        if (manager.state.value is WatchTogetherState.InRoom) form.update { it.copy(password = "") }
                    } catch (e: CancellationException) {
                        throw e
                    } finally {
                        // A cancelled join leaves the shared manager in Joining until leave resets it.
                        if (manager.state.value is WatchTogetherState.Joining) {
                            withContext(NonCancellable) {
                                withTimeoutOrNull(5_000) { manager.leave() }
                            }
                        }
                    }
                }
            }
        }
    }
}
