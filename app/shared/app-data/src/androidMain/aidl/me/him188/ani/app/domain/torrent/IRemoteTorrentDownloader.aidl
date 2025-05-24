// IRemoteTorrentDownloader.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.callback.ITorrentDownloaderStatsCallback;
import me.him188.ani.app.domain.torrent.cont.ContTorrentDownloaderFetchTorrent;
import me.him188.ani.app.domain.torrent.cont.ContTorrentDownloaderStartDownload;
import me.him188.ani.app.domain.torrent.IRemoteTorrentSession;
import me.him188.ani.app.domain.torrent.IDisposableHandle;
import me.him188.ani.app.domain.torrent.parcel.PTorrentLibInfo;
import me.him188.ani.app.domain.torrent.parcel.PEncodedTorrentInfo;

// Declare any non-default types here with import statements

interface IRemoteTorrentDownloader {
    IDisposableHandle getTotalStatus(ITorrentDownloaderStatsCallback flow);
    
    PTorrentLibInfo getVendor();
    
    IDisposableHandle fetchTorrent(in String uri, int timeoutSeconds, in ContTorrentDownloaderFetchTorrent cont);
    
    IDisposableHandle startDownload(in PEncodedTorrentInfo data, in ContTorrentDownloaderStartDownload cont);
    
    String getSaveDirForTorrent(in PEncodedTorrentInfo data);
    
    String[] listSaves();
    
    void close();
}