/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.media.selector.autoSelect
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

interface MediaAutoCacheService {
    suspend fun checkCache()

    fun startRegularCheck(scope: CoroutineScope)
}

fun DefaultMediaAutoCacheService.Companion.createWithKoin(
    koin: Koin = KoinPlatform.getKoin()
): DefaultMediaAutoCacheService = DefaultMediaAutoCacheService(
    mediaFetcherLazy = koin.get<MediaSourceManager>().mediaFetcher,
    mediaSelectorFactory = MediaSelectorFactory.withKoin(koin),
    subjectCollections = { settings ->
        koin.get<SubjectCollectionRepository>()
            .mostRecentlyUpdatedSubjectCollectionsFlow(settings.mostRecentCount, listOf(UnifiedCollectionType.DOING))
            .first()
    },
    configLazy = flow {
        emitAll(koin.get<SettingsRepository>().mediaCacheSettings.flow)
    },
    epsNotCached = {
        koin.get<EpisodeCollectionRepository>().subjectEpisodeCollectionInfosFlow(it).first()
    },
    cacheManager = koin.inject(),
    targetStorage = flow {
        emit(koin.get<MediaCacheManager>())
    }.flatMapLatest { manager ->
        manager.enabledStorages.mapNotNull { it.firstOrNull() }
    },
)

// TODO: refactor the shit DefaultMediaAutoCacheService
class DefaultMediaAutoCacheService(
    private val mediaFetcherLazy: Flow<MediaFetcher>,
    private val mediaSelectorFactory: MediaSelectorFactory,
    /**
     * Emits list of subjects to be considered caching. 通常是 "在看" 分类的. 只需要前几个 (根据配置 [MediaCacheSettings.mostRecentOnly]).
     */
    private val subjectCollections: suspend (MediaCacheSettings) -> List<SubjectCollectionInfo>,
    private val configLazy: Flow<MediaCacheSettings>,
    private val epsNotCached: suspend (subjectId: Int) -> List<EpisodeCollectionInfo>,
    /**
     * Used to query if a episode already has a cache.
     */
    cacheManager: Lazy<MediaCacheManager>,
    /**
     * Target storage to make caches to. It must be managed by the [MediaCacheManager].
     */
    private val targetStorage: Flow<MediaCacheStorage>,
) : MediaAutoCacheService {
    private val cacheManager by cacheManager

    override suspend fun checkCache() {
        logger.info { "DefaultMediaAutoCacheService.checkCache: start" }

        val config = configLazy.first()
        val collections = subjectCollections(config).run {
            if (config.mostRecentOnly) {
                take(config.mostRecentCount)
            } else this
        }

        logger.info { "checkCache: checking ${collections.size} subjects" }

        for (subject in collections) {
            val firstUnwatched = firstEpisodeToCache(
                eps = epsNotCached(subject.subjectId),
                recurrence = subject.recurrence,
                hasAlreadyCached = {
                    cacheManager.cacheStatusForEpisode(subject.subjectId, it.episodeInfo.episodeId)
                        .firstOrNull() != EpisodeCacheStatus.NotCached
                },
                maxCount = config.maxCountPerSubject,
            ).firstOrNull() ?: continue // 都看过了

            logger.info { "Caching ${subject.debugName()} ${firstUnwatched.episodeInfo.name}" }
            createCache(subject, firstUnwatched)
            logger.info { "Completed creating cache for ${subject.debugName()} ${firstUnwatched.episodeInfo.name}, delay 1 min" }

            delay(1.minutes) // don't fetch too fast from sources
        }

        logger.info { "DefaultMediaAutoCacheService.checkCache: all ${collections.size} subjects checked" }
    }

    private suspend fun createCache(
        subject: SubjectCollectionInfo,
        firstUnwatched: EpisodeCollectionInfo
    ) {
        val fetcher = mediaFetcherLazy.first()
        val fetchSession = fetcher.newSession(
            MediaFetchRequest.create(
                subject.subjectInfo,
                firstUnwatched.episodeInfo,
            ),
        )
        val selector = mediaSelectorFactory.create(
            subject.subjectId,
            episodeId = firstUnwatched.episodeInfo.episodeId,
            fetchSession.cumulativeResults,
        )
        val selected = selector.autoSelect.awaitCompletedAndSelectDefault(fetchSession)
        if (selected == null) {
            logger.info { "No media selected for ${subject.debugName()} ${firstUnwatched.episodeInfo.name}" }
            return
        }
        val cache = targetStorage.first().cache(
            selected, MediaCacheMetadata(fetchSession.request.first()),
            firstUnwatched.episodeInfo.toEpisodeMetadata(),
        )
        logger.info { "Created cache '${cache.cacheId}' for ${subject.debugName()} ${firstUnwatched.episodeInfo.name}" }
    }

    override fun startRegularCheck(scope: CoroutineScope) {
        scope.launch(CoroutineName("MediaAutoCacheService.startRegularCheck")) {
            while (true) {
                val config = configLazy.first()
                if (!config.enabled) {
                    delay(1.hours)
                    continue
                }
                try {
                    checkCache()
                } catch (e: Throwable) {
                    logger.error(e) { "Failed to do regular cache check" }
                }
                delay(1.hours)
            }
        }
    }

    private fun SubjectCollectionInfo.debugName() = subjectInfo.displayName

    companion object {
        private val logger = logger<DefaultMediaAutoCacheService>()

        /**
         */
        // public for testing
        fun firstEpisodeToCache(
            eps: List<EpisodeCollectionInfo>,
            recurrence: SubjectRecurrence?,
            hasAlreadyCached: suspend (EpisodeCollectionInfo) -> Boolean,
            maxCount: Int = Int.MAX_VALUE,
        ): Flow<EpisodeCollectionInfo> {
            var cachedCount = 0
            return eps
                .asSequence()
                .takeWhile { it.episodeInfo.isKnownCompleted(recurrence) }
                .dropWhile {
                    it.collectionType.isDoneOrDropped() // 已经看过的不考虑
                }
                .run {
                    val seq = this
                    flow {
                        for (it in seq) {
                            if (cachedCount >= maxCount) { // 已经缓存了足够多的
                                break
                            }

                            if (!hasAlreadyCached(it)) {
                                emit(it)
                            }
                            cachedCount++
                        }
                    }
                }
        }
    }
}