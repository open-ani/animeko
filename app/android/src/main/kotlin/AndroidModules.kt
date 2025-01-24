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
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import me.him188.ani.android.activity.MainActivity
import me.him188.ani.android.navigation.AndroidBrowserNavigator
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.AndroidWebMediaResolver
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.domain.torrent.DefaultTorrentManager
import me.him188.ani.app.domain.torrent.LocalAnitorrentEngineFactory
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.domain.torrent.TorrentEngineFactory
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.domain.torrent.client.RemoteAnitorrentEngine
import me.him188.ani.app.domain.torrent.peer.PeerFilterSettings
import me.him188.ani.app.domain.torrent.service.TorrentServiceConnection
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.AndroidPermissionManager
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.BaseComponentActivity
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.platform.notification.AndroidNotifManager
import me.him188.ani.app.platform.notification.NotifManager
import me.him188.ani.app.tools.update.AndroidUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.resolve
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
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

fun getAndroidModules(
    defaultTorrentCacheDir: File,
    torrentServiceConnection: TorrentServiceConnection,
    coroutineScope: CoroutineScope,
) = module {
    single<PermissionManager> {
        AndroidPermissionManager()
    }
    single<NotifManager> {
        AndroidNotifManager(
            NotificationManagerCompat.from(androidContext()),
            getContext = { androidContext() },
            activityIntent = {
                Intent(androidContext(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
//                androidContext().packageManager.getLaunchIntentForPackage(androidContext().packageName)
//                    ?: Intent(Intent.ACTION_MAIN).apply {
//                        setPackage(androidContext().packageName)
//                    }
            },
            coroutineScope.coroutineContext,
        ).apply { createChannels() }
    }
    single<BrowserNavigator> { AndroidBrowserNavigator() }

    single<TorrentServiceConnection> { torrentServiceConnection }

    single<TorrentManager> {
        val context = androidContext()
        val defaultTorrentCachePath = defaultTorrentCacheDir.absolutePath
        val cacheDir = runBlocking {
            val settings = get<SettingsRepository>().mediaCacheSettings
            val dir = settings.flow.first().saveDir

            // 首次启动设置为应用内部私有目录
            if (dir == null) {
                settings.update { copy(saveDir = defaultTorrentCachePath) }
                return@runBlocking defaultTorrentCachePath
            }

            if (dir.startsWith(context.filesDir.absolutePath)) {
                // 在设置中保存的是私有目录，直接返回
                return@runBlocking dir
            }

//            context.contentResolver.persistedUriPermissions.forEach { p ->
//                val storage = DocumentsContractApi19.parseUriToStorage(context, p.uri)
//
//                if (storage != null && dir.startsWith(storage)) {
//                    return@runBlocking if (p.isReadPermission && p.isWritePermission) {
//                        // 需要再次验证目录权限
//                        try {
//                            withContext(Dispatchers.IO) {
//                                File(storage).resolve("pieces/.nomedia")
//                                    .apply { parentFile.mkdirs() }
//                                    .apply { createNewFile() }
//                                    .writeText(" ")
//                            }
//                            dir
//                        } catch (ex: IOException) {
//                            // 实际上没有权限，释放 uri
//                            logger.warn(ex) { "failed to write to .nomedia" }
//                            context.contentResolver.releasePersistableUriPermission(
//                                p.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
//                            )
//                            resetToDefault()
//                        }
//                    } else {
//                        // 在设置中保存的外部共享目录没有完整的读写权限，直接切换回默认的内部私有目录,
//                        // 避免读写权限不足错误导致 App 崩溃
//                        resetToDefault()
//                    }
//                }
//            }

            // 检查外部私有目录
            if (context.getExternalFilesDir(null) == null) {
                // 外部私有目录不可用，直接切换回默认的私有目录，避免读写权限不足错误导致 App 崩溃
                settings.update { copy(saveDir = defaultTorrentCachePath) }
                Toast.makeText(context, "BT 存储位置不可用，已切换回默认存储位置", Toast.LENGTH_LONG).show()
                return@runBlocking defaultTorrentCachePath
            }

            // 外部私有目录可用
            dir
        }

        val oldCacheDir = Path(cacheDir).resolve("api").inSystem
        if (oldCacheDir.exists() && oldCacheDir.isDirectory()) {
            val piecesDir = oldCacheDir.resolve("pieces")
            if (piecesDir.exists() && piecesDir.isDirectory() && piecesDir.list().isNotEmpty()) {
                Toast.makeText(context, "旧 BT 引擎的缓存已不被支持，请重新缓存", Toast.LENGTH_LONG).show()
            }
            thread(name = "DeleteOldCaches") {
                try {
                    oldCacheDir.deleteRecursively()
                } catch (ex: Exception) {
                    logger<TorrentManager>().warn(ex) { "Failed to delete old caches in $oldCacheDir" }
                }
            }
        }

        DefaultTorrentManager.create(
            coroutineScope.coroutineContext,
            get(),
            proxyProvider = get<ProxyProvider>().proxy,
            get(),
            get(),
            baseSaveDir = { Path(cacheDir).inSystem },
            if (AniApplication.FEATURE_USE_TORRENT_SERVICE) {
                object : TorrentEngineFactory {
                    override fun createTorrentEngine(
                        parentCoroutineContext: CoroutineContext,
                        config: Flow<AnitorrentConfig>,
                        proxyConfig: Flow<ProxyConfig?>,
                        peerFilterSettings: Flow<PeerFilterSettings>,
                        saveDir: SystemPath
                    ): TorrentEngine {
                        return RemoteAnitorrentEngine(
                            get(),
                            config,
                            proxyConfig,
                            peerFilterSettings,
                            saveDir,
                            parentCoroutineContext,
                        )
                    }
                }
            } else {
                LocalAnitorrentEngineFactory
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
                .map { TorrentMediaResolver(it) }
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
                        Intent(context, TorrentServiceConnection.anitorrentServiceClass)
                            .apply { putExtra("stopService", true) },
                    )
                    exitProcess(status)
                }
            }
        }
    }
}
