/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.repository.SettingsRepository
import me.him188.ani.app.domain.torrent.client.RemoteAnitorrentEngine
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnection
import me.him188.ani.app.platform.MeteredNetworkDetector
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.resolve
import kotlin.coroutines.CoroutineContext

class RemoteTorrentManager(
    private val connection: TorrentServiceConnection,
    parentCoroutineContext: CoroutineContext,
    saveDir: (type: TorrentEngineType) -> SystemPath,
    proxySettingsFlow: Flow<ProxySettings>,
    anitorrentConfigFlow: Flow<AnitorrentConfig>,
    isMeteredNetworkFlow: Flow<Boolean>,
    peerFilterConfig: Flow<TorrentPeerConfig>
) : AbstractTorrentManager(
    parentCoroutineContext,
    saveDir,
    proxySettingsFlow,
    anitorrentConfigFlow,
    isMeteredNetworkFlow,
    peerFilterConfig
) {

    override fun createAniTorrentEngine(
        config: Flow<AnitorrentConfig>,
        proxySettings: Flow<ProxySettings>,
        peerFilterSettings: Flow<TorrentPeerConfig>,
        saveDir: SystemPath,
        parentCoroutineContext: CoroutineContext
    ): TorrentEngine {
        return RemoteAnitorrentEngine(
            connection,
            config,
            proxySettings,
            peerFilterSettings,
            saveDir,
            parentCoroutineContext
        )
    }
    
    companion object {
        fun create(
            connection: TorrentServiceConnection,
            parentCoroutineContext: CoroutineContext,
            settingsRepository: SettingsRepository,
            meteredNetworkDetector: MeteredNetworkDetector,
            baseSaveDir: () -> SystemPath,
        ): RemoteTorrentManager {
            val saveDirLazy by lazy(baseSaveDir)
            return RemoteTorrentManager(
                connection,
                parentCoroutineContext,
                { type -> saveDirLazy.resolve(type.id) },
                settingsRepository.proxySettings.flow,
                settingsRepository.anitorrentConfig.flow,
                meteredNetworkDetector.isMeteredNetworkFlow.distinctUntilChanged(),
                settingsRepository.torrentPeerConfig.flow,
            )
        }
    }
}