/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.configIfEnabledOrNull
import me.him188.ani.app.data.network.AniSubjectRelationIndexService
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.BangumiBangumiCommentServiceImpl
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.network.BangumiEpisodeService
import me.him188.ani.app.data.network.BangumiEpisodeServiceImpl
import me.him188.ani.app.data.network.BangumiProfileService
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.network.BangumiSubjectSearchService
import me.him188.ani.app.data.network.BangumiSubjectService
import me.him188.ani.app.data.network.RemoteBangumiSubjectService
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createDatabaseBuilder
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.RepositoryUsernameProvider
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepositoryImpl
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepository
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepositoryImpl
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.media.MikanIndexCacheRepository
import me.him188.ani.app.data.repository.media.MikanIndexCacheRepositoryImpl
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepositoryImpl
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.EpisodeScreenshotRepository
import me.him188.ani.app.data.repository.player.WhatslinkEpisodeScreenshotRepository
import me.him188.ani.app.data.repository.repositoryModules
import me.him188.ani.app.data.repository.subject.BangumiSubjectSearchCompletionRepository
import me.him188.ani.app.data.repository.subject.DefaultSubjectRelationsRepository
import me.him188.ani.app.data.repository.subject.FollowedSubjectsRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepositoryImpl
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.data.repository.torrent.peer.PeerFilterSubscriptionRepository
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenRepositoryImpl
import me.him188.ani.app.domain.danmaku.DanmakuManager
import me.him188.ani.app.domain.danmaku.DanmakuManagerImpl
import me.him188.ani.app.domain.media.cache.DefaultMediaAutoCacheService
import me.him188.ani.app.domain.media.cache.MediaAutoCacheService
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.MediaCacheManagerImpl
import me.him188.ani.app.domain.media.cache.createWithKoin
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.DirectoryMediaCacheStorage
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManagerImpl
import me.him188.ani.app.domain.media.fetch.toClientProxyConfig
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionRequesterImpl
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater
import me.him188.ani.app.domain.session.AniAuthClient
import me.him188.ani.app.domain.session.BangumiSessionManager
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStatus
import me.him188.ani.app.domain.session.finalState
import me.him188.ani.app.domain.session.unverifiedAccessToken
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.domain.update.UpdateManager
import me.him188.ani.app.domain.usecase.useCaseModules
import me.him188.ani.app.ui.subject.details.state.DefaultSubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.DelegateBangumiClient
import me.him188.ani.datasources.bangumi.createBangumiClient
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.childScopeContext
import me.him188.ani.utils.coroutines.onReplacement
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.proxy
import me.him188.ani.utils.ktor.registerLogging
import me.him188.ani.utils.ktor.userAgent
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes

private val Scope.client get() = get<BangumiClient>()
private val Scope.database get() = get<AniDatabase>()
private val Scope.settingsRepository get() = get<SettingsRepository>()


fun KoinApplication.getCommonKoinModule(getContext: () -> Context, coroutineScope: CoroutineScope) =
    listOf(useCaseModules(), repositoryModules(), otherModules(getContext, coroutineScope))

