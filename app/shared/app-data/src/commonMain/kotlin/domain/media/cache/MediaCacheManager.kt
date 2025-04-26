/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList

abstract class MediaCacheManager(
    val storagesIncludingDisabled: List<MediaCacheStorage>,
    final override val backgroundScope: CoroutineScope,
) : HasBackgroundScope { // available via inject
    val enabledStorages: Flow<List<MediaCacheStorage>> = flowOf(storagesIncludingDisabled)

    private val cacheListFlow: Flow<List<MediaCache>> by lazy {
        val flows = storagesIncludingDisabled.map { it.listFlow }
        if (flows.isEmpty()) {
            flowOfEmptyList()
        } else combine(flows) {
            it.asSequence().flatten().toList()
        }
    }

    @Stable
    fun listCacheForSubject(
        subjectId: Int,
    ): Flow<List<MediaCache>> {
        val subjectIdString = subjectId.toString()
        return cacheListFlow.map { list ->
            list.filter { cache ->
                cache.metadata.subjectId == subjectIdString
            }
        }
    }

    /**
     * Returns the cache status for the episode, updated lively and sampled for 1000ms.
     */
    @Stable
    fun cacheStatusForEpisode(
        subjectId: Int,
        episodeId: Int,
    ): Flow<EpisodeCacheStatus> {
        val subjectIdString = subjectId.toString()
        val episodeIdString = episodeId.toString()
        return cacheListFlow.transformLatest { list ->
            var hasAnyCached: MediaCache? = null
            var hasAnyCaching: MediaCache? = null

            for (mediaCache in list) {
                if (mediaCache.metadata.subjectId == subjectIdString && mediaCache.metadata.episodeId == episodeIdString) {
                    hasAnyCaching = mediaCache
                    if (mediaCache.isFinished()) {
                        hasAnyCached = mediaCache
                    }
                }
            }

            val target = hasAnyCached ?: hasAnyCaching
            if (target == null) {
                emit(EpisodeCacheStatus.NotCached)
            } else {
                emitAll(
                    target.fileStats.map {
                        if (it.downloadProgress.isFinished) {
                            EpisodeCacheStatus.Cached(totalSize = it.totalSize)
                        } else {
                            EpisodeCacheStatus.Caching(progress = it.downloadProgress, totalSize = it.totalSize)
                        }
                    },
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun deleteCache(cache: MediaCache): Boolean {
        for (storage in enabledStorages.first()) {
            if (storage.delete(cache)) {
                return true
            }
        }
        return false
    }

    suspend fun deleteFirstCache(filter: (MediaCache) -> Boolean): Boolean {
        for (storage in enabledStorages.first()) {
            if (storage.deleteFirst(filter)) {
                return true
            }
        }
        return false
    }

    suspend fun findFirstCache(filter: (MediaCache) -> Boolean): MediaCache? {
        for (storage in enabledStorages.first()) {
            storage.listFlow.first().find(filter)?.let {
                return it
            }
        }
        return null
    }

    suspend fun closeAllCaches() = supervisorScope {
        for (storage in enabledStorages.first()) {
            for (mediaCache in storage.listFlow.first()) {
                launch { mediaCache.close() }
            }
        }
    }

    companion object {
        /**
         * 本地数据源不允许有多个示例. 必须是 Factory:MediaSource:Instance = 1:1:1 的关系.
         */
        const val LOCAL_FS_MEDIA_SOURCE_ID = "local-file-system"
    }
}

class MediaCacheManagerImpl(
    storagesIncludingDisabled: List<MediaCacheStorage>,
    backgroundScope: CoroutineScope,
) : MediaCacheManager(storagesIncludingDisabled, backgroundScope)
