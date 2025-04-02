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
import io.ktor.client.plugins.auth.providers.BearerTokens
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
import kotlinx.coroutines.withContext
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.app.data.models.preference.ThemeSettings
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
import me.him188.ani.app.data.network.RecommendationRepository
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
import me.him188.ani.app.data.repository.user.TokenRepositoryImpl
import me.him188.ani.app.domain.comment.TurnstileState
import me.him188.ani.app.domain.danmaku.DanmakuManager
import me.him188.ani.app.domain.danmaku.DanmakuManagerImpl
import me.him188.ani.app.domain.foundation.ConvertSendCountExceedExceptionFeature
import me.him188.ani.app.domain.foundation.ConvertSendCountExceedExceptionFeatureHandler
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider.HoldingInstanceMatrix
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.ServerListFeature
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.app.domain.foundation.ServerListFeatureHandler
import me.him188.ani.app.domain.foundation.UseBangumiTokenFeature
import me.him188.ani.app.domain.foundation.UseBangumiTokenFeatureHandler
import me.him188.ani.app.domain.foundation.UserAgentFeature
import me.him188.ani.app.domain.foundation.UserAgentFeatureHandler
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.domain.media.cache.DefaultMediaAutoCacheService
import me.him188.ani.app.domain.media.cache.MediaAutoCacheService
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.MediaCacheManagerImpl
import me.him188.ani.app.domain.media.cache.createWithKoin
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.InvalidMediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.DataStoreMediaCacheStorage
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManagerImpl
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionRequesterImpl
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater
import me.him188.ani.app.domain.session.AniApiProvider
import me.him188.ani.app.domain.session.AniAuthClient
import me.him188.ani.app.domain.session.AniAuthClientImpl
import me.him188.ani.app.domain.session.AniAuthConfigurator
import me.him188.ani.app.domain.session.AniAuthStateProvider
import me.him188.ani.app.domain.session.BangumiSessionManager
import me.him188.ani.app.domain.session.ConstantFailureAniAuthClient
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStatus
import me.him188.ani.app.domain.session.finalState
import me.him188.ani.app.domain.session.unverifiedAccessToken
import me.him188.ani.app.domain.session.unverifiedAccessTokenOrNull
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
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.isDirectory
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

/**
 * @see me.him188.ani.app.data.persistent.PlatformDataStoreManager.mediaCacheMetadataStore
 */
@Deprecated("Since 4.8, metadata is now stored in the datastore. This will be removed in the future.")
fun Context.getMediaMetadataDir(): SystemPath {
    return files.dataDir.resolve("media-cache")
}

fun KoinApplication.getCommonKoinModule(getContext: () -> Context, coroutineScope: CoroutineScope) =
    listOf(useCaseModules(), repositoryModules(), otherModules(getContext, coroutineScope))

