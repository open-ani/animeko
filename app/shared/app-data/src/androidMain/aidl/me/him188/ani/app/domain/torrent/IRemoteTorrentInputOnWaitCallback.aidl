// IRemoteTorrentInputOnWaitCallback.aidl
package me.him188.ani.app.domain.torrent;

// Declare any non-default types here with import statements

interface IRemoteTorrentInputOnWaitCallback {
	void onWait(int pieceIndex);
}