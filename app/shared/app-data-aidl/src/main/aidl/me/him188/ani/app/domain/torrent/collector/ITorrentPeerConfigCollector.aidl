// ITorrentPeerConfigCollector.aidl
package me.him188.ani.app.domain.torrent.collector;

import me.him188.ani.app.domain.torrent.parcel.PTorrentPeerFilterSettings;

// Declare any non-default types here with import statements

interface ITorrentPeerConfigCollector {
    void collect(in PTorrentPeerFilterSettings config);
}