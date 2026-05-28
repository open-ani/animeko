package me.him188.ani.app.torrent.anitorrent

import me.him188.ani.app.torrent.api.TorrentLibraryLoader

internal actual fun getAnitorrentTorrentLibraryLoader(): TorrentLibraryLoader = WebTorrentLibraryLoader

private object WebTorrentLibraryLoader : TorrentLibraryLoader {
    override fun loadLibraries() {
        throw UnsupportedOperationException("Anitorrent is not available in the browser build")
    }
}
