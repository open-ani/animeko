/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ios

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.data.tracking.AniListTrackingSource
import me.him188.ani.app.domain.episode.EpisodeTrackingSync
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.cache.engine.AlwaysUseTorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.tracking.TrackingEpisodeSynchronizer
import me.him188.ani.app.ios.tracking.IosAniListAccountConnector
import me.him188.ani.app.ios.tracking.IosAniListBindingStore
import me.him188.ani.app.ios.tracking.IosRegistryEpisodeTrackingSync
import me.him188.ani.app.ios.tracking.IosTrackingCredentialStore
import me.him188.ani.app.ios.tracking.aniListTokenFromRedirect
import me.him188.ani.app.tracking.anilist.AniListTrackingProvider
import me.him188.ani.app.tracking.anilist.createAniListHttpClient
import me.him188.ani.app.ui.foundation.icons.AniListTrackingIcon
import me.him188.ani.app.ui.foundation.icons.TrackingIconRenderer
import me.him188.ani.app.ui.foundation.tracking.TrackingAccountConnector
import me.him188.ani.tracking.api.TrackingSource
import me.him188.ani.utils.ktor.getPlatformKtorEngine
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.hls.HlsPlaybackPreparer
import me.him188.ani.app.domain.media.hls.PlatformHlsPlaybackPreparer
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.IosWebMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileUriMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.OfflineDownloadMediaResolver
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.UnsupportedCaptchaBrowserFactory
import me.him188.ani.app.domain.torrent.DefaultTorrentManager
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.data.repository.user.QrLoginRepository
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.IosBrowserNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.AniHostingUIViewController
import me.him188.ani.app.platform.AppStartupTasks
import me.him188.ani.app.platform.GrantedPermissionManager
import me.him188.ani.app.platform.IosContext
import me.him188.ani.app.platform.IosContextFiles
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.StartupTimeMonitor
import me.him188.ani.app.platform.StepName
import me.him188.ani.app.platform.createAppRootCoroutineScope
import me.him188.ani.app.platform.getCommonKoinModule
import me.him188.ani.app.platform.rememberPlatformWindow
import me.him188.ani.app.platform.startCommonKoinModule
import me.him188.ani.app.platform.trace.recordAppStart
import me.him188.ani.app.tools.update.IosUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.TestGlobalLifecycleOwner
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.SkikoOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toast
import me.him188.ani.app.ui.foundation.widgets.ToastViewModel
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.app.videoplayer.player.AniAVKitMediampPlayerFactory
import me.him188.ani.torrent.offline.OfflineDownloadEngine
import me.him188.ani.torrent.pikpak.PikPakCredentials
import me.him188.ani.torrent.pikpak.PikPakOfflineDownloadEngine
import me.him188.ani.torrent.pikpak.PikPakSessionStoreAdapter
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.io.SystemCacheDir
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemSupportDir
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.IosLoggingConfigurator
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.ffmpeg.FFmpegKit
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

class AniIosApplication(
    val context: IosContext,
    val aniNavigator: AniNavigator,
    val onBackPressedDispatcherOwner: SkikoOnBackPressedDispatcherOwner,
    private val scope: CoroutineScope,
    private val aniListConnector: IosAniListAccountConnector? = null,
) {
    /**
     * 处理打开 App 的 `ani://` 链接. 由 Swift 的 `onOpenURL` 调用.
     *
     * @return 是否识别了这个链接
     */
    @Suppress("unused") // used in Swift
    fun openUrl(url: String): Boolean {
        // 扫码登录: 系统相机扫描电视上的二维码后, 网页跳转到 ani://qr-login?requestId=...
        val qrLoginRequestId = QrLoginRepository.parseRequestId(url)
        if (qrLoginRequestId != null) {
            scope.launch(Dispatchers.Main) {
                if (!aniNavigator.isBackStackReady()) {
                    aniNavigator.awaitBackStack()
                    delay(1000) // 等待初始化好, 否则跳转可能无效
                }
                aniNavigator.navigateQrLoginConfirm(qrLoginRequestId)
            }
            return true
        }

        // AniList OAuth 重定向: ani://anilist-auth#access_token=...
        val aniListToken = aniListTokenFromRedirect(url)
        if (aniListToken != null) {
            val connector = aniListConnector ?: return false
            scope.launch(Dispatchers.Default) {
                try {
                    connector.completeRedirect(aniListToken)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    logger<AniIosApplication>().warn {
                        "AniList sign-in failed: ${failure::class.simpleName}"
                    }
                }
            }
            return true
        }

        return false
    }
}

