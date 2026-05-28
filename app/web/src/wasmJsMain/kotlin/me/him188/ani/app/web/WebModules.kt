package me.him188.ani.app.web

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.mediasource.web.NoopWebCaptchaCoordinator
import me.him188.ani.app.domain.mediasource.web.WebCaptchaCoordinator
import me.him188.ani.app.domain.torrent.TorrentManager
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.OpenBrowserResult
import me.him188.ani.app.navigation.QQ_GROUP_JOIN_LINK
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.GrantedPermissionManager
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadOptions
import me.him188.ani.utils.httpdownloader.DownloadProgress
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.httpdownloader.HttpDownloader
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.MediampPlayerFactoryLoader
import org.koin.dsl.module

fun getWebModules(coroutineScope: CoroutineScope) = module {
    single<BrowserNavigator> {
        object : BrowserNavigator {
            override fun openBrowser(context: Context, url: String): OpenBrowserResult = runCatching {
                window.open(url, "_blank")
            }.fold(
                onSuccess = { OpenBrowserResult.Success },
                onFailure = { OpenBrowserResult.Failure(it, url) },
            )

            override fun openJoinGroup(context: Context): OpenBrowserResult = openBrowser(context, QQ_GROUP_JOIN_LINK)

            override fun intentActionView(context: Context, url: String): OpenBrowserResult = openBrowser(context, url)
        }
    }

    single<TorrentManager> {
        object : TorrentManager {
            override val engines = emptyList<me.him188.ani.app.domain.torrent.TorrentEngine>()
        }
    }

    single<MediaCacheManager> { object : MediaCacheManager(emptyList(), coroutineScope) {} }

    single<MediaResolver> {
        MediaResolver.from(
            HttpStreamingMediaResolver(),
            WasmWebMediaResolver(
                httpClientProvider = get(),
                matcherLoader = get<MediaSourceManager>().webVideoMatcherLoader,
            ),
        )
    }

    single<MediampPlayerFactory<*>> {
        MediampPlayerFactoryLoader.first()
    }

    single<HttpDownloader> { NoopHttpDownloader }
    single<WebCaptchaCoordinator> { NoopWebCaptchaCoordinator }
    single<PermissionManager> { GrantedPermissionManager }
}

private object NoopHttpDownloader : HttpDownloader {
    override val progressFlow: Flow<DownloadProgress> = emptyFlow()
    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> = emptyFlow()
    override val downloadStatesFlow: Flow<List<DownloadState>> = emptyFlow()
    override suspend fun init() {}
    override suspend fun download(url: String, options: DownloadOptions): DownloadId = DownloadId(url)
    override suspend fun downloadWithId(downloadId: DownloadId, url: String, options: DownloadOptions): DownloadState? =
        null

    override suspend fun resume(downloadId: DownloadId): Boolean = false
    override suspend fun getActiveDownloadIds(): List<DownloadId> = emptyList()
    override suspend fun pause(downloadId: DownloadId): Boolean = false
    override suspend fun pauseAll(): List<DownloadId> = emptyList()
    override suspend fun cancel(downloadId: DownloadId): Boolean = false
    override suspend fun cancelAll() {}
    override suspend fun remove(downloadId: DownloadId): Boolean = false
    override suspend fun getState(downloadId: DownloadId): DownloadState? = null
    override suspend fun getAllStates(): List<DownloadState> = emptyList()
    override fun close() {}
}
