/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.danmaku.DanmakuConfigSerializer
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.AnalyticsSettings
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.OneshotActionConfig
import me.him188.ani.app.data.models.preference.ProfileSettings
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.network.danmaku.AniBangumiSeverBaseUrls
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepository
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.serializeSubscriptionToString
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater
import me.him188.ani.app.domain.settings.ProxySettingsFlowProxyProvider
import me.him188.ani.app.domain.settings.ProxyTester
import me.him188.ani.app.domain.settings.ServiceConnectionTester
import me.him188.ani.app.domain.settings.ServiceConnectionTesters
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.settings.framework.AbstractSettingsViewModel
import me.him188.ani.app.ui.settings.framework.ConnectionTestResult
import me.him188.ani.app.ui.settings.framework.ConnectionTester
import me.him188.ani.app.ui.settings.framework.DefaultConnectionTesterRunner
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.tabs.about.AboutTabInfo
import me.him188.ani.app.ui.settings.tabs.app.SoftwareUpdateGroupState
import me.him188.ani.app.ui.settings.tabs.media.CacheDirectoryGroupState
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectionGroupState
import me.him188.ani.app.ui.settings.tabs.media.source.EditMediaSourceState
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceGroupState
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceLoader
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceSubscriptionGroupState
import me.him188.ani.app.ui.settings.tabs.network.ConfigureProxyState
import me.him188.ani.app.ui.settings.tabs.network.ConfigureProxyUIState
import me.him188.ani.app.ui.settings.tabs.network.ProxyTestCase
import me.him188.ani.app.ui.settings.tabs.network.ProxyTestCaseState
import me.him188.ani.app.ui.settings.tabs.network.ProxyTestItem
import me.him188.ani.app.ui.settings.tabs.network.ProxyTestState
import me.him188.ani.app.ui.settings.tabs.network.SystemProxyPresentation
import me.him188.ani.app.ui.settings.tabs.network.toDataSettings
import me.him188.ani.app.ui.settings.tabs.network.toUIConfig
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsViewModel : AbstractSettingsViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val permissionManager: PermissionManager by inject()
    private val bangumiClient: BangumiClient by inject()
    private val danmakuRegexFilterRepository: DanmakuRegexFilterRepository by inject()

    private val mediaSourceManager: MediaSourceManager by inject()
    private val mediaSourceInstanceRepository: MediaSourceInstanceRepository by inject()
    private val mediaSourceSubscriptionRepository: MediaSourceSubscriptionRepository by inject()
    private val mediaSourceSubscriptionUpdater: MediaSourceSubscriptionUpdater by inject()
    private val mediaSourceCodecManager: MediaSourceCodecManager by inject()
    private val clientProvider: HttpClientProvider by inject()
    private val tokenRepository: TokenRepository by inject()

    private val proxyProvider = ProxySettingsFlowProxyProvider(settingsRepository.proxySettings.flow, backgroundScope)

    private val loopTasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    val softwareUpdateGroupState: SoftwareUpdateGroupState = SoftwareUpdateGroupState(
        updateSettings = settingsRepository.updateSettings.stateInBackground(UpdateSettings.Default.copy(_placeholder = -1)),
    )

    val uiSettings: SettingsState<UISettings> =
        settingsRepository.uiSettings.stateInBackground(UISettings.Default.copy(_placeholder = -1))

    val themeSettings: SettingsState<ThemeSettings> =
        settingsRepository.themeSettings.stateInBackground(ThemeSettings.Default.copy(_placeholder = -1))

    val videoScaffoldConfig: SettingsState<VideoScaffoldConfig> =
        settingsRepository.videoScaffoldConfig.stateInBackground(VideoScaffoldConfig.Default.copy(_placeholder = -1))

    val videoResolverSettingsState: SettingsState<VideoResolverSettings> =
        settingsRepository.videoResolverSettings.stateInBackground(VideoResolverSettings.Default.copy(_placeholder = -1))

    val mediaCacheSettingsState: SettingsState<MediaCacheSettings> =
        settingsRepository.mediaCacheSettings.stateInBackground(MediaCacheSettings.Default.copy(_placeholder = -1))

    val torrentSettingsState: SettingsState<AnitorrentConfig> =
        settingsRepository.anitorrentConfig.stateInBackground(AnitorrentConfig.Default.copy(_placeholder = -1))

    val cacheDirectoryGroupState = CacheDirectoryGroupState(
        mediaCacheSettingsState,
        permissionManager,
        onGetBackupData = {
            withContext(Dispatchers.IO_) {
                serializeSettingsBackup()
            }
        },
        onRestoreSettings = {
            withContext(Dispatchers.IO_) {
                restoreSettingsBackup(it)
            }
        },
    )

    private val mediaSelectorSettingsState: SettingsState<MediaSelectorSettings> =
        settingsRepository.mediaSelectorSettings.stateInBackground(MediaSelectorSettings.Default.copy(_placeholder = -1))

    private val defaultMediaPreferenceState =
        settingsRepository.defaultMediaPreference.stateInBackground(MediaPreference.PlatformDefault.copy(_placeholder = -1))

    val mediaSelectionGroupState = MediaSelectionGroupState(
        defaultMediaPreferenceState = defaultMediaPreferenceState,
        mediaSelectorSettingsState = mediaSelectorSettingsState,
    )

    val debugSettingsState = settingsRepository.debugSettings.stateInBackground(DebugSettings(_placeHolder = -1))
    val isInDebugMode by derivedStateOf {
        debugSettingsState.value.enabled
    }

    // region ConfigureProxy
    private val proxyTester = ProxyTester(
        clientProvider = clientProvider,
        flowScope = backgroundScope,
    )

    private val configureProxyUiState = combine(
        settingsRepository.proxySettings.flow,
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

    val configureProxyState = ConfigureProxyState(
        state = configureProxyUiState,
        onUpdateConfig = { newConfig ->
            launchInBackground {
                settingsRepository.proxySettings.update { newConfig.toDataSettings() }
            }
        },
        onRequestReTest = { proxyTester.restartTest() },
    )
    // endregion

    val danmakuSettingsState =
        settingsRepository.danmakuSettings.stateInBackground(placeholder = DanmakuSettings(_placeholder = -1))

    val danmakuFilterConfigState =
        settingsRepository.danmakuFilterConfig.stateInBackground(DanmakuFilterConfig.Default.copy(_placeholder = -1))

    val danmakuRegexFilterState = DanmakuRegexFilterState(
        list = danmakuRegexFilterRepository.flow.produceState(emptyList()),
        add = {
            launchInBackground { danmakuRegexFilterRepository.add(it) }
        },
        edit = { regex, filter ->
            launchInBackground {
                danmakuRegexFilterRepository.update(filter.id, filter.copy(regex = regex))
            }
        },
        remove = {
            launchInBackground { danmakuRegexFilterRepository.remove(it) }
        },
        switch = {
            launchInBackground {
                danmakuRegexFilterRepository.update(it.id, it.copy(enabled = !it.enabled))
            }
        },
    )

    val danmakuServerTesters = DefaultConnectionTesterRunner(
        AniBangumiSeverBaseUrls.list.map {
            ConnectionTester(id = it) {
                clientProvider.get().use {
                    get("$it/status")
                }
                ConnectionTestResult.SUCCESS
            }
        },
        backgroundScope,
    )


    // do not add more, check ui first.
    val otherTesters: DefaultConnectionTesterRunner<ConnectionTester> = DefaultConnectionTesterRunner(
        listOf(
            ConnectionTester(
                id = "Bangumi", // Bangumi 顺便也测一下
            ) {
                if (bangumiClient.testConnectionMaster() == ConnectionStatus.SUCCESS) {
                    ConnectionTestResult.SUCCESS
                } else {
                    ConnectionTestResult.FAILED
                }
            },
        ),
        backgroundScope,
    )

    private val mediaSourceLoader = MediaSourceLoader(
        mediaSourceManager,
        mediaSourceSubscriptionRepository.flow,
        backgroundScope.coroutineContext,
    )
    val mediaSourceGroupState = MediaSourceGroupState(
        mediaSourceLoader.mediaSourcesFlow.produceState(emptyList()),
        mediaSourceLoader.availableMediaSourceTemplates.produceState(emptyList()),
        onReorder = { mediaSourceInstanceRepository.partiallyReorder(it) },
        backgroundScope,
    )

    val editMediaSourceState = EditMediaSourceState(
        getConfigFlow = { id ->
            mediaSourceManager.instanceConfigFlow(id).map {
                checkNotNull(it) { "Could not find MediaSourceConfig for id $id" }
            }
        },
        onAdd = { factoryId, instanceId, config ->
            mediaSourceManager.addInstance(instanceId, instanceId, factoryId, config)
        },
        onEdit = { instanceId, config -> mediaSourceManager.updateConfig(instanceId, config) },
        onDelete = { instanceId -> mediaSourceManager.removeInstance(instanceId) },
        onSetEnabled = { instanceId, enabled -> mediaSourceManager.setEnabled(instanceId, enabled) },
        backgroundScope,
    )

    private val subscriptionsState = mediaSourceSubscriptionRepository.flow.produceState(emptyList())
    val mediaSourceSubscriptionGroupState = MediaSourceSubscriptionGroupState(
        subscriptionsState = subscriptionsState,
        onUpdateAll = { mediaSourceSubscriptionUpdater.updateAllOutdated(force = true) },
        onAdd = { mediaSourceSubscriptionRepository.add(it) },
        onDelete = {
            launchInBackground {
                for (save in mediaSourceManager.getListBySubscriptionId(it.subscriptionId)) {
                    mediaSourceManager.removeInstance(save.instanceId)
                }
                mediaSourceSubscriptionRepository.remove(it)
            }
        },
        onExportLocalChangesToString = { subscription ->
            val saves = mediaSourceManager.getListBySubscriptionId(subscription.subscriptionId)
            mediaSourceCodecManager.serializeSubscriptionToString(saves)
        },
        backgroundScope,
    )

    val debugTriggerState = DebugTriggerState(debugSettingsState, backgroundScope)
    val aboutTabInfo = AboutTabInfo(currentAniBuildConfig.versionName)

    val selfInfoFlow = SelfInfoStateProducer(koin = getKoin()).flow

    suspend fun startProxyTesterLoop() {
        loopTasker.invoke {
            launch { proxyTester.testRunnerLoop() }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private suspend fun serializeSettingsBackup(): String {
        val backup = SettingsBackup(
            danmakuEnabled = settingsRepository.danmakuEnabled.flow.first(),
            danmakuConfig = settingsRepository.danmakuConfig.flow.first(),
            danmakuFilterConfig = settingsRepository.danmakuFilterConfig.flow.first(),
            mediaSelectorSettings = settingsRepository.mediaSelectorSettings.flow.first(),
            defaultMediaPreference = settingsRepository.defaultMediaPreference.flow.first(),
            profileSettings = settingsRepository.profileSettings.flow.first(),
            proxySettings = settingsRepository.proxySettings.flow.first(),
            mediaCacheSettings = settingsRepository.mediaCacheSettings.flow.first(),
            danmakuSettings = settingsRepository.danmakuSettings.flow.first(),
            uiSettings = settingsRepository.uiSettings.flow.first(),
            themeSettings = settingsRepository.themeSettings.flow.first(),
            updateSettings = settingsRepository.updateSettings.flow.first(),
            videoScaffoldConfig = settingsRepository.videoScaffoldConfig.flow.first(),
            videoResolverSettings = settingsRepository.videoResolverSettings.flow.first(),
            anitorrentConfig = settingsRepository.anitorrentConfig.flow.first(),
            torrentPeerConfig = settingsRepository.torrentPeerConfig.flow.first(),
            oneshotActionConfig = settingsRepository.oneshotActionConfig.flow.first(),
            analyticsSettings = settingsRepository.analyticsSettings.flow.first(),
            debugSettings = settingsRepository.debugSettings.flow.first(),
            tokenStore = tokenRepository.getTokenSaveSnapshot(),
        )

        return json.encodeToString(SettingsBackup.serializer(), backup)
    }

    @Suppress("DuplicatedCode")
    private suspend fun restoreSettingsBackup(content: String): Boolean {
        val backup = json.decodeFromString(SettingsBackup.serializer(), content)

        backup.danmakuEnabled?.let { settingsRepository.danmakuEnabled.set(it) }
        backup.danmakuConfig?.let { settingsRepository.danmakuConfig.set(it) }
        backup.danmakuFilterConfig?.let { settingsRepository.danmakuFilterConfig.set(it) }
        backup.mediaSelectorSettings?.let { settingsRepository.mediaSelectorSettings.set(it) }
        backup.defaultMediaPreference?.let { settingsRepository.defaultMediaPreference.set(it) }
        backup.profileSettings?.let { settingsRepository.profileSettings.set(it) }
        backup.proxySettings?.let { settingsRepository.proxySettings.set(it) }
        backup.mediaCacheSettings?.let { settingsRepository.mediaCacheSettings.set(it) }
        backup.danmakuSettings?.let { settingsRepository.danmakuSettings.set(it) }
        backup.uiSettings?.let { settingsRepository.uiSettings.set(it) }
        backup.themeSettings?.let { settingsRepository.themeSettings.set(it) }
        backup.updateSettings?.let { settingsRepository.updateSettings.set(it) }
        backup.videoScaffoldConfig?.let { settingsRepository.videoScaffoldConfig.set(it) }
        backup.videoResolverSettings?.let { settingsRepository.videoResolverSettings.set(it) }
        backup.anitorrentConfig?.let { settingsRepository.anitorrentConfig.set(it) }
        backup.torrentPeerConfig?.let { settingsRepository.torrentPeerConfig.set(it) }
        backup.oneshotActionConfig?.let { settingsRepository.oneshotActionConfig.set(it) }
        backup.analyticsSettings?.let { settingsRepository.analyticsSettings.set(it) }
        backup.debugSettings?.let { settingsRepository.debugSettings.set(it) }
        backup.tokenStore?.let { tokenRepository.restoreFromTokenSave(it) }

        return true
    }
}

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

@Serializable
private data class SettingsBackup(
    val danmakuEnabled: Boolean?,
    @Serializable(with = DanmakuConfigSerializer::class) val danmakuConfig: DanmakuConfig?,
    val danmakuFilterConfig: DanmakuFilterConfig?,
    val mediaSelectorSettings: MediaSelectorSettings?,
    val defaultMediaPreference: MediaPreference?,
    val profileSettings: ProfileSettings?,
    val proxySettings: ProxySettings?,
    val mediaCacheSettings: MediaCacheSettings?,
    val danmakuSettings: DanmakuSettings?,
    val uiSettings: UISettings?,
    val themeSettings: ThemeSettings?,
    val updateSettings: UpdateSettings?,
    val videoScaffoldConfig: VideoScaffoldConfig?,
    val videoResolverSettings: VideoResolverSettings?,
    val anitorrentConfig: AnitorrentConfig?,
    val torrentPeerConfig: TorrentPeerConfig?,
    val oneshotActionConfig: OneshotActionConfig?,
    val analyticsSettings: AnalyticsSettings?,
    val debugSettings: DebugSettings?,
    val tokenStore: TokenSave?
)