/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.step


import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.onboarding.WizardLayoutParams
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.tabs.network.ConfigureProxyGroup
import me.him188.ani.app.ui.settings.tabs.network.ConfigureProxyUIState
import me.him188.ani.app.ui.settings.tabs.network.ProxyUIConfig

@Composable
internal fun ConfigureProxyStep(
    state: ConfigureProxyUIState,
    onUpdate: (config: ProxyUIConfig) -> Unit,
    onRequestReTest: () -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: WizardLayoutParams = WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass),
) {
    SettingsTab {
        ConfigureProxyGroup(
            state,
            onUpdate,
            onRequestReTest,
            modifier,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSelectProxyStep() {
    ProvideCompositionLocalsForPreview {
        ConfigureProxyStep(
            state = ConfigureProxyUIState.Placeholder,
            onUpdate = { },
            onRequestReTest = { },
        )
    }
}