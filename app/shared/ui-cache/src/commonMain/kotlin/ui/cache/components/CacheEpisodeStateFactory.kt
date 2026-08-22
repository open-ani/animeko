/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.torrent.api.files.averageRate
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.coroutines.sampleWithInitial
import kotlin.time.Duration.Companion.seconds

internal data class CacheWithEngine(
    val cache: MediaCache,
    val engineKey: MediaCacheEngineKey,
)

/**
 * 合并所有启用的存储中的缓存, 并附带各自的引擎类型.
 */
internal fun Flow<List<MediaCacheStorage>>.allCachesWithEngineFlow(): Flow<List<CacheWithEngine>> {
    return flatMapLatest { list ->
        if (list.isEmpty()) return@flatMapLatest flowOfEmptyList()
        val listFlow = list.map { storage ->
            storage.listFlow.map { caches ->
                caches.map { CacheWithEngine(it, storage.engine.engineKey) }
            }
        }
        combine(listFlow) { it.asSequence().flatten().toList() }
    }
}

/**
 * 为一个 [MediaCache] 创建持续更新的 [CacheEpisodeState] flow.
 *
 * 全局缓存管理页和条目缓存页共用此逻辑.
 */
internal fun HasBackgroundScope.createCacheEpisodeStateFlow(
    groupId: String,
    mediaCache: CacheWithEngine,
    subjectCollectionType: Flow<UnifiedCollectionType?>,
): Flow<CacheEpisodeState> {
    val statsFlow = mediaCache.cache.fileStats
        .combine(
            mediaCache.cache.fileStats
                .shareInBackground(replay = 1).map { it.downloadedBytes.inBytes }.averageRate(),
        ) { stats, downloadSpeed ->
            CacheEpisodeState.Stats(
                downloadSpeed = downloadSpeed.bytes,
                progress = stats.downloadProgress,
                totalSize = stats.totalSize,
            )
        }
        .sampleWithInitial(1.seconds)
        .stateInBackground(CacheEpisodeState.Stats.Unspecified)
    // stateInBackground has distinctUntilChanged

    val stateFlow = mediaCache.cache.state
        .map(::toCacheEpisodePaused)
        .stateInBackground(CacheEpisodePaused.IN_PROGRESS)

    val metadata = mediaCache.cache.metadata
    return combine(
        statsFlow,
        stateFlow,
        subjectCollectionType,
        mediaCache.cache.canPlay,
    ) { stats, state, type, canPlay ->
        val subjectId = metadata.subjectId.toInt()
        val episodeId = metadata.episodeId.toInt()
        CacheEpisodeState(
            groupId = groupId,
            subjectId = subjectId,
            episodeId = episodeId,
            cacheId = mediaCache.cache.cacheId,
            sort = metadata.episodeSort,
            subjectName = metadata.subjectNameCN ?: metadata.subjectNames.firstOrNull() ?: "",
            displayName = metadata.episodeName,
            creationTime = metadata.creationTime,
            screenShots = emptyList(),
            stats = stats,
            state = state,
            engineKey = mediaCache.engineKey,
            subjectCollectionType = type,
            playability = when {
                subjectId == 0 || episodeId == 0 -> CacheEpisodeState.Playability.INVALID_SUBJECT_EPISODE_ID
                !canPlay -> CacheEpisodeState.Playability.STREAMING_NOT_SUPPORTED
                else -> CacheEpisodeState.Playability.PLAYABLE
            },
            mediaSourceId = mediaCache.cache.origin.mediaSourceId,
        )
    }
}

internal fun toCacheEpisodePaused(state: MediaCacheState): CacheEpisodePaused {
    return when (state) {
        MediaCacheState.IN_PROGRESS -> CacheEpisodePaused.IN_PROGRESS
        MediaCacheState.PAUSED -> CacheEpisodePaused.PAUSED
        MediaCacheState.FAILED -> CacheEpisodePaused.FAILED
        MediaCacheState.COMPLETED -> CacheEpisodePaused.COMPLETED
    }
}