private fun KoinApplication.otherModules(getContext: () -> Context, coroutineScope: CoroutineScope) = module {
    // Repositories
    single<AniAuthClient> {
        val settings = get<SettingsRepository>()
        AniAuthClient(
            settings.proxySettings.flow.map { it.default }.map { proxySettings ->
                proxySettings.toClientProxyConfig()
            },
            parentCoroutineContext = coroutineScope.coroutineContext,
        )
    }
    single<TokenRepository> { TokenRepositoryImpl(getContext().dataStores.tokenStore) }
    single<EpisodePreferencesRepository> { EpisodePreferencesRepositoryImpl(getContext().dataStores.preferredAllianceStore) }
    single<SessionManager> { BangumiSessionManager(koin, coroutineScope.coroutineContext) }
    single<BangumiClient> {
        val settings = get<SettingsRepository>()
        val sessionManager by inject<SessionManager>()
        DelegateBangumiClient(
            settings.proxySettings.flow.map { it.default }.map { proxySettings ->
                createBangumiClient(
                    @OptIn(OpaqueSession::class)
                    sessionManager.unverifiedAccessToken,
                    proxySettings.toClientProxyConfig(),
                    coroutineScope.coroutineContext,
                    userAgent = getAniUserAgent(currentAniBuildConfig.versionName),
                )
            }.onReplacement {
                it.close()
            }.shareIn(coroutineScope, started = SharingStarted.Lazily, replay = 1),
        )
    }

    single<RepositoryUsernameProvider> {
        RepositoryUsernameProvider {
            when (val finalState = get<SessionManager>().finalState.first()) {
                SessionStatus.Guest,
                SessionStatus.Expired,
                SessionStatus.NoToken -> throw RepositoryAuthorizationException()

                SessionStatus.NetworkError -> throw RepositoryNetworkException()
                SessionStatus.ServiceUnavailable -> throw RepositoryServiceUnavailableException()
                is SessionStatus.Verified -> finalState.userInfo.username
                    ?: throw IllegalStateException("RepositoryUsernameProvider: Username is null")
            }
        }
    }
    single<SubjectCollectionRepository> {
        SubjectCollectionRepositoryImpl(
            api = suspend { client.getApi() }.asFlow(),
            bangumiSubjectService = get(),
            subjectCollectionDao = database.subjectCollection(),
//            characterDao = database.character(),
//            characterActorDao = database.characterActor(),
//            personDao = database.person(),
//            subjectCharacterRelationDao = database.subjectCharacterRelation(),
//            subjectPersonRelationDao = database.subjectPersonRelation(),
            subjectRelationsDao = database.subjectRelations(),
            episodeCollectionRepository = get(),
            animeScheduleRepository = get(),
            bangumiEpisodeService = get(),
            episodeCollectionDao = database.episodeCollection(),
            sessionManager = get(),
            nsfwModeSettingsFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode },
            enableAllEpisodeTypes = settingsRepository.debugSettings.flow.map { it.showAllEpisodes },
        )
    }
    single<FollowedSubjectsRepository> {
        FollowedSubjectsRepository(
            subjectCollectionRepository = get(),
            animeScheduleRepository = get(),
            episodeCollectionRepository = get(),
            settingsRepository = get(),
        )
    }
    single<BangumiSubjectSearchService> {
        BangumiSubjectSearchService(
            searchApi = suspend { client.getSearchApi() }.asFlow(),
        )
    }
    single<SubjectSearchRepository> {
        SubjectSearchRepository(
            bangumiSubjectSearchService = get(),
            subjectService = get(),
        )
    }
    single<BangumiSubjectSearchCompletionRepository> {
        BangumiSubjectSearchCompletionRepository(
            bangumiSubjectSearchService = get(),
            settingsRepository = get(),
        )
    }
    single<SubjectSearchHistoryRepository> {
        SubjectSearchHistoryRepository(database.searchHistory(), database.searchTag())
    }
    single<SubjectRelationsRepository> {
        DefaultSubjectRelationsRepository(
            database.subjectCollection(),
            database.subjectRelations(),
            bangumiSubjectService = get(),
            subjectCollectionRepository = get(),
            aniSubjectRelationIndexService = get(),
        )
    }

    // Data layer network services
    single<BangumiSubjectService> {
        RemoteBangumiSubjectService(
            client,
            suspend { client.getApi() }.asFlow(),
            sessionManager = get(),
            usernameProvider = get(),
        )
    }
    single<BangumiEpisodeService> { BangumiEpisodeServiceImpl() }

    single<BangumiRelatedPeopleService> { BangumiRelatedPeopleService(get()) }
    single<AnimeScheduleRepository> { AnimeScheduleRepository(get()) }
    single<BangumiCommentRepository> {
        BangumiCommentRepository(
            get(),
            database.episodeCommentDao(),
            database.subjectReviews(),
        )
    }
    single<EpisodeCollectionRepository> {
        EpisodeCollectionRepository(
            subjectDao = database.subjectCollection(),
            episodeCollectionDao = database.episodeCollection(),
            bangumiEpisodeService = get(),
            animeScheduleRepository = get(),
            subjectCollectionRepository = inject(),
            enableAllEpisodeTypes = settingsRepository.debugSettings.flow.map { it.showAllEpisodes },
        )
    }
    single<EpisodeProgressRepository> {
        EpisodeProgressRepository(
            episodeCollectionRepository = get(),
            cacheManager = get(),
        )
    }
    single<EpisodeScreenshotRepository> { WhatslinkEpisodeScreenshotRepository() }
    single<BangumiCommentService> { BangumiBangumiCommentServiceImpl(get()) }
    single<MediaSourceInstanceRepository> {
        MediaSourceInstanceRepositoryImpl(getContext().dataStores.mediaSourceSaveStore)
    }
    single<MediaSourceSubscriptionRepository> {
        MediaSourceSubscriptionRepository(getContext().dataStores.mediaSourceSubscriptionStore)
    }
    single<EpisodePlayHistoryRepository> {
        EpisodePlayHistoryRepository(getContext().dataStores.episodeHistoryStore)
    }
    single<AniSubjectRelationIndexService> {
        AniSubjectRelationIndexService(lazy { get<AniAuthClient>().subjectRelationsApi })
    }
    single<PeerFilterSubscriptionRepository> {
        val settings = get<SettingsRepository>()
        // TODO: extract client?
        val client = settings.proxySettings.flow.map { it.default }.map { proxySettings ->
            createDefaultHttpClient {
                userAgent(getAniUserAgent())
                proxy(proxySettings.configIfEnabledOrNull?.toClientProxyConfig())
                expectSuccess = true
            }.apply {
                registerLogging(logger<MediaSourceSubscriptionUpdater>())
            }
        }.onReplacement {
            it.close()
        }.shareIn(coroutineScope, started = SharingStarted.Lazily, replay = 1)

        PeerFilterSubscriptionRepository(
            dataStore = getContext().dataStores.peerFilterSubscriptionStore,
            ruleSaveDir = getContext().files.dataDir.resolve("peerfilter-subs"),
            httpClient = client,
        )
    }
    single<BangumiProfileService> { BangumiProfileService() }
    single<AnimeScheduleService> { AnimeScheduleService(lazy { get<AniAuthClient>().scheduleApi }) }
    single<TrendsRepository> { TrendsRepository(lazy { get<AniAuthClient>().trendsApi }) }

    single<DanmakuManager> {
        DanmakuManagerImpl(
            parentCoroutineContext = coroutineScope.coroutineContext,
        )
    }
    single<UpdateManager> {
        UpdateManager(
            saveDir = getContext().files.cacheDir.resolve("updates/download"),
        )
    }
    single<SettingsRepository> { PreferencesRepositoryImpl(getContext().dataStores.preferencesStore) }
    single<DanmakuRegexFilterRepository> { DanmakuRegexFilterRepositoryImpl(getContext().dataStores.danmakuFilterStore) }
    single<MikanIndexCacheRepository> { MikanIndexCacheRepositoryImpl(getContext().dataStores.mikanIndexStore) }

    single<AniDatabase> {
        getContext().createDatabaseBuilder()
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO_)
            .build()
    }

    // Media
    single<MediaCacheManager> {
        val id = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID

        fun getMediaMetadataDir(engineId: String) = getContext().files.dataDir
            .resolve("media-cache").resolve(engineId)

        val engines = get<TorrentManager>().engines
        MediaCacheManagerImpl(
            storagesIncludingDisabled = buildList(capacity = engines.size) {
                if (currentAniBuildConfig.isDebug) {
                    // 注意, 这个必须要在第一个, 见 [DefaultTorrentManager.engines] 注释
                    add(
                        DirectoryMediaCacheStorage(
                            mediaSourceId = "test-in-memory",
                            metadataDir = getMediaMetadataDir("test-in-memory"),
                            engine = DummyMediaCacheEngine("test-in-memory"),
                            coroutineScope.childScopeContext(),
                        ),
                    )
                }
                for (engine in engines) {
                    add(
                        DirectoryMediaCacheStorage(
                            mediaSourceId = id,
                            metadataDir = getMediaMetadataDir(engine.type.id),
                            engine = TorrentMediaCacheEngine(
                                mediaSourceId = id,
                                torrentEngine = engine,
                            ),
                            coroutineScope.childScopeContext(),
                        ),
                    )
                }
            },
            backgroundScope = coroutineScope.childScope(),
        )
    }


    single<MediaSourceCodecManager> {
        MediaSourceCodecManager()
    }
    single<MediaSourceManager> {
        MediaSourceManagerImpl(
            additionalSources = {
                get<MediaCacheManager>().storagesIncludingDisabled.map { it.cacheMediaSource }
            },
        )
    }
    single<MediaSourceSubscriptionUpdater> {
        val settings = get<SettingsRepository>()
        val client = settings.proxySettings.flow.map { it.default }.map { proxySettings ->
            createDefaultHttpClient {
                userAgent(getAniUserAgent())
                proxy(proxySettings.configIfEnabledOrNull?.toClientProxyConfig())
                expectSuccess = true
            }.apply {
                registerLogging(logger<MediaSourceSubscriptionUpdater>())
            }
        }.onReplacement {
            it.close()
        }.shareIn(coroutineScope, started = SharingStarted.Lazily, replay = 1)
        MediaSourceSubscriptionUpdater(
            get<MediaSourceSubscriptionRepository>(),
            get<MediaSourceManager>(),
            get<MediaSourceCodecManager>(),
            requester = MediaSourceSubscriptionRequesterImpl(client),
        )
    }
    single<SelectorMediaSourceEpisodeCacheRepository> {
        SelectorMediaSourceEpisodeCacheRepository(
            webSubjectInfoDao = database.webSearchSubjectInfoDao(),
            webEpisodeInfoDao = database.webSearchEpisodeInfoDao(),
        )
    }

    // Caching

    single<MediaAutoCacheService> {
        DefaultMediaAutoCacheService.createWithKoin()
    }

    single<MeteredNetworkDetector> { createMeteredNetworkDetector(getContext()) }
    single<SubjectDetailsStateFactory> { DefaultSubjectDetailsStateFactory(coroutineScope.coroutineContext) }
}


