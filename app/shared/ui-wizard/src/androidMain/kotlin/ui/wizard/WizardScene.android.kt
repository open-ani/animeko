/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.session.AuthStateNew
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.wizard.navigation.rememberWizardController
import me.him188.ani.app.ui.wizard.step.ConfigureProxyUIState
import me.him188.ani.app.ui.wizard.step.GrantNotificationPermissionState
import me.him188.ani.app.ui.wizard.step.ProxyTestCaseState
import me.him188.ani.app.ui.wizard.step.ProxyTestItem
import me.him188.ani.app.ui.wizard.step.ProxyTestState
import me.him188.ani.app.ui.wizard.step.ProxyUIConfig
import me.him188.ani.app.ui.wizard.step.ThemeSelectUIState

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp", showSystemUi = false)
@Preview(showBackground = true, device = "spec:width=1920px,height=1080px,dpi=240", showSystemUi = false)
@Composable
fun PreviewWizardScene() {
    ProvideCompositionLocalsForPreview {
        val scope = rememberCoroutineScope()
        WizardScene(
            rememberWizardController(),
            remember { createTestWizardPresentationState(scope) },
            contactActions = { },
            navigationIcon = { },
            onFinishWizard = { },
        )
    }
}

internal fun createTestWizardPresentationState(scope: CoroutineScope): WizardPresentationState {
    return WizardPresentationState(
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