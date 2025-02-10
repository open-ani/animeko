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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.data.models.preference.MediaSourceProxySettings
import me.him188.ani.app.data.models.preference.ProxyAuthorization
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ServerListFeature
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.domain.session.AniApiProvider
import me.him188.ani.app.domain.session.AniAuthClient
import me.him188.ani.app.domain.session.AuthorizationCancelledException
import me.him188.ani.app.domain.session.AuthorizationException
import me.him188.ani.app.domain.session.ExternalOAuthRequest
import me.him188.ani.app.domain.session.OAuthResult
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStatus
import me.him188.ani.app.domain.settings.ProxySettingsFlowProxyProvider
import me.him188.ani.app.domain.settings.ServiceConnectionTester
import me.him188.ani.app.domain.settings.ServiceConnectionTesters
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.GrantedPermissionManager
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.icons.AnimekoIcon
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.settings.framework.AbstractSettingsViewModel
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.theme.AnimekoIconColor
import me.him188.ani.app.ui.theme.BangumiNextIconColor
import me.him188.ani.app.ui.wizard.navigation.WizardController
import me.him188.ani.app.ui.wizard.step.AuthorizeUIState
import me.him188.ani.app.ui.wizard.step.ConfigureProxyUIState
import me.him188.ani.app.ui.wizard.step.NotificationPermissionState
import me.him188.ani.app.ui.wizard.step.ProxyTestCaseState
import me.him188.ani.app.ui.wizard.step.ProxyTestItem
import me.him188.ani.app.ui.wizard.step.ProxyTestState
import me.him188.ani.app.ui.wizard.step.ProxyUIConfig
import me.him188.ani.app.ui.wizard.step.ProxyUIMode
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.datasources.bangumi.BangumiClientImpl
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.FlowRunning
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.trace
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.currentTimeMillis
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class WizardViewModel : AbstractSettingsViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    private val themeSettings = settingsRepository.themeSettings
        .stateInBackground(ThemeSettings.Default.copy(_placeholder = -1))
    private val proxySettings = settingsRepository.proxySettings
    private val bitTorrentEnabled = mutableStateOf(true)

    // region ConfigureProxy
    private val proxyTestRunning = FlowRunning()
    private val proxyTestRestarter = FlowRestarter()

    private val proxyProvider = ProxySettingsFlowProxyProvider(proxySettings.flow, backgroundScope)
    private val clientProvider: HttpClientProvider by inject()

    private val connectionTester = clientProvider.configurationFlow.map {
        val client = clientProvider.get(
            setOf(ServerListFeature.withValue(ServerListFeatureConfig.Default)),
        )

        ServiceConnectionTesters.createDefault(
            bangumiClient = BangumiClientImpl(client),
            aniClient = ApiInvoker(client) { TrendsAniApi(AniApiProvider.baseurl, it) },
        )
    }
        .stateInBackground(
            ServiceConnectionTester(emptyList()),
            SharingStarted.WhileSubscribed(),
        )

    private val proxyTestItems = connectionTester
        .flatMapLatest { tester -> tester.results }
        .map { it.idToStateMap.toUIState() }
        .stateInBackground(
            emptyList(),
            SharingStarted.WhileSubscribed(),
        )

    private val configureProxyUiState = combine(
        proxySettings.flow,
        proxyProvider.proxy,
        proxyTestRunning.isRunning,
        proxyTestItems,
    ) { settings, proxy, running, items ->
        ConfigureProxyUIState(
            config = settings.toUIConfig(),
            systemProxy = if (settings.default.mode == ProxyMode.SYSTEM && proxy != null) {
                SystemProxyPresentation.Detected(proxy)
            } else {
                SystemProxyPresentation.NotDetected
            },
            testState = ProxyTestState(testRunning = running, items = items),
        )
    }
        .stateInBackground(
            ConfigureProxyUIState.Default,
            SharingStarted.WhileSubscribed(),
        )
    
    private val configureProxyState = ConfigureProxyState(
        state = configureProxyUiState,
        onUpdateConfig = { newConfig ->
            launchInBackground {
                if (shouldRerunProxyTestManually(proxySettings.flow.first().toUIConfig(), newConfig)) {
                    proxyTestRestarter.restart()
                }
                proxySettings.update { newConfig.toDataSettings() }
            }
        },
        onRequestReTest = { proxyTestRestarter.restart() },
    )
    // endregion

    // region BitTorrentFeature
    private val permissionManager: PermissionManager by inject()
    private val notificationPermissionGrant = MutableStateFlow(false)
    private val lastGrantPermissionResult = MutableStateFlow<Boolean?>(null)
    private val requestNotificationPermissionTasker = MonoTasker(backgroundScope)

    private val notificationPermissionState = combine(
        notificationPermissionGrant,
        lastGrantPermissionResult,
    ) { grant, lastResult ->
        NotificationPermissionState(
            showGrantNotificationItem = permissionManager !is GrantedPermissionManager,
            granted = grant,
            lastRequestResult = lastResult,
        )
    }
        .stateInBackground(
            NotificationPermissionState.Placeholder,
            SharingStarted.WhileSubscribed(),
        )

    private val bitTorrentFeatureState = BitTorrentFeatureState(
        enabled = SettingsState(
            valueState = bitTorrentEnabled,
            onUpdate = { bitTorrentEnabled.value = it },
            placeholder = true,
            backgroundScope = backgroundScope,
        ),
        notificationPermissionState = notificationPermissionState,
        onCheckPermissionState = { checkNotificationPermission(it) },
        onRequestNotificationPermission = { requestNotificationPermission(it) },
        onOpenSystemNotificationSettings = { openSystemNotificationSettings(it) },
    )
    // endregion

    // region BangumiAuthorize
    private val sessionManager: SessionManager by inject()
    private val browserNavigator: BrowserNavigator by inject()
    private val authClient: AniAuthClient by inject()

    private val authorizeTasker = MonoTasker(backgroundScope)
    private val currentRequestAuthorizeId = MutableStateFlow<String?>(null)
    private val authorizeUiState = currentRequestAuthorizeId
        .transformLatest ui@{ requestId ->
            if (requestId == null) {
                emit(AuthorizeUIState.Idle)
                logger.trace { "[AuthUIState] got null request id, stopped checking" }
                return@ui
            } else {
                emit(AuthorizeUIState.AwaitingResult(requestId))
            }
            logger.trace { "[AuthUIState][$requestId] start checking authorization request" }

            sessionManager.state
                .collectLatest { sessionState ->
                    // 如果有 token, 直接获取当前 session 的状态即可
                    if (sessionState !is SessionStatus.NoToken)
                        return@collectLatest collectFromSessionStatus(requestId, sessionState, null)

                    sessionManager.processingRequest
                        .filterNotNull()
                        .flatMapLatest {
                            logger.trace { "[AuthUIState][$requestId] current processing request: $it" }
                            it.state
                        }
                        .collectLatest { requestState ->
                            collectFromSessionStatus(requestId, sessionState, requestState)
                        }
                }
        }
        .stateInBackground(
            AuthorizeUIState.Idle,
            SharingStarted.WhileSubscribed(),
        )

    private val bangumiAuthorizeState = BangumiAuthorizeState(
        authorizeUiState,
        onClickNavigateAuthorize = { startAuthorize(it) },
        onCancelAuthorize = { cancelAuthorize() },
        onCheckCurrentToken = { checkCurrentAuthorizeToken() },
        onClickNavigateToBangumiDev = {
            browserNavigator.openBrowser(it, "https://next.bgm.tv/demo/access-token/create")
        },
        onUseGuestMode = { setGuestSession() },
        onAuthorizeViaToken = { setAuthorizationToken(it) },
    )

    val wizardController = WizardController()
    val wizardState = WizardPresentationState(
        selectThemeState = themeSettings,
        configureProxyState = configureProxyState,
        bitTorrentFeatureState = bitTorrentFeatureState,
        bangumiAuthorizeState = bangumiAuthorizeState,
    )
    // endregion

    init {
        launchInBackground {
            connectionTester
                .restartable(restarter = proxyTestRestarter)
                .collectLatest { tester ->
                    proxyTestRunning.withRunning { tester.testAll() }
                }
        }
        launchInBackground {
            currentRequestAuthorizeId
                .filterNotNull()
                .flatMapLatest { sessionManager.processingRequest }
                .collectLatest { processingRequest ->
                    if (processingRequest == null) return@collectLatest
                    val currentRequestId = currentRequestAuthorizeId.value ?: return@collectLatest
                    logger.trace {
                        "[AuthCheckLoop][$currentRequestId] current processing request: $processingRequest"
                    }

                    checkAuthorizeStatus(currentRequestId, processingRequest)
                }
        }
    }

    /**
     * 在 [proxySettings] 更新后, [clientProvider] 可能不会 emit 新 client:
     * - 在系统代理为 null 情况下, 从 禁用代理 设置为 系统代理 或反之.
     * 
     * 另外没改也要测试
     */
    private fun shouldRerunProxyTestManually(prev: ProxyUIConfig, curr: ProxyUIConfig): Boolean {
        if (prev == curr) return true

        val prevMode = prev.mode
        val currMode = curr.mode
        val noSystemProxy = configureProxyUiState.value.systemProxy is SystemProxyPresentation.NotDetected

        if (prevMode == ProxyUIMode.SYSTEM && currMode == ProxyUIMode.DISABLED && noSystemProxy) {
            return true
        }
        if (prevMode == ProxyUIMode.DISABLED && currMode == ProxyUIMode.SYSTEM && noSystemProxy) {
            return true
        }
        return false
    }

    private fun openSystemNotificationSettings(context: ContextMP) {
        permissionManager.openSystemNotificationSettings(context)
    }

    private fun requestNotificationPermission(context: ContextMP) {
        requestNotificationPermissionTasker.launch {
            if (permissionManager.checkNotificationPermission(context)) return@launch
            val result = permissionManager.requestNotificationPermission(context)
            lastGrantPermissionResult.value = result
            notificationPermissionGrant.value = result
        }
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

    private fun startAuthorize(context: ContextMP) {
        val newRequestId = Uuid.random().toString()
        // we want this to launch as quickly as possible
        authorizeTasker.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                sessionManager.clearSession()
                sessionManager.requireAuthorize(
                    onLaunch = { openBrowserAuthorize(context, newRequestId) },
                    skipOnGuest = false,
                )
            } catch (e: AuthorizationCancelledException) {
                logger.trace { "Authorization request ${currentRequestAuthorizeId.value} is cancelled" }
            } catch (e: AuthorizationException) {
                logger.trace(e) { "Authorization request ${currentRequestAuthorizeId.value} failed" }
            } catch (e: Throwable) {
                throw IllegalStateException(
                    "Unknown exception during processing authorization request ${currentRequestAuthorizeId.value}, see cause",
                    e,
                )
            }
        }
        // set new id will cause the checkAuthorizeStatus to start
        currentRequestAuthorizeId.value = newRequestId
    }

    private fun cancelAuthorize() {
        authorizeTasker.cancel()
        currentRequestAuthorizeId.value = null
    }

    private fun checkCurrentAuthorizeToken() {
        launchInBackground {
            // 如果用户第一次进入 APP, 通过向导授权了之后没完成向导, 然后退出 APP 冲进, 会再一次进入向导, 
            // 这时已经有 token 了, 所以需要 emit 一个 stub request id 让 authorizeUiState 跑起来
            // 但是我们不启动 authorizeTasker, 因为这个时候不需要真的去授权, 只是为了让 UI 能正确显示目前的授权状态
            if (sessionManager.state.first() !is SessionStatus.NoToken) {
                currentRequestAuthorizeId.value = "-1"
            }
        }
    }

    private fun setAuthorizationToken(token: String) {
        launchInBackground {
            sessionManager.setSession(
                AccessTokenSession(
                    accessToken = token,
                    expiresAtMillis = currentTimeMillis() + 365.days.inWholeMilliseconds,
                ),
            )
            // trigger ui update
            currentRequestAuthorizeId.value = "-1"
        }
    }

    private fun setGuestSession() {
        // 因为设置 GuestSession 之后会马上进入主界面, backgroundScope 会被取消
        // 所以这里使用 GlobalScope 确保这个任务能完成, 
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch { sessionManager.setSession(GuestSession) }
    }

    private suspend fun FlowCollector<AuthorizeUIState>.collectFromSessionStatus(
        requestId: String,
        sessionState: SessionStatus,
        requestState: ExternalOAuthRequest.State?,
    ) {
        logger.trace {
            "[AuthUIState][$requestId] " +
                    "session: ${sessionState::class.simpleName}, " +
                    "request: ${requestState?.let { it::class.simpleName }}"
        }
        when (sessionState) {
            is SessionStatus.Verified -> {
                val userInfo = sessionState.userInfo
                emit(
                    AuthorizeUIState.Success(
                        userInfo.username ?: userInfo.id.toString(),
                        userInfo.avatarUrl,
                    ),
                )
            }

            is SessionStatus.Loading -> {
                emit(AuthorizeUIState.AwaitingResult(requestId))
            }

            SessionStatus.NetworkError,
            SessionStatus.ServiceUnavailable -> {
                emit(AuthorizeUIState.Error(requestId, "网络错误, 请重试"))
            }

            SessionStatus.Expired -> {
                emit(AuthorizeUIState.Error(requestId, "token 已过期，请重试"))
            }

            SessionStatus.NoToken -> when (requestState) {
                ExternalOAuthRequest.State.Launching,
                ExternalOAuthRequest.State.AwaitingCallback,
                ExternalOAuthRequest.State.Processing -> {
                    emit(AuthorizeUIState.AwaitingResult(requestId))
                }

                is ExternalOAuthRequest.State.Failed -> {
                    emit(AuthorizeUIState.Error(requestId, requestState.toString()))
                }

                else -> {}
            }

            SessionStatus.Guest -> emit(AuthorizeUIState.Idle)
        }
    }

    private suspend fun checkAuthorizeStatus(
        requestId: String,
        request: ExternalOAuthRequest,
    ) {
        while (true) {
            val resp = authClient.getResult(requestId)
            resp.fold(
                onSuccess = {
                    if (it == null) {
                        return@fold
                    }
                    logger.trace {
                        "[AuthCheckLoop][$requestId] " +
                                "Check OAuth result success, request is $request, " +
                                "token expires in ${it.expiresIn.seconds}"
                    }
                    request.onCallback(
                        Result.success(
                            OAuthResult(
                                accessToken = it.accessToken,
                                refreshToken = it.refreshToken,
                                expiresIn = it.expiresIn.seconds,
                            ),
                        ),
                    )
                    return
                },
                onKnownFailure = {
                    logger.trace { "[AuthCheckLoop][$requestId] Check OAuth result failed: $it" }
                },
            )
            delay(1000)
        }
    }

    fun finishWizard() {
        // 因为更新设置之后会马上进入主界面, backgroundScope 会被取消
        // 所以这里使用 GlobalScope 确保这个任务能完成, 
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            settingsRepository.uiSettings.update { copy(startupToWelcomeWizard = false) }
        }
    }
}

