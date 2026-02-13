// IRemotePieceList.aidl
package me.him188.ani.app.domain.torrent;

import me.him188.ani.app.domain.torrent.IPieceStateObserver;
import me.him188.ani.app.domain.torrent.IDisposableHandle;

// Declare any non-default types here with import statements

interface IRemotePieceList {
    long[] getImmutableSizeArray();
    
    long[] getImmutableDataOffsetArray();
    
    int getImmutableInitialPieceIndex();
    
    SharedMemory getPieceStateArrayMemRegion();
    
    // register a state observer for specific piece
    // trigger update when the new state is already write to shared memory.
    IDisposableHandle registerPieceStateObserver(int pieceIndex, IPieceStateObserver observer);
    
    void dispose();
}