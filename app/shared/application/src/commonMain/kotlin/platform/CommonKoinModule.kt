/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.data.network.AniCommentReportService
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.AniPersonCommentService
import me.him188.ani.app.data.network.AniSubjectRelationIndexService
import me.him188.ani.app.data.network.AniSubjectSearchService
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.BangumiBangumiCommentServiceImpl
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.network.DefaultWatchTogetherApiService
import me.him188.ani.app.data.network.EpisodeService
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.network.RemoteSubjectService
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.network.WatchTogetherApiService
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.MIGRATION_19_20
import me.him188.ani.app.data.persistent.database.createDatabaseBuilder
import me.him188.ani.app.data.repository.media.MediaSourceSaves
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.player.PlaybackHistorySyncer
import me.him188.ani.app.data.repository.repositoryModules
import me.him188.ani.app.data.repository.torrent.peer.PeerFilterSubscriptionRepository
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.ConvertSendCountExceedExceptionFeature
import me.him188.ani.app.domain.foundation.ConvertSendCountExceedExceptionFeatureHandler
import me.him188.ani.app.domain.foundation.CookieJarFeatureHandler
import me.him188.ani.app.domain.foundation.WebSourceIdentityFeatureHandler
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider.HoldingInstanceMatrix
import me.him188.ani.app.domain.foundation.DefaultVersionExpiryService
import me.him188.ani.app.domain.foundation.DistributionChannelFeatureHandler
import me.him188.ani.app.domain.foundation.GlobalHttpEventBus
import me.him188.ani.app.domain.foundation.GlobalHttpEvents
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.ServerListFeature
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.app.domain.foundation.ServerListFeatureHandler
import me.him188.ani.app.domain.foundation.SseFeatureHandler
import me.him188.ani.app.domain.foundation.UseAniTokenFeatureHandler
import me.him188.ani.app.domain.foundation.UserAgentFeature
import me.him188.ani.app.domain.foundation.UserAgentFeatureHandler
import me.him188.ani.app.domain.foundation.VersionExpiryFeatureHandler
import me.him188.ani.app.domain.foundation.VersionExpiryService
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.captcha.BrowserImageCaptchaSolver
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.GirigiriSearchRoute
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.MacCmsImageCaptchaSolver
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.MediaCacheManagerImpl
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.KtorPersistentHttpDownloader
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.HttpMediaCacheStorage
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.cache.storage.TorrentMediaCacheStorage
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
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.usecase.useCaseModules
import me.him188.ani.app.ui.subject.details.state.DefaultSubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.BangumiClientImpl
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.childScopeContext
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes

private val Scope.client get() = get<BangumiClient>()
private val Scope.database get() = get<AniDatabase>()
private val Scope.settingsRepository get() = get<SettingsRepository>()
private val Scope.aniApiProvider get() = get<AniApiProvider>()

/**
 * 各端共享的 Koin 装配，默认包含完整缓存/BT 模块。
 *
 * [enableMediaCache] 为 false 时只绑定空存储的 [MediaCacheManager]，不注册 [HttpDownloader]。
 */
fun KoinApplication.getCommonKoinModule(
    getContext: () -> Context,
    coroutineScope: CoroutineScope,
    enableMediaCache: Boolean = true,
) = listOf(
    useCaseModules(),
    repositoryModules(getContext, coroutineScope),
    otherModules(getContext, coroutineScope, enableMediaCache),
)

