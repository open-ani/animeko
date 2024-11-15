// IRemoteTorrentFileEntry.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.callback.ITorrentFileEntryStatsCallback;
import me.him188.ani.app.domain.torrent.IRemotePieceList;
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileHandle;
import me.him188.ani.app.domain.torrent.IDisposableHandle;
import me.him188.ani.app.domain.torrent.parcel.PTorrentInputParameter;

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
	
	PTorrentInputParameter getTorrentInputParams();
	
	void torrentInputOnWait(int pieceIndex);
}