/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import me.him188.ani.android.navigation.AndroidBrowserNavigator
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaCacheMigrator
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.AndroidWebMediaResolver
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.domain.torrent.DefaultTorrentManager
import me.him188.ani.app.domain.torrent.IRemoteAniTorrentEngine
import me.him188.ani.app.domain.torrent.LocalAnitorrentEngineFactory
import me.him188.ani.app.domain.torrent.RemoteAnitorrentEngineFactory
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.domain.torrent.service.AniTorrentService
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnection
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnectionManager
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.AndroidContextFiles
import me.him188.ani.app.platform.AndroidPermissionManager
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.BaseComponentActivity
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.files
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.tools.update.AndroidUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.io.toKtPath
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.MediampPlayerFactoryLoader
import org.openani.mediamp.compose.MediampPlayerSurfaceProviderLoader
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayerFactory
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurfaceProvider
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun getAndroidModules(
    serviceConnectionManager: TorrentServiceConnectionManager,
    coroutineScope: CoroutineScope,
) = module {
    single<PermissionManager> {
        AndroidPermissionManager()
    }
    single<BrowserNavigator> { AndroidBrowserNavigator() }

    single<TorrentEngineAccess> { serviceConnectionManager }
    single<TorrentServiceConnection<IRemoteAniTorrentEngine>> { serviceConnectionManager.connection }

    single<MediaSaveDirProvider> {
        val context = androidContext()
        val defaultBaseMediaCacheDir = context.files.defaultBaseMediaCacheDir.absolutePath

        // 如果外部目录没 mounted, 那也要使用内部目录
        val saveDir = if (!defaultBaseMediaCacheDir.startsWith(context.filesDir.absolutePath) &&
            Environment.getExternalStorageState(File(defaultBaseMediaCacheDir)) == Environment.MEDIA_MOUNTED
        ) {
            defaultBaseMediaCacheDir
        } else {
            (context.files as AndroidContextFiles).fallbackInternalBaseMediaCacheDir.absolutePath
        }

        object : MediaSaveDirProvider {
            override val saveDir: String = saveDir
        }
    }

    single<TorrentManager> {
        val context = androidContext()
        val logger = logger<TorrentManager>()

        val legacyInternalPath = context.filesDir.resolve(TorrentMediaCacheEngine.LEGACY_MEDIA_CACHE_DIR).absolutePath
        val oldCacheDir = Path(legacyInternalPath).resolve("api").inSystem

        if (oldCacheDir.exists() && oldCacheDir.isDirectory()) {
            val piecesDir = oldCacheDir.resolve("pieces")
            if (piecesDir.exists() && piecesDir.isDirectory() && piecesDir.list().isNotEmpty()) {
                Toast.makeText(context, "旧 BT 引擎的缓存已不被支持，请重新缓存", Toast.LENGTH_LONG).show()
            }
            thread(name = "DeleteOldCaches") {
                try {
                    oldCacheDir.deleteRecursively()
                } catch (ex: Exception) {
                    logger.warn(ex) { "Failed to delete old caches in $oldCacheDir" }
                }
            }
        }

        val saveDir = get<MediaSaveDirProvider>().saveDir
        logger.info { "TorrentManager base save directory: $saveDir" }

        DefaultTorrentManager.create(
            coroutineScope.coroutineContext,
            get(),
            client = get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI),
            get(),
            get(),
            baseSaveDir = { Path(saveDir).inSystem },
            RemoteAnitorrentEngineFactory(get(), get(), get<ProxyProvider>().proxy),
        )
    }

    single<HttpMediaCacheEngine> {
        val logger = logger<TorrentManager>()

        @Suppress("DEPRECATION")
        val saveDir = get<MediaSaveDirProvider>().saveDir
            .let { Path(it).resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR) }
        logger.info { "HttpMediaCacheEngine base save directory: $saveDir" }

        HttpMediaCacheEngine(
            mediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
            downloader = get<HttpDownloader>(),
            saveDir = saveDir,
            mediaResolver = get<MediaResolver>(),
        )
    }

    single<MediaCacheMigrator> {
        val context = androidContext()
        MediaCacheMigrator(
            context = context,
            metadataStore = context.dataStores.mediaCacheMetadataStore,
            m3u8DownloaderStore = context.dataStores.m3u8DownloaderStore,
            mediaCacheManager = get(),
            settingsRepo = get(),
            appTerminator = get(),
            mediaCacheBaseDirProvider = get(),
            migrationChecker = object : MediaCacheMigrator.MigrationChecker {
                override suspend fun requireMigrateTorrentCache(): Boolean {
                    return requireMigrate(context.files.dataDir.resolve(TorrentMediaCacheEngine.LEGACY_MEDIA_CACHE_DIR))
                }

                override suspend fun requireMigrateWebM3uCache(): Boolean {
                    @Suppress("DEPRECATION")
                    return requireMigrate(context.files.dataDir.resolve(HttpMediaCacheEngine.LEGACY_MEDIA_CACHE_DIR))
                }

                private fun requireMigrate(fallbackPath: SystemPath): Boolean {
                    val defaultMediaCacheDir = context.files.defaultBaseMediaCacheDir

                    // 旧的缓存目录如果有内容，则考虑需要迁移
                    if (fallbackPath.exists() && fallbackPath.list().isNotEmpty()) {
                        // 如果 defaultMediaCacheDir 不是内部目录, 则说明是外部目录, 并且外部目录如果是可用的, 则需要进行迁移. 
                        // 这是绝大部分用户更新到 4.11 后的 path.
                        if (!defaultMediaCacheDir.absolutePath.startsWith(context.filesDir.absolutePath) &&
                            Environment.getExternalStorageState(defaultMediaCacheDir.toFile()) == Environment.MEDIA_MOUNTED
                        ) {
                            return true
                        }
                    }

                    return false
                }
            },
            getNewBaseSaveDir = {
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.toPath()?.toKtPath()?.inSystem
            },
            getLegacyTorrentSaveDir = {
                context.filesDir.resolve(TorrentMediaCacheEngine.LEGACY_MEDIA_CACHE_DIR)
                    .toPath().toKtPath().inSystem
            },
        )
    }

    single<MediampPlayerFactory<*>> {
        MediampPlayerFactoryLoader.register(ExoPlayerMediampPlayerFactory())
        MediampPlayerSurfaceProviderLoader.register(ExoPlayerMediampPlayerSurfaceProvider())
        MediampPlayerFactoryLoader.first()
    }

    factory<MediaResolver> {
        MediaResolver.from(
            get<TorrentManager>().engines
                .map { TorrentMediaResolver(it, get()) }
                .plus(LocalFileMediaResolver())
                .plus(HttpStreamingMediaResolver())
                .plus(AndroidWebMediaResolver(get<MediaSourceManager>().webVideoMatcherLoader)),
        )
    }
    single<UpdateInstaller> { AndroidUpdateInstaller() }

    single<AppTerminator> {
        object : AppTerminator {
            override fun exitApp(context: ContextMP, status: Int): Nothing {
                runBlocking(Dispatchers.Main.immediate) {
                    (context.findActivity() as? BaseComponentActivity)?.finishAffinity()
                    context.startService(
                        Intent(context, AniTorrentService.actualServiceClass)
                            .apply { putExtra("stopService", true) },
                    )
                    exitProcess(status)
                }
            }
        }
    }
}