private fun KoinApplication.otherModules(
    getContext: () -> Context,
    coroutineScope: CoroutineScope,
    enableMediaCache: Boolean,
) = module {
    // Application services
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
    single<ServerSelector> {
        ServerSelector(
            settingsRepository.danmakuSettings.flow.map { it.useGlobal },
            proxyProvider = get(),
            coroutineScope,
        )
    }
    single<HttpClientProvider> {
        val sessionManager by inject<SessionManager>()
        DefaultHttpClientProvider(
            get(), coroutineScope,
            featureHandlers = listOf(
                UserAgentFeatureHandler,
                UseAniTokenFeatureHandler(
                    sessionManager.sessionFlow.map {
                        (it as? AccessTokenSession)?.tokens?.aniAccessToken
                    },
                    onRefresh = { null },
                ),
                ServerListFeatureHandler(
                    get<ServerSelector>().flow,
                ),
                DistributionChannelFeatureHandler { currentAniBuildConfig.distroChannel },
                ConvertSendCountExceedExceptionFeatureHandler,
                VersionExpiryFeatureHandler, // handle 426 Upgrade Required -> show blocking dialog
                SseFeatureHandler,
                CookieJarFeatureHandler, // web 数据源统一 cookie jar (构造时注入)
                WebSourceIdentityFeatureHandler, // web 数据源 per-host UA 对齐
            ),
        )
    }
    // Web 数据源验证码处理 (docs/dev/media/web-captcha.md)
    single<WebSourceCookieJar> { WebSourceCookieJar() }
    single<WebSourceIdentityRegistry> { WebSourceIdentityRegistry() }
    single<WebSessionManager> {
        val browserFactory = get<CaptchaBrowserFactory>()
        val evaluator = PageEvaluator()
        val recognizer = get<ImageCaptchaRecognizer>()
        val settingsRepository = get<SettingsRepository>()
        WebSessionManager(
            browserFactory = browserFactory,
            evaluator = evaluator,
            cookieJar = get(),
            identityRegistry = get(),
            client = get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.BROWSER,
                cookieJar = get(),
                identityRegistry = get(),
            ),
            backgroundScope = coroutineScope,
            solvers = listOf(
                MacCmsImageCaptchaSolver(recognizer),
                BrowserImageCaptchaSolver(recognizer),
            ),
            solverEnabled = {
                settingsRepository.mediaSelectorSettings.flow.first().enableImageCaptchaAutoSolve
            },
            searchRoutes = listOf(GirigiriSearchRoute(evaluator)),
            maxSessions = browserFactory.recommendedMaxSessions,
        )
    }
    single<VersionExpiryService> { DefaultVersionExpiryService() }
    // Wire Global HTTP event bus to VersionExpiryService
    run {
        val service = koin.inject<VersionExpiryService>()
        GlobalHttpEventBus = object : GlobalHttpEvents {
            override fun onVersionExpired(latestVersion: String?) {
                service.value.onVersionExpired(latestVersion)
            }
        }
    }
    single<AniApiProvider> { AniApiProvider(get<HttpClientProvider>().get(useAniToken = true)) }
    single<WatchTogetherApiService> {
        DefaultWatchTogetherApiService(
            provider = get(),
            eventsClient = get<HttpClientProvider>().get(useAniToken = true, useSse = true),
        )
    }
    single<LocalPlaybackBridge> { LocalPlaybackBridge() }
    single<PlaybackAutomationGate> { PlaybackAutomationGate() }
    single(createdAtStart = true) {
        WatchTogetherManager(
            scope = coroutineScope,
            api = get(),
            settings = get<SettingsRepository>().watchTogetherSettings,
            sessionStateProvider = get(),
            playbackBridge = get(),
            automationGate = get(),
        ).also { it.start() }
    }
    single<BangumiClient> {
        BangumiClientImpl(
            get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.ANI,
            ),
        )
    }

    single<AniSubjectSearchService> {
        AniSubjectSearchService(
            subjectApi = aniApiProvider.subjectApi,
        )
    }

    // Data layer network services
    single<SubjectService> {
        RemoteSubjectService(
            aniApiProvider.subjectApi,
            sessionManager = get(),
        )
    }
    single<EpisodeService> { EpisodeServiceImpl(aniApiProvider.subjectApi) }

    single<BangumiRelatedPeopleService> { BangumiRelatedPeopleService(get<AniApiProvider>().subjectApi) }
    single<BangumiCommentService> { BangumiBangumiCommentServiceImpl(get<AniApiProvider>().subjectApi) }
    single<AniEpisodeCommentService> { AniEpisodeCommentService(get<AniApiProvider>().episodesApi) }
    single<AniCommentReportService> { AniCommentReportService(get<AniApiProvider>().commentsApi) }
    single<AniPersonCommentService> {
        AniPersonCommentService(
            personsApi = get<AniApiProvider>().personsApi,
            charactersApi = get<AniApiProvider>().charactersApi,
        )
    }
    single(createdAtStart = true) {
        PlaybackHistorySyncer(
            repository = get(),
            api = aniApiProvider.playbackHistoryApi,
            sessionStateProvider = get(),
            scope = coroutineScope,
        ).also { it.start() }
    }
    single<AniSubjectRelationIndexService> {
        val provider = get<AniApiProvider>()
        AniSubjectRelationIndexService(provider.subjectRelationsApi)
    }

    single<AnimeScheduleService> { AnimeScheduleService(get<AniApiProvider>().scheduleApi) }
    // TV 横版 backdrop / 分集剧照; 未配置 ani.tmdb.api.token 时自动关闭 (atv-architecture.md §4.3-R3)
    single<TmdbImageService> { TmdbImageService(get(), getContext().dataStores.tmdbImageCacheStore) }
    single<BangumiSummaryService> { BangumiSummaryService(get()) }

    single<UpdateManager> {
        UpdateManager(
            saveDir = getContext().files.cacheDir.resolve("updates/download"),
        )
    }

    single<AniDatabase> {
        getContext().createDatabaseBuilder()
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                startVersions = buildList {
                    addAll(1..15) // 16 is destructive
                }.toIntArray(),
            )
            .addMigrations(MIGRATION_19_20)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO_)
            .build()
    }

    if (enableMediaCache) {
        single<HttpDownloader> {
            KtorPersistentHttpDownloader(
                dao = database.httpCacheDownloadStateDao(),
                get<HttpClientProvider>().get(),
                fileSystem = SystemFileSystem,
                baseSaveDir = get<MediaSaveDirProvider>().saveDir
                    .let { Path(it).resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR) },
                scope = coroutineScope,
            )
        }

        single<MediaCacheManager> {
            val id = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID
            val engines = get<TorrentManager>().engines
            val metadataStore = getContext().dataStores.mediaCacheMetadataStore

            MediaCacheManagerImpl(
                storagesIncludingDisabled = buildList(capacity = engines.size) {
                    /*if (currentAniBuildConfig.isDebug) {
                        // 注意, 这个必须要在第一个, 见 [DefaultTorrentManager.engines] 注释
                        add(
                            @Suppress("DEPRECATION")
                            TorrentMediaCacheStorage(
                                mediaSourceId = "test-in-memory",
                                store = metadataStore,
                                engine = DummyMediaCacheEngine("test-in-memory"),
                                "[debug]dummy",
                                coroutineScope.childScopeContext(),
                            ),
                        )
                    }*/
                    for (engine in engines) {
                        add(
                            @Suppress("DEPRECATION")
                            TorrentMediaCacheStorage(
                                mediaSourceId = id,
                                store = metadataStore,
                                torrentEngine = TorrentMediaCacheEngine(
                                    mediaSourceId = id,
                                    engineKey = MediaCacheEngineKey(engine.type.id),
                                    torrentEngine = engine,
                                    engineAccess = get(),
                                    dao = database.torrentCacheInfoDao(),
                                    baseSaveDirProvider = get(),
                                ),
                                displayName = "LocalTorrent",
                                parentCoroutineContext = coroutineScope.childScopeContext(),
                                shareRatioLimitFlow = settingsRepository.anitorrentConfig.flow
                                    .map { it.shareRatioLimit },
                            ),
                        )
                    }
                    add(
                        @Suppress("DEPRECATION")
                        HttpMediaCacheStorage(
                            mediaSourceId = id,
                            store = metadataStore,
                            dao = database.httpCacheDownloadStateDao(),
                            httpEngine = get<HttpMediaCacheEngine>(),
                            displayName = "LocalWebM3u",
                            coroutineScope.childScopeContext(),
                        ),
                    )
                },
                backgroundScope = coroutineScope.childScope(),
            )
        }
    } else {
        single<MediaCacheManager> {
            MediaCacheManagerImpl(
                storagesIncludingDisabled = emptyList(),
                backgroundScope = coroutineScope.childScope(),
            )
        }
    }

    // Media source services
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

    // Caching
    single<MeteredNetworkDetector> { createMeteredNetworkDetector(getContext()) }
    single<SubjectDetailsStateFactory> { DefaultSubjectDetailsStateFactory() }
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
        koin.get<SessionManager>().clearSessionIfAccessTokenExpired()
        // We have to block here to read the saved proxy settings
        when (val proxyProvider = koin.get<HttpClientProvider>()) {
            // compile-safe type cast
            is DefaultHttpClientProvider -> proxyProvider.startProxyListening(holdingInstanceMatrixSequence())
        }
    }
    // Now, the proxy settings is ready. Other components can use http clients.

    coroutineScope.launch {
        // TV 不装配缓存模块: HttpDownloader 无绑定时跳过; 空引擎 MediaCacheManager 的循环自然为空.
        koin.getOrNull<HttpDownloader>()?.init() // restore http download states first
        koin.getOrNull<MediaCacheManager>()?.let { manager ->
            for (storage in manager.storagesIncludingDisabled) {
                storage.restorePersistedCaches()
            }
        }
    }

    coroutineScope.launch {
        val subscriptionUpdater = koin.get<MediaSourceSubscriptionUpdater>()
        while (currentCoroutineContext().isActive) {
            val nextDelay = subscriptionUpdater.updateAllOutdated()
            delay(nextDelay.coerceAtLeast(10.minutes))
        }
    }

    coroutineScope.launch {
        val currentSaves = context.dataStores.mediaSourceSaveStore.data.first()
        val defaultInstanceIds = MediaSourceSaves.Default.instances.map { it.instanceId }
        // 如果当前的数据源列表的 instance ids 都在默认列表里, 说明用户没有自定义过数据源, 直接写入默认源
        if (currentSaves.instances.all { it.instanceId in defaultInstanceIds }) {
            context.dataStores.mediaSourceSaveStore.updateData { MediaSourceSaves.Default }
        }
    }

    coroutineScope.launch {
        val peerFilterRepo = koin.get<PeerFilterSubscriptionRepository>()
        peerFilterRepo.updateOrLoadAll()
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
