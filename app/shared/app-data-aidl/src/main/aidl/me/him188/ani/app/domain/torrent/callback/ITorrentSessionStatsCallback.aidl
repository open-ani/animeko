// ITorrentSessionStatsCallback.aidl
package me.him188.ani.app.domain.torrent.callback;

import me.him188.ani.app.domain.torrent.parcel.PTorrentSessionStats;

// Declare any non-default types here with import statements

interface ITorrentSessionStatsCallback {
    void onEmit(in PTorrentSessionStats stat);
}