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
    RemoteAnitorrent("anitorrent")
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
                    // 必须跟着 enableIdFilter 一起判断: "总是过滤异常指纹" 这个开关在设置界面里是嵌在
                    // "过滤客户端指纹" 的展开区内的 (PeerFilterEditPane), 外层一关它就看不见了.
                    // 而 blockInvalidId 默认是 true, enableIdFilter 默认是 false, 于是开箱即得一个
                    // 看不见又关不掉的指纹过滤器 —— 它会拦掉所有 peer id 不是 `-xxxxxx-` 格式的客户端,
                    // 国内 swarm 里这种占比很高, 表现就是 "peer 连上一堆但全被拦, 速度 0, BT 不下载".
                    // (2026-08-11 真机: 单个种子一小时内拦掉 256 个 peer, 只剩 1 个还 choke 我们)
                    config.enableIdFilter && config.blockInvalidId,
                )
            },
            baseSaveDir().resolve(TorrentEngineType.Anitorrent.id),
        )
    }

    override val engines: List<TorrentEngine> by lazy {
        // 注意, 是故意只启用一个下载器的, 因为每个下载器都会创建一个 DirectoryMediaCacheStorage
        // 并且使用相同的 mediaSourceId: MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID.
        // 搜索数据源时会使用 mediaSourceId 作为 map key, 导致总是只会用一个 storage.
        // 
        // 如果要支持多个, 需要考虑将所有 storage 合并成一个 MediaSource.

        listOf(anitorrent)
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
        ): DefaultTorrentManager {
            return DefaultTorrentManager(
                parentCoroutineContext = parentCoroutineContext,
                factory = torrentEngineFactory,
                client = client,
                settingsRepository = settingsRepository,
                meteredNetworkDetector = meteredNetworkDetector,
                subscriptionRepository = subscriptionRepository,
                baseSaveDir = baseSaveDir,
            )
        }
    }
}