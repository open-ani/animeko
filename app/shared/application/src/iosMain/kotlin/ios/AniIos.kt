/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.torrent.DefaultTorrentManager
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.navigation.NoopBrowserNavigator
import me.him188.ani.app.platform.AppStartupTasks
import me.him188.ani.app.platform.GrantedPermissionManager
import me.him188.ani.app.platform.IosContext
import me.him188.ani.app.platform.IosContextFiles
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.platform.createAppRootCoroutineScope
import me.him188.ani.app.platform.getCommonKoinModule
import me.him188.ani.app.platform.startCommonKoinModule
import me.him188.ani.app.tools.update.IosUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.TestGlobalLifecycleOwner
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.isSystemInFullscreen
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.SkikoOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toast
import me.him188.ani.app.ui.foundation.widgets.ToastViewModel
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.utils.io.SystemCacheDir
import me.him188.ani.utils.io.SystemDocumentDir
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.openani.mediamp.DummyMediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused") // used in Swift
fun MainViewController(): UIViewController {
    val scope = createAppRootCoroutineScope()

    val context = IosContext(
        IosContextFiles(
            cacheDir = SystemCacheDir.apply { createDirectories() },
            dataDir = SystemDocumentDir.apply { createDirectories() },
        ),
    )
    AppStartupTasks.printVersions()

    val koin = startKoin {
        modules(getCommonKoinModule({ context }, scope))
        modules(getIosModules(SystemDocumentDir.resolve("torrent"), scope))
    }.startCommonKoinModule(scope).koin

    koin.get<TorrentManager>() // start sharing, connect to DHT now

    val aniNavigator = AniNavigator()
    val onBackPressedDispatcherOwner = SkikoOnBackPressedDispatcherOwner(
        aniNavigator,
        @OptIn(TestOnly::class)
        TestGlobalLifecycleOwner, // TODO: ios lifecycle
    )

    return ComposeUIViewController {
        AniApp {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalPlatformWindow provides remember {
                    PlatformWindow()
                },
                LocalOnBackPressedDispatcherOwner provides onBackPressedDispatcherOwner,
            ) {
                Box(
                    Modifier.background(color = MaterialTheme.colorScheme.background)
                        .ifThen(!isSystemInFullscreen()) {
                            statusBarsPadding() // Windows 有, macOS 没有
                        }
                        .fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        val paddingByWindowSize by animateDpAsState(0.dp)

                        val vm = viewModel { ToastViewModel() }

                        val showing by vm.showing.collectAsStateWithLifecycle()
                        val content by vm.content.collectAsStateWithLifecycle()

                        CompositionLocalProvider(
                            LocalNavigator provides aniNavigator,
                            LocalToaster provides remember {
                                object : Toaster {
                                    override fun toast(text: String) {
                                        vm.show(text)
                                    }
                                }
                            },
                        ) {
                            Box(Modifier.padding(all = paddingByWindowSize)) {
                                AniAppContent(aniNavigator)
                                Toast({ showing }, { Text(content) })
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getIosModules(
    defaultTorrentCacheDir: SystemPath,
    coroutineScope: CoroutineScope,
) = module {
    single<PermissionManager> {
        GrantedPermissionManager
    }
    single<BrowserNavigator> { NoopBrowserNavigator }
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
    single<MediampPlayerFactory<*>> {
        DummyMediampPlayer.Factory
    }


    factory<MediaResolver> {
        MediaResolver.from(
            get<TorrentManager>().engines
                .map { TorrentMediaResolver(it) }
                .plus(LocalFileMediaResolver())
                .plus(HttpStreamingMediaResolver()),
        )
    }
    single<UpdateInstaller> { IosUpdateInstaller }
}
