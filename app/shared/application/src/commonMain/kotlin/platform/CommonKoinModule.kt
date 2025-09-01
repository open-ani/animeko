/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.data.network.AniSubjectRelationIndexService
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.BangumiBangumiCommentServiceImpl
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.network.BangumiFullSyncStateResolver
import me.him188.ani.app.data.network.BangumiProfileService
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.network.BangumiSubjectSearchService
import me.him188.ani.app.data.network.EpisodeService
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.network.RecommendationRepository
import me.him188.ani.app.data.network.RemoteSubjectService
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.createDatabaseBuilder
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
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
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
import me.him188.ani.app.domain.comment.TurnstileState
import me.him188.ani.app.domain.danmaku.DanmakuManager
import me.him188.ani.app.domain.foundation.ConvertSendCountExceedExceptionFeature
import me.him188.ani.app.domain.foundation.ConvertSendCountExceedExceptionFeatureHandler
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider.HoldingInstanceMatrix
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.ServerListFeature
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.app.domain.foundation.ServerListFeatureHandler
import me.him188.ani.app.domain.foundation.UseAniTokenFeatureHandler
import me.him188.ani.app.domain.foundation.UserAgentFeature
import me.him188.ani.app.domain.foundation.UserAgentFeatureHandler
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.MediaCacheManagerImpl
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.DataStoreMediaCacheStorage
import me.him188.ani.app.domain.media.cache.storage.MediaCacheMigrator
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManagerImpl
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionRequesterImpl
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater
import me.him188.ani.app.domain.session.AniSessionRefresher
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.domain.settings.SettingsBasedProxyProvider
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.domain.update.UpdateManager
import me.him188.ani.app.domain.usecase.useCaseModules
import me.him188.ani.app.ui.subject.details.state.DefaultSubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.BangumiClientImpl
import me.him188.ani.datasources.bangumi.turnstileBaseUrl
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.childScopeContext
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.httpdownloader.KtorPersistentHttpDownloader
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes
import me.him188.ani.app.ui.comment.TurnstileState as CreateTurnstileState

private val Scope.client get() = get<BangumiClient>()
private val Scope.database get() = get<AniDatabase>()
private val Scope.settingsRepository get() = get<SettingsRepository>()
private val Scope.aniApiProvider get() = get<AniApiProvider>()

fun KoinApplication.getCommonKoinModule(getContext: () -> Context, coroutineScope: CoroutineScope) =
    listOf(useCaseModules(), repositoryModules(getContext().dataStores), otherModules(getContext, coroutineScope))