private fun KoinApplication.otherModules(getContext: () -> Context, coroutineScope: CoroutineScope) = module {
    // Repositories
    single<ProxyProvider> { SettingsBasedProxyProvider(get(), coroutineScope) }
    single<HttpClientProvider> {
        val sessionManager by inject<SessionManager>()
        DefaultHttpClientProvider(
            get(), coroutineScope,
            featureHandlers = listOf(
                UserAgentFeatureHandler,
                UseBangumiTokenFeatureHandler(
                    @OptIn(OpaqueSession::class)
                    sessionManager.unverifiedAccessToken,
                    onRefresh = {
                        val before = sessionManager.finalState.first()
                        logger.info("Ktor believes Bangumi token is invalid. Refreshing. Current state: $before")
                        if (before !is SessionStatus.Expired) {
                            sessionManager.retry()
                        }
                        logger.info("Retry started. Now waiting for session to be verified.")
                        // `finalState.first()` wait for `retry` to complete.
                        // If `retry` succeeds, `finalState` will receive SessionStatus.Verified with a valid token.
                        sessionManager.finalState.first().unverifiedAccessTokenOrNull?.let {
                            BearerTokens(it, "")
                        }.also {
                            logger.info("Result: ${it?.accessToken}")
                        }
                    },
                ),
                ServerListFeatureHandler(
                    settingsRepository.danmakuSettings.flow.map { danmakuSettings ->
                        if (danmakuSettings.useGlobal) {
                            AniServers.optimizedForGlobal
                        } else {
                            AniServers.optimizedForCN
                        }
                    },
                ),
                ConvertSendCountExceedExceptionFeatureHandler,
            ),
        )
    }
    single<AniApiProvider> { AniApiProvider(get<HttpClientProvider>().get()) }
    single<AniAuthClient> {
        AniAuthClientImpl(get<AniApiProvider>().oauthApi)
    }
    single<TokenRepository> { TokenRepositoryImpl(getContext().dataStores.tokenStore) }
    single<EpisodePreferencesRepository> { EpisodePreferencesRepositoryImpl(getContext().dataStores.preferredAllianceStore) }
    single<SessionManager> { BangumiSessionManager(koin, coroutineScope.coroutineContext) }
    single<BangumiClient> {
        BangumiClientImpl(
            get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.ANI,
                useBangumiToken = true,
            ),
            get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.ANI,
                useBangumiToken = false,
            ),
        )
    }
    single<AniAuthStateProvider> {
        AniAuthConfigurator(
            get(),
            ConstantFailureAniAuthClient, // Read-only configurator doesn't need a auth client.
            onLaunchAuthorize = { }, // Read-only configurator doesn't need to do real authorization.
            parentCoroutineContext = coroutineScope.coroutineContext,
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
            api = client.api,
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
            searchApi = client.searchApi,
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
            client.api,
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
        EpisodePlayHistoryRepositoryImpl(getContext().dataStores.episodeHistoryStore)
    }
    single<AniSubjectRelationIndexService> {
        val provider = get<AniApiProvider>()
        AniSubjectRelationIndexService(provider.subjectRelationsApi)
    }

    single<PeerFilterSubscriptionRepository> {
        val client = koin.get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI)
        PeerFilterSubscriptionRepository(
            dataStore = getContext().dataStores.peerFilterSubscriptionStore,
            ruleSaveDir = getContext().files.dataDir.resolve("peerfilter-subs"),
            httpClient = client,
        )
    }
    single<BangumiProfileService> { BangumiProfileService() }
    single<AnimeScheduleService> { AnimeScheduleService(get<AniApiProvider>().scheduleApi) }
    single<TrendsRepository> { TrendsRepository(get<AniApiProvider>().trendsApi, get<BangumiClient>().nextTrendingApi) }
    single<RecommendationRepository> { RecommendationRepository(get<TrendsRepository>()) }

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

    single<HttpDownloader> {
        KtorPersistentHttpDownloader(
            dataStore = getContext().dataStores.m3u8DownloaderStore,
            get<HttpClientProvider>().get(),
            fileSystem = SystemFileSystem,
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
                            engine = DummyMediaCacheEngine(
                                "test-in-memory",
                                engineKey = MediaCacheEngineKey("test-in-memory"),
                            ),
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
                        engine = HttpMediaCacheEngine(
                            mediaSourceId = id,
                            downloader = get<HttpDownloader>(),
                            engineKey = MediaCacheEngineKey("web-m3u"),
                            dataDir = getContext().files.dataDir.resolve("web-m3u-cache").path,
                            mediaResolver = get<MediaResolver>(),
                        ),
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
fun KoinApplication.startCommonKoinModule(context: Context, coroutineScope: CoroutineScope): KoinApplication {
    // Start the proxy provider very soon (before initialization of any other components)
    runBlocking {
        // We have to block here to read the saved proxy settings
        when (val proxyProvider = koin.get<HttpClientProvider>()) {
            // compile-safe type cast
            is DefaultHttpClientProvider -> proxyProvider.startProxyListening(holdingInstanceMatrixSequence())
        }
    }
    // Now, the proxy settings is ready. Other components can use http clients.

    coroutineScope.launch {
        val metadataStore = context.dataStores.mediaCacheMetadataStore
        val storages = koin.get<MediaCacheManager>().storagesIncludingDisabled
        val legacyMetadataDir = context.getMediaMetadataDir()

        // Since 4.8, metadata is stored in the datastore. Migration workaround.
        // remove in 5.x
        @Suppress("DEPRECATION")
        @OptIn(InvalidMediaCacheEngineKey::class)
        withContext(Dispatchers.IO_) {
            storages
                .firstOrNull { it.engine is TorrentMediaCacheEngine }
                .let { storage ->
                    if (storage != null) {
                        val dir = legacyMetadataDir.resolve("anitorrent")
                        if (dir.exists() && dir.isDirectory()) {
                            DataStoreMediaCacheStorage.migrateMetadataFromV47(metadataStore, storage, dir)
                        }
                    }
                }
            storages
                .filterIsInstance<DataStoreMediaCacheStorage>()
                .firstOrNull { it.engine is HttpMediaCacheEngine }
                .let { storage ->
                    if (storage != null) {
                        val dir = legacyMetadataDir.resolve("web-m3u")
                        if (dir.exists() && dir.isDirectory()) {
                            DataStoreMediaCacheStorage.migrateMetadataFromV47(metadataStore, storage, dir)
                        }
                    }
                }
            // Delete the whole metadata dir
            legacyMetadataDir.deleteRecursively()
        }

        koin.get<HttpDownloader>().init() // 这涉及读取 DownloadState, 需要在加载 storage metadata 前调用.

        val manager = koin.get<MediaCacheManager>()
        for (storage in manager.storages) {
            storage.first()?.restorePersistedCaches()
        }

        koin.get<MediaAutoCacheService>().startRegularCheck(coroutineScope)
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
                    UseBangumiTokenFeature.withValue(false),
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
                UseBangumiTokenFeature.withValue(true),
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
