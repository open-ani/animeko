/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import android.os.Environment
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.io.files.Path
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.AndroidWebMediaResolver
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.OfflineDownloadMediaResolver
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.torrent.DefaultTorrentManager
import me.him188.ani.app.domain.torrent.TorrentEngineFactory
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.platform.AndroidContextFiles
import me.him188.ani.app.platform.files
import me.him188.ani.torrent.offline.OfflineDownloadEngine
import me.him188.ani.torrent.pikpak.PikPakCredentials
import me.him188.ani.torrent.pikpak.PikPakOfflineDownloadEngine
import me.him188.ani.torrent.pikpak.PikPakSessionStoreAdapter
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope
import org.koin.dsl.module

/** Android 下载存储与资源解析; 引擎运行位置由应用 flavor 提供. */
fun getAndroidMediaModules(
    coroutineScope: CoroutineScope,
    torrentEngineFactory: Scope.() -> TorrentEngineFactory,
) = module {
    single<MediaSaveDirProvider> {
        val context = androidContext()
        val defaultBaseMediaCacheDir = context.files.defaultMediaCacheBaseDir.absolutePath

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
            torrentEngineFactory(),
        )
    }

    single<HttpMediaCacheEngine> {
        val logger = logger<TorrentManager>()
        val pikpakConfig = get<SettingsRepository>().pikpakConfig.flow
            .stateIn(coroutineScope, SharingStarted.Eagerly, PikPakConfig.Default)

        val saveDir = get<MediaSaveDirProvider>().saveDir
            .let { Path(it).resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR) }
        logger.info { "HttpMediaCacheEngine base save directory: $saveDir" }

        HttpMediaCacheEngine(
            dao = get<AniDatabase>().httpCacheDownloadStateDao(),
            mediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
            downloader = get<HttpDownloader>(),
            saveDir = saveDir,
            mediaResolver = get<MediaResolver>(),
            pikpakConfig = { pikpakConfig.value },
        )
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
                .plus(LocalFileMediaResolver())
                .plus(HttpStreamingMediaResolver())
                .plus(
                    AndroidWebMediaResolver(
                        get<MediaSourceManager>().webVideoMatcherLoader,
                        get<SettingsRepository>(),
                        get<WebSessionManager>(),
                    ),
                ),
        )
    }
}
