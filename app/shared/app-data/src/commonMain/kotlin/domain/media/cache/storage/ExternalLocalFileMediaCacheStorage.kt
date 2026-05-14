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
import kotlinx.collections.immutable.minus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.cache.LocalFileMediaCache
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isRegularFile
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.logging.error
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class ExternalLocalFileMediaCacheStorage(
    override val mediaSourceId: String,
    private val store: DataStore<List<MediaCacheSave>>,
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractDataStoreMediaCacheStorage(
    mediaSourceId = mediaSourceId,
    datastore = store,
    engine = ExternalLocalFileMediaCacheEngine(mediaSourceId),
    displayName = "LocalFile",
    parentCoroutineContext = parentCoroutineContext,
) {
    private val lock = Mutex()

    override suspend fun restorePersistedCaches() {
        lock.withLock {
            cleanupInvalidBindings()
            refreshCache()
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
            logger.error(e) { "Failed to restore external local file cache for ${origin.mediaId}" }
            null
        }
    }

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean,
    ): MediaCache {
        return lock.withLock {
            listFlow.value.firstOrNull { isSameMediaAndEpisode(it, media, metadata) }?.let { return it }

            if (!engine.supports(media)) {
                throw UnsupportedOperationException("Engine does not support media: $media")
            }

            val previousBinding = listFlow.value.firstOrNull {
                it.metadata.subjectId == metadata.subjectId && it.metadata.episodeId == metadata.episodeId
            }
            if (previousBinding != null) {
                removeCache(previousBinding)
            }

            val cache = engine.createCache(
                media,
                metadata,
                episodeMetadata,
                scope.coroutineContext,
            )

            withContext(Dispatchers.IO_) {
                store.updateData { list ->
                    list.filterNot {
                        it.engine == engine.engineKey &&
                                it.metadata.subjectId == metadata.subjectId &&
                                it.metadata.episodeId == metadata.episodeId
                    } + MediaCacheSave(cache.origin, cache.metadata, engine.engineKey)
                }
            }

            listFlow.update { plus(cache) }

            if (resume) {
                cache.resume()
            }

            cache
        }
    }

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        return lock.withLock {
            val cache = listFlow.value.firstOrNull(predicate) ?: return false
            removeCache(cache)
        }
    }

    private suspend fun cleanupInvalidBindings() {
        val invalidMediaIds = metadataFlow.first()
            .filter { !isValidLocalFile(it.origin) }
            .map { it.origin.mediaId }
            .toSet()

        if (invalidMediaIds.isEmpty()) {
            return
        }

        withContext(Dispatchers.IO_) {
            store.updateData { list ->
                list.filterNot {
                    it.engine == engine.engineKey && it.origin.mediaId in invalidMediaIds
                }
            }
        }
    }

    private suspend fun removeCache(cache: MediaCache): Boolean {
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

    private fun isValidLocalFile(origin: Media): Boolean {
        val file = (origin.download as? ResourceLocation.LocalFile)
            ?.filePath
            ?.let(::Path)
            ?.inSystem
            ?: return false
        return file.exists() && file.isRegularFile()
    }

    private class ExternalLocalFileMediaCacheEngine(
        private val mediaSourceId: String,
    ) : MediaCacheEngine {
        override val engineKey: MediaCacheEngineKey = MediaCacheEngineKey.ExternalLocalFile
        override val stats: Flow<MediaStats> = flowOf(MediaStats.Zero)

        override fun supports(media: Media): Boolean {
            return media !is CachedMedia && media.download is ResourceLocation.LocalFile
        }

        override suspend fun restore(
            origin: Media,
            metadata: MediaCacheMetadata,
            parentContext: CoroutineContext,
        ): MediaCache? {
            return createBindingOrNull(origin, metadata)
        }

        override suspend fun createCache(
            origin: Media,
            metadata: MediaCacheMetadata,
            episodeMetadata: EpisodeMetadata,
            parentContext: CoroutineContext,
        ): MediaCache {
            return createBindingOrNull(origin, metadata)
                ?: error("External local file cache only supports existing local files: ${origin.download}")
        }

        override suspend fun deleteUnusedCaches(all: List<MediaCache>) {
        }

        private fun createBindingOrNull(origin: Media, metadata: MediaCacheMetadata): LocalFileMediaCache? {
            val file = (origin.download as? ResourceLocation.LocalFile)
                ?.filePath
                ?.let(::Path)
                ?.inSystem
                ?: return null
            if (!file.exists() || !file.isRegularFile()) {
                return null
            }
            return LocalFileMediaCache(
                origin = origin,
                metadata = metadata,
                file = file,
                backedMediaSourceId = mediaSourceId,
                onCloseAndDeleteFiles = { _ -> },
            )
        }
    }
}
