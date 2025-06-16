/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.onboarding.navigation.WizardController
import me.him188.ani.app.ui.onboarding.navigation.WizardDefaults
import me.him188.ani.app.ui.onboarding.navigation.WizardNavHost
import me.him188.ani.app.ui.onboarding.step.ConfigureProxyStep
import me.him188.ani.app.ui.onboarding.step.GrantNotificationPermissionState
import me.him188.ani.app.ui.onboarding.step.ThemeSelectStep
import me.him188.ani.app.ui.onboarding.step.ThemeSelectUIState
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.network.ConfigureProxyState
import me.him188.ani.app.ui.settings.tabs.network.ConfigureProxyUIState
import me.him188.ani.app.ui.settings.tabs.network.ProxyOverallTestState
import me.him188.ani.app.ui.settings.tabs.network.ProxyTestCase
import me.him188.ani.app.ui.settings.tabs.network.ProxyTestCaseState
import me.him188.ani.app.ui.settings.tabs.network.ProxyTestItem
import me.him188.ani.app.ui.settings.tabs.network.ProxyTestState
import me.him188.ani.app.ui.settings.tabs.network.ProxyUIConfig
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.OnboardingStart
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
    val finishOnboarding = {
        vm.finishOnboarding()
        onFinishOnboarding()
    }

    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(vm) {
        val job = scope.launch { vm.startAuthorizeCheckAndProxyTesterLoop() }
        onPauseOrDispose { job.cancel() }
    }

    Surface(color = AniThemeDefaults.pageContentBackgroundColor) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            OnboardingScreen(
                modifier = modifier,
                controller = vm.wizardController,
                state = vm.onboardingState,
                onFinishOnboarding = finishOnboarding,
                contactActions = contactActions,
                navigationIcon = navigationIcon,
                windowInsets = windowInsets,
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    controller: WizardController,
    state: OnboardingPresentationState,
    onFinishOnboarding: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var bangumiShowTokenAuthorizePage by remember { mutableStateOf(false) }

    val proxyState by state.configureProxyState.state.collectAsStateWithLifecycle(ConfigureProxyUIState.Placeholder)
    val grantNotificationPermissionState by state.bitTorrentFeatureState.grantNotificationPermissionState
        .collectAsStateWithLifecycle(GrantNotificationPermissionState.Placeholder)

    DisposableEffect(Unit) {
        Analytics.recordEvent(OnboardingStart)
        onDispose { }
    }

    WizardNavHost(
        controller,
        onCompleted = onFinishOnboarding,
        modifier = modifier,
        windowInsets = windowInsets,
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
                onUpdateSeedColor = { state.themeSelectState.onUpdateSeedColor(it) },
            )
        }
        step(
            "proxy",
            title = { Text("网络设置") },
            autoSkip = { proxyState.overallState == ProxyOverallTestState.SUCCESS },
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

            DisposableEffect(Unit) {
                configureProxyState.onRequestReTest()
                onDispose { }
            }

            DisposableEffect(Unit) {
                Analytics.recordEvent(AnalyticsEvent.OnboardingNetworkEnter)
                onDispose { }
            }

            ConfigureProxyStep(
                state = proxyState,
                onUpdate = { new ->
                    configureProxyState.updateConfig(proxyState.config, new, proxyState.systemProxy)
                },
                onRequestReTest = { configureProxyState.onRequestReTest() },
            )
        }
        /*step(
            "bittorrent",
            { Text("BT 播放和缓存") },
            forwardButton = {
                WizardDefaults.GoForwardButton(
                    {
                        scope.launch {
                            controller.goForward()
                        }
                    },
                    enabled = state.bitTorrentFeatureState.enabled.value,
                    colors = if (grantNotificationPermissionState.granted) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                )
            },
        ) {
            val monoTasker = rememberUiMonoTasker()

            val configState = state.bitTorrentFeatureState.enabled

            LifecycleResumeEffect(state) {
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
        }*/
    }
}

@TestOnly
internal fun createTestOnboardingPresentationState(scope: CoroutineScope): OnboardingPresentationState {
    return OnboardingPresentationState(
        themeSelectState = ThemeSelectStepState(
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
        bitTorrentFeatureState = BitTorrentFeatureStepState(
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
    )
}