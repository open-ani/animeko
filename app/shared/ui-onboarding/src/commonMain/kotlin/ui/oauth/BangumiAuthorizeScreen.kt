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
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.app.ui.login.EmailLoginScreenLayout

@Composable
fun BangumiAuthorizeScreen(
    vm: BangumiAuthorizeViewModel,
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

    BangumiAuthorizeScreen(
        state = state,
        onClickAuthorize = {
            scope.launch {
                val currentState = state
                if (currentState is AuthState.AwaitingResult) return@launch

                vm.doOAuth(
                    state is AuthState.NoAniAccount || (currentState is AuthState.Failed && !currentState.loggedIn),
                ) {
                    browserNavigator.openBrowser(context, it)
                }
            }
        },
        onCancelAuthorize = { vm.cancelCurrentOAuth() },
        onNavigateSettings = onNavigateSettings,
        onNavigateBack = onNavigateBack,
        contactActions = contactActions,
    )
}

@Composable
internal fun BangumiAuthorizeScreen(
    state: AuthState,
    onClickAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    contactActions: @Composable () -> Unit,
) {
    EmailLoginScreenLayout(
        onBangumiLoginClick = {},
        onNavigateSettings = onNavigateSettings,
        onNavigateBack = onNavigateBack,
        title = { Text("授权 Bangumi 登录") },
        showThirdPartyLogin = false,
    ) { scrollState ->
        BangumiAuthorizeLayout(
            authorizeState = state,
            contactActions = contactActions,
            onClickAuthorize = onClickAuthorize,
            onCancelAuthorize = onCancelAuthorize,
            scrollState = scrollState,
        )
    }
}