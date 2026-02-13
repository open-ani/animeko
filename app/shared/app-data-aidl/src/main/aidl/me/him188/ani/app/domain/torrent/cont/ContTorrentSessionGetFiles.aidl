// ContTorrentSessionGetFiles.aidl
package me.him188.ani.app.domain.torrent.cont;

import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntryList;
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException;

// Declare any non-default types here with import statements

interface ContTorrentSessionGetFiles {
    void resume(in IRemoteTorrentFileEntryList value);
    void resumeWithException(in RemoteContinuationException exception);
}