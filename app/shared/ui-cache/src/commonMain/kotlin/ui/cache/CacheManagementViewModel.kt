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
import me.him188.ani.app.data.repository.subject.staticSubjectImageLargeUrl
import me.him188.ani.app.domain.media.cache.DeleteCacheByCacheIdUseCase
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.engine.sum
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheGroupState
import me.him188.ani.app.ui.cache.components.CacheWithEngine
import me.him188.ani.app.ui.cache.components.allCachesWithEngineFlow
import me.him188.ani.app.ui.cache.components.createCacheEpisodeStateFlow
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.coroutines.sampleWithInitial
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

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
            .allCachesWithEngineFlow()
            .shareInBackground()

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
                val displayInfo = subjectRepository.getSubjectDisplayInfoOffline(subjectId.toInt())
                    .onStart { emit(null) }

                val entriesFlow =
                    combine(caches.map { createCacheEpisodeStateFlow(groupId, it, collectionType) }) { states ->
                        // 防止意外情况出现了相同的 list key, 也就是相同的数据源的同一剧集缓存.
                        // 就算出现了 duplicated key, 这两个 item 对应的 cache 是同一个引用.
                        states.toList().distinctBy { it.listItemKey }
                    }

                combine(entriesFlow, collectionType, displayInfo) { entries, type, info ->
                    CacheGroupState(
                        subjectId = subjectId.toInt(),
                        subjectName = info?.displayName
                            ?: caches.first().cache.metadata.run { subjectNameCN ?: subjectNames.firstOrNull() ?: "" },
                        entries = entries,
                        collectionType = type,
                        imageUrl = info?.imageLarge ?: staticSubjectImageLargeUrl(subjectId.toInt()),
                        totalEpisodeCount = info?.totalEpisodes?.takeIf { it > 0 },
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
