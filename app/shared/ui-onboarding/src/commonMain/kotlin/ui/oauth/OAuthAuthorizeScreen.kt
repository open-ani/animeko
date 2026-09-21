/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.oauth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.login.EmailLoginScreenLayout
import org.jetbrains.compose.resources.*

@Composable
fun OAuthAuthorizeScreen(
    vm: OAuthAuthorizeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateSettings: () -> Unit,
    onAuthorizeSuccess: () -> Unit,
    contactActions: @Composable () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle(AuthState.NoAniAccount)
    val scope = rememberCoroutineScope()
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.collectNewLoginEvent {
            onAuthorizeSuccess()
        }
    }

    suspend fun startOAuth(currentState: AuthState) {
        if (currentState is AuthState.AwaitingResult) return
        vm.doOAuth(
            currentState is AuthState.NoAniAccount || (currentState is AuthState.Failed && !currentState.loggedIn),
        ) {
            browserNavigator.openBrowser(context, it)
        }
    }

    if (vm.platform.startsAuthorizationImmediately) {
        LaunchedEffect(vm) {
            // 等到真实的登录状态 (而不是 collectAsState 的初始值) 才能决定是注册还是绑定
            startOAuth(vm.state.first { it is AuthState.Idle })
        }
    }

    OAuthAuthorizeScreen(
        platform = vm.platform,
        state = state,
        onClickAuthorize = {
            scope.launch { startOAuth(state) }
        },
        onCancelAuthorize = { vm.cancelCurrentOAuth() },
        onNavigateSettings = onNavigateSettings,
        onNavigateBack = onNavigateBack,
        contactActions = contactActions,
    )
}

@Composable
internal fun OAuthAuthorizeScreen(
    platform: OAuthPlatform,
    state: AuthState,
    onClickAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    contactActions: @Composable () -> Unit,
) {
    EmailLoginScreenLayout(
        onThirdPartyLoginClick = {},
        onNavigateSettings = onNavigateSettings,
        onNavigateBack = onNavigateBack,
        title = { Text(stringResource(Lang.oauth_authorize_title, platform.displayName)) },
    ) { scrollState ->
        OAuthAuthorizeLayout(
            platform = platform,
            authorizeState = state,
            contactActions = contactActions,
            onClickAuthorize = onClickAuthorize,
            onCancelAuthorize = onCancelAuthorize,
            scrollState = scrollState,
        )
    }
}

/**
 * 进入页面就打开浏览器开始授权, 不需要用户再点一次. 页面只用于展示等待、失败与取消.
 * Bangumi 例外: 它的说明与帮助问答对不熟悉 Bangumi 的用户有用, 保留手动开始.
 */
private val OAuthPlatform.startsAuthorizationImmediately: Boolean
    get() = this != OAuthPlatform.BANGUMI
