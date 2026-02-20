// ITorrentFileEntryStatsCallback.aidl
package me.him188.ani.app.domain.torrent.callback;

import me.him188.ani.app.domain.torrent.parcel.PTorrentFileEntryStats;

// Declare any non-default types here with import statements

interface ITorrentFileEntryStatsCallback {
    void onEmit(in PTorrentFileEntryStats stat);
}