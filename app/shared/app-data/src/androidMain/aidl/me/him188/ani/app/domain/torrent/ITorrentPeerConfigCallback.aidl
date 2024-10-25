// ITorrentPeerConfigCallback.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.parcel.PTorrentPeerConfig;

// Declare any non-default types here with import statements

interface ITorrentPeerConfigCallback {
    void onEmit(in PTorrentPeerConfig config);
}