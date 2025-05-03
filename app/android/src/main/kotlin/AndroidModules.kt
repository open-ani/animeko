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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import me.him188.ani.android.navigation.AndroidBrowserNavigator
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.TorrentEngineAccess
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
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnectionManager
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnection
import me.him188.ani.app.navigation.BrowserNavigator
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
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toFile
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

    single<TorrentManager> {
        val context = androidContext()
        val logger = logger<TorrentManager>()

        val defaultTorrentCachePath = context.files.defaultBaseMediaCacheDir.absolutePath
        val fallbackInternalPath = context.filesDir.resolve("torrent-caches") // hard-coded directory name before 4.9

        val saveDir = runBlocking {
            val settings = get<SettingsRepository>().mediaCacheSettings
            val dir = settings.flow.first().saveDir

            // 首次启动设置空间
            if (dir == null) {
                settings.update { copy(saveDir = defaultTorrentCachePath) }
                return@runBlocking defaultTorrentCachePath
            }

            // dir != null 可能是外部或者内部
            if (dir.startsWith(context.filesDir.absolutePath)) {
                if (!defaultTorrentCachePath.startsWith(context.filesDir.absolutePath)) {
                    // 如果当前是内部但是默认外部目录可用，则请求迁移. 这是绝大部分用户更新到 4.9 后的 path
                    AniApplication.instance.requiresTorrentCacheMigration.value = true
                }
                return@runBlocking dir
            } else {
                // 如果当前目录是外部但是外部不可用，可能是因为 SD 卡或者其他可移动存储被移除, 直接使用 fallback.
                if (Environment.getExternalStorageState(File(dir)) != Environment.MEDIA_MOUNTED) {
                    val fallbackPathString = fallbackInternalPath.absolutePath
                    settings.update { copy(saveDir = fallbackPathString) }
                    Toast.makeText(context, "BT 存储位置不可用，已切换回应用私有存储位置", Toast.LENGTH_LONG).show()
                    return@runBlocking fallbackPathString
                }
            }

            // 外部私有目录可用
            dir
        }

        logger.info { "TorrentManager base save dir: $saveDir" }

        val oldCacheDir = Path(saveDir).resolve("api").inSystem
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

        DefaultTorrentManager.create(
            coroutineScope.coroutineContext,
            get(),
            client = get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI),
            get(),
            get(),
            baseSaveDir = { Path(saveDir).inSystem },
            if (AniApplication.FEATURE_USE_TORRENT_SERVICE) {
                RemoteAnitorrentEngineFactory(get(), get(), get<ProxyProvider>().proxy)
            } else {
                LocalAnitorrentEngineFactory
            },
        )
    }

    single<HttpMediaCacheEngine> {
        val context = androidContext()
        val logger = logger<TorrentManager>()

        val defaultMediaCacheDir = context.files.defaultBaseMediaCacheDir
        val fallbackInternalPath =
            context.files.dataDir.resolve("web-m3u-cache") // hard-coded directory name before 4.11

        // 旧的缓存目录如果有内容，则考虑需要迁移
        if (fallbackInternalPath.exists() && fallbackInternalPath.list().isNotEmpty()) {
            // 如果 defaultMediaCacheDir 不是内部目录, 则说明是外部目录, 并且外部目录如果是可用的, 则需要进行迁移. 
            // 这是绝大部分用户更新到 4.11 后的 path.
            if (!defaultMediaCacheDir.absolutePath.startsWith(context.filesDir.absolutePath) &&
                Environment.getExternalStorageState(defaultMediaCacheDir.toFile()) == Environment.MEDIA_MOUNTED
            ) {
                AniApplication.instance.requiresWebM3uCacheMigration.value = true
            }
        }

        val saveDir = defaultMediaCacheDir.resolve("web-m3u")
        logger.info { "HttpMediaCacheEngine base save dir: $saveDir" }

        HttpMediaCacheEngine(
            mediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
            downloader = get<HttpDownloader>(),
            saveDir = saveDir.path,
            mediaResolver = get<MediaResolver>(),
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
