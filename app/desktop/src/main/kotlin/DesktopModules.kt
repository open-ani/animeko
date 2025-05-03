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
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.AlwaysUseTorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaCacheMigrator
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
import me.him188.ani.app.platform.files
import me.him188.ani.app.tools.update.DesktopUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.dsl.module
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.MediampPlayerFactoryLoader
import org.openani.mediamp.compose.MediampPlayerSurfaceProviderLoader
import org.openani.mediamp.vlc.VlcMediampPlayerFactory
import org.openani.mediamp.vlc.compose.VlcMediampPlayerSurfaceProvider
import java.io.File
import kotlin.io.path.Path

private interface SaveDirProvider {
    val saveDir: String?
}

fun getDesktopModules(getContext: () -> DesktopContext, scope: CoroutineScope) = module {
    single<TorrentEngineAccess> { AlwaysUseTorrentEngineAccess }

    single<SaveDirProvider> {
        object : SaveDirProvider {
            override val saveDir: String? = runBlocking {
                get<SettingsRepository>().mediaCacheSettings.flow.first().saveDir
            }
        }
    }

    single<TorrentManager> {
        val defaultTorrentCachePath = getContext().files.defaultBaseMediaCacheDir

        val saveDir = runBlocking {
            val settings = get<SettingsRepository>().mediaCacheSettings
            val dir = get<SaveDirProvider>().saveDir

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
    single<HttpMediaCacheEngine> {
        val context = getContext()

        val baseSaveDir = runBlocking {
            val dirFromSettings = get<SaveDirProvider>().saveDir
            if (dirFromSettings == null) {
                // 不能为 null, 因为 startCommonModule 一定先加载了上面的 TorrentManager, 
                // 而上面的 TorrentManager 初始化了这个 settings.
                logger("HttpMediaCacheEngineInKoinInitialization").warn {
                    "Save directory from settings repository should not be null. " +
                            "It should be set by dependency injection initialization of TorrentManager. " +
                            "Use default save directory instead."
                }
            }

            dirFromSettings ?: context.files.defaultBaseMediaCacheDir.absolutePath
        }

        HttpMediaCacheEngine(
            mediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
            downloader = get<HttpDownloader>(),
            saveDir = Path(baseSaveDir).resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR).toKtPath(),
            mediaResolver = get<MediaResolver>(),
        )
    }

    single<MediaCacheMigrator> {
        get<TorrentManager>()
        get<HttpMediaCacheEngine>()

        MediaCacheMigrator(
            context = getContext(),
            metadataStore = getContext().dataStores.mediaCacheMetadataStore,
            m3u8DownloaderStore = getContext().dataStores.m3u8DownloaderStore,
            mediaCacheManager = get(),
            settingsRepo = get(),
            appTerminator = get(),
            migrationChecker = object : MediaCacheMigrator.MigrationChecker {
                override suspend fun requireMigrateTorrentCache(): Boolean {
                    return false
                }

                override suspend fun requireMigrateWebM3uCache(): Boolean {
                    val baseSaveDir = get<SettingsRepository>().mediaCacheSettings.flow.first().saveDir
                        ?: getContext().files.defaultBaseMediaCacheDir.absolutePath

                    @Suppress("DEPRECATION")
                    val legacySaveDir = getContext().files.dataDir.resolve(HttpMediaCacheEngine.LEGACY_MEDIA_CACHE_DIR)

                    // 旧的缓存目录如果有内容，则考虑需要迁移
                    if (legacySaveDir.exists() && legacySaveDir.list().isNotEmpty()) {
                        // 有权限才去迁移
                        if (File(baseSaveDir).run { canRead() && canWrite() }) {
                            return true
                        }
                    }

                    return false
                }
            },
            getNewBaseSaveDir = {
                get<SettingsRepository>().mediaCacheSettings.flow.first().saveDir
                    ?.let { kotlinx.io.files.Path(it).inSystem }
            },
            getLegacyTorrentSaveDir = {
                error("Media caches on desktop should not be migrated by MediaCacheMigrator")
            },
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
                .map { TorrentMediaResolver(it, get()) }
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