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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import me.him188.ani.app.data.persistent.DataStoreJson
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.engine.InvalidMediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
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
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.useDirectoryEntries
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Deprecated("Since 4.8, metadata is stored in the datastore. This will be removed in the future.")
const val METADATA_FILE_EXTENSION = "metadata"

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

    private val metadataFlow = store.data
        .map { list ->
            list.filter { it.engine == engine.engineKey }
                .sortedBy { it.origin.mediaId } // consistent stable order
        }

    override suspend fun restorePersistedCaches() {
        val metadataFlowSnapshot = metadataFlow.first()

        val allRecovered = MutableStateFlow(persistentListOf<MediaCache>())
        val semaphore = Semaphore(8)

        supervisorScope {
            metadataFlowSnapshot.forEach { (origin, metadata, _) ->
                launch {
                    semaphore.withPermit {
                        restoreFile(
                            origin,
                            metadata,
                            reportRecovered = { cache ->
                                lock.withLock {
                                    listFlow.value += cache
                                }
                                allRecovered.update { plus(cache) }
                            },
                        )
                    }
                }
            }
        }

        engine.deleteUnusedCaches(allRecovered.value)
    }

    private suspend fun restoreFile(
        origin: Media,
        metadata: MediaCacheMetadata,
        reportRecovered: suspend (MediaCache) -> Unit,
    ) = withContext(Dispatchers.IO) {

        try {
            val cache = engine.restore(origin, metadata, scope.coroutineContext)
            logger.info { "Cache restored: ${origin.mediaId}, result=${cache}" }

            if (cache != null) {
                reportRecovered(cache)
                cache.resume()
                logger.info { "Cache resumed: $cache" }
            }

            // try to migrate
            /*if (cache != null) {
                val newSaveName = getSaveFilename(cache)
                if (file.name != newSaveName) {
                    logger.warn {
                        "Metadata file name mismatch, renaming: " +
                                "${file.name} -> $newSaveName"
                    }
                    file.moveTo(metadataDir.resolve(newSaveName))
                }
            }*/
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

    /**
     * Locks accesses to [listFlow]
     */
    private val lock = Mutex()

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
                cacheEquals(it, media, metadata)
            }?.let { return@withLock it }

            if (!engine.supports(media)) {
                throw UnsupportedOperationException("Engine does not support media: $media")
            }
            val cache = engine.createCache(
                media, metadata,
                episodeMetadata,
                scope.coroutineContext,
            )
            withContext(Dispatchers.IO) {
                store.updateData { list ->
                    list + MediaCacheSave(media, metadata, engine.engineKey)
                }
            }
            listFlow.value += cache
            cache
        }.also {
            if (resume) {
                it.resume()
            }
        }
    }

    private fun cacheEquals(
        it: MediaCache,
        media: Media,
        metadata: MediaCacheMetadata = it.metadata
    ) = it.origin.mediaId == media.mediaId && it.metadata.episodeSort == metadata.episodeSort

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        lock.withLock {
            val cache = listFlow.value.firstOrNull(predicate) ?: return false
            listFlow.value -= cache
            withContext(Dispatchers.IO) {
                store.updateData { list ->
                    list.filterNot { it.engine == engine.engineKey && it.origin.mediaId == cache.origin.mediaId }
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
    }

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
