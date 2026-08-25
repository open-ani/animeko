/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.subject

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.nameCnOrName
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.danmaku.DanmakuRepository
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.media.cache.DeleteCacheByCacheIdUseCase
import me.him188.ani.app.domain.media.cache.DeleteCacheByEpisodeIdUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.requester.CacheRequestStage
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequest
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequester
import me.him188.ani.app.domain.media.cache.requester.EpisodeCacheRequesterImpl
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.eventHandling
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.allCachesWithEngineFlow
import me.him188.ani.app.ui.cache.components.createCacheEpisodeStateFlow
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.CacheCreate
import me.him188.ani.utils.analytics.recordEvent
import me.him188.ani.utils.coroutines.flows.combine
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration

@Stable
interface SubjectCacheViewModel {
    val subjectId: Int
    val subjectTitle: String?

    val mediaSelectorSettingsFlow: Flow<MediaSelectorSettings>

    /**
     * 单个条目的缓存管理页面的状态
     */
    val cacheListState: EpisodeCacheListState

    val mediaSourceInfoProvider: MediaSourceInfoProvider

    /**
     * 该条目当前已有的缓存的 UI 状态列表, 按剧集序号排序.
     */
    val cacheEpisodesFlow: StateFlow<List<CacheEpisodeState>>

    fun pauseCache(cache: CacheEpisodeState)
    fun resumeCache(cache: CacheEpisodeState)
    fun deleteCache(cache: CacheEpisodeState)

    /**
     * 暂停该条目的所有未完成缓存.
     */
    fun pauseAllCaches()

    /**
     * 继续该条目的所有已暂停缓存.
     */
    fun resumeAllCaches()
}

