// IRemoteTorrentSession.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.callback.ITorrentSessionStatsCallback;
import me.him188.ani.app.domain.torrent.cont.ContTorrentSessionGetFiles;
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntryList;
import me.him188.ani.app.domain.torrent.parcel.PPeerInfo;
import me.him188.ani.app.domain.torrent.IDisposableHandle;

// Declare any non-default types here with import statements

interface IRemoteTorrentSession {
    IDisposableHandle getSessionStats(ITorrentSessionStatsCallback flow);
    
    String getName();
    
    IDisposableHandle getFiles(in ContTorrentSessionGetFiles cont);
    
    PPeerInfo[] getPeers();
    
    void close();
    
    void closeIfNotInUse();
}