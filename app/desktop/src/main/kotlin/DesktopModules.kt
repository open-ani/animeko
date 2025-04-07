/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.repository.WindowStateRepository
import me.him188.ani.app.data.repository.WindowStateRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.DesktopWebMediaResolver
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.torrent.DefaultTorrentManager
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.DesktopBrowserNavigator
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.DefaultAppTerminator
import me.him188.ani.app.platform.DesktopContext
import me.him188.ani.app.platform.GrantedPermissionManager
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.tools.update.DesktopUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.dsl.module
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.MediampPlayerFactoryLoader
import org.openani.mediamp.compose.MediampPlayerSurfaceProviderLoader
import org.openani.mediamp.vlc.VlcMediampPlayerFactory
import org.openani.mediamp.vlc.compose.VlcMediampPlayerSurfaceProvider
import java.io.File
import kotlin.io.path.Path

fun getDesktopModules(getContext: () -> DesktopContext, scope: CoroutineScope) = module {
    single<TorrentManager> {
        val defaultTorrentCachePath = getContext().torrentDataCacheDir

        val saveDir = runBlocking {
            val settings = get<SettingsRepository>().mediaCacheSettings
            val dir = settings.flow.first().saveDir

            // 首次启动设置默认 dir
            if (dir == null) {
                val finalPathString = defaultTorrentCachePath.absolutePath
                settings.update { copy(saveDir = finalPathString) }
                return@runBlocking finalPathString
            }

            // 如果当前目录没有权限读写, 直接使用默认目录
            if (!File(dir).run { canRead() && canWrite() }) {
                val fallbackPathString = defaultTorrentCachePath.absolutePath
                settings.update { copy(saveDir = fallbackPathString) }
                return@runBlocking fallbackPathString
            }

            dir
        }
        logger<TorrentManager>().info { "TorrentManager base save dir: $saveDir" }

        DefaultTorrentManager.create(
            scope.coroutineContext,
            get(),
            client = get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI),
            get(),
            get(),
            baseSaveDir = { Path(saveDir).toKtPath().inSystem },
        )
    }
    single<MediampPlayerFactory<*>> {
        MediampPlayerFactoryLoader.register(VlcMediampPlayerFactory())
        MediampPlayerSurfaceProviderLoader.register(VlcMediampPlayerSurfaceProvider())
        MediampPlayerFactoryLoader.first()
    }
    single<BrowserNavigator> { DesktopBrowserNavigator() }
    factory<MediaResolver> {
        MediaResolver.from(
            get<TorrentManager>().engines
                .map { TorrentMediaResolver(it) }
                .plus(LocalFileMediaResolver())
                .plus(HttpStreamingMediaResolver())
                .plus(
                    DesktopWebMediaResolver(
                        getContext(),
                        get<MediaSourceManager>().webVideoMatcherLoader,
                    ),
                ),
        )
    }
    single<UpdateInstaller> { DesktopUpdateInstaller.currentOS() }
    single<PermissionManager> { GrantedPermissionManager }
    single<WindowStateRepository> { WindowStateRepositoryImpl(getContext().dataStores.savedWindowStateStore) }
    single<AppTerminator> { DefaultAppTerminator }
}