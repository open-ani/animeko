/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.session.AuthStateNew
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.wizard.navigation.WizardController
import me.him188.ani.app.ui.wizard.navigation.WizardDefaults
import me.him188.ani.app.ui.wizard.navigation.WizardNavHost
import me.him188.ani.app.ui.wizard.step.BangumiAuthorizeStep
import me.him188.ani.app.ui.wizard.step.BitTorrentFeatureStep
import me.him188.ani.app.ui.wizard.step.ConfigureProxyStep
import me.him188.ani.app.ui.wizard.step.ConfigureProxyUIState
import me.him188.ani.app.ui.wizard.step.GrantNotificationPermissionState
import me.him188.ani.app.ui.wizard.step.ProxyOverallTestState
import me.him188.ani.app.ui.wizard.step.RequestNotificationPermission
import me.him188.ani.app.ui.wizard.step.ThemeSelectStep
import me.him188.ani.app.ui.wizard.step.ThemeSelectUIState

@Composable
fun WizardScreen(
    vm: WizardViewModel,
    onFinishWizard: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        WizardPage(
            modifier = modifier,
            wizardController = vm.wizardController,
            wizardState = vm.wizardState,
            onFinishWizard = {
                vm.finishWizard()
                onFinishWizard()
            },
            contactActions = contactActions,
            navigationIcon = navigationIcon,
            windowInsets = windowInsets,
        )
    }
}

@Composable
fun WizardPage(
    wizardController: WizardController,
    wizardState: WizardPresentationState,
    onFinishWizard: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent()
) {
    Box(Modifier.windowInsetsPadding(windowInsets)) {
        WizardScene(
            controller = wizardController,
            state = wizardState,
            navigationIcon = navigationIcon,
            modifier = modifier,
            contactActions = contactActions,
            onFinishWizard = onFinishWizard,
        )
    }
}

