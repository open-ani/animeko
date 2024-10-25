// IRemoteAniTorrentEngine.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.IRemoteTorrentDownloader;
import me.him188.ani.app.domain.torrent.IAnitorrentConfigCallback;
import me.him188.ani.app.domain.torrent.IProxySettingsCallback;
import me.him188.ani.app.domain.torrent.ITorrentPeerConfigCallback;

// Declare any non-default types here with import statements

interface IRemoteAniTorrentEngine {
    IAnitorrentConfigCallback getAnitorrentConfigFlow();
    IProxySettingsCallback getProxySettingsFlow();
    ITorrentPeerConfigCallback getTorrentPeerConfigFlow();
    void setSaveDir(String saveDir);
    
    IRemoteTorrentDownloader getDownlaoder();
}