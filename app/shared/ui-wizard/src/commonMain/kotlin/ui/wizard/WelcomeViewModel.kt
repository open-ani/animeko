/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Preview
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.wizard.navigation.WizardController
import me.him188.ani.app.ui.wizard.step.ProxyTestCaseState
import me.him188.ani.app.ui.wizard.step.ProxyTestItem
import me.him188.ani.app.ui.wizard.step.ProxyTestState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WelcomeViewModel : AbstractViewModel(), KoinComponent {
    private val proxyProvider: ProxyProvider by inject()

    private val proxyTestTasker = MonoTasker(backgroundScope)

    private val themeConfig = mutableStateOf(ThemeSettings.Default)
    private val proxyConfig = mutableStateOf(ProxySettings.Default)
    private val proxyTestCases: StateFlow<List<ProxyTestCase>> =
        MutableStateFlow(ProxyTestCase.All)

    private val currentProxyTestMode = MutableStateFlow(ProxyMode.DISABLED)
    private val systemProxy = proxyProvider.proxy
        .map {
            if (it == null) SystemProxyPresentation.NotDetected
            else SystemProxyPresentation.Detected(it)
        }
        .onStart { emit(SystemProxyPresentation.Detecting) }
        .shareInBackground()
    private val proxyTestResults = proxyTestCases.map { cases ->
        persistentMapOf<ProxyTestCaseEnums, ProxyTestCaseState>().mutate { map ->
            map.putAll(cases.associate { it.name to ProxyTestCaseState.INIT })
        }
    }

    private val proxyTestState = ProxyTestState(
        testRunning = proxyTestTasker.isRunning.produceState(false),
        currentTestMode = currentProxyTestMode.produceState(ProxyMode.DISABLED),
        items = combine(proxyTestCases, proxyTestResults) { cases, results ->
            cases.map { ProxyTestItem(it, results.getValue(it.name)) }
        }
            .stateIn(backgroundScope, SharingStarted.Lazily, emptyList())
            .produceState(),
    )

    private val selectProxyState = SelectProxyState(
        configState = SettingsState(
            valueState = proxyConfig,
            onUpdate = { proxyConfig.value = it },
            placeholder = ProxySettings.Default,
            backgroundScope = backgroundScope,
        ),
        systemProxy = systemProxy.produceState(SystemProxyPresentation.Detecting),
        testState = proxyTestState,
        onUpdateProxyTestMode = { currentProxyTestMode.value = it },
    )

    var welcomeNavController: NavController? by mutableStateOf(null)

    val wizardController = WizardController()
    val wizardState = WizardPresentationState(
        selectThemeState = SettingsState(
            valueState = themeConfig,
            onUpdate = { themeConfig.value = it },
            placeholder = ThemeSettings.Default,
            backgroundScope = backgroundScope,
        ),
        selectProxyState = selectProxyState,
    )
}

@Stable
class WizardPresentationState(
    val selectThemeState: SettingsState<ThemeSettings>,
    val selectProxyState: SelectProxyState,
)

@Stable
class SelectProxyState(
    val configState: SettingsState<ProxySettings>,
    val systemProxy: State<SystemProxyPresentation>,
    val testState: ProxyTestState,
    val onUpdateProxyTestMode: (ProxyMode) -> Unit
)

@Immutable
enum class ProxyTestCaseEnums {
    ANI_DANMAKU_API,
    BANGUMI_V0,
    BANGUMI_P1,
}

@Immutable
sealed class ProxyTestCase(
    val name: ProxyTestCaseEnums,
    val icon: ImageVector,
    val url: String
) {
    data object AniDanmakuApi : ProxyTestCase(
        name = ProxyTestCaseEnums.ANI_DANMAKU_API,
        icon = Icons.Default.Preview,
        url = "https://danmaku-cn.myani.org/status",
    )

    data object BangumiMasterApi : ProxyTestCase(
        name = ProxyTestCaseEnums.BANGUMI_V0,
        icon = Icons.Default.Preview,
        url = "https://api.bgm.tv/",
    )

    data object BangumiNextApi : ProxyTestCase(
        name = ProxyTestCaseEnums.BANGUMI_P1,
        icon = Icons.Default.Preview,
        url = "https://next.bgm.tv",
    )

    companion object {
        val All = listOf(AniDanmakuApi, BangumiMasterApi, BangumiNextApi)
    }
}