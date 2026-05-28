package me.him188.ani.app.torrent.anitorrent

import me.him188.ani.app.torrent.api.HttpFileDownloader
import me.him188.ani.app.torrent.api.TorrentDownloaderConfig
import me.him188.ani.utils.io.SystemPath
import kotlin.coroutines.CoroutineContext

internal actual fun createAnitorrentTorrentDownloader(
    rootDataDirectory: SystemPath,
    httpFileDownloader: HttpFileDownloader,
    torrentDownloaderConfig: TorrentDownloaderConfig,
    parentCoroutineContext: CoroutineContext,
): AnitorrentTorrentDownloader<*, *> {
    throw UnsupportedOperationException("Anitorrent is not available in the browser build")
}
