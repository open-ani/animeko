/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.client

import android.annotation.SuppressLint
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.domain.torrent.IPieceStateObserver
import me.him188.ani.app.domain.torrent.IRemotePieceList
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceListSubscriptions
import me.him188.ani.app.torrent.api.pieces.PieceState

@SuppressLint("NewApi")
class RemotePieceList(
    private val remote: IRemotePieceList,
) : PieceList(
    remote.immutableSizeArray,
    remote.immutableDataOffsetArray,
    remote.immutableInitialPieceIndex
) {
    private val pieceStateSharedMem by lazy { remote.pieceStateArrayMemRegion }
    private val pieceStateBuf by lazy { pieceStateSharedMem.mapReadOnly() }

    override var Piece.state: PieceState
        get() = PIECE_STATE_ENTRIES[pieceStateBuf.get(pieceIndex).toInt()]
        set(_) { error("set Piece state is not allowed in remote PieceList") }

    override fun Piece.compareAndSetState(expect: PieceState, update: PieceState): Boolean {
        error("set Piece state is not allowed in remote PieceList")
    }

    override fun subscribePieceState(
        piece: Piece,
        onStateChange: PieceList.(Piece, PieceState) -> Unit
    ): PieceListSubscriptions.Subscription { 
        error("unimplemented.") 
    }

    override fun unsubscribePieceState(subscription: PieceListSubscriptions.Subscription) {
        error("unimplemented.") 
    }

    override suspend fun Piece.awaitFinished() {
        val readyState = suspendCancellableCoroutine { cont ->
            val disposableHandle = remote.registerPieceStateObserver(
                pieceIndex,
                object : IPieceStateObserver.Stub() {
                    override fun onUpdate() {
                        val newState = state
                        if (newState == PieceState.READY) {
                            cont.resumeWith(Result.success(newState))
                        }
                    }
                },
            )
            // todo: 如果在 register 此 cancellation handler 前上面已经 resume 了,
            // 那这个 disposable handle 是不是不会被调用?
            cont.invokeOnCancellation { disposableHandle.dispose() }
        }
        require(state == readyState) { "Remote state of piece $this is changed from READY to $state" }
    }
    
    fun dispose() {
        remote.dispose()
    }
    
    companion object {
        private val PIECE_STATE_ENTRIES: List<PieceState> = PieceState.entries
    }
}