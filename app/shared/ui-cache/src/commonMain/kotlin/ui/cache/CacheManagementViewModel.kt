/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.media.cache.DeleteCacheByCacheIdUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.engine.sum
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.torrent.api.files.averageRate
import me.him188.ani.app.ui.cache.components.CacheEpisodePaused
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheGroupState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.coroutines.sampleWithInitial
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

internal data class CacheWithEngine(
    val cache: MediaCache,
    val engineKey: MediaCacheEngineKey,
)

@Stable
class CacheManagementViewModel : AbstractViewModel(), KoinComponent {
    private val cacheManager: MediaCacheManager by inject()
    private val deleteCacheByCacheIdUseCase: DeleteCacheByCacheIdUseCase by inject()
    private val subjectRepository: SubjectCollectionRepository by inject()

    val stateFlow = run {
        val overallStatsFlow = cacheManager.enabledStorages
            .overallStatsFlow()
            .sampleWithInitial(1.seconds)
            .stateInBackground(MediaStats.Unspecified)

        val allCachesFlow = cacheManager.enabledStorages
            .flatMapLatest { list ->
                if (list.isEmpty()) return@flatMapLatest flowOfEmptyList()
                val listFlow = list.map { storage ->
                    storage.listFlow.map { caches ->
                        caches.map { CacheWithEngine(it, storage.engine.engineKey) }
                    }
                }
                combine(listFlow) { it.asSequence().flatten().toList() }
            }.shareInBackground()

        val groupsFlow = allCachesFlow.transformLatest {
            supervisorScope { emitAll(createCacheGroupStates(it)) } // supervisorScope won't finish itself
        }.shareInBackground()

        combine(overallStatsFlow, groupsFlow, ::CacheManagementState)
            .stateInBackground(CacheManagementState.Placeholder) // has distinctUntilChanged
    }

    private fun createCacheGroupStates(allCaches: List<CacheWithEngine>): Flow<List<CacheGroupState>> {
        val groupStateFlows = allCaches
            .groupBy { it.cache.metadata.subjectId }
            .map { (subjectId, caches) ->
                val groupId = subjectId
                val collectionType = subjectRepository.getSubjectCollectionTypeOffline(subjectId.toInt())
                    .onStart { emit(UnifiedCollectionType.NOT_COLLECTED) }

                combine(caches.map { createCacheEpisodeFlow(groupId, it, collectionType) }) { states ->
                    states.toList()
                }.combine(collectionType) { entries, type ->
                    CacheGroupState(
                        subjectId.toInt(),
                        caches.first().cache.metadata.run { subjectNameCN ?: subjectNames.firstOrNull() ?: "" },
                        entries = entries,
                        collectionType = type,
                    )
                }
            }

        if (groupStateFlows.isEmpty()) {
            return flowOfEmptyList()
        }

        return combine(groupStateFlows) { array ->
            array.sortedWith(
                compareByDescending<CacheGroupState> { it.entries.any { entry -> !entry.isFinished } }
                    .thenByDescending { it.entries.maxOfOrNull { entry -> entry.creationTime ?: 0 } },
            )
        }
    }

    private fun createCacheEpisodeFlow(
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
            .map {
                when (it) {
                    MediaCacheState.IN_PROGRESS -> CacheEpisodePaused.IN_PROGRESS
                    MediaCacheState.PAUSED -> CacheEpisodePaused.PAUSED
                }
            }
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
            )
        }
    }

    fun pauseCache(cache: CacheEpisodeState) {
        backgroundScope.launch {
            cacheManager.findFirstCache { it.cacheId == cache.cacheId }?.pause()
        }
    }

    fun resumeCache(cache: CacheEpisodeState) {
        backgroundScope.launch {
            cacheManager.findFirstCache { it.cacheId == cache.cacheId }?.resume()
        }
    }

    fun deleteCache(cache: CacheEpisodeState) {
        backgroundScope.launch {
            deleteCacheByCacheIdUseCase(cache.subjectId, cache.episodeId, cache.cacheId)
        }
    }
}

internal fun Flow<List<MediaCacheStorage>>.overallStatsFlow(): Flow<MediaStats> {
    return flatMapLatest { storages ->
        if (storages.isEmpty()) {
            flowOf(MediaStats.Zero)
        } else {
            storages.map { it.stats }.sum()
        }
    }
}