private fun KoinApplication.otherModules(getContext: () -> Context, coroutineScope: CoroutineScope) = module {
    // Repositories
    single<ProxyProvider> { SettingsBasedProxyProvider(get(), coroutineScope) }
    single<SessionManager> {
        SessionManager(
            tokenRepository = get(),
            coroutineScope = coroutineScope,
            refreshSession = AniSessionRefresher { aniApiProvider.userAuthApi },
        )
    }
    single<SessionStateProvider> {
        get<SessionManager>().stateProvider
    }
    single<HttpClientProvider> {
        val sessionManager by inject<SessionManager>()
        DefaultHttpClientProvider(
            get(), coroutineScope,
            featureHandlers = listOf(
                UserAgentFeatureHandler,
                UseAniTokenFeatureHandler(
                    sessionManager.accessTokenSessionFlow.map {
                        it?.tokens?.aniAccessToken
                    },
                    onRefresh = { null },
                ),
                ServerListFeatureHandler(
                    settingsRepository.danmakuSettings.flow.map { danmakuSettings ->
                        when (danmakuSettings.useGlobal) {
                            true -> {
                                AniServers.optimizedForGlobal
                            }

                            false -> {
                                AniServers.optimizedForCN
                            }

                            null -> {
                                // 根据时区推断
                                if (AniServers.shouldUseGlobalServer()) {
                                    AniServers.optimizedForGlobal
                                } else {
                                    AniServers.optimizedForCN
                                }
                            }
                        }
                    },
                ),
                ConvertSendCountExceedExceptionFeatureHandler,
            ),
        )
    }
    single<AniApiProvider> { AniApiProvider(get<HttpClientProvider>().get(useAniToken = true)) }
    single<TokenRepository> { TokenRepository(getContext().dataStores.tokenStore) }
    single<EpisodePreferencesRepository> { EpisodePreferencesRepositoryImpl(getContext().dataStores.preferredAllianceStore) }
    single<BangumiClient> {
        BangumiClientImpl(
            get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.ANI,
            ),
        )
    }

    single<SubjectCollectionRepository> {
        SubjectCollectionRepositoryImpl(
            api = client.api,
            subjectService = get(),
            subjectCollectionDao = database.subjectCollection(),
//            characterDao = database.character(),
//            characterActorDao = database.characterActor(),
//            personDao = database.person(),
//            subjectCharacterRelationDao = database.subjectCharacterRelation(),
//            subjectPersonRelationDao = database.subjectPersonRelation(),
            subjectRelationsDao = database.subjectRelations(),
            episodeCollectionRepository = get(),
            animeScheduleRepository = get(),
            episodeService = get(),
            episodeCollectionDao = database.episodeCollection(),
            sessionManager = get(),
            nsfwModeSettingsFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode },
            getEpisodeTypeFiltersUseCase = get(),
        )
    }
    single<FollowedSubjectsRepository> {
        FollowedSubjectsRepository(
            subjectCollectionRepository = get(),
            animeScheduleRepository = get(),
            episodeCollectionRepository = get(),
            settingsRepository = get(),
            sessionManager = get(),
        )
    }
    single<BangumiSubjectSearchService> {
        BangumiSubjectSearchService(
            searchApi = client.searchApi,
        )
    }
    single<SubjectSearchRepository> {
        SubjectSearchRepository(
            bangumiSubjectSearchService = get(),
            subjectCollectionRepository = get(),
            subjectService = get(),
        )
    }
    single<BangumiSubjectSearchCompletionRepository> {
        BangumiSubjectSearchCompletionRepository(
            bangumiSubjectSearchService = get(),
            subjectCollectionRepository = get(),
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
            subjectService = get(),
            subjectCollectionRepository = get(),
            aniSubjectRelationIndexService = get(),
        )
    }

    // Data layer network services
    single<SubjectService> {
        RemoteSubjectService(
            client,
            client.api,
            aniApiProvider.subjectApi,
            sessionManager = get(),
        )
    }
    single<EpisodeService> { EpisodeServiceImpl(aniApiProvider.subjectApi) }

    single<BangumiFullSyncStateResolver> {
        BangumiFullSyncStateResolver(coroutineScope.coroutineContext, get())
    }

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
            episodeService = get(),
            animeScheduleRepository = get(),
            subjectCollectionRepository = inject(),
            getEpisodeTypeFiltersUseCase = get(),
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
        EpisodePlayHistoryRepositoryImpl(getContext().dataStores.episodeHistoryStore)
    }
    single<AniSubjectRelationIndexService> {
        val provider = get<AniApiProvider>()
        AniSubjectRelationIndexService(provider.subjectRelationsApi)
    }

    single<PeerFilterSubscriptionRepository> {
        PeerFilterSubscriptionRepository(
            dataStore = getContext().dataStores.peerFilterSubscriptionStore,
            ruleSaveDir = getContext().files.dataDir.resolve("peerfilter-subs"),
            httpClient = get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI),
            builtinPeerFilterRuleApi = get<AniApiProvider>().pfRuleApi,
        )
    }
    single<BangumiProfileService> { BangumiProfileService() }
    single<AnimeScheduleService> { AnimeScheduleService(get<AniApiProvider>().scheduleApi) }
    single<TrendsRepository> { TrendsRepository(get<AniApiProvider>().trendsApi, get<BangumiClient>().nextTrendingApi) }
    single<RecommendationRepository> { RecommendationRepository(get<TrendsRepository>()) }

    single<DanmakuManager> {
        DanmakuManager(
            parentCoroutineContext = coroutineScope.coroutineContext,
            danmakuApi = aniApiProvider.danmakuApi,
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
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                startVersions = buildList {
                    addAll(1..15) // 16 is destructive
                }.toIntArray(),
            )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO_)
            .build()
    }

    single<HttpDownloader> {
        KtorPersistentHttpDownloader(
            dataStore = getContext().dataStores.m3u8DownloaderStore,
            get<HttpClientProvider>().get(),
            fileSystem = SystemFileSystem,
            baseSaveDir = get<MediaSaveDirProvider>().saveDir
                .let { Path(it).resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR) },
        )
    }

    // Media
    single<MediaCacheManager> {
        val id = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID
        val engines = get<TorrentManager>().engines
        val metadataStore = getContext().dataStores.mediaCacheMetadataStore

        MediaCacheManagerImpl(
            storagesIncludingDisabled = buildList(capacity = engines.size) {
                if (currentAniBuildConfig.isDebug) {
                    // 注意, 这个必须要在第一个, 见 [DefaultTorrentManager.engines] 注释
                    add(
                        @Suppress("DEPRECATION")
                        DataStoreMediaCacheStorage(
                            mediaSourceId = "test-in-memory",
                            store = metadataStore,
                            engine = DummyMediaCacheEngine("test-in-memory"),
                            "[debug]dummy",
                            coroutineScope.childScopeContext(),
                        ),
                    )
                }
                for (engine in engines) {
                    add(
                        @Suppress("DEPRECATION")
                        DataStoreMediaCacheStorage(
                            mediaSourceId = id,
                            store = metadataStore,
                            engine = TorrentMediaCacheEngine(
                                mediaSourceId = id,
                                engineKey = MediaCacheEngineKey(engine.type.id),
                                torrentEngine = engine,
                                engineAccess = get(),
                                mediaCacheMetadataStore = metadataStore,
                                baseSaveDirProvider = get(),
                                shareRatioLimitFlow = settingsRepository.anitorrentConfig.flow
                                    .map { it.shareRatioLimit },
                            ),
                            displayName = "本地",
                            coroutineScope.childScopeContext(),
                        ),
                    )
                }
                add(
                    @Suppress("DEPRECATION")
                    DataStoreMediaCacheStorage(
                        mediaSourceId = id,
                        store = metadataStore,
                        engine = get<HttpMediaCacheEngine>(),
                        displayName = "本地",
                        coroutineScope.childScopeContext(),
                    ),
                )
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
        val settings = koin.get<ProxyProvider>()
        val client = get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI)
        MediaSourceSubscriptionUpdater(
            get<MediaSourceSubscriptionRepository>(),
            get<MediaSourceManager>(),
            get<MediaSourceCodecManager>(),
            requester = MediaSourceSubscriptionRequesterImpl(client, get<AniApiProvider>().subscriptionApi),
        )
    }
    single<SelectorMediaSourceEpisodeCacheRepository> {
        SelectorMediaSourceEpisodeCacheRepository(
            webSubjectInfoDao = database.webSearchSubjectInfoDao(),
            webEpisodeInfoDao = database.webSearchEpisodeInfoDao(),
        )
    }

    // Caching
    single<MeteredNetworkDetector> { createMeteredNetworkDetector(getContext()) }
    single<SubjectDetailsStateFactory> { DefaultSubjectDetailsStateFactory() }

    single<TurnstileState> {
        CreateTurnstileState(
            buildString {
                append(get<BangumiClient>().turnstileBaseUrl)
                append("?redirect_uri=")
                append(TurnstileState.CALLBACK_INTERCEPTION_PREFIX)
            },
        )
    }
}

