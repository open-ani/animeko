// IRemoteTorrentFileHandle.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.IRemoteTorrentFileEntry;

// Declare any non-default types here with import statements

interface IRemoteTorrentFileHandle {
	IRemoteTorrentFileEntry getTorrentFileEntry();

    void resume(int priorityEnum);
    
    void pause();
    /** 预缓存文件内 [start, endInclusive] 字节范围. start < 0 表示取消. */
    void setPrefetchRange(long start, long endInclusive);
    
    void close();
    
    void closeAndDelete();
}