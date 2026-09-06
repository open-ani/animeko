/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.network.WatchTogetherJoinException
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.watchtogether.SyncAction
import me.him188.ani.app.domain.watchtogether.WatchTogetherConnectionState
import me.him188.ani.app.domain.watchtogether.WatchTogetherEffect
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.WatchTogetherState
import me.him188.ani.app.ui.foundation.AbstractViewModel

data class TvTogetherState(
    val roomName: String = "",
    val password: String = "",
    val joining: Boolean = false,
    val joined: Boolean = false,
    val isHost: Boolean = false,
    val following: Boolean = true,
    val requiresLogin: Boolean = false,
    val connection: String = "",
    val watching: String = "",
    val members: List<String> = emptyList(),
    val error: String? = null,
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

/** App-scoped so room recovery and following continue across TV navigation entries. */
class TvWatchTogetherViewModel(
    private val manager: WatchTogetherManager,
    private val settings: SettingsRepository,
    sessionStateProvider: SessionStateProvider,
) : AbstractViewModel() {
    private val form = MutableStateFlow(TvTogetherState())
    private var joinJob: Job? = null
    private val navigation = Channel<TvTogetherNavigation>(Channel.BUFFERED)
    val navigationEvents = navigation.receiveAsFlow()
    private val room = manager.state.flatMapLatest { state ->
        when (state) {
            is WatchTogetherState.InRoom -> combine(
                state.session.snapshot,
                state.session.connection,
                state.session.following,
            ) { snapshot, connection, following ->
                TvTogetherState(
                    roomName = state.session.roomName,
                    joined = true,
                    isHost = state.session.isHost,
                    following = following,
                    connection = when (connection) {
                        WatchTogetherConnectionState.ConnectedSse -> "已连接"
                        WatchTogetherConnectionState.Reconnecting -> "正在重新连接…"
                        WatchTogetherConnectionState.DegradedPolling -> "正在重试实时连接，暂时定时同步"
                    },
                    watching = snapshot.playback?.info?.let { "${it.subjectName} · 第 ${it.episodeSort} 集 · ${if (it.loading == true) "加载中" else if (it.paused) "已暂停" else "播放中"}" }
                        .orEmpty(),
                    members = snapshot.members.sortedByDescending { it.isHost }.map { member ->
                        "${if (member.isHost) "房主 · " else ""}${member.nickname} · ${if (member.watching?.loading == true) "加载中" else if (member.watching != null) "观看中" else "等待播放"}"
                    },
                )
            }

            is WatchTogetherState.Joining -> flowOf(TvTogetherState(joining = true))
            else -> flowOf(TvTogetherState())
        }
    }
    val uiState = combine(room, form, sessionStateProvider.stateFlow) { room, form, session ->
        room.copy(
            roomName = if (room.joined) room.roomName else form.roomName, password = form.password,
            requiresLogin = session !is SessionState.Valid, error = form.error,
        )
    }.stateIn(backgroundScope, SharingStarted.Eagerly, TvTogetherState())

    init {
        manager.start()
        backgroundScope.launch {
            val lastRoomName = settings.watchTogetherSettings.flow.first().lastRoomName
            form.update { if (it.roomName.isEmpty()) it.copy(roomName = lastRoomName) else it }
        }
        backgroundScope.launch {
            manager.effects.collect { effect ->
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
                            error = "房间已结束",
                            password = "",
                        )
                    }

                    WatchTogetherEffect.RejoinFailed -> form.update { it.copy(error = "恢复房间失败，请重新加入") }
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
            is TvTogetherIntent.Foreground -> manager.setAppForeground(intent.value)
            TvTogetherIntent.ToggleFollowing -> backgroundScope.launch { manager.setFollowing(!uiState.value.following) }
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
                    form.update { it.copy(error = "请输入房间名称") }
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
                        if (result == null) form.update { it.copy(error = "加入超时，请检查网络") }
                        result?.onFailure { error ->
                            val message = when ((error as? WatchTogetherJoinException)?.failure) {
                                WatchTogetherJoinFailure.WRONG_PASSWORD -> "房间密码不正确"
                                WatchTogetherJoinFailure.ROOM_FULL -> "房间人数已满"
                                WatchTogetherJoinFailure.ROOM_CLOSED -> "房间已关闭"
                                WatchTogetherJoinFailure.INVALID_NAME -> "房间名称不符合要求"
                                WatchTogetherJoinFailure.INVALID_PASSWORD -> "密码不符合要求"
                                WatchTogetherJoinFailure.RATE_LIMITED -> "操作过于频繁，请稍后重试"
                                else -> "加入失败"
                            }
                            form.update { it.copy(error = message) }
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