/**
 * 会在非 preview 环境调用. 用来初始化一些模块
 */
fun KoinApplication.startCommonKoinModule(coroutineScope: CoroutineScope): KoinApplication {
    koin.get<MediaAutoCacheService>().startRegularCheck(coroutineScope)

    coroutineScope.launch {
        val manager = koin.get<MediaCacheManager>()
        for (storage in manager.storages) {
            storage.first()?.restorePersistedCaches()
        }
    }

    coroutineScope.launch {
        val subscriptionUpdater = koin.get<MediaSourceSubscriptionUpdater>()
        while (currentCoroutineContext().isActive) {
            val nextDelay = subscriptionUpdater.updateAllOutdated()
            delay(nextDelay.coerceAtLeast(1.minutes))
        }
    }

    coroutineScope.launch {
        // TODO: 这里是自动删除旧版数据源. 在未来 3.14 左右就可以去除这个了
        val removedFactoryIds = setOf("ntdm", "mxdongman", "nyafun", "gugufan", "xfdm", "acg.rip")
        val manager = koin.get<MediaSourceInstanceRepository>()
        for (instance in manager.flow.first()) {
            if (instance.factoryId.value in removedFactoryIds) {
                manager.remove(instanceId = instance.instanceId)
            }
        }
    }

    coroutineScope.launch {
        val peerFilterRepo = koin.get<PeerFilterSubscriptionRepository>()
        peerFilterRepo.loadOrUpdateAll()
    }

    return this
}


fun createAppRootCoroutineScope(): CoroutineScope {
    val logger = logger("ani-root")
    return CoroutineScope(
        CoroutineExceptionHandler { coroutineContext, throwable ->
            logger.warn(throwable) {
                "Uncaught exception in coroutine $coroutineContext"
            }
        } + SupervisorJob() + Dispatchers.Default,
    )
}
