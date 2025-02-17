/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.MediaSourceProxySettings
import me.him188.ani.app.data.models.preference.ProxyAuthorization
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.session.AniAuthClient
import me.him188.ani.app.domain.session.AniAuthConfigurator
import me.him188.ani.app.domain.session.AuthStateNew
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.settings.ProxySettingsFlowProxyProvider
import me.him188.ani.app.domain.settings.ProxyTester
import me.him188.ani.app.domain.settings.ServiceConnectionTester
import me.him188.ani.app.domain.settings.ServiceConnectionTesters
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.GrantedPermissionManager
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.icons.Animeko
import me.him188.ani.app.ui.foundation.icons.AnimekoIconColor
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.icons.BangumiNextIconColor
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.onboarding.navigation.WizardController
import me.him188.ani.app.ui.onboarding.step.ConfigureProxyUIState
import me.him188.ani.app.ui.onboarding.step.GrantNotificationPermissionState
import me.him188.ani.app.ui.onboarding.step.ProxyTestCaseState
import me.him188.ani.app.ui.onboarding.step.ProxyTestItem
import me.him188.ani.app.ui.onboarding.step.ProxyTestState
import me.him188.ani.app.ui.onboarding.step.ProxyUIConfig
import me.him188.ani.app.ui.onboarding.step.ProxyUIMode
import me.him188.ani.app.ui.onboarding.step.ThemeSelectUIState
import me.him188.ani.app.ui.settings.framework.AbstractSettingsViewModel
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import me.him188.ani.utils.coroutines.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class OnboardingViewModel : AbstractSettingsViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    private val themeSettings = settingsRepository.themeSettings
    private val proxySettings = settingsRepository.proxySettings
    private val bitTorrentEnabled = mutableStateOf(true)

    // region ThemeSelect
    private val themeSelectFlow = themeSettings.flow.map {
        ThemeSelectUIState(
            darkMode = it.darkMode,
            useDynamicTheme = it.useDynamicTheme,
            seedColor = it.seedColor,
        )
    }
        .stateInBackground(
            ThemeSelectUIState.Placeholder,
            SharingStarted.WhileSubscribed(),
        )

    private val themeSelectState = ThemeSelectState(
        state = themeSelectFlow,
        onUpdateUseDarkMode = { darkMode ->
            launchInBackground {
                themeSettings.update { copy(darkMode = darkMode) }
            }
        },
        onUpdateUseDynamicTheme = { useDynamicTheme ->
            launchInBackground {
                themeSettings.update { copy(useDynamicTheme = useDynamicTheme) }
            }
        },
        onUpdateSeedColor = { seedColor ->
            launchInBackground {
                themeSettings.update { copy(useDynamicTheme = false, seedColorValue = seedColor.value) }
            }
        },
    )
    // endregion
    
    // region ConfigureProxy
    private val clientProvider: HttpClientProvider by inject()
    private val proxyProvider = ProxySettingsFlowProxyProvider(proxySettings.flow, backgroundScope)

    private val proxyTester = ProxyTester(
        clientProvider = clientProvider,
        flowScope = backgroundScope,
    )

    private val configureProxyUiState = combine(
        proxySettings.flow,
        proxyProvider.proxy,
        proxyTester.testRunning,
        proxyTester.testResult,
    ) { settings, proxy, running, result ->
        ConfigureProxyUIState(
            config = settings.toUIConfig(),
            systemProxy = if (settings.default.mode == ProxyMode.SYSTEM && proxy != null) {
                SystemProxyPresentation.Detected(proxy)
            } else {
                SystemProxyPresentation.NotDetected
            },
            testState = ProxyTestState(
                testRunning = running,
                items = result.idToStateMap.toUIState(),
            ),
        )
    }
        .stateInBackground(
            ConfigureProxyUIState.Placeholder,
            SharingStarted.WhileSubscribed(),
        )
    
    private val configureProxyState = ConfigureProxyState(
        state = configureProxyUiState,
        onUpdateConfig = { newConfig ->
            launchInBackground {
                proxySettings.update { newConfig.toDataSettings() }
            }
        },
        onRequestReTest = { proxyTester.restartTest() },
    )
    // endregion

    // region BitTorrentFeature
    private val permissionManager: PermissionManager by inject()
    private val notificationPermissionGrant = MutableStateFlow(false)
    private val lastGrantPermissionResult = MutableStateFlow<Boolean?>(null)

    private val grantNotificationPermissionState = combine(
        notificationPermissionGrant,
        lastGrantPermissionResult,
    ) { grant, lastResult ->
        GrantNotificationPermissionState(
            showGrantNotificationItem = permissionManager !is GrantedPermissionManager,
            granted = grant,
            lastRequestResult = lastResult,
        )
    }
        .stateInBackground(
            GrantNotificationPermissionState.Placeholder,
            SharingStarted.WhileSubscribed(),
        )

    private val bitTorrentFeatureState = BitTorrentFeatureState(
        enabled = SettingsState(
            valueState = bitTorrentEnabled,
            onUpdate = { bitTorrentEnabled.value = it },
            placeholder = true,
            backgroundScope = backgroundScope,
        ),
        grantNotificationPermissionState = grantNotificationPermissionState,
        onCheckPermissionState = { checkNotificationPermission(it) },
        onRequestNotificationPermission = { requestNotificationPermission(it) },
        onOpenSystemNotificationSettings = { openSystemNotificationSettings(it) },
    )
    // endregion

    // region BangumiAuthorize
    private val sessionManager: SessionManager by inject()
    private val browserNavigator: BrowserNavigator by inject()
    private val authClient: AniAuthClient by inject()

    private var currentAppContext: ContextMP? = null
    private val authLoopTasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    private val authConfigurator = AniAuthConfigurator(
        sessionManager = sessionManager,
        authClient = authClient,
        onLaunchAuthorize = { requestId ->
            currentAppContext?.let { openBrowserAuthorize(it, requestId) }
        },
        parentCoroutineContext = backgroundScope.coroutineContext,
    )

    private val bangumiAuthorizeState = BangumiAuthorizeState(
        authConfigurator.state,
        onClickNavigateAuthorize = {
            currentAppContext = it
            backgroundScope.launch { authConfigurator.startAuthorize() }
        },
        onCancelAuthorize = { authConfigurator.cancelAuthorize() },
        onCheckCurrentToken = { authConfigurator.checkAuthorizeState() },
        onClickNavigateToBangumiDev = {
            browserNavigator.openBrowser(it, "https://next.bgm.tv/demo/access-token/create")
        },
        onUseGuestMode = { 
            val currentState = authConfigurator.state.value
            // 如果有 session 那就不设置为 guest session
            if (currentState is AuthStateNew.Success && !currentState.isGuest) return@BangumiAuthorizeState
            authConfigurator.setGuestSession()
        },
        onAuthorizeByToken = { 
            backgroundScope.launch { authConfigurator.setAuthorizationToken(it) }
        },
    )

    val wizardController = WizardController()
    val onboardingState = OnboardingPresentationState(
        themeSelectState = themeSelectState,
        configureProxyState = configureProxyState,
        bitTorrentFeatureState = bitTorrentFeatureState,
        bangumiAuthorizeState = bangumiAuthorizeState,
    )
    // endregion
    
    suspend fun startAuthorizeCheckAndProxyTesterLoop() {
        authLoopTasker.invoke {
            launch { authConfigurator.authorizeRequestCheckLoop() }
            launch { proxyTester.testRunnerLoop() }
        }
    }

    private fun openSystemNotificationSettings(context: ContextMP) {
        permissionManager.openSystemNotificationSettings(context)
    }

    private suspend fun requestNotificationPermission(context: ContextMP): Boolean {
        if (permissionManager.checkNotificationPermission(context)) return true
        val result = permissionManager.requestNotificationPermission(context)

        lastGrantPermissionResult.value = result
        notificationPermissionGrant.value = result

        return result
    }

    private fun checkNotificationPermission(context: ContextMP) {
        val result = permissionManager.checkNotificationPermission(context)
        notificationPermissionGrant.update { result }
        if (result) lastGrantPermissionResult.update { null }
    }

    private suspend fun openBrowserAuthorize(context: ContextMP, requestId: String) {
        val base = currentAniBuildConfig.aniAuthServerUrl.removeSuffix("/")
        val url = "${base}/v1/login/bangumi/oauth?requestId=${requestId.encodeURLParameter()}"

        withContext(Dispatchers.Main) {
            browserNavigator.openBrowser(context, url)
        }
    }
    
    suspend fun collectNewLoginEvent(onLogin: suspend () -> Unit) {
        sessionManager.events
            .filterIsInstance<SessionEvent.Login>()
            .collectLatest { onLogin() }
    }

    fun finishOnboarding() {
        // 因为更新设置之后会马上进入主界面, backgroundScope 会被取消
        // 所以这里使用 GlobalScope 确保这个任务能完成, 
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            settingsRepository.uiSettings.update { copy(onboardingCompleted = true) }
        }
    }
}

