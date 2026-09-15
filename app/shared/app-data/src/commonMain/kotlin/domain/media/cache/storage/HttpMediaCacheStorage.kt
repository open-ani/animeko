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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import me.him188.ani.app.domain.media.cache.DownloaderStatus
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.error
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class HttpMediaCacheStorage(
    override val mediaSourceId: String,
    private val store: DataStore<List<MediaCacheSave>>,
    private val dao: HttpCacheDownloadStateDao,
    private val httpEngine: MediaCacheEngine,
    private val displayName: String,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractDataStoreMediaCacheStorage(mediaSourceId, store, httpEngine, displayName, parentCoroutineContext) {
    /**
     * Locks access to mutable operations.
     */
    private val lock = Mutex()

    override suspend fun restorePersistedCaches() {
        val allRecovered = refreshCache()
        httpEngine.deleteUnusedCaches(allRecovered)
    }

    override suspend fun refreshCache(): List<MediaCache> {
        return lock.withLock {
            super.refreshCache()
        }
    }

    override suspend fun restoreFile(
        origin: Media,
        metadata: MediaCacheMetadata,
        reportRecovered: suspend (MediaCache) -> Unit,
    ): MediaCache? = withContext(Dispatchers.IO_) {
        try {
            super.restoreFile(origin, metadata, reportRecovered)
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
    ): MediaCache {
        return lock.withLock {
            listFlow.value.firstOrNull {
                isSameMediaAndEpisode(it, media, metadata)
            }?.let { return it }

            if (!engine.supports(media)) {
                throw UnsupportedOperationException("Engine does not support media: $media")
            }

            val pending = PendingHttpMediaCache(media, metadata)
            var createdForCleanup: MediaCache? = null
            listFlow.value += pending

            try {
                val createdCache = httpEngine.createCache(
                    media,
                    metadata,
                    episodeMetadata,
                    scope.coroutineContext,
                )
                createdForCleanup = createdCache

                withContext(Dispatchers.IO_) {
                    store.updateData { list ->
                        list + MediaCacheSave(createdCache.origin, createdCache.metadata, engine.engineKey)
                    }
                }

                pending.attach(createdCache, resume)
                pending
            } catch (e: Throwable) {
                listFlow.value = listFlow.value.filterNot { it === pending }
                createdForCleanup?.closeAndDeleteFiles()
                throw e
            }
        }
    }

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        return lock.withLock {
            super.deleteFirst(predicate)
        }
    }
}

private class PendingHttpMediaCache(
    override val origin: Media,
    override val metadata: MediaCacheMetadata,
) : MediaCache {
    private val delegate = MutableStateFlow<MediaCache?>(null)
    private val desiredState = MutableStateFlow(MediaCacheState.IN_PROGRESS)
    override val isDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val state: Flow<MediaCacheState> = delegate.flatMapLatest {
        it?.state ?: desiredState
    }

    override val canPlay: Flow<Boolean> = delegate.flatMapLatest {
        it?.canPlay ?: flowOf(false)
    }

    override val fileStats: Flow<MediaCache.FileStats> = delegate.flatMapLatest {
        it?.fileStats ?: flowOf(MediaCache.FileStats.Unspecified)
    }

    override val sessionStats: Flow<MediaCache.SessionStats> = delegate.flatMapLatest {
        it?.sessionStats ?: flowOf(MediaCache.SessionStats.Unspecified)
    }

    override val downloaderStatus: Flow<DownloaderStatus?> = delegate.flatMapLatest {
        it?.downloaderStatus ?: flowOf(null)
    }

    suspend fun attach(cache: MediaCache, resume: Boolean) {
        delegate.value = cache
        if (isDeleted.value) {
            cache.closeAndDeleteFiles()
            return
        }

        when (desiredState.value) {
            MediaCacheState.IN_PROGRESS -> if (resume) cache.resume()
            MediaCacheState.PAUSED -> cache.pause()
            MediaCacheState.FAILED,
            MediaCacheState.COMPLETED,
                -> Unit
        }
    }

    override suspend fun getCachedMedia() =
        delegate.value?.getCachedMedia()
            ?: throw IllegalStateException("Cache is still being created")

    override suspend fun pause() {
        delegate.value?.pause() ?: run {
            desiredState.value = MediaCacheState.PAUSED
        }
    }

    override suspend fun close() {
        isDeleted.value = true
        delegate.value?.close()
    }

    override suspend fun resume() {
        delegate.value?.resume() ?: run {
            desiredState.value = MediaCacheState.IN_PROGRESS
        }
    }

    override suspend fun closeAndDeleteFiles() {
        isDeleted.value = true
        delegate.value?.closeAndDeleteFiles()
    }
}
