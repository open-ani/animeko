/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api.pieces

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import me.him188.ani.app.torrent.api.pieces.PieceListSubscriptions.Subscription

interface PieceSubscribable {
    /**
     * 订阅 [Piece.state] 的变化
     *
     * @param piece [Piece.Invalid] 订阅所有 Piece 变化
     */
    fun subscribePieceState(piece: Piece, onStateChange: PieceList.(Piece, PieceState) -> Unit): Subscription

    /**
     * 取消订阅 [Piece.state] 的变化
     */
    fun unsubscribePieceState(subscription: Subscription)
}

class PieceListSubscriptions {
    // use object identity
    class Subscription(
        /**
         * `-1` for subscribing all pieces
         */
        val pieceIndex: Int,
        val onStateChange: PieceList.(Subscription, Piece) -> Unit,
    )

    private val subscriptions: MutableStateFlow<PersistentList<Subscription>> = MutableStateFlow(persistentListOf())

    /**
     * Call related subscribers
     */
    fun notifyPieceStateChanges(pieceList: PieceList, changedPiece: Piece) {
        val subscriptions = subscriptions.value
        for (subscription in subscriptions) {
            if (subscription.pieceIndex == -1 || subscription.pieceIndex == changedPiece.pieceIndex) {
                subscription.onStateChange(pieceList, subscription, changedPiece)
            }
        }
    }

    fun subscribe(pieceIndex: Int, onStateChange: PieceList.(Subscription, Piece) -> Unit): Subscription {
        val subscriptions = subscriptions
        while (true) {
            val prevValue = subscriptions.value
            val sub = Subscription(pieceIndex, onStateChange)
            val nextValue = prevValue.plus(sub)
            if (subscriptions.compareAndSet(prevValue, nextValue)) {
                return sub
            }
        }
    }

    fun unsubscribe(subscription: Subscription) {
        subscriptions.update { list ->
            list.minus(subscription)
        }
    }
}