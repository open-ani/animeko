/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.oauth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.auth.BangumiOAuthClient
import me.him188.ani.app.domain.session.auth.OAuthConfigurator
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BangumiAuthorizeViewModel : AbstractViewModel(), KoinComponent {
    private val aniApiProvider: AniApiProvider by inject()
    private val sessionManager: SessionManager by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val browserNavigator: BrowserNavigator by inject()

    private var currentContext: ContextMP? = null
    private val tasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    private val configurator = OAuthConfigurator(
        client = BangumiOAuthClient(aniApiProvider.bangumiApi, sessionStateProvider),
        sessionManager = sessionManager,
        sessionStateProvider = sessionStateProvider,
        onOpenUrl = { url ->
            browserNavigator.openBrowser(
                currentContext ?: throw IllegalStateException("Current context is null, failed to open browser"),
                url,
            )
        },
    )

    val state: Flow<AuthState> =
        combine(sessionStateProvider.stateFlow, configurator.state) { sessionState, authState ->
            when (authState) {
                is OAuthConfigurator.State.Idle -> {
                    if (sessionState is SessionState.Valid) {
                        AuthState.LoggedInAni(sessionState.bangumiConnected)
                    } else {
                        AuthState.NoAniAccount
                    }
                }

                is OAuthConfigurator.State.AwaitingResult -> AuthState.AwaitingResult
                is OAuthConfigurator.State.UnknownError -> AuthState.UnknownError(LoadError.fromException(authState.error))
                is OAuthConfigurator.State.KnownError -> AuthState.KnownError(authState.error)
                is OAuthConfigurator.State.Success -> {
                    if (sessionState is SessionState.Valid) {
                        AuthState.Success
                    } else {
                        AuthState.AwaitingResult
                    }
                }
            }
        }

    suspend fun startOAuth(context: ContextMP, isRegister: Boolean) {
        currentContext = context
        tasker.invoke {
            if (configurator.state.value is OAuthConfigurator.State.AwaitingResult) {
                // 已经在等待结果了, 不需要重复开始
                return@invoke
            }

            configurator.auth(isRegister)
        }
    }

    suspend fun collectNewLoginEvent(block: () -> Unit) {
        sessionManager.stateProvider
            .eventFlow
            .filterIsInstance<SessionEvent.NewLogin>()
            .collect { block() }
    }

    fun cancelCurrentOAuth() {
        tasker.cancelCurrent()
    }
}