@Stable
class OnboardingPresentationState(
    val themeSelectState: ThemeSelectState,
    val configureProxyState: ConfigureProxyState,
    val bitTorrentFeatureState: BitTorrentFeatureState,
    val bangumiAuthorizeState: BangumiAuthorizeState,
)

@Stable
class ThemeSelectState(
    val state: Flow<ThemeSelectUIState>,
    val onUpdateUseDarkMode: (DarkMode) -> Unit,
    val onUpdateUseDynamicTheme: (Boolean) -> Unit,
    val onUpdateSeedColor: (Color) -> Unit,
)

@Stable
class ConfigureProxyState(
    val state: Flow<ConfigureProxyUIState>,
    private val onUpdateConfig: (ProxyUIConfig) -> Unit,
    val onRequestReTest: () -> Unit,
) {
    fun updateConfig(
        currentConfig: ProxyUIConfig, 
        newConfig: ProxyUIConfig,
        currentSystemProxy: SystemProxyPresentation
    ) {
        if (shouldRerunProxyTestManually(currentConfig, newConfig, currentSystemProxy)) {
            onRequestReTest()
        }
        onUpdateConfig(newConfig)
    }
    
    private fun shouldRerunProxyTestManually(
        prev: ProxyUIConfig, 
        curr: ProxyUIConfig, 
        systemProxy: SystemProxyPresentation
    ): Boolean {
        if (prev == curr) return true

        val prevMode = prev.mode
        val currMode = curr.mode
        val noSystemProxy = systemProxy is SystemProxyPresentation.NotDetected

        if (prevMode == ProxyUIMode.SYSTEM && currMode == ProxyUIMode.DISABLED && noSystemProxy) {
            return true
        }
        if (prevMode == ProxyUIMode.DISABLED && currMode == ProxyUIMode.SYSTEM && noSystemProxy) {
            return true
        }
        return false
    }
}

