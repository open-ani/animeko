// ITorrentDownloaderStatsCallback.aidl
package me.him188.ani.app.domain.torrent.callback;

import me.him188.ani.app.domain.torrent.parcel.PTorrentDownloaderStats;

// Declare any non-default types here with import statements

interface ITorrentDownloaderStatsCallback {
    void onEmit(in PTorrentDownloaderStats stat);
}