/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.ui.main.MainScreenSharedViewModel

class TvMainViewModel(
    private val repository: UserRepository,
    sessionStateProvider: SessionStateProvider,
) : MainScreenSharedViewModel() {
    private val logoutMutex = Mutex()
    private val errors = Channel<LoadError>(Channel.BUFFERED)
    val logoutErrors = errors.receiveAsFlow()

    val uiState = combine(
        sessionStateProvider.stateFlow,
        repository.selfInfoFlow,
    ) { session, selfInfo ->
        val loggedIn = session is SessionState.Valid ||
                (session is SessionState.Invalid && session.reason == InvalidSessionReason.NETWORK_ERROR && selfInfo != null)
        TvMainUiState(selfInfo.takeIf { loggedIn }, isLoggedIn = loggedIn)
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvMainUiState(isLoggedIn = null))

    fun onIntent(intent: TvMainIntent) {
        when (intent) {
            TvMainIntent.Logout -> {
                if (!logoutMutex.tryLock()) return
                backgroundScope.launch {
                    try {
                        repository.clearSelfInfo()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors.send(LoadError.fromException(e))
                    } finally {
                        logoutMutex.unlock()
                    }
                }
            }
        }
    }
}

sealed interface TvMainIntent {
    data object Logout : TvMainIntent
}