/**
 * 会在非 preview 环境调用. 用来初始化一些模块
 */
fun KoinApplication.startCommonKoinModule(
    context: Context,
    coroutineScope: CoroutineScope,
): KoinApplication {
    // Start the proxy provider very soon (before initialization of any other components)
    runBlocking {
        // We have to block here to read the saved proxy settings
        when (val proxyProvider = koin.get<HttpClientProvider>()) {
            // compile-safe type cast
            is DefaultHttpClientProvider -> proxyProvider.startProxyListening(holdingInstanceMatrixSequence())
        }
    }
    // Now, the proxy settings is ready. Other components can use http clients.

    val migrationCacheCompleted = CompletableDeferred<Unit>()

    coroutineScope.launch {
        val mediaCacheMigrator = koin.get<MediaCacheMigrator>()
        val requiresMigration = mediaCacheMigrator.startupMigrationCheckAndGetIfRequiresMigration(coroutineScope)

        // 只有不需要迁移的时候才能, 才初始化 http downloader 
        if (!requiresMigration) {
            koin.get<HttpDownloader>().init() // 这涉及读取 DownloadState, 需要在加载 storage metadata 前调用.
        }

        // 只有不需要迁移缓存时才能迁移旧 session token
        migrationCacheCompleted.complete(Unit)

        val manager = koin.get<MediaCacheManager>()
        for (storage in manager.storagesIncludingDisabled) {
            if (!requiresMigration) storage.restorePersistedCaches()
        }
    }

    @Suppress("DEPRECATION")
    coroutineScope.launch {
        migrationCacheCompleted.await()
        val migrationResult = SessionManager.migrateBangumiToken(koin)
        SessionManager.migrationResult.complete(migrationResult)
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
        peerFilterRepo.updateOrLoadAll()
    }

    // TODO: For ThemeSettings migration. Delete in the future.
    @Suppress("DEPRECATION")
    coroutineScope.launch {
        val settingsRepository = koin.get<SettingsRepository>()
        val uiSettings = settingsRepository.uiSettings
        val uiSettingsContent = uiSettings.flow.first()
        val legacyThemeSettings = uiSettingsContent.theme
        val themeSettings = settingsRepository.themeSettings

        if (legacyThemeSettings != null) {
            val newThemeSettings = ThemeSettings(
                darkMode = legacyThemeSettings.darkMode,
                useDynamicTheme = legacyThemeSettings.dynamicTheme,
            )
            themeSettings.update { newThemeSettings }
            uiSettings.update { uiSettingsContent.copy(theme = null) }
        }
    }

    koin.get<SessionManager>().startBackgroundJob()
    return this
}

/**
 * 需要一直持有的 http client 实例列表
 */
private fun holdingInstanceMatrixSequence() = sequence {
    for (userAgent in ScopedHttpClientUserAgent.entries) {
        yield(
            HoldingInstanceMatrix(
                setOf(
                    UserAgentFeature.withValue(userAgent),
                    ServerListFeature.withValue(ServerListFeatureConfig.Default),
                    ConvertSendCountExceedExceptionFeature.withValue(true),
                ),
            ),
        )
    }

    yield(
        HoldingInstanceMatrix(
            setOf(
                UserAgentFeature.withValue(ScopedHttpClientUserAgent.ANI),
                ServerListFeature.withValue(ServerListFeatureConfig.Default),
                ConvertSendCountExceedExceptionFeature.withValue(true),
            ),
        ),
    )
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
