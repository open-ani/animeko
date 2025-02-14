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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.session.AuthStateNew
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.wizard.navigation.WizardController
import me.him188.ani.app.ui.wizard.navigation.WizardDefaults
import me.him188.ani.app.ui.wizard.navigation.WizardNavHost
import me.him188.ani.app.ui.wizard.step.BangumiAuthorizeStep
import me.him188.ani.app.ui.wizard.step.BitTorrentFeatureStep
import me.him188.ani.app.ui.wizard.step.ConfigureProxyStep
import me.him188.ani.app.ui.wizard.step.ConfigureProxyUIState
import me.him188.ani.app.ui.wizard.step.GrantNotificationPermissionState
import me.him188.ani.app.ui.wizard.step.ProxyOverallTestState
import me.him188.ani.app.ui.wizard.step.ProxyTestCaseState
import me.him188.ani.app.ui.wizard.step.ProxyTestItem
import me.him188.ani.app.ui.wizard.step.ProxyTestState
import me.him188.ani.app.ui.wizard.step.ProxyUIConfig
import me.him188.ani.app.ui.wizard.step.RequestNotificationPermission
import me.him188.ani.app.ui.wizard.step.ThemeSelectStep
import me.him188.ani.app.ui.wizard.step.ThemeSelectUIState
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
fun OnboardingScreen(
    vm: OnboardingViewModel,
    onFinishOnboarding: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    LaunchedEffect(Unit) {
        vm.collectNewLoginEvent {
            onFinishOnboarding()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        OnboardingPage(
            modifier = modifier,
            wizardController = vm.wizardController,
            state = vm.onboardingState,
            onFinishOnboarding = {
                vm.finishOnboarding()
                onFinishOnboarding()
            },
            contactActions = contactActions,
            navigationIcon = navigationIcon,
            windowInsets = windowInsets,
        )
    }
}

@Composable
fun OnboardingPage(
    wizardController: WizardController,
    state: OnboardingPresentationState,
    onFinishOnboarding: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent()
) {
    OnboardingScene(
        controller = wizardController,
        state = state,
        navigationIcon = navigationIcon,
        modifier = modifier,
        windowInsets = windowInsets,
        contactActions = contactActions,
        onFinishOnboarding = onFinishOnboarding,
    )
}

@Composable
internal fun OnboardingScene(
    controller: WizardController,
    state: OnboardingPresentationState,
    onFinishOnboarding: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var bangumiShowTokenAuthorizePage by remember { mutableStateOf(false) }

    val authorizeState by state.bangumiAuthorizeState.state.collectAsStateWithLifecycle(AuthStateNew.Idle)
    val proxyState by state.configureProxyState.state.collectAsStateWithLifecycle(ConfigureProxyUIState.Placeholder)

    WizardNavHost(
        controller,
        modifier = modifier,
        windowInsets = windowInsets
    ) {
        step(
            "theme",
            { Text("主题设置") },
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

            BitTorrentFeatureStep(
                bitTorrentEnabled = configState.value,
                onBitTorrentEnableChanged = { configState.update(it) },
                bitTorrentCheckFeatureItem = { }, // disabled because we haven't support disable torrent engine.
                requestNotificationPermission = if (grantNotificationPermissionState.showGrantNotificationItem) {
                    { layoutParams ->
                        Column(modifier = Modifier.padding(horizontal = layoutParams.horizontalPadding)) {
                            RequestNotificationPermission(
                                granted = grantNotificationPermissionState.granted,
                                onRequestNotificationPermission = {
                                    monoTasker.launch {
                                        if (grantNotificationPermissionState.lastRequestResult == false) {
                                            state.bitTorrentFeatureState.onOpenSystemNotificationSettings(context)
                                        } else {
                                            state.bitTorrentFeatureState.onRequestNotificationPermission(context)
                                        }
                                    }
                                }
                            )
                        }
                        
                    }
                } else null,
            )
        }
        step(
            "bangumi",
            { Text("Bangumi 授权") },
            forwardButton = {
                WizardDefaults.GoForwardButton(
                    onFinishOnboarding,
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
                        scope.launch {
                            state.bangumiAuthorizeState.onUseGuestMode()
                            onFinishOnboarding()
                        }
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

@TestOnly
internal fun createTestOnboardingPresentationState(scope: CoroutineScope): OnboardingPresentationState {
    return OnboardingPresentationState(
        themeSelectState = ThemeSelectState(
            state = flowOf(ThemeSelectUIState.Placeholder),
            onUpdateUseDarkMode = { },
            onUpdateUseDynamicTheme = { },
            onUpdateSeedColor = { },
        ),
        configureProxyState = ConfigureProxyState(
            state = flowOf(
                ConfigureProxyUIState(
                    config = ProxyUIConfig.Default,
                    systemProxy = SystemProxyPresentation.Detecting,
                    testState = ProxyTestState(
                        testRunning = false,
                        items = buildList {
                            add(ProxyTestItem(ProxyTestCase.AniDanmakuApi, ProxyTestCaseState.RUNNING))
                            add(ProxyTestItem(ProxyTestCase.BangumiApi, ProxyTestCaseState.SUCCESS))
                            add(ProxyTestItem(ProxyTestCase.BangumiNextApi, ProxyTestCaseState.FAILED))
                        },
                    ),
                ),
            ),
            onUpdateConfig = { },
            onRequestReTest = { },
        ),
        bitTorrentFeatureState = BitTorrentFeatureState(
            enabled = SettingsState(
                valueState = stateOf(true),
                onUpdate = { },
                placeholder = true,
                backgroundScope = scope,
            ),
            grantNotificationPermissionState = flowOf(
                GrantNotificationPermissionState(
                    showGrantNotificationItem = true,
                    granted = false,
                    lastRequestResult = null,
                ),
            ),
            onCheckPermissionState = { },
            onRequestNotificationPermission = { false },
            onOpenSystemNotificationSettings = { },
        ),
        bangumiAuthorizeState = BangumiAuthorizeState(
            state = flowOf(AuthStateNew.Idle),
            onClickNavigateAuthorize = { },
            onCheckCurrentToken = { },
            onCancelAuthorize = { },
            onClickNavigateToBangumiDev = { },
            onAuthorizeViaToken = { },
            onUseGuestMode = { },
        ),
    )
}