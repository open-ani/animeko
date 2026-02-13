// ContTorrentDownloaderFetchTorrent.aidl
package me.him188.ani.app.domain.torrent.cont;

import me.him188.ani.app.domain.torrent.parcel.PEncodedTorrentInfo;
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException;

// Declare any non-default types here with import statements

interface ContTorrentDownloaderFetchTorrent {
    void resume(in PEncodedTorrentInfo value);
    void resumeWithException(in RemoteContinuationException exception);
}