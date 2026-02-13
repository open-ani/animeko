// IRemoteTorrentFileEntry.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.callback.ITorrentFileEntryStatsCallback;
import me.him188.ani.app.domain.torrent.cont.ContTorrentFileEntryGetInputParams;
import me.him188.ani.app.domain.torrent.cont.ContTorrentFileEntryResolveFile;
import me.him188.ani.app.domain.torrent.IRemotePieceList;
import me.him188.ani.app.domain.torrent.IRemoteTorrentFileHandle;
import me.him188.ani.app.domain.torrent.IDisposableHandle;
import me.him188.ani.app.domain.torrent.parcel.PTorrentInputParameter;

// Declare any non-default types here with import statements

interface IRemoteTorrentFileEntry {
	IDisposableHandle getFileStats(ITorrentFileEntryStatsCallback flow);
	
	long getLength();
	
	String getPathInTorrent();
	
	String getFileName();
	
	IRemotePieceList getPieces();
	
	boolean getSupportsStreaming();
	
	IRemoteTorrentFileHandle createHandle();
	
	IDisposableHandle resolveFile(in ContTorrentFileEntryResolveFile cont);
	
	String resolveFileMaybeEmptyOrNull();
	
	IDisposableHandle getTorrentInputParams(in ContTorrentFileEntryGetInputParams cont);
	
	void torrentInputOnWait(int pieceIndex);
}