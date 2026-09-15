/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import me.him188.ani.app.data.repository.torrent.peer.PeerFilterSubscriptionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.platform.MeteredNetworkDetector
import me.him188.ani.datasources.api.topic.FileSize.Companion.kiloBytes
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

/**
 * 管理本地 BT 下载器的实现. 根据配置选择不同的下载器.
 *
 * 目前支持的下载实现:
 * - anitorrent
 */
interface TorrentManager {
    val engines: List<TorrentEngine>
}

enum class TorrentEngineType(
    val id: String,
) {
    Anitorrent("anitorrent"),
    RemoteAnitorrent("anitorrent"),
    PikPak("pikpak")
}

/**
 * Default implementation of [TorrentManager], which manages parameters of the torrent engine.
 */
class DefaultTorrentManager(
    parentCoroutineContext: CoroutineContext,
    factory: TorrentEngineFactory,
    settingsRepository: SettingsRepository,
    client: ScopedHttpClient,
    subscriptionRepository: PeerFilterSubscriptionRepository,
    meteredNetworkDetector: MeteredNetworkDetector,
    baseSaveDir: () -> SystemPath,
    private val pikpak: TorrentEngine? = null,
) : TorrentManager {
    private val scope = parentCoroutineContext.childScope()
    private val logger = logger<DefaultTorrentManager>()

    private val anitorrent: TorrentEngine by lazy {
        factory.createTorrentEngine(
            scope.coroutineContext + CoroutineName("AnitorrentEngine"),
            combine(
                settingsRepository.anitorrentConfig.flow,
                meteredNetworkDetector.isMeteredNetworkFlow.distinctUntilChanged(),
            ) { config, isMetered ->
                val isUploadLimited = isMetered && config.limitUploadOnMeteredNetwork
                val limit = if (isUploadLimited) 10.kiloBytes else config.uploadRateLimit
                logger.debug { "Anitorrent upload rate limit: $limit/s" }
                config.copy(uploadRateLimit = limit)
            },
            client = client,
            combine(
                settingsRepository.torrentPeerConfig.flow,
                subscriptionRepository.rulesFlow,
            ) { config, rules ->
                PeerFilterSettings(
                    rules + config.createRuleWithEnabled(),
                    config.enableIdFilter && config.blockInvalidId,
                )
            },
            baseSaveDir().resolve(TorrentEngineType.Anitorrent.id),
        )
    }

    override val engines: List<TorrentEngine> by lazy {
        // 每个引擎都会创建一个 storage, 而它们共用 MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID.
        // 同一个种子若在两个引擎里各缓存一次, 产生的 CachedMedia id 相同, MediaFetcher 的 distinctBy 会丢掉一个.
        // 因此这里可以同时启用两个引擎: 一个 media 只会被其中一个引擎缓存, id 不会撞车. 见 selectTorrentStorage.
        listOfNotNull(pikpak, anitorrent)
    }

    companion object {
        fun create(
            parentCoroutineContext: CoroutineContext,
            settingsRepository: SettingsRepository,
            client: ScopedHttpClient,
            subscriptionRepository: PeerFilterSubscriptionRepository,
            meteredNetworkDetector: MeteredNetworkDetector,
            baseSaveDir: () -> SystemPath,
            torrentEngineFactory: TorrentEngineFactory = LocalAnitorrentEngineFactory,
            pikpak: TorrentEngine? = null,
        ): DefaultTorrentManager {
            return DefaultTorrentManager(
                parentCoroutineContext = parentCoroutineContext,
                factory = torrentEngineFactory,
                client = client,
                settingsRepository = settingsRepository,
                meteredNetworkDetector = meteredNetworkDetector,
                subscriptionRepository = subscriptionRepository,
                baseSaveDir = baseSaveDir,
                pikpak = pikpak,
            )
        }
    }
}