// Called from Swift
@Suppress("unused")
fun startIosApp(): AniIosApplication {
    val startupTimeMonitor = StartupTimeMonitor()
    val scope = createAppRootCoroutineScope()

    val context = IosContext(
        IosContextFiles(
            cacheDir = SystemCacheDir.apply { createDirectories() },
            dataDir = SystemSupportDir.apply { createDirectories() },
        ),
    )
    startupTimeMonitor.mark(StepName.WindowAndContext)

    AppStartupTasks.printVersions()
    IosLoggingConfigurator.configure(context.files.logsDir.path, SystemFileSystem)
    initializeIosFfmpegRuntime()
    startupTimeMonitor.mark(StepName.Logging)

    val koin = startKoin {
        modules(getCommonKoinModule({ context }, scope))
        modules(getIosModules(context, context.files.dataDir.resolve("torrent"), scope))
    }.startCommonKoinModule(context, scope).koin
    startupTimeMonitor.mark(StepName.Modules)
    val userRepository = koin.get<UserRepository>()

    val analyticsInitializer = scope.launch {
        val settingsRepository = koin.get<SettingsRepository>()
        val settings = settingsRepository.analyticsSettings.flow.first()
        settingsRepository.analyticsSettings.update { settings }
        if (settings.isBugReportEnabled) {
            AppStartupTasks.initializeSentry(settings.deviceId)
        }
//        if (settings.isAnalyticsEnabled) {
//            AppStartupTasks.initializeAnalytics {
//                AnalyticsImpl(
//                    AnalyticsConfig.create(),
//                    settings.deviceId,
//                    userId = { userRepository.selfInfoFlow.first()?.id },
//                    AnalyticsSecrets(
//                        apiSecret = AniBuildConfigIos.firebaseGAApiSecret,
//                        firebaseAppId = AniBuildConfigIos.firebaseGAAppId,
//                    ),
//                    scope.coroutineContext,
//                ).apply {
//                    init()
//                }
//            }
//        }
    }

    koin.get<TorrentManager>() // start sharing, connect to DHT now

    val aniNavigator = AniNavigator()
    val onBackPressedDispatcherOwner = SkikoOnBackPressedDispatcherOwner(
        aniNavigator,
        @OptIn(TestOnly::class)
        TestGlobalLifecycleOwner, // TODO: ios lifecycle
    )

    runBlocking { analyticsInitializer.join() }
    startupTimeMonitor.mark(StepName.Analytics)

    scope.launch {
        analyticsInitializer.join()
        Analytics.recordAppStart(startupTimeMonitor)
    }

    return AniIosApplication(
        context = context,
        aniNavigator = aniNavigator,
        onBackPressedDispatcherOwner = onBackPressedDispatcherOwner,
        scope = scope,
        aniListConnector = koin.getOrNull<IosAniListAccountConnector>(),
    )
}

private fun initializeIosFfmpegRuntime() {
    val resourcePath = NSBundle.mainBundle.resourcePath ?: return
    val runtimePath = "$resourcePath/FFmpegRuntime"
    if (NSFileManager.defaultManager.fileExistsAtPath(runtimePath)) {
        FFmpegKit.initialize(runtimePath)
    }
}

