// IRemoteTorrentFileHandle.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry;

// Declare any non-default types here with import statements

interface IRemoteTorrentFileHandle {
	IRemoteTorrentFileEntry getTorrentFileEntry();

    void resume(int priorityEnum);
    
    void pause();
    
    void close();
    
    void closeAndDelete();
}