@Immutable
enum class ProxyTestCaseEnums {
    ANI,
    BANGUMI,
    BANGUMI_NEXT,
}

@Immutable
sealed class ProxyTestCase(
    val name: ProxyTestCaseEnums,
    val icon: ImageVector,
    val color: Color
) {
    data object AniDanmakuApi : ProxyTestCase(
        name = ProxyTestCaseEnums.ANI,
        icon = Icons.Default.Animeko,
        color = AnimekoIconColor,
    )

    data object BangumiApi : ProxyTestCase(
        name = ProxyTestCaseEnums.BANGUMI,
        icon = Icons.Default.BangumiNext,
        color = BangumiNextIconColor,
    )

    data object BangumiNextApi : ProxyTestCase(
        name = ProxyTestCaseEnums.BANGUMI_NEXT,
        icon = Icons.Default.BangumiNext,
        color = BangumiNextIconColor,
    )
}

@Stable
class BitTorrentFeatureState(
    val enabled: SettingsState<Boolean>,
    val grantNotificationPermissionState: Flow<GrantNotificationPermissionState>,
    val onCheckPermissionState: (ContextMP) -> Unit,
    val onRequestNotificationPermission: suspend (ContextMP) -> Boolean,
    val onOpenSystemNotificationSettings: (ContextMP) -> Unit,
)

@Stable
class BangumiAuthorizeState(
    val state: Flow<AuthStateNew>,
    val onCheckCurrentToken: () -> Unit,
    val onClickNavigateAuthorize: (ContextMP) -> Unit,
    val onCancelAuthorize: () -> Unit,
    val onAuthorizeByToken: (String) -> Unit,
    val onClickNavigateToBangumiDev: (ContextMP) -> Unit,
    val onUseGuestMode: suspend () -> Unit,
)

// region transform between ui ProxyUIConfig and data ProxySettings

internal fun ProxyMode.toUIMode(): ProxyUIMode {
    return when (this) {
        ProxyMode.DISABLED -> ProxyUIMode.DISABLED
        ProxyMode.SYSTEM -> ProxyUIMode.SYSTEM
        ProxyMode.CUSTOM -> ProxyUIMode.CUSTOM
    }
}

internal fun ProxyUIMode.toDataMode(): ProxyMode {
    return when (this) {
        ProxyUIMode.DISABLED -> ProxyMode.DISABLED
        ProxyUIMode.SYSTEM -> ProxyMode.SYSTEM
        ProxyUIMode.CUSTOM -> ProxyMode.CUSTOM
    }
}

internal fun ProxySettings.toUIConfig(): ProxyUIConfig {
    return ProxyUIConfig(
        mode = default.mode.toUIMode(),
        manualUrl = default.customConfig.url,
        manualUsername = default.customConfig.authorization?.username,
        manualPassword = default.customConfig.authorization?.password,
    )
}

internal fun ProxyUIConfig.toDataSettings(): ProxySettings {
    return ProxySettings(
        default = MediaSourceProxySettings(
            mode = mode.toDataMode(),
            customConfig = MediaSourceProxySettings.Default.customConfig.copy(
                url = manualUrl,
                authorization = if (manualUsername != null && manualPassword != null) {
                    ProxyAuthorization(manualUsername, manualPassword)
                } else null,
            ),
        ),
    )
}

// endregion

private fun Map<String, ServiceConnectionTester.TestState>.toUIState(): List<ProxyTestItem> {
    return buildList {
        this@toUIState.forEach { (id, state) ->
            val case = when (id) {
                ServiceConnectionTesters.ID_ANI -> ProxyTestCase.AniDanmakuApi
                ServiceConnectionTesters.ID_BANGUMI -> ProxyTestCase.BangumiApi
                ServiceConnectionTesters.ID_BANGUMI_NEXT -> ProxyTestCase.BangumiNextApi
                else -> return@forEach
            }
            val result = when (state) {
                is ServiceConnectionTester.TestState.Idle -> ProxyTestCaseState.INIT
                is ServiceConnectionTester.TestState.Testing -> ProxyTestCaseState.RUNNING
                is ServiceConnectionTester.TestState.Success -> ProxyTestCaseState.SUCCESS
                is ServiceConnectionTester.TestState.Failed -> ProxyTestCaseState.FAILED
                is ServiceConnectionTester.TestState.Error -> ProxyTestCaseState.FAILED // todo
            }
            add(ProxyTestItem(case, result))
        }
    }
}