@Stable
class WizardPresentationState(
    val selectThemeState: SettingsState<ThemeSettings>,
    val configureProxyState: ConfigureProxyState,
    val bitTorrentFeatureState: BitTorrentFeatureState,
    val bangumiAuthorizeState: BangumiAuthorizeState,
)

@Stable
class ConfigureProxyState(
    val state: Flow<ConfigureProxyUIState>,
    val onUpdateConfig: (ProxyUIConfig) -> Unit,
    val onRequestReTest: () -> Unit,
)

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
        icon = Icons.Default.AnimekoIcon,
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
    val notificationPermissionState: Flow<NotificationPermissionState>,
    val onCheckPermissionState: (ContextMP) -> Unit,
    val onRequestNotificationPermission: (ContextMP) -> Unit,
    val onOpenSystemNotificationSettings: (ContextMP) -> Unit,
)

@Stable
class BangumiAuthorizeState(
    val state: Flow<AuthorizeUIState>,
    val onCheckCurrentToken: () -> Unit,
    val onClickNavigateAuthorize: (ContextMP) -> Unit,
    val onCancelAuthorize: () -> Unit,
    val onAuthorizeViaToken: (String) -> Unit,
    val onClickNavigateToBangumiDev: (ContextMP) -> Unit,
    val onUseGuestMode: () -> Unit,
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