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
import me.him188.ani.app.data.models.user.externalAccount
import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.auth.BangumiOAuthClient
import me.him188.ani.app.domain.session.auth.ExternalOAuthClient
import me.him188.ani.app.domain.session.auth.OAuthConfigurator
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.domain.session.canAccessAniApiNow
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 使用第三方账号 [platform] 登录 / 注册, 或为当前用户绑定该账号.
 */
class OAuthAuthorizeViewModel(
    val platform: OAuthPlatform,
) : AbstractViewModel(), KoinComponent {
    private val aniApiProvider: AniApiProvider by inject()
    private val sessionManager: SessionManager by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val userRepository: UserRepository by inject()

    private val tasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    private val configurator = OAuthConfigurator(
        client = when (platform) {
            OAuthPlatform.BANGUMI -> BangumiOAuthClient(aniApiProvider.bangumiApi, sessionStateProvider)
            else -> ExternalOAuthClient(platform, aniApiProvider.oauthApi)
        },
        sessionManager = sessionManager,
        sessionStateProvider = sessionStateProvider,
    )

    val state: Flow<AuthState> =
        combine(sessionStateProvider.stateFlow, userRepository.selfInfoFlow, configurator.state) { sessionState, selfInfo, authState ->
            when (authState) {
                is OAuthConfigurator.State.Idle -> {
                    if (sessionState is SessionState.Valid) {
                        val bound = when (platform) {
                            OAuthPlatform.BANGUMI -> sessionState.bangumiConnected
                            else -> selfInfo?.externalAccount(platform.id) != null
                        }
                        AuthState.LoggedInAni(bound)
                    } else {
                        AuthState.NoAniAccount
                    }
                }

                is OAuthConfigurator.State.AwaitingResult -> AuthState.AwaitingResult
                is OAuthConfigurator.State.Failed -> AuthState.Failed(
                    authState.error,
                    sessionStateProvider.canAccessAniApiNow(),
                )

                is OAuthConfigurator.State.Success -> {
                    if (sessionState is SessionState.Valid) {
                        AuthState.Success
                    } else {
                        AuthState.AwaitingResult
                    }
                }
            }
        }

    suspend fun doOAuth(isRegister: Boolean, onOpenUrl: suspend (String) -> Unit): Boolean {
        val res = tasker.invoke {
            configurator.auth(isRegister, onOpenUrl)
        }
        return res is OAuthConfigurator.State.Success
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
