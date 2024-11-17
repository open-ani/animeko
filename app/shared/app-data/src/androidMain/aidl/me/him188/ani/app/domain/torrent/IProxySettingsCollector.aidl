// IProxySettingsCollector.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.parcel.PProxySettings;

// Declare any non-default types here with import statements

interface IProxySettingsCollector {
    void collect(in PProxySettings config);
}