@Stable
class SubjectCacheViewModelImpl(
    override val subjectId: Int,
) : AbstractViewModel(), KoinComponent, SubjectCacheViewModel {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val cacheManager: MediaCacheManager by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()
    private val subjectRelationsRepository: SubjectRelationsRepository by inject()
    private val danmakuRepository: DanmakuRepository by inject()
    private val deleteCacheByEpisodeIdUseCase: DeleteCacheByEpisodeIdUseCase by inject()
    private val deleteCacheByCacheIdUseCase: DeleteCacheByCacheIdUseCase by inject()

    private val subjectInfoFlow = subjectCollectionRepository.subjectCollectionFlow(subjectId)
        .retryWithBackoffDelay()
        .shareInBackground()
    override val subjectTitle by subjectInfoFlow.map { it.subjectInfo.nameCnOrName }.produceState(null)
    override val mediaSelectorSettingsFlow: Flow<MediaSelectorSettings> get() = settingsRepository.mediaSelectorSettings.flow

    private val episodeCollectionsFlow =
        episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
            .retryWithBackoffDelay()
            .shareInBackground()

    private val episodesFlow =
        episodeCollectionsFlow.take(1).combineTransform(subjectInfoFlow) { episodes, subjectInfo ->
            supervisorScope {
                // 每个 episode 都为一个 flow, 然后合并
                emit(
                    episodes.map { episodeCollection ->
                        val episode = episodeCollection.episodeInfo

                        val cacheStatusFlow = cacheManager.cacheStatusForEpisode(subjectId, episode.episodeId)

                        val cacheRequester = EpisodeCacheRequester(
                            mediaSourceManager.mediaFetcher,
                            MediaSelectorFactory.withKoin(),
                            storagesLazy = cacheManager.enabledStorages,
                        )
                        EpisodeCacheState(
                            episodeId = episode.episodeId,
                            cacheRequester = cacheRequester,
                            currentStageState = cacheRequester.stage.produceState(scope = this),
                            infoState = stateOf(
                                EpisodeCacheInfo(
                                    sort = episode.sort,
                                    ep = episode.ep,
                                    title = episode.displayName,
                                    watchStatus = episodeCollection.collectionType,
                                    hasPublished = episode.isKnownCompleted(subjectInfo.recurrence),
                                ),
                            ),
                            cacheStatusState = cacheStatusFlow.produceState(null, this),
                            backgroundScope = this,
                        )
                    },
                )
            }
        }.shareInBackground()

    /**
     * 单个条目的缓存管理页面的状态
     */
    override val cacheListState: EpisodeCacheListState = EpisodeCacheListStateImpl(
        episodes = episodesFlow.produceState(emptyList()),
        currentEpisode = episodesFlow.flatMapLatest { episodes ->
            combine(
                episodes.map { episodeCacheState ->
                    episodeCacheState.cacheRequester.stage.map { episodeCacheState to it }
                },
            ) { results ->
                results.firstOrNull { (_, stage) ->
                    stage is CacheRequestStage.Working
                }?.first
            }
        }.produceState(null),
        onRequestCache = { episode, autoSelectByCached ->
            val subjectInfo = subjectInfoFlow.first().subjectInfo
            episode.cacheRequester.request(
                EpisodeCacheRequest(
                    subjectInfo,
                    episodeCollectionRepository.episodeCollectionInfoFlow(subjectInfo.subjectId, episode.episodeId)
                        .map { it.episodeInfo }.first(),
                ),
            ).run {
                if (autoSelectByCached) {
                    tryAutoSelectByCachedSeason(
                        cacheManager.listCacheForSubject(subjectId).first(),
                    )
                } else this
            }
        },
        onRequestCacheComplete = { target ->
            val episodeInfo = episodeCollectionsFlow.first().firstOrNull { it.episodeId == target.episode.episodeId }
                ?: error(
                    "Episode ${target.episode} not found from episodes: ${
                        episodeCollectionsFlow.first().joinToString { it.episodeId.toString() }
                    }",
                )

            val cache = target.storage.cache(
                target.media, target.metadata,
                episodeInfo.episodeInfo.toEpisodeMetadata(),
            )
            danmakuRepository.cacheDanmakuIfNeeded(target.toDanmakuFetchRequest(cache))
            Analytics.recordEvent(CacheCreate) {
                val subjectInfo = subjectInfoFlow.first()
                put("subject_id", subjectInfo.subjectId)
                put("episode_id", episodeInfo.episodeId)
                put(
                    "media_source_name",
                    when (target.media.kind) {
                        MediaSourceKind.WEB -> "web"
                        MediaSourceKind.BitTorrent -> "bt"
                        MediaSourceKind.LocalCache -> null // impossible
                    },
                )
            }
        },
        onDeleteCache = { episode ->
            deleteCacheByEpisodeIdUseCase(subjectId, episode.episodeId)
        },
    )
    override val mediaSourceInfoProvider: MediaSourceInfoProvider = MediaSourceInfoProvider(
        getSourceInfoFlow = {
            mediaSourceManager.infoFlowByMediaSourceId(it)
        },
    )

    override val cacheEpisodesFlow: StateFlow<List<CacheEpisodeState>> = cacheManager.enabledStorages
        .allCachesWithEngineFlow()
        .map { list -> list.filter { it.cache.metadata.subjectId.toIntOrNull() == subjectId } }
        // 其他条目的缓存变化不应重建本条目的所有行状态 (会重置下载速度统计)
        .distinctUntilChanged()
        .transformLatest { caches ->
            if (caches.isEmpty()) {
                emit(emptyList())
                return@transformLatest
            }
            val collectionType = subjectCollectionRepository.getSubjectCollectionTypeOffline(subjectId)
                .onStart { emit(UnifiedCollectionType.NOT_COLLECTED) }
            emitAll(
                combine(
                    caches.map { createCacheEpisodeStateFlow(subjectId.toString(), it, collectionType) },
                ) { states ->
                    states.toList()
                        .distinctBy { it.listItemKey }
                        .sortedBy { it.sort }
                },
            )
        }
        .stateInBackground(emptyList())

    override fun pauseCache(cache: CacheEpisodeState) {
        launchInBackground {
            cacheManager.findFirstCache { it.cacheId == cache.cacheId }?.pause()
        }
    }

    override fun resumeCache(cache: CacheEpisodeState) {
        launchInBackground {
            cacheManager.findFirstCache { it.cacheId == cache.cacheId }?.resume()
        }
    }

    override fun deleteCache(cache: CacheEpisodeState) {
        launchInBackground {
            deleteCacheByCacheIdUseCase(cache.subjectId, cache.episodeId, cache.cacheId)
        }
    }

    override fun pauseAllCaches() {
        launchInBackground {
            cacheManager.listCacheForSubject(subjectId).first().forEach { cache ->
                // 只暂停下载中的. 尤其不能对已完成的缓存调用 pause, 否则 web 缓存会被持久化为暂停态而无法播放.
                if (cache.state.first() == MediaCacheState.IN_PROGRESS) {
                    cache.pause()
                }
            }
        }
    }

    override fun resumeAllCaches() {
        launchInBackground {
            cacheManager.listCacheForSubject(subjectId).first().forEach { cache ->
                if (cache.state.first() == MediaCacheState.PAUSED) {
                    cache.resume()
                }
            }
        }
    }

    init {
        launchInBackground {
            val firstWorkingEpisode = episodesFlow
                .flatMapLatest { list ->
                    list.map { state -> state.cacheRequester.stage.map { state to it } }
                        .combine {
                            it.firstNotNullOfOrNull { (state, stage) ->
                                if (stage is CacheRequestStage.Working || stage is CacheRequestStage.Done) {
                                    state
                                } else null
                            }
                        }
                }

            firstWorkingEpisode
                .mapLatest { episodeCacheState ->
                    episodeCacheState ?: return@mapLatest

                    // 请求缓存下一集时会 cancel 这个 scope
                    coroutineScope {
                        var job: Job? = null
                        episodeCacheState.cacheRequester.stage.collect { stage ->
                            when (stage) {
                                is EpisodeCacheRequesterImpl.SelectMedia -> {
                                    job?.cancel()
                                    job = null
                                    job = launch {
                                        stage.mediaSelector.eventHandling.savePreferenceOnSelect {
                                            episodePreferencesRepository.setMediaPreference(subjectId, it)
                                        }
                                    }
                                }

                                is EpisodeCacheRequesterImpl.SelectStorage -> {}
                                is CacheRequestStage.Done -> {} // SelectMedia 之后可能会立即到 Done, 还没来得及保存, 所以不能 cancel job
                                CacheRequestStage.Idle -> {
                                }
                            }
                        }
                    }
                }.collect()
        }
    }

    private suspend fun EpisodeCacheTargetInfo.toDanmakuFetchRequest(cache: MediaCache): DanmakuFetchRequest {
        return DanmakuFetchRequest(
            subjectId = request.subjectInfo.subjectId,
            subjectPrimaryName = request.subjectInfo.displayName,
            subjectNames = request.subjectInfo.allNames,
            subjectPublishDate = request.subjectInfo.airDate,
            episodeId = request.episodeInfo.episodeId,
            episodeSort = request.episodeInfo.sort,
            episodeEp = request.episodeInfo.ep,
            episodeName = request.episodeInfo.displayName,
            filename = media.originalTitle,
            fileSize = cache.fileStats.first().totalSize.inBytes,
            fileHash = null,
            videoDuration = Duration.ZERO,
        )
    }
}
