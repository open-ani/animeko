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
import io.ktor.client.request.get
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.domain.media.fetch.toClientProxyConfig
import me.him188.ani.app.domain.settings.ProxySettingsFlowProxyProvider
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.GrantedPermissionManager
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.wizard.navigation.WizardController
import me.him188.ani.app.ui.wizard.step.NotificationPermissionState
import me.him188.ani.app.ui.wizard.step.ProxyTestCaseState
import me.him188.ani.app.ui.wizard.step.ProxyTestItem
import me.him188.ani.app.ui.wizard.step.ProxyTestState
import me.him188.ani.utils.coroutines.flows.FlowRunning
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.proxy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WelcomeViewModel : AbstractViewModel(), KoinComponent {
    private val permissionManager: PermissionManager by inject()
    private val themeConfig = mutableStateOf(ThemeSettings.Default)
    private val proxyConfig = MutableStateFlow(ProxySettings.Default)
    private val bitTorrentEnabled = mutableStateOf(true)

    private val proxyTestRunning = FlowRunning()
    private val proxyProvider = ProxySettingsFlowProxyProvider(proxyConfig, backgroundScope)
    private val proxyTestCases: StateFlow<List<ProxyTestCase>> =
        MutableStateFlow(ProxyTestCase.All)
    private val currentProxyTestMode = proxyConfig.map { it.default.mode }
    private val proxyTestResults = MutableStateFlow(
        persistentMapOf<ProxyTestCaseEnums, ProxyTestCaseState>()
            .mutate { map ->
                map.putAll(
                    proxyTestCases.value.associate { it.name to ProxyTestCaseState.INIT },
                )
            },
    )

    private val systemProxyPresentation =
        combine(currentProxyTestMode, proxyProvider.proxy) { mode, proxy ->
            if (mode == ProxyMode.SYSTEM && proxy != null)
                SystemProxyPresentation.Detected(proxy)
            else SystemProxyPresentation.NotDetected
        }
            .stateIn(backgroundScope, SharingStarted.Lazily, SystemProxyPresentation.Detecting)

    private val configureProxyState = ConfigureProxyState(
        configState = SettingsState(
            valueState = proxyConfig.produceState(),
            onUpdate = { proxyConfig.value = it },
            placeholder = ProxySettings.Default,
            backgroundScope = backgroundScope,
        ),
        systemProxy = systemProxyPresentation.produceState(),
        testState = ProxyTestState(
            testRunning = proxyTestRunning.isRunning.produceState(false),
            items = combine(proxyTestCases, proxyTestResults) { cases, results ->
                cases.map {
                    ProxyTestItem(it, results[it.name] ?: ProxyTestCaseState.INIT)
                }
            }
                .stateIn(backgroundScope, SharingStarted.Lazily, emptyList())
                .produceState(),
        ),
    )

    private val notificationPermissionGrant = MutableStateFlow(false)
    private val lastGrantPermissionResult = MutableStateFlow<Boolean?>(null)
    private val requestNotificationPermissionTasker = MonoTasker(backgroundScope)

    private val bitTorrentFeatureState = BitTorrentFeatureState(
        enabled = SettingsState(
            valueState = bitTorrentEnabled,
            onUpdate = { bitTorrentEnabled.value = it },
            placeholder = true,
            backgroundScope = backgroundScope,
        ),
        notificationPermissionState = NotificationPermissionState(
            showGrantNotificationItem = permissionManager !is GrantedPermissionManager,
            granted = notificationPermissionGrant.produceState(),
            lastRequestResult = lastGrantPermissionResult.produceState(),
        ),
        onRequestNotificationPermission = { requestNotificationPermission(it) },
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
        configureProxyState = configureProxyState,
        bitTorrentFeatureState = bitTorrentFeatureState
    )

    init {
        launchInBackground {
            combine(
                proxyProvider.proxy
                    .map {
                        createDefaultHttpClient {
                            proxy(it?.toClientProxyConfig())
                            expectSuccess = false
                        }
                    },
                proxyTestCases,
            ) { client, cases ->
                client to cases
            }.collectLatest { (client, cases) ->
                proxyTestRunning.withRunning {
                    proxyTestResults.update {
                        mutate {
                            it.clear()
                            cases.forEach { case -> put(case.name, ProxyTestCaseState.RUNNING) }
                        }
                    }

                    coroutineScope {
                        val testDeferred = cases.map { case ->
                            async {
                                runCatching {
                                    client.get(case.url)
                                }.onSuccess {
                                    proxyTestResults.update {
                                        put(case.name, ProxyTestCaseState.SUCCESS)
                                    }
                                }.onFailure {
                                    proxyTestResults.update {
                                        put(case.name, ProxyTestCaseState.FAILED)
                                    }
                                }
                            }
                        }

                        testDeferred.awaitAll()
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission(context: ContextMP) {
        requestNotificationPermissionTasker.launch {
            if (permissionManager.checkNotificationPermission(context)) return@launch
            val result = permissionManager.requestNotificationPermission(context)
            lastGrantPermissionResult.value = result
            notificationPermissionGrant.value = result
        }
    }

    fun checkNotificationPermission(context: ContextMP) {
        val result = permissionManager.checkNotificationPermission(context)
        notificationPermissionGrant.update { result }
        if (result) lastGrantPermissionResult.update { null }
    }
}

@Stable
class WizardPresentationState(
    val selectThemeState: SettingsState<ThemeSettings>,
    val configureProxyState: ConfigureProxyState,
    val bitTorrentFeatureState: BitTorrentFeatureState,
)

@Stable
class ConfigureProxyState(
    val configState: SettingsState<ProxySettings>,
    val systemProxy: State<SystemProxyPresentation>,
    val testState: ProxyTestState,
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

@Stable
class BitTorrentFeatureState(
    val enabled: SettingsState<Boolean>,
    val notificationPermissionState: NotificationPermissionState,
    val onRequestNotificationPermission: (ContextMP) -> Unit,
)