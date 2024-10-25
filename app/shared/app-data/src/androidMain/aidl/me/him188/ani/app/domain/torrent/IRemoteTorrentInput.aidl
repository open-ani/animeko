// IRemoteTorrentInput.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.IRemoteTorrentInputOnWaitCallback;
import me.him188.ani.app.domain.torrent.IRemotePieceList;
import me.him188.ani.app.domain.torrent.IDisposableHandle;

// Declare any non-default types here with import statements

interface IRemoteTorrentInput {
	String getSaveFile();

    IRemotePieceList getPieces();
    
    long getLogicalStartOffset();
    
    IRemoteTorrentInputOnWaitCallback getOnWaitCallback();
    
    int getBufferSize();
    
    long getSize();
}