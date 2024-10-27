/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import android.annotation.SuppressLint
import android.os.SharedMemory
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IPieceStateObserver
import me.him188.ani.app.domain.torrent.IRemotePieceList
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceListSubscriptions
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.utils.coroutines.childScope
import kotlin.coroutines.CoroutineContext

@SuppressLint("NewApi")
class PieceListProxy(
    private val delegate: PieceList,
    context: CoroutineContext
) : IRemotePieceList.Stub(), CoroutineScope by context.childScope() {
    private val pieceStateSharedMem = SharedMemory.create("piece_list_states$delegate", delegate.sizes.size)
    private val pieceStatesRwBuf = pieceStateSharedMem.mapReadWrite()
    
    private val pieceStateSubscriber: PieceListSubscriptions.Subscription
    private val internalPieceStateObservers: MutableList<PieceStateObserver> = mutableListOf()
    
    init {
        with(delegate) {
            // fill shared buffer of piece state
            sizes.forEachIndexed { index, _ -> 
                val piece = getByPieceIndex(index)
                pieceStatesRwBuf.put(index, piece.state.ordinal.toByte()) 
            }

            // subscribe new changes
            pieceStateSubscriber = 
                subscribePieceState(Piece.Invalid) { piece, state -> onPieceStateChange(piece, state) }
        }
    }
    
    private fun onPieceStateChange(piece: Piece, state: PieceState) {
        // update shared memory first
        pieceStatesRwBuf.put(piece.pieceIndex, state.ordinal.toByte())
        internalPieceStateObservers.forEach { observer ->
            // notify observer to get new state.
            if (piece.pieceIndex == observer.pieceIndex) observer.observer.onUpdate()
        }
    }
    
    override fun getImmutableSizeArray(): LongArray {
        return delegate.sizes
    }

    override fun getImmutableDataOffsetArray(): LongArray {
        return delegate.dataOffsets
    }

    override fun getImmutableInitialPieceIndex(): Int {
        return delegate.initialPieceIndex
    }

    override fun getPieceStateArrayMemRegion(): SharedMemory {
        return pieceStateSharedMem
    }

    override fun registerPieceStateObserver(
        pieceIndex: Int,
        observer: IPieceStateObserver?
    ): IDisposableHandle? {
        if (observer == null) return null
        val newObserver = PieceStateObserver(pieceIndex, observer)
        
        internalPieceStateObservers.add(newObserver)
        return DisposableHandleProxy { internalPieceStateObservers.remove(newObserver) }
    }

    override fun dispose() {
        delegate.unsubscribePieceState(pieceStateSubscriber)
    }
    
    private class PieceStateObserver(val pieceIndex: Int, val observer: IPieceStateObserver)
}