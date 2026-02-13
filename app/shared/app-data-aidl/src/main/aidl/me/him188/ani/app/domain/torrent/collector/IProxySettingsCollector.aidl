// IProxySettingsCollector.aidl
package me.him188.ani.app.domain.torrent.collector;

import me.him188.ani.app.domain.torrent.parcel.PProxyConfig;

// Declare any non-default types here with import statements

interface IProxySettingsCollector {
    void collect(in PProxyConfig config);
}