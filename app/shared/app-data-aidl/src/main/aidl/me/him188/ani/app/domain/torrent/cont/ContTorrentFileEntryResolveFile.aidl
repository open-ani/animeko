// ContTorrentFileEntryResolveFile.aidl
package me.him188.ani.app.domain.torrent.cont;

import me.him188.ani.app.domain.torrent.parcel.PTorrentInputParameter;
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException;

// Declare any non-default types here with import statements

interface ContTorrentFileEntryResolveFile {
    void resume(in String value);
    void resumeWithException(in RemoteContinuationException exception);
}