// IPieceStateObserver.aidl
package me.him188.ani.app.domain.torrent;

// Declare any non-default types here with import statements

interface IPieceStateObserver {
	// trigger on one piece has updated to the shared memory.
    void onUpdate();
}