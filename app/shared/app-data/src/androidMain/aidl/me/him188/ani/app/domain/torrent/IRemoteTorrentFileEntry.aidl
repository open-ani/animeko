// IRemoteTorrentFileEntry.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.ITorrentFileEntryStatsCallback;
import me.him188.ani.app.domain.torrent.IRemotePieceList;
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileHandle;
import me.him188.ani.app.domain.torrent.IRemoteTorrentInput;
import me.him188.ani.app.domain.torrent.IDisposableHandle;

// Declare any non-default types here with import statements

interface IRemoteTorrentFileEntry {
	IDisposableHandle getFileStats(ITorrentFileEntryStatsCallback flow);
	
	long getLength();
	
	String getPathInTorrent();
	
	IRemotePieceList getPieces();
	
	boolean getSupportsStreaming();
	
	IRemoteTorrentFileHandle createHandle();
	
	String resolveFile();
	
	String resolveFileMaybeEmptyOrNull();
	
	IRemoteTorrentInput createInput();
}