@Suppress("FunctionName", "unused") // used in Swift
fun MainViewController(app: AniIosApplication): UIViewController {
    val contentViewController = ComposeUIViewController {
        ProvideIosResourceEnvironment {
            CompositionLocalProvider(
                LocalOnBackPressedDispatcherOwner provides app.onBackPressedDispatcherOwner,
                LocalContext provides app.context,
            ) {
                AniApp {
                    val platformWindow = rememberPlatformWindow()
                    CompositionLocalProvider(
                        LocalPlatformWindow provides platformWindow,
                    ) {
                        Box(
                            Modifier.background(color = MaterialTheme.colorScheme.surfaceContainerLowest)
                                .fillMaxSize(),
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                val paddingByWindowSize by animateDpAsState(0.dp)

                                val vm = viewModel { ToastViewModel() }

                                val showing by vm.showing.collectAsStateWithLifecycle()
                                val content by vm.content.collectAsStateWithLifecycle()

                                CompositionLocalProvider(
                                    LocalNavigator provides app.aniNavigator,
                                    LocalToaster provides remember {
                                        object : Toaster {
                                            override fun toast(text: String) {
                                                vm.show(text)
                                            }
                                        }
                                    },
                                ) {
                                    Box(Modifier.padding(all = paddingByWindowSize)) {
                                        AniAppContent(app.aniNavigator)
                                        Toast({ showing }, { Text(content) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    return AniHostingUIViewController().apply {
        addChildViewController(contentViewController)
        view.addSubview(contentViewController.view)
        fillMaxSize(this, contentViewController)
    }
}

private fun fillMaxSize(container: AniHostingUIViewController, contentViewController: UIViewController) {
    contentViewController.didMoveToParentViewController(container)
    contentViewController.view.translatesAutoresizingMaskIntoConstraints = false

    NSLayoutConstraint.activateConstraints(
        listOf(
            contentViewController.view.topAnchor.constraintEqualToAnchor(container.view.topAnchor),
            contentViewController.view.bottomAnchor.constraintEqualToAnchor(container.view.bottomAnchor),
            contentViewController.view.leadingAnchor.constraintEqualToAnchor(container.view.leadingAnchor),
            contentViewController.view.trailingAnchor.constraintEqualToAnchor(container.view.trailingAnchor),
        ),
    )
}

fun getIosModules(
    context: IosContext,
    defaultTorrentCacheDir: SystemPath,
    coroutineScope: CoroutineScope,
) = module {
    single {
        AniListTrackingProvider(
            createAniListHttpClient(getPlatformKtorEngine()),
            IosTrackingCredentialStore(AniListTrackingProvider.ID),
        )
    }
    single { AniListTrackingSource(IosAniListBindingStore(), get<AniListTrackingProvider>(), inject()) }
    single<TrackingSource> { get<AniListTrackingSource>() }
    single { IosAniListAccountConnector(get<AniListTrackingProvider>()) }
    single<TrackingAccountConnector> { get<IosAniListAccountConnector>() }
    single<TrackingIconRenderer> { AniListTrackingIcon(AniListTrackingProvider.ID) }
    single<EpisodeTrackingSync> { IosRegistryEpisodeTrackingSync(get<TrackingEpisodeSynchronizer>(), coroutineScope) }

    single<TorrentEngineAccess> { AlwaysUseTorrentEngineAccess }

    single<PermissionManager> {
        GrantedPermissionManager
    }
    single<BrowserNavigator> { IosBrowserNavigator() }
    // iOS 暂无 CaptchaBrowser 实现: isInteractiveSupported = false, UI 显示降级提示
    single<CaptchaBrowserFactory> { UnsupportedCaptchaBrowserFactory }
    single<ImageCaptchaRecognizer> { IosOnnxImageCaptchaRecognizer() }
    single<TorrentManager> {
        DefaultTorrentManager.create(
            coroutineScope.coroutineContext,
            settingsRepository = get(),
            client = get<HttpClientProvider>().get(),
            subscriptionRepository = get(),
            meteredNetworkDetector = get(),
            baseSaveDir = { defaultTorrentCacheDir },
        )
    }
    single<HttpMediaCacheEngine> {
        @Suppress("DEPRECATION")
        HttpMediaCacheEngine(
            dao = get<AniDatabase>().httpCacheDownloadStateDao(),
            mediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
            downloader = get<HttpDownloader>(),
            saveDir = context.files.defaultMediaCacheBaseDir
                .resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR).path,
            mediaResolver = get<MediaResolver>(),
        )
    }
    single<MediampPlayerFactory<*>> {
        AniAVKitMediampPlayerFactory()
    }
    single<HlsPlaybackPreparer> { PlatformHlsPlaybackPreparer(get()) }
    single<MediaSaveDirProvider> {
        object : MediaSaveDirProvider {
            override val saveDir: String
                get() = context.files.defaultMediaCacheBaseDir.absolutePath
        }
    }


    single<OfflineDownloadEngine> {
        val settings = get<SettingsRepository>()
        val configState = settings.pikpakConfig.flow
            .stateIn(coroutineScope, SharingStarted.Eagerly, initialValue = PikPakConfig.Default)
        val credentialsFlow = configState
            .map { cfg ->
                if (cfg.enabled && cfg.username.isNotEmpty() &&
                    (cfg.password.isNotEmpty() || cfg.refreshToken.isNotEmpty())
                ) {
                    PikPakCredentials(cfg.username, cfg.password)
                } else null
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, initialValue = null)
        val sessionStore = PikPakSessionStoreAdapter(
            readRefreshToken = { configState.value.refreshToken },
            writeRefreshToken = { rt ->
                settings.pikpakConfig.update { copy(refreshToken = rt) }
            },
            // PikPakConfig.password stays on disk obscured (AES-CTR with a
            // hardcoded key, the same approach as `rclone obscure`; see
            // ObscuredStringSerializer). We need to keep it because a
            // server-side revoke of the refresh token would otherwise leave
            // the engine with no recovery path — Test and playback would
            // silently fail until the user re-typed the password.
            // PikPakAcceleratorGroup never echoes the stored value back to
            // the password field, so the obscured copy is what the eyedrop
            // attacker would see.
            onSessionSaved = {},
        )
        PikPakOfflineDownloadEngine(
            scopedHttpClient = get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI),
            credentials = credentialsFlow,
            scope = coroutineScope,
            sessionStore = sessionStore,
            slotQueueLength = { configState.value.slotQueueLength },
        )
    }
    factory<MediaResolver> {
        val torrentResolvers = get<TorrentManager>().engines.map { TorrentMediaResolver(it, get()) }
        val btFallback = MediaResolver.from(torrentResolvers)
        MediaResolver.from(
            listOf<MediaResolver>(OfflineDownloadMediaResolver(get(), fallback = btFallback))
                .plus(torrentResolvers)
                .plus(LocalFileUriMediaResolver())
                .plus(HttpStreamingMediaResolver())
                .plus(
                    IosWebMediaResolver(
                        get<MediaSourceManager>().webVideoMatcherLoader,
                        context,
                        get<SettingsRepository>(),
                    ),
                ),
        )
    }
    single<UpdateInstaller> { IosUpdateInstaller }
}
