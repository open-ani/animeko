/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.storage

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class TorrentMediaCacheStorage(
    override val mediaSourceId: String,
    private val store: DataStore<List<MediaCacheSave>>,
    private val torrentEngine: TorrentMediaCacheEngine,
    private val shareRatioLimitFlow: Flow<Float>,
    private val displayName: String,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractDataStoreMediaCacheStorage(mediaSourceId, store, torrentEngine, displayName, parentCoroutineContext) {
    private val statSubscriptionScope = RestartableCoroutineScope(scope.coroutineContext)

    /**
     * Locks access to mutable operations.
     */
    private val lock = Mutex()

    /**
     * App 必须先在启动时候恢复过一次之后才能 refresh caches
     */
    private val requestStartupRestoreFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)


    init {
        scope.launch {
            val startupRestored = CompletableDeferred<Unit>()
            val serviceConnected = torrentEngine.isServiceConnected.buffer(Channel.RENDEZVOUS).produceIn(this)
            val requestStartupRestore = requestStartupRestoreFlow.produceIn(this)

            while (true) {
                select<Unit> {
                    // 如果在 APP 启动时 serviceConnected 状态变了, 忽略处理
                    serviceConnected.onReceive {
                        if (!startupRestored.isCompleted) return@onReceive
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
        requestStartupRestoreFlow.emit(true)
    }

    override suspend fun refreshCache(): List<MediaCache> {
        return lock.withLock {
            statSubscriptionScope.restart()
            super.refreshCache()
        }
    }

    override suspend fun restoreFile(
        origin: Media,
        metadata: MediaCacheMetadata,
        reportRecovered: suspend (MediaCache) -> Unit,
    ): MediaCache? = withContext(Dispatchers.IO_) {
        try {
            val cache = super.restoreFile(origin, metadata, reportRecovered)

            when (cache) {
                is TorrentMediaCacheEngine.TorrentMediaCache -> {
                    logger.info { "Cache resumed: $cache, subscribe to media cache stats." }
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
        return lock.withLock {
            val cache = super.cache(media, metadata, episodeMetadata, false)
            check(cache is TorrentMediaCacheEngine.TorrentMediaCache) { "Cache does not implement TorrentMediaCache." }

            statSubscriptionScope.launch {
                cache.subscribeStats(shareRatioLimitFlow)
            }

            cache
        }.also {
            if (resume) {
                it.resume()
            }
        }
    }

    override fun close() {
        torrentEngine.close()
        statSubscriptionScope.close()
        super.close()
    }

}
