/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.torrent.engines.AnitorrentEngine
import me.him188.ani.app.platform.MeteredNetworkDetector
import me.him188.ani.datasources.api.topic.FileSize.Companion.kiloBytes
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import kotlin.coroutines.CoroutineContext

/**
 * 管理本地 BT 下载器的实现. 根据配置选择不同的下载器.
 *
 * 目前支持的下载实现:
 * - anitorrent
 */
interface TorrentManager {
    val anitorrent: AnitorrentEngine

    val engines: List<TorrentEngine>
}

enum class TorrentEngineType(
    val id: String,
) {
    Anitorrent("anitorrent"),
}

class DefaultTorrentManager(
    parentCoroutineContext: CoroutineContext,
    private val saveDir: (type: TorrentEngineType) -> SystemPath,
    private val proxySettingsFlow: Flow<ProxySettings>,
    private val anitorrentConfigFlow: Flow<AnitorrentConfig>,
    private val isMeteredNetworkFlow: Flow<Boolean>,
    private val peerFilterConfig: Flow<TorrentPeerConfig>,
    val platform: Platform,
) : TorrentManager {
    private val scope = parentCoroutineContext.childScope()

    override val anitorrent: AnitorrentEngine by lazy {
        AnitorrentEngine(
            combine(anitorrentConfigFlow, isMeteredNetworkFlow) { config, isMetered ->
                val isUploadLimited = isMetered && config.limitUploadOnMeteredNetwork 
                config.copy(uploadRateLimit = if (isUploadLimited) 1.kiloBytes else config.uploadRateLimit)
            },
            proxySettingsFlow,
            peerFilterConfig,
            saveDir(TorrentEngineType.Anitorrent),
            scope.coroutineContext + CoroutineName("AnitorrentEngine"),
        )
    }

    override val engines: List<TorrentEngine> by lazy {
        // 注意, 是故意只启用一个下载器的, 因为每个下载器都会创建一个 DirectoryMediaCacheStorage
        // 并且使用相同的 mediaSourceId: MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID.
        // 搜索数据源时会使用 mediaSourceId 作为 map key, 导致总是只会用一个 storage.
        // 
        // 如果要支持多个, 需要考虑将所有 storage 合并成一个 MediaSource.

        when (platform) {
            is Platform.Desktop -> listOf(anitorrent)
            is Platform.Android -> listOf(anitorrent)
            is Platform.Ios -> listOf() // TODO IOS anitorrent
            else -> error("unreachable")
        }
    }

    companion object {
        fun create(
            parentCoroutineContext: CoroutineContext,
            settingsRepository: SettingsRepository,
            meteredNetworkDetector: MeteredNetworkDetector,
            baseSaveDir: () -> SystemPath,
            platform: Platform = currentPlatform(),
        ): DefaultTorrentManager {
            val saveDirLazy by lazy(baseSaveDir)
            return DefaultTorrentManager(
                parentCoroutineContext,
                { type ->
                    saveDirLazy.resolve(type.id)
                },
                settingsRepository.proxySettings.flow,
                settingsRepository.anitorrentConfig.flow,
                meteredNetworkDetector.isMeteredNetworkFlow.distinctUntilChanged(),
                settingsRepository.torrentPeerConfig.flow,
                platform,
            )
        }
    }
}
