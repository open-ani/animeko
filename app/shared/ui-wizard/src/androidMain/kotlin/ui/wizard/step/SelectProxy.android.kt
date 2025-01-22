/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.wizard.ProxyTestCase

@Preview(showBackground = true)
@Composable
fun PreviewSelectProxyStep() {
    ProvideFoundationCompositionLocalsForPreview {
        SelectProxy(
            config = ProxySettings.Default,
            testRunning = true,
            currentTestMode = ProxyMode.DISABLED,
            testItems = remember {
                buildList {
                    add(ProxyTestItem(ProxyTestCase.AniDanmakuApi, ProxyTestCaseState.RUNNING))
                    add(ProxyTestItem(ProxyTestCase.BangumiMasterApi, ProxyTestCaseState.SUCCESS))
                    add(ProxyTestItem(ProxyTestCase.BangumiNextApi, ProxyTestCaseState.FAILED))
                }
            },
            systemProxy = SystemProxyPresentation.NotDetected,
            onUpdate = { _, _ -> },
        )
    }
}