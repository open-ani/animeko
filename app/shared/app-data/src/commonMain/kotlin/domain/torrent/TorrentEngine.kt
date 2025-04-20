/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import androidx.annotation.CallSuper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.peer.PeerFilter
import me.him188.ani.app.torrent.api.peer.PeerInfo
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.onReplacement
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException


/**
 * 一个 BT 引擎, 用于在具体实现 (例如 Anitorrent) 之上封装加载 native 依赖库和持有单例 [TorrentDownloader] 的抽象. 同时还会考虑配置  [TorrentEngineConfig].
 *
 * 要实现 [TorrentEngine], 推荐继承 [AbstractTorrentEngine].
 */
interface TorrentEngine : AutoCloseable {
    /**
     * 该引擎的类别
     */
    val type: TorrentEngineType

    /**
     * 该实现的位置, 用于标识不同的下载器.
     */
    val location: MediaSourceLocation

    /**
     * 是否被当前平台支持
     */
    val isSupported: Boolean

    /**
     * 测试是否可以连接到这个引擎. 不能连接一定代表无法使用, 但能连接不一定代表能使用.
     */
    suspend fun testConnection(): Boolean

    /**
     * 创建一个下载器. 若已经有一个下载器在运行, 则会返回同一个下载器.
     *
     * 返回的 [TorrentDownloader] 不应当被[关闭][TorrentDownloader.close].
     * 如过关闭了, 下次调用 [getDownloader] 仍然会返回同一个已经被关闭的实例.
     *
     * 注意: 在一些平台上需要考虑 [TorrentEngineAccess], 否则可能会导致此函数永久挂起.
     *
     * @throws UnsupportedOperationException 当 [isSupported] emit 了 `false` 时抛出
     * @throws TorrentDownloaderInitializationException 当创建失败时抛出
     * @throws CancellationException 当协程被取消时抛出
     */
    @Throws(
        TorrentDownloaderInitializationException::class,
        CancellationException::class,
    )
    suspend fun getDownloader(): TorrentDownloader
}

class TorrentDownloaderInitializationException(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * [TorrentEngine] 的默认实现
 */
abstract class AbstractTorrentEngine<Downloader : TorrentDownloader, Config : Any>(
    final override val type: TorrentEngineType,
    protected val config: Flow<Config>,
    protected val client: ScopedHttpClient,
    protected val peerFilterSettings: Flow<PeerFilterSettings>,
    parentCoroutineContext: CoroutineContext,
) : TorrentEngine {
    protected val logger = logger<AbstractTorrentEngine<*, *>>()
    protected val scope = parentCoroutineContext.childScope()

    // AbstractTorrentEngine 创建时会立刻获取 downloader 和 config，如果在 subclass 初始化完成之前获取可能会出现问题
    // subclass 初始化之后需要 complete 此 deferred
    protected val initialized: CompletableDeferred<Unit> = CompletableDeferred()

    private val downloader = flow { emit(config.first()) }
        .map { config ->
            // TODO: 这里不能 combine proxySettings, 因为这会导致更换设置时重新创建 downloader, 而 #775.
            //  而且在播放视频时, 关闭 downloader, 视频仍然会持有旧的 torrent session, 而旧的已经被关闭了, 视频就会一直显示缓冲中.
            //  目前没有必要在 proxySettings 变更时重新创建 downloader, 因为 downloader 不会使用代理.
            initialized.await()

            newInstance(config).also { downloader ->
                scope.coroutineContext.job.invokeOnCompletion {
                    downloader.close()
                }
            }
        }
        .retry(3) { e ->
            if (e is UnsupportedOperationException) {
                logger.warn { "Failed to create TorrentDownloader $type because it is not supported" }
                return@retry false
            }
            logger.warn(e) { "Failed to create TorrentDownloader $type, retrying later" }
            true
        }
        .onReplacement {
            closeInstance(it)
        }
        .stateIn(scope, SharingStarted.Lazily, null)

    init {
        scope.launch {
            initialized.await() // subclass 初始化完成后再开始
            config.drop(1).debounce(1000).collect {
                downloader.value?.applyConfig(it)
            }
        }
        scope.launch {
            initialized.await() // subclass 初始化完成后再开始
            combine(peerFilterSettings, downloader) { s, d -> s to d }
                .collectLatest { (settings, downloader) ->
                    if (downloader == null) return@collectLatest
                    downloader.applyPeerFilter(PeerFilterSettingsAsPeerFilter(settings))
                }
        }
    }

    protected abstract suspend fun newInstance(config: Config): Downloader

    protected abstract suspend fun Downloader.applyConfig(config: Config)

    protected abstract suspend fun Downloader.applyPeerFilter(filter: PeerFilter)

    @CallSuper
    protected open fun closeInstance(downloader: Downloader) {
        downloader.close()
    }

    @CallSuper
    override fun close() {
        this.scope.cancel()
    }

    final override suspend fun getDownloader(): Downloader {
        if (!isSupported) throw UnsupportedOperationException("Engine $this is not supported")
        return try {
            downloader.filterNotNull().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw TorrentDownloaderInitializationException(cause = e)
        }
    }
}

private class PeerFilterSettingsAsPeerFilter(
    private val config: PeerFilterSettings,
) : PeerFilter {
    private val logger = logger<PeerFilterSettingsAsPeerFilter>()
    private val correspondingFilters = buildList {
        if (config.blockInvalidId) {
            add(PeerInvalidIdFilter)
        }
        config.rules.forEach { rule ->
            addAll(rule.blockedIpPattern.map(::PeerIpFilter))
            addAll(rule.blockedIdRegex.map(::PeerIdFilter))
            addAll(rule.blockedClientRegex.map(::PeerClientFilter))
        }
    }

    override fun shouldBlock(info: PeerInfo): Boolean {
        try {
            val blockingRule = correspondingFilters.firstOrNull { it.shouldBlock(info) }
            if (blockingRule != null) {
                logger.info { "Peer ${info.describe()} is blocked by rule ${blockingRule.describe()}" }
            }
            return blockingRule != null
        } catch (e: Throwable) {
            logger.warn(e) { "Exception while checking whether to block peer ${info.describe()}. Accepting connection." }
            return false
        }
    }

    override fun describe(): String {
        return "PeerFilterSettingsAsPeerFilter"
    }
}

private fun PeerInfo.describe(): String = "${this.ipAddr}(${this.client})"
