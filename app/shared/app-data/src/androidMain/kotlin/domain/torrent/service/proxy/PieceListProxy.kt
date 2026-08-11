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
import android.os.DeadObjectException
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IPieceStateObserver
import me.him188.ani.app.domain.torrent.IRemotePieceList
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceListSubscriptions
import me.him188.ani.app.torrent.api.pieces.PieceSubscribable
import me.him188.ani.app.torrent.api.pieces.containsAbsolutePieceIndex
import me.him188.ani.app.torrent.api.pieces.forEach
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
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

    init {
        require(delegate is PieceSubscribable) { "Delegate $delegate is not PieceSubscribable" }
        delegate.forEach { piece ->
            pieceStatesRwBuf.put(piece.indexInList, piece.state.ordinal.toByte())
        }

        // subscribe changes to shared memory
        pieceStateSubscriber = (delegate as PieceSubscribable)
            .subscribePieceState(Piece.Invalid) { piece, state ->
                with(delegate) {
                    // **必须先判范围**: subscribePieceState(Piece.Invalid) 的语义是"订阅所有 piece",
                    // 而订阅登记表是整个 torrent 共用的 —— 多文件种子(整季合集)里, 别的文件的 piece
                    // 也会通知到这里. 那些 piece 的 indexInList (= pieceIndex - initialPieceIndex)
                    // 会超出本 file 的 piece 数, put 直接抛
                    //     IndexOutOfBoundsException: index=567 out of bounds (limit=324)
                    //
                    // 后果远不止"这一次镜像没写": 异常从 notifyPieceStateChanges 一路抛到
                    // AnitorrentTorrentDownloader 的事件循环 (那里只记一行 "Error while handling event"),
                    // **排在它后面的订阅者当次全部收不到通知** —— 包括 registerPieceStateObserver
                    // 注册的那些, 也就是正在等 piece 完成的那一方.
                    //
                    // 于是: BT 确实在下载、piece 也在完成, 但 TorrentInput.fillBuffer 里的
                    // awaitFinished 永远等不到; 而 mediamp 0.3.0 的 setMediaData 会挂起到媒体
                    // 真正打开, 于是它不返回, 界面一直停在 DecodingData ("正在解析磁力链或查询元数据").
                    //
                    // 2026-08-11 真机: 23GB 整季 BDrip 合集, 这个异常每秒刷几十条. v1 时代
                    // setMediaData 不挂起, 同一个 bug 只表现为"卡缓冲", 所以一直没被发现.
                    if (containsAbsolutePieceIndex(piece.pieceIndex)) {
                        pieceStatesRwBuf.put(piece.indexInList, state.ordinal.toByte())
                    }
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
                .subscribePieceState(getByPieceIndex(pieceIndex)) { _, _ ->
                    try {
                        observer.onUpdate()
                    } catch (doe: DeadObjectException) {
                        logger.warn(doe) { "Failed to push piece state of piece $pieceIndex to client." }
                    }
                }
        }

        return DisposableHandleProxy {
            (delegate as PieceSubscribable).unsubscribePieceState(subscription)
        }
    }

    override fun dispose() {
        (delegate as PieceSubscribable).unsubscribePieceState(pieceStateSubscriber)
        pieceStateSharedMem.close()
    }
}