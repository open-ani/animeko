// IAnitorrentConfigCollector.aidl
package me.him188.ani.app.domain.torrent.collector;

import me.him188.ani.app.domain.torrent.parcel.PAnitorrentConfig;

// Declare any non-default types here with import statements

interface IAnitorrentConfigCollector {
    void collect(in PAnitorrentConfig config);
}