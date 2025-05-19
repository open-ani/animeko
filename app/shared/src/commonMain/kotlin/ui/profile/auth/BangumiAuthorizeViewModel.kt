/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile.auth

import androidx.compose.runtime.Stable
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.auth.AniAuthConfigurator
import me.him188.ani.app.domain.session.auth.AuthState
import me.him188.ani.app.domain.session.auth.OAuthClient
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BangumiAuthorizeViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()
    private val browserNavigator: BrowserNavigator by inject()
    private val authClient: OAuthClient by inject()
    private val settings: SettingsRepository by inject()

    private var currentAppContext: ContextMP? = null
    private val authLoopTasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    private val authConfigurator = AniAuthConfigurator(
        sessionManager = sessionManager,
        authClient = authClient,
        onLaunchAuthorize = { requestId ->
            currentAppContext?.let { openBrowserAuthorize(it, requestId) }
        },
        parentCoroutineContext = backgroundScope.coroutineContext,
    )

    val state: Flow<BangumiAuthorizeState> =
        authConfigurator.state
            .map { authState ->
                BangumiAuthorizeState(
                    authState = authState,
                )
            }
            .stateInBackground(
                initialValue = BangumiAuthorizeState.Placeholder,
                SharingStarted.WhileSubscribed(),
            )

    fun navigateToAuthorize(context: ContextMP) {
        currentAppContext = context
        backgroundScope.launch { authConfigurator.startAuthorize() }
    }

    fun cancelAuthorize() {
        authConfigurator.cancelAuthorize()
    }

    fun checkCurrentToken() {
        authConfigurator.checkAuthorizeState()
    }

    fun navigateToBangumiDev(context: ContextMP) {
        browserNavigator.openBrowser(context, "https://next.bgm.tv/demo/access-token/create")
    }

    fun authorizeByToken(token: String) {
        backgroundScope.launch { authConfigurator.authorizeByBangumiToken(token) }
    }

    suspend fun authCheckLoop() {
        authLoopTasker.invoke {
            authConfigurator.authorizeRequestCheckLoop()
        }
    }

    private suspend fun openBrowserAuthorize(context: ContextMP, requestId: String) {
        val base = currentAniBuildConfig.aniAuthServerUrl.removeSuffix("/")
        val url = "${base}/v1/login/bangumi/oauth?requestId=${requestId.encodeURLParameter()}"

        withContext(Dispatchers.Main) {
            browserNavigator.openBrowser(context, url)
        }
    }

    suspend fun collectNewLoginEvent(onLogin: suspend () -> Unit) {
        sessionManager.events
            .filterIsInstance<SessionEvent.NewLogin>()
            .collectLatest { onLogin() }
    }
}

@Stable
class BangumiAuthorizeState(
    val authState: AuthState,
) {
    companion object {
        @Stable
        val Placeholder = BangumiAuthorizeState(AuthState.NotAuthed)
    }
}