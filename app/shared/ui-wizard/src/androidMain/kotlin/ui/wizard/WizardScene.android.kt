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
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.wizard.navigation.rememberWizardController
import me.him188.ani.app.ui.wizard.step.ProxyTestCaseState
import me.him188.ani.app.ui.wizard.step.ProxyTestItem
import me.him188.ani.app.ui.wizard.step.ProxyTestState

@Preview
@Composable
fun PreviewWizardScene() {
    ProvideFoundationCompositionLocalsForPreview {
        val scope = rememberCoroutineScope()
        WizardScene(
            rememberWizardController(),
            remember { createTestWizardPresentationState(scope) },
        )
    }
}

internal fun createTestWizardPresentationState(scope: CoroutineScope): WizardPresentationState {
    return WizardPresentationState(
        selectThemeState = SettingsState(
            valueState = stateOf(ThemeSettings.Default),
            onUpdate = { },
            placeholder = ThemeSettings.Default,
            backgroundScope = scope,
        ),
        configureProxyState = ConfigureProxyState(
            configState = SettingsState(
                valueState = stateOf(ProxySettings.Default),
                onUpdate = { },
                placeholder = ProxySettings.Default,
                backgroundScope = scope,
            ),
            systemProxy = stateOf(SystemProxyPresentation.Detecting),
            testState = ProxyTestState(
                testRunning = stateOf(false),
                items = stateOf(
                    buildList {
                        add(ProxyTestItem(ProxyTestCase.AniDanmakuApi, ProxyTestCaseState.RUNNING))
                        add(ProxyTestItem(ProxyTestCase.BangumiMasterApi, ProxyTestCaseState.SUCCESS))
                        add(ProxyTestItem(ProxyTestCase.BangumiNextApi, ProxyTestCaseState.FAILED))
                    },
                ),
            ),
        ),
    )
}