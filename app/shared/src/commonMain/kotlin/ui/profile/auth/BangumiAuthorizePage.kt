/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.onboarding.navigation.WizardNavHost
import me.him188.ani.app.ui.onboarding.navigation.rememberWizardController
import me.him188.ani.app.ui.onboarding.step.BangumiAuthorizeStep

/**
 * Reuse of [BangumiAuthorizeStep]
 */
@Composable
fun BangumiAuthorizePage(
    vm: BangumiAuthorizeViewModel,
    onClickBackNavigation: () -> Unit,
    onFinishLogin: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle(BangumiAuthorizeState.Placeholder)

    LaunchedEffect(vm) {
        vm.collectNewLoginEvent {
            onFinishLogin()
        }
    }

    // 这里不可以在无 lifecycle 时停止 loop, 因为服务器收到请求就会删掉库里的内容, 客户端如果 cancel 就再也收不到登录结果了
    LaunchedEffect(vm) {
        vm.authCheckLoop()
    }

    Surface(color = AniThemeDefaults.pageContentBackgroundColor) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            BangumiAuthorizePage(
                authState = state.authState,
                onCheckCurrentToken = { vm.checkCurrentToken() },
                onClickNavigateAuthorize = { vm.navigateToAuthorize(context) },
                onCancelAuthorize = { vm.cancelAuthorize() },
                onAuthorizeByToken = { vm.authorizeByToken(it) },
                onClickNavigateToBangumiDev = { vm.navigateToBangumiDev(context) },
                onClickBack = onClickBackNavigation,
                modifier = modifier,
                windowInsets = windowInsets,
            )
        }
    }
}

@Composable
fun BangumiAuthorizePage(
    authState: AuthState,
    onCheckCurrentToken: () -> Unit,
    onClickNavigateAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    onAuthorizeByToken: (String) -> Unit,
    onClickNavigateToBangumiDev: () -> Unit,
    onClickBack: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val scope = rememberCoroutineScope()
    var bangumiShowTokenAuthorizePage by remember { mutableStateOf(false) }

    DisposableEffect(bangumiShowTokenAuthorizePage) {
        if (!bangumiShowTokenAuthorizePage) onCheckCurrentToken()
        onDispose {
            onCancelAuthorize()
        }
    }

    BackHandler(bangumiShowTokenAuthorizePage) {
        bangumiShowTokenAuthorizePage = false
    }

    WizardNavHost(
        rememberWizardController(),
        onCompleted = { },
        modifier = modifier,
        windowInsets = windowInsets,
    ) {
        step(
            key = "bangumi_authorize",
            title = { Text("登录 Bangumi 账号") },
            navigationIcon = {
                BackNavigationIconButton(
                    {
                        if (bangumiShowTokenAuthorizePage) {
                            bangumiShowTokenAuthorizePage = false
                            onCheckCurrentToken()
                        } else {
                            onClickBack()
                        }
                    },
                )
            },
            skipButton = { },
            controlBar = { insets ->
                Spacer(Modifier.padding(insets.asPaddingValues()))
            },
        ) {
            BangumiAuthorizeStep(
                authorizeState = authState,
                showTokenAuthorizePage = bangumiShowTokenAuthorizePage,
                contactActions = { AniContactList() },
                onSetShowTokenAuthorizePage = {
                    bangumiShowTokenAuthorizePage = it
                    if (it) scope.launch { scrollTopAppBarExpanded() }
                },
                onClickAuthorize = onClickNavigateAuthorize,
                onCancelAuthorize = onCancelAuthorize,
                onAuthorizeByToken = onAuthorizeByToken,
                onClickNavigateToBangumiDev = onClickNavigateToBangumiDev,
            )
        }
    }
}