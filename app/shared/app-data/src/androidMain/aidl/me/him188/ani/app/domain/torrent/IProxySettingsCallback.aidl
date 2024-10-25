// IProxySettingsCallback.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.parcel.PProxySettings;

// Declare any non-default types here with import statements

interface IProxySettingsCallback {
    void onEmit(in PProxySettings config);
}