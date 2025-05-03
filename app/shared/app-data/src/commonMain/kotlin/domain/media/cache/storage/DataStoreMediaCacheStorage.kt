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
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.whileSelect
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import me.him188.ani.app.data.persistent.DataStoreJson
import me.him188.ani.app.domain.media.cache.LocalFileMediaCache
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.engine.InvalidMediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.MetadataKey
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.matches
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.RestartableCoroutineScope
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.useDirectoryEntries
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 本地目录缓存, 管理本地目录以及元数据的存储, 调用 [MediaCacheEngine] 进行缓存的实际创建
 */
class DataStoreMediaCacheStorage(
    override val mediaSourceId: String,
    private val store: DataStore<List<MediaCacheSave>>,
    override val engine: MediaCacheEngine,
    private val displayName: String,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val clock: Clock = Clock.System,
) : MediaCacheStorage {
    private val scope: CoroutineScope = parentCoroutineContext.childScope()
    private val statSubscriptionScope = RestartableCoroutineScope(scope.coroutineContext)

    private val metadataFlow = store.data
        .map { list ->
            list.filter { it.engine == engine.engineKey }
                .sortedBy { it.origin.mediaId } // consistent stable order
        }

    /**
     * Locks access to mutable operations.
     */
    private val lock = Mutex()

    /**
     * App 必须先在启动时候恢复过一次之后才能 refresh caches
     */
    private val requestStartupRestoreFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /**
     * 已经恢复的 [LocalFileMediaCache], 不会重复恢复.
     */
    private val restoredLocalFileMediaCacheIds = MutableStateFlow(persistentListOf<String>())

    init {
        if (engine is TorrentMediaCacheEngine) {
            scope.launch {
                val startupRestored = CompletableDeferred<Unit>()
                val serviceConnected = engine.isServiceConnected.buffer(Channel.RENDEZVOUS).produceIn(this)
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
                            engine.deleteUnusedCaches(allRecovered)
                            startupRestored.complete(Unit)
                        }
                    }
                }
            }
        }
    }

    override suspend fun restorePersistedCaches() {
        if (engine is TorrentMediaCacheEngine) {
            requestStartupRestoreFlow.emit(true)
        } else {
            val allRecovered = refreshCache()
            engine.deleteUnusedCaches(allRecovered)
        }
    }

    private suspend fun refreshCache(): List<MediaCache> {
        statSubscriptionScope.restart()

        return lock.withLock {
            val allRecovered = MutableStateFlow(persistentListOf<MediaCache>())
            val metadataFlowSnapshot = metadataFlow.first()
            val semaphore = Semaphore(8)

            supervisorScope {
                metadataFlowSnapshot.forEach { (origin, metadata, _) ->
                    if (origin.mediaId in restoredLocalFileMediaCacheIds.value) return@forEach

                    semaphore.acquire()
                    @OptIn(DelicateCoroutinesApi::class)
                    launch(start = CoroutineStart.ATOMIC) {
                        try {
                            restoreFile(origin, metadata) {
                                if (it is LocalFileMediaCache) {
                                    restoredLocalFileMediaCacheIds.update { plus(it.origin.mediaId) }
                                }
                                allRecovered.update { plus(it) }
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            }

            // 新 restore 的加上 list 中已经有的 LocalFileMediaCache
            listFlow.update {
                allRecovered.value +
                        listFlow.value.filter { it.origin.mediaId in restoredLocalFileMediaCacheIds.value }
            }
            allRecovered.value
        }
    }

    private suspend fun restoreFile(
        origin: Media,
        metadata: MediaCacheMetadata,
        reportRecovered: suspend (MediaCache) -> Unit,
    ) = withContext(Dispatchers.IO_) {
        try {
            val cache = engine.restore(origin, metadata, statSubscriptionScope.currentCoroutineContext)
            logger.info { "Cache restored: ${origin.mediaId}, result=${cache}" }
            if (cache == null) return@withContext

            reportRecovered(cache)
            cache.resume()

            when (cache) {
                is TorrentMediaCacheEngine.TorrentMediaCache -> {
                    logger.info { "Cache resumed: $cache, subscribe to media cache stats." }
                    statSubscriptionScope.launch {
                        cache.subscribeStats { cache.appendExtra(it) }
                    }
                }

                else -> {
                    logger.info { "Cache resumed: $cache" }
                    return@withContext
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to restore cache for ${origin.mediaId}" }
        }
    }

    override val listFlow: MutableStateFlow<List<MediaCache>> = MutableStateFlow(emptyList())

    override val cacheMediaSource: MediaSource by lazy {
        MediaCacheStorageSource(this, displayName, MediaSourceLocation.Local)
    }
    override val stats: Flow<MediaStats> = engine.stats.map { stats ->
        MediaStats(
            uploaded = stats.uploaded,
            downloaded = stats.downloaded,
            uploadSpeed = stats.uploadSpeed,
            downloadSpeed = stats.downloadSpeed,
        )
    }

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean
    ): MediaCache {
        @Suppress("NAME_SHADOWING")
        val metadata = metadata.withExtra(
            mapOf(
                MediaCacheMetadata.KEY_CREATION_TIME to clock.now().toEpochMilliseconds()
                    .toString(),
            ),
        )
        return lock.withLock {
            logger.info { "$mediaSourceId creating cache, metadata=$metadata" }
            listFlow.value.firstOrNull {
                isSameMediaAndEpisode(it, media, metadata)
            }?.let { return@withLock it }

            if (!engine.supports(media)) {
                throw UnsupportedOperationException("Engine does not support media: $media")
            }
            val cache = engine.createCache(
                media, metadata,
                episodeMetadata,
                scope.coroutineContext,
            )

            withContext(Dispatchers.IO_) {
                store.updateData { list ->
                    list + MediaCacheSave(cache.origin, cache.metadata, engine.engineKey)
                }
            }

            if (cache is TorrentMediaCacheEngine.TorrentMediaCache) {
                logger.info { "Cache created: $cache, subscribe to media cache stats." }
                statSubscriptionScope.launch {
                    cache.subscribeStats { cache.appendExtra(it) }
                }
            }

            listFlow.update { plus(cache) }
            cache
        }.also {
            if (resume) {
                it.resume()
            }
        }
    }

    override suspend fun delete(cache: MediaCache): Boolean {
        return deleteFirst { isSameMediaAndEpisode(it, cache.origin, cache.metadata) }
    }

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        lock.withLock {
            val cache = listFlow.value.firstOrNull(predicate) ?: return false
            listFlow.update { minus(cache) }
            restoredLocalFileMediaCacheIds.update { minus(cache.origin.mediaId) }
            withContext(Dispatchers.IO_) {
                store.updateData { list ->
                    list.filterNot { isSameMediaAndEpisode(cache, it) }
                }
            }
            cache.closeAndDeleteFiles()
            return true
        }
    }

    override fun close() {
        if (engine is AutoCloseable) {
            engine.close()
        }
        scope.cancel()
        statSubscriptionScope.close()
    }

    // 添加额外的 metadata extras, 如果 datastore 中没有这个 media cache, 则不添加
    private suspend fun MediaCache.appendExtra(newMetadataExtras: Map<MetadataKey, String>) {
        logger.info { "Cache ${origin.mediaId} append new extras, size = ${newMetadataExtras.size}." }
        store.updateData { originalList ->
            val existing = originalList.indexOfFirst {
                isSameMediaAndEpisode(this, it)
            }
            if (existing == -1) return@updateData originalList

            originalList.toMutableList().apply {
                val existingSave = removeAt(existing)
                add(
                    // 更新时只使用 datastore 的数据
                    MediaCacheSave(
                        existingSave.origin,
                        existingSave.metadata.withExtra(newMetadataExtras),
                        existingSave.engine,
                    ),
                )
            }
        }
    }

    private fun isSameMediaAndEpisode(
        cache: MediaCache,
        media: Media,
        metadata: MediaCacheMetadata = cache.metadata
    ) = cache.origin.mediaId == media.mediaId &&
            metadata.subjectId == cache.metadata.subjectId &&
            metadata.episodeId == cache.metadata.episodeId

    private fun isSameMediaAndEpisode(cache: MediaCache, save: MediaCacheSave): Boolean =
        isSameMediaAndEpisode(cache, save.origin, save.metadata)

    companion object {
        private val logger = logger<DataStoreMediaCacheStorage>()

        @Deprecated("Since 4.8, metadata is stored in the datastore. This method is for migration only.")
        @InvalidMediaCacheEngineKey
        suspend fun migrateMetadataFromV47(
            metadataStore: DataStore<List<MediaCacheSave>>,
            storage: MediaCacheStorage,
            dir: SystemPath
        ) = dir.useDirectoryEntries { entries ->
            entries.forEach { file ->
                val save = try {
                    DataStoreJson.decodeFromString(LegacyMediaCacheSaveSerializer, file.readText())
                        .copy(engine = storage.engine.engineKey)
                } catch (e: SerializationException) {
                    logger.error(e) { "Failed to deserialize metadata file ${file.name}, ignoring migration." }
                    return@useDirectoryEntries
                }

                metadataStore.updateData { originalList ->
                    val existing = originalList.indexOfFirst {
                        it.origin.mediaId == save.origin.mediaId &&
                                it.metadata.subjectId == save.metadata.subjectId &&
                                it.metadata.episodeId == save.metadata.episodeId
                    }
                    if (existing != -1) {
                        logger.warn {
                            "Duplicated media cache metadata ${originalList[existing].origin.mediaId} found while migrating, " +
                                    "override to new ${save.origin.mediaId}, engine: ${save.engine}."
                        }
                        originalList.toMutableList().apply {
                            removeAt(existing)
                            add(save)
                        }
                    } else {
                        logger.info { "Migrating media cache metadata ${save.origin.mediaId}, engine: ${storage.engine.engineKey}." }
                        originalList + save
                    }
                }
            }
        }
    }
}

/**
 * 将 [MediaCacheStorage] 作为 [MediaSource], 这样可以被 [MediaFetcher] 搜索到以播放.
 */
private class MediaCacheStorageSource(
    private val storage: MediaCacheStorage,
    private val displayName: String,
    override val location: MediaSourceLocation = MediaSourceLocation.Local,
) : MediaSource {
    override val mediaSourceId: String get() = storage.mediaSourceId
    override val kind: MediaSourceKind get() = MediaSourceKind.LocalCache

    override suspend fun checkConnection(): ConnectionStatus = ConnectionStatus.SUCCESS

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        return SinglePagePagedSource {
            storage.listFlow.first().mapNotNull { cache ->
                val kind = query.matches(cache.metadata)
                if (kind == null) null
                else MediaMatch(cache.getCachedMedia(), kind)
            }.asFlow()
        }
    }

    override val info: MediaSourceInfo = MediaSourceInfo(
        displayName,
        "本地缓存",
        isSpecial = true,
    )
}