@Composable
internal fun WizardScene(
    controller: WizardController,
    state: WizardPresentationState,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    onFinishWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var bangumiShowTokenAuthorizePage by remember { mutableStateOf(false) }

    val authorizeState by state.bangumiAuthorizeState.state.collectAsStateWithLifecycle(AuthStateNew.Idle)
    val proxyState by state.configureProxyState.state.collectAsStateWithLifecycle(ConfigureProxyUIState.Placeholder)

    WizardNavHost(
        controller,
        modifier = modifier,
    ) {
        step(
            "theme",
            { Text("主题设置") },
            backwardButton = { Spacer(Modifier) },
            navigationIcon = navigationIcon,
        ) {
            val themeSelectUiState by state.themeSelectState.state
                .collectAsStateWithLifecycle(ThemeSelectUIState.Placeholder)
            
            ThemeSelectStep(
                config = themeSelectUiState,
                onUpdateUseDarkMode = { state.themeSelectState.onUpdateUseDarkMode(it) },
                onUpdateUseDynamicTheme = { state.themeSelectState.onUpdateUseDynamicTheme(it) },
                onUpdateSeedColor = { state.themeSelectState.onUpdateSeedColor(it) }
            )
        }
        step(
            "proxy",
            title = { Text("网络设置") },
            forwardButton = {
                WizardDefaults.GoForwardButton(
                    {
                        scope.launch {
                            controller.goForward()
                        }
                    },
                    enabled = proxyState.overallState == ProxyOverallTestState.SUCCESS,
                )
            },
            skipButton = {
                WizardDefaults.SkipButton(
                    {
                        scope.launch {
                            controller.goForward()
                        }
                    },
                )
            },
        ) {
            val configureProxyState = state.configureProxyState

            ConfigureProxyStep(
                state = proxyState,
                onUpdate = { configureProxyState.onUpdateConfig(it) },
                onRequestReTest = { configureProxyState.onRequestReTest() }
            )
        }
        step("bittorrent", { Text("BitTorrent") }) {
            val monoTasker = rememberUiMonoTasker()
            
            val configState = state.bitTorrentFeatureState.enabled
            val grantNotificationPermissionState by state.bitTorrentFeatureState.grantNotificationPermissionState
                .collectAsStateWithLifecycle(GrantNotificationPermissionState.Placeholder)

            LifecycleResumeEffect(Unit) {
                state.bitTorrentFeatureState.onCheckPermissionState(context)
                onPauseOrDispose { }
            }

            DisposableEffect(Unit) {
                state.bitTorrentFeatureState.onCheckPermissionState(context)
                onDispose { }
            }

            BitTorrentFeatureStep(
                bitTorrentEnabled = configState.value,
                onBitTorrentEnableChanged = { configState.update(it) },
                requestNotificationPermission = if (grantNotificationPermissionState.showGrantNotificationItem) {
                    {
                        RequestNotificationPermission(
                            grantedNotificationPermission = grantNotificationPermissionState.granted,
                            showPermissionError = grantNotificationPermissionState.lastRequestResult == false,
                            onRequestNotificationPermission = {
                                monoTasker.launch {
                                    val granted = state.bitTorrentFeatureState.onRequestNotificationPermission(context)
                                    // 授权失败就滚动到底部, 底部有错误信息
                                    if (!granted) wizardScrollState.animateScrollTo(wizardScrollState.maxValue)
                                }
                            },
                            onOpenSystemNotificationSettings = {
                                state.bitTorrentFeatureState.onOpenSystemNotificationSettings(context)
                            },
                        )
                    }
                } else null,
            )
        }
        step(
            "bangumi",
            { Text("Bangumi 授权") },
            forwardButton = {
                WizardDefaults.GoForwardButton(
                    onFinishWizard,
                    text = "完成",
                    enabled = authorizeState is AuthStateNew.Success,
                )
            },
            navigationIcon = {
                BackNavigationIconButton(
                    {
                        if (bangumiShowTokenAuthorizePage) {
                            bangumiShowTokenAuthorizePage = false
                            state.bangumiAuthorizeState.onCheckCurrentToken()
                        } else {
                            scope.launch {
                                controller.goBackward()
                            }
                        }
                    },
                )
            },
            skipButton = {
                WizardDefaults.SkipButton(
                    {
                        state.bangumiAuthorizeState.onUseGuestMode()
                        onFinishWizard()
                    },
                    text = "跳过",
                )
            },
        ) {
            // 如果 45s 没等到结果, 那可以认为用户可能遇到了麻烦, 我们自动滚动到底部, 底部有帮助列表
            LaunchedEffect(authorizeState) {
                if (authorizeState is AuthStateNew.AwaitingResult) {
                    delay(45_000)
                    coroutineScope {
                        launch { scrollTopAppBarCollapsed() }
                        launch { wizardScrollState.animateScrollTo(wizardScrollState.maxValue) }
                    }
                }
            }
            
            // 每次进入这一步都会检查 token 是否有效, 以及退出这一步时要取消正在进行的授权请求
            DisposableEffect(Unit) {
                state.bangumiAuthorizeState.onCheckCurrentToken()
                onDispose {
                    state.bangumiAuthorizeState.onCancelAuthorize()
                }
            }

            BackHandler(bangumiShowTokenAuthorizePage) {
                bangumiShowTokenAuthorizePage = false
            }

            BangumiAuthorizeStep(
                authorizeState = authorizeState,
                showTokenAuthorizePage = bangumiShowTokenAuthorizePage,
                contactActions = contactActions,
                onSetShowTokenAuthorizePage = { bangumiShowTokenAuthorizePage = it },
                onClickAuthorize = { state.bangumiAuthorizeState.onClickNavigateAuthorize(context) },
                onCancelAuthorize = { state.bangumiAuthorizeState.onCancelAuthorize() },
                onAuthorizeViaToken = { state.bangumiAuthorizeState.onAuthorizeViaToken(it) },
                onClickNavigateToBangumiDev = {
                    state.bangumiAuthorizeState.onClickNavigateToBangumiDev(context)
                }
            )
        }
    }
}