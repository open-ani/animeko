/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import me.him188.ani.app.torrent.api.files.averageRate
import me.him188.ani.app.ui.cache.components.CacheEpisodePaused
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheGroupCommonInfo
import me.him188.ani.app.ui.cache.components.CacheGroupState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.unwrapCached
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
            .flatMapLatest { list ->
                list.map { it.stats }.sum()
            }
            .sampleWithInitial(1.seconds)
            .stateInBackground(MediaStats.Unspecified)

        val groupsFlow = cacheManager.enabledStorages
            .flatMapLatest { storages ->
                if (storages.isEmpty()) {
                    flowOfEmptyList()
                } else {
                    val listFlow = storages.map { storage ->
                        storage.listFlow.map { caches ->
                            caches.map { cache ->
                                CacheWithEngine(cache, storage.engine.engineKey)
                            }
                        }
                    }
                    if (listFlow.isEmpty()) {
                        flowOfEmptyList()
                    } else {
                        combine(listFlow) { it.asSequence().flatten().toList() }
                            .transformLatest { allCaches ->
                                supervisorScope {
                                    emitAll(createCacheGroupStates(allCaches))
                                } // supervisorScope won't finish itself
                            }
                    }
                }
            }
            .shareInBackground()

        combine(overallStatsFlow, groupsFlow, ::CacheManagementState)
            .stateInBackground(CacheManagementState.Placeholder) // has distinctUntilChanged
    }

    private fun createCacheGroupStates(allCaches: List<CacheWithEngine>): Flow<List<CacheGroupState>> {
        val groupStateFlows = allCaches
            .groupBy { it.cache.origin.unwrapCached().mediaId }
            .map { (_, mediaCaches) ->
                check(mediaCaches.isNotEmpty())

                val firstCache = mediaCaches.first()

                val groupId = firstCache.cache.origin.unwrapCached().mediaId
                val statsFlow = firstCache.cache.sessionStats
                    .combine(
                        firstCache.cache.sessionStats.map { it.downloadedBytes.inBytes }.averageRate(),
                    ) { stats, downloadSpeed ->
                        CacheGroupState.Stats(
                            downloadSpeed = downloadSpeed.bytes,
                            downloadedSize = stats.downloadedBytes,
                            uploadSpeed = stats.uploadSpeed,
                        )
                    }
                    .sampleWithInitial(1.seconds)
                    .onStart {
                        emit(
                            CacheGroupState.Stats(
                                FileSize.Unspecified,
                                FileSize.Unspecified,
                                FileSize.Unspecified,
                            ),
                        )
                    }

                val episodeFlows = mediaCaches.map { mediaCache ->
                    createCacheEpisodeFlow(mediaCache)
                }.let { episodeFlows ->
                    if (episodeFlows.isEmpty()) {
                        flowOfEmptyList()
                    } else {
                        combine(episodeFlows) { it.toList() }
                    }
                }.shareInBackground()

                val commonInfo = createGroupCommonInfo(
                    subjectId = firstCache.cache.metadata.subjectId.toInt(),
                    firstCache = firstCache.cache,
                    subjectDisplayName = firstCache.cache.metadata.subjectNameCN
                        ?: firstCache.cache.metadata.subjectNames.firstOrNull()
                        ?: firstCache.cache.origin.originalTitle,
                    imageUrl = null,
                )

                combine(
                    statsFlow,
                    episodeFlows,
                    subjectRepository.getSubjectCollectionTypeOffline(commonInfo.subjectId)
                        .onStart { emit(UnifiedCollectionType.NOT_COLLECTED) },
                ) { stats, episodes, collectionType ->
                    CacheGroupState(
                        id = groupId,
                        commonInfo = commonInfo,
                        episodes = episodes,
                        stats = stats,
                        engineKey = firstCache.engineKey,
                        collectionType = collectionType,
                    )
                }
            }

        if (groupStateFlows.isEmpty()) {
            return flowOfEmptyList()
        }
        return combine(groupStateFlows) { array ->
            array.sortedWith(
                compareByDescending<CacheGroupState> { it.latestCreationTime }
                    .thenByDescending { it.cacheId }, // 只有旧的缓存会没有时间, 才会走这个
            )
        }
    }

    private fun createGroupCommonInfo(
        subjectId: Int,
        firstCache: MediaCache,
        subjectDisplayName: String,
        imageUrl: String?,
    ) = CacheGroupCommonInfo(
        subjectId = subjectId,
        subjectDisplayName,
        mediaSourceId = firstCache.origin.unwrapCached().mediaSourceId,
        allianceName = firstCache.origin.unwrapCached().properties.alliance,
        imageUrl = imageUrl,
    )

    private fun createCacheEpisodeFlow(mediaCache: CacheWithEngine): Flow<CacheEpisodeState> {
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
        return combine(statsFlow, stateFlow, mediaCache.cache.canPlay) { stats, state, canPlay ->
            val subjectId = metadata.subjectId.toInt()
            val episodeId = metadata.episodeId.toInt()
            CacheEpisodeState(
                subjectId = subjectId,
                episodeId = episodeId,
                cacheId = mediaCache.cache.cacheId,
                sort = metadata.episodeSort,
                displayName = metadata.episodeName,
                creationTime = metadata.creationTime,
                screenShots = emptyList(),
                stats = stats,
                state = state,
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