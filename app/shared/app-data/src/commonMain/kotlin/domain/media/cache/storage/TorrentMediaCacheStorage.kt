/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.storage

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.RestartableCoroutineScope
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class TorrentMediaCacheStorage(
    override val mediaSourceId: String,
    private val store: DataStore<List<MediaCacheSave>>,
    private val torrentEngine: TorrentMediaCacheEngine,
    private val shareRatioLimitFlow: Flow<Float>,
    private val displayName: String,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractDataStoreMediaCacheStorage(
    mediaSourceId, store, torrentEngine, displayName, parentCoroutineContext,
) {
    private val statSubscriptionScope = RestartableCoroutineScope(scope.coroutineContext)

    /**
     * Locks access to mutable operations.
     */
    private val lock = Mutex()

    /**
     * App 必须先在启动时候恢复过一次之后才能 refresh caches
     */
    private val requestStartupRestore = Channel<Unit>(Channel.CONFLATED)

    private val startupRestored = CompletableDeferred<Unit>()

    init {
        // 引擎判断「删掉这条记录后同一 media 还剩哪些集」时直接读 store, 读的必须是本存储写入的那一份.
        check(store === torrentEngine.metadataStore) {
            "TorrentMediaCacheStorage and its TorrentMediaCacheEngine must share one metadata store."
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val serviceConnected = torrentEngine.isServiceConnected.buffer(Channel.RENDEZVOUS).produceIn(this)

            while (true) {
                select<Unit> {
                    // 如果在 APP 启动时 serviceConnected 状态变了, 忽略处理
                    serviceConnected.onReceive {
                        if (!startupRestored.isCompleted) {
                            logger.warn { "Startup torrent cache restoration is not completed, skip restore on service connected." }
                            return@onReceive
                        }
                        logger.debug { "Refreshing torrent caches on service connection changed, connected: $it." }
                        refreshCache()
                    }

                    requestStartupRestore.onReceive {
                        logger.debug { "Restoring persisted torrent caches on startup." }
                        val allRecovered = refreshCache()
                        torrentEngine.deleteUnusedCaches(allRecovered)
                        startupRestored.complete(Unit)
                    }
                }
            }
        }
    }

    override suspend fun restorePersistedCaches() {
        requestStartupRestore.send(Unit)
    }

    override suspend fun refreshCache(): List<MediaCache> {
        return lock.withLock {
            statSubscriptionScope.restart()
            super.refreshCache()
        }
    }

    /**
     * The torrent row describes the whole torrent, so it may only fill in a record that is the media's
     * only one. A season pack's other episodes keep their own (empty) completion state and restore
     * through the engine.
     */
    private suspend fun backfillLegacyMetadata(origin: Media, metadata: MediaCacheMetadata): MediaCacheMetadata {
        if (metadata.pathInTorrent != null) return metadata
        val recordCount = metadataFlow.first().count { it.origin.mediaId == origin.mediaId }
        if (recordCount != 1) return metadata
        return torrentEngine.backfillFromTorrentRow(origin.mediaId, metadata)
    }

    override suspend fun restoreFile(
        origin: Media,
        metadata: MediaCacheMetadata,
        reportRecovered: suspend (MediaCache) -> Unit,
    ): MediaCache? = withContext(Dispatchers.IO_) {
        try {
            val upgraded = backfillLegacyMetadata(origin, metadata)
            val cache = super.restoreFile(origin, upgraded, reportRecovered)
            if (cache != null && upgraded != metadata) {
                persistMetadata(cache, upgraded)
            }

            when (cache) {
                is TorrentMediaCacheEngine.TorrentMediaCache -> {
                    logger.info { "Cache resumed: $cache, subscribe to media cache stats." }
                    cache.onMetadataUpdated = { persistMetadata(cache, it) }
                    statSubscriptionScope.launch {
                        cache.subscribeStats(shareRatioLimitFlow)
                    }
                }

                else -> {
                    logger.info { "Cache resumed: $cache" }
                }
            }

            cache
        } catch (e: Exception) {
            logger.error(e) { "Failed to restore cache for ${origin.mediaId}" }
            null
        }
    }

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean
    ): TorrentMediaCacheEngine.TorrentMediaCache {
        var promoting = false
        return lock.withLock {
            // 已存在同一资源同一剧集的记录时直接复用; 统计订阅只对新建的记录进行, 避免同一记录被重复订阅.
            val existing = listFlow.value.firstOrNull { isSameMediaAndEpisode(it, media, metadata) }
            promoting = existing != null && existing.metadata.autoCached && !metadata.autoCached
            val cache = existing ?: super.cache(media, metadata, episodeMetadata, false)
            check(cache is TorrentMediaCacheEngine.TorrentMediaCache) { "Cache does not implement TorrentMediaCache." }

            if (existing == null) {
                cache.onMetadataUpdated = { persistMetadata(cache, it) }
                statSubscriptionScope.launch {
                    cache.subscribeStats(shareRatioLimitFlow)
                }
            }

            cache
        }.also {
            when {
                // 普通 resume() 见记录仍标着 autoCached 会直接返回, 用户会看到添加成功却什么都没发生.
                // 只有显式添加才转正, 启动恢复一类的 resume 不能把跟随播放的记录变成完整下载.
                promoting -> it.resumeByUser()
                resume -> it.resume()
            }
        }
    }

    override fun close() {
        torrentEngine.close()
        statSubscriptionScope.close()
        super.close()
    }

}
