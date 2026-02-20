// ContTorrentDownloaderStartDownload.aidl
package me.him188.ani.app.domain.torrent.cont;

import me.him188.ani.app.domain.torrent.IRemoteTorrentSession;
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException;

// Declare any non-default types here with import statements

interface ContTorrentDownloaderStartDownload {
    void resume(in IRemoteTorrentSession value);
    void resumeWithException(in RemoteContinuationException exception);
}