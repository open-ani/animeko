/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.service.proxy

import android.os.Build
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IPieceStateObserver
import me.him188.ani.app.domain.torrent.IRemotePieceList
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceListSubscriptions
import me.him188.ani.app.torrent.api.pieces.PieceSubscribable
import me.him188.ani.app.torrent.api.pieces.forEach
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.logger
import kotlin.coroutines.CoroutineContext

@RequiresApi(Build.VERSION_CODES.O_MR1)
class PieceListProxy(
    private val delegate: PieceList,
    context: CoroutineContext
) : IRemotePieceList.Stub() {
    private val logger = logger<PieceListProxy>()
    private val scope = context.childScope()
    
    private val pieceStateSharedMem = SharedMemory.create("piece_list_states$delegate", delegate.sizes.size)
    private val pieceStatesRwBuf = pieceStateSharedMem.mapReadWrite()
    
    private val pieceStateSubscriber: PieceListSubscriptions.Subscription
    private val stateObservers: MutableList<PieceStateObserver> = mutableListOf()
    
    init {
        require(delegate is PieceSubscribable) { "Delegate $delegate is not PieceSubscribable" }
        with(delegate) {
            delegate.forEach { piece ->
                pieceStatesRwBuf.put(piece.indexInList, piece.state.ordinal.toByte())
            }

            // subscribe changes to shared memory
            pieceStateSubscriber = (this as PieceSubscribable)
                .subscribePieceState(Piece.Invalid) { piece, state ->
                    pieceStatesRwBuf.put(piece.indexInList, state.ordinal.toByte())
                }
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

        val subscription = with(delegate) {
            (this as PieceSubscribable)
                .subscribePieceState(getByPieceIndex(pieceIndex)) { _, _ -> observer.onUpdate() }
        }

        return DisposableHandleProxy {
            (delegate as PieceSubscribable).unsubscribePieceState(subscription)
        }
    }

    override fun dispose() {
        (delegate as PieceSubscribable).unsubscribePieceState(pieceStateSubscriber)
        pieceStateSharedMem.close()
    }
    
    private class PieceStateObserver(val pieceIndex: Int, val observer: IPieceStateObserver)
}