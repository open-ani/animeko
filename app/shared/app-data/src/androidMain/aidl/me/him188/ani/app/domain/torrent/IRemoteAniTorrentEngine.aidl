// IRemoteAniTorrentEngine.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.IRemoteTorrentDownloader;
import me.him188.ani.app.domain.torrent.IAnitorrentConfigCollector;
import me.him188.ani.app.domain.torrent.IProxySettingsCollector;
import me.him188.ani.app.domain.torrent.ITorrentPeerConfigCollector;

// Declare any non-default types here with import statements

interface IRemoteAniTorrentEngine {
    IAnitorrentConfigCollector getAnitorrentConfigCollector();
    IProxySettingsCollector getProxySettingsCollector();
    ITorrentPeerConfigCollector getTorrentPeerConfigCollector();
    void setSaveDir(String saveDir);
    
    IRemoteTorrentDownloader getDownlaoder();
}