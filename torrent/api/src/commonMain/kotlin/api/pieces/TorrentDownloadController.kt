/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api.pieces

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Torrent 下载优先级控制器.
 *
 * 下载有两个阶段:
 * 1. Metadata: 下载视频文件首尾的元数据, 使播放器尽快初始化.
 * 2. Sequential: 顺序下载视频文件的中间部分.
 *
 * 通过设置播放需要的 pieces 为最高优先级, 设置其他所有 pieces 为忽略来确保 libtorrent 优先下载所需的 pieces.
 *
 * ## 索引窗口
 *
 * 该控制器会维护一个索引窗口, 请求的所有 pieces 的索引都在这个窗口内. 当窗口内的首部 pieces 下载完成后, 窗口才会向后移动. 若窗口内任意非首部 pieces 下载完成, 则不会移动窗口.
 *
 * 这是为了让 libtorrent 专注于下载最影响当前播放体验的区块, 否则 libtorrent 为了整体下载速度, 可能会导致即将要播放的区块下载缓慢.
 *
 * ### 示例
 *
 * ```
 * // 假设 10 个 pieces, 窗口大小为 3, 当前请求了 3-5:
 *
 * 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
 *       |-----|
 *
 * // 假设 4 下载完成. 因为 4 不是窗口内的第一个 piece, 所以窗口不变, 不会请求更多的 piece.
 * // 假设 3 下载完成, 因为 3 是窗口的第一个 piece, 窗口会前进:
 *
 * 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
 *          |-----|
 *
 * // 由于 4 也已经下载完成了, 窗口继续前进, 直到一个没有下载 piece:
 *
 * 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
 *             |-----|
 *
 * // 现在将会请求下载 pieces 5-7.
 * ```
 *
 *
 * @param windowSize 窗口大小, 即请求的最后一个 piece 的索引与第一个 piece 的索引之差的最大值.
 * @param headerSize 将文件首部多少字节作为 metadata, 在 metadata 阶段请求
 * @param footerSize 将文件尾部多少字节作为 metadata, 在 metadata 阶段请求
 * @param possibleFooterSize 如果 seek 到这个范围内, 考虑它是 footer, 不会重置 piece priority
 */
class TorrentDownloadController(
    private val pieces: PieceList, // sorted
    private val priorities: PiecePriorities,
    private val windowSize: Int = 8,
    private val headerSize: Long = 128 * 1024,
    private val footerSize: Long = headerSize,
    private val possibleFooterSize: Long = headerSize,
) : SynchronizedObject() {
    private val totalPieceSize: Long = pieces.sumOf { it.size }
    private val pieceOffsetStart = with(pieces) { pieces.first().dataStartOffset }

    private val footerPieces = pieces.dropWhile { it.dataLastOffset < pieceOffsetStart + totalPieceSize - footerSize }
    private val possibleFooterRange = pieces
        .dropWhile { it.dataLastOffset < pieceOffsetStart + totalPieceSize - possibleFooterSize }
        .let {
            if (it.isEmpty()) IntRange.EMPTY
            else it.first().pieceIndex..it.last().pieceIndex
        }

    private val lastIndex = try {
        pieces.last().pieceIndex
    } catch (ex: NoSuchElementException) {
        -1
    }

    private var currentWindowStart = pieces.initialPieceIndex

    // inclusive
    private var currentWindowEnd = (currentWindowStart + windowSize - 1).coerceAtMost(lastIndex)

    private var downloadingPieces: MutableList<Int> =
        (currentWindowStart until (currentWindowStart + windowSize).coerceAtMost(lastIndex)).toMutableList()


    fun isDownloading(pieceIndex: Int): Boolean = synchronized(this) {
        return downloadingPieces.contains(pieceIndex)
    }

    fun onTorrentResumed() = synchronized(this) {
        if (currentWindowEnd == -1) return@synchronized
        onSeek(pieces.initialPieceIndex)
    }

    fun onSeek(pieceIndex: Int) = synchronized(this) {
        if (currentWindowEnd == -1) return@synchronized
        if (pieceIndex in possibleFooterRange) {
            // seek 到 footer 附近, 不重置 piece priority
            if (pieceIndex !in downloadingPieces) {
                downloadingPieces.add(0, pieceIndex)
            }
            return
        }
        downloadingPieces.clear()
        fillWindow(pieceIndex)
        priorities.downloadOnly(downloadingPieces, possibleFooterRange)
    }

    /**
     * 找接下来最近的还未完成的 piece, 如果没有, 返回 -1
     */
    private fun findNextDownloadingPiece(startIndex: Int): Int {
        for (index in startIndex..lastIndex) {
            if (with(pieces) { pieces.getByPieceIndex(index).state } != PieceState.FINISHED) {
                return index
            }
        }
        return -1
    }

    fun onPieceDownloaded(pieceIndex: Int) = synchronized(this) {
        if (currentWindowEnd == -1) return@synchronized
        if (!downloadingPieces.remove(pieceIndex)) {
            return
        }

        val newWindowEnd = findNextDownloadingPiece(currentWindowEnd + 1)
        if (newWindowEnd != -1 && newWindowEnd != currentWindowEnd) {
            downloadingPieces.add(newWindowEnd)
            currentWindowEnd = newWindowEnd
        }
        priorities.downloadOnly(downloadingPieces, possibleFooterRange)
    }

    /**
     * seek 到 pieceIndex 不一定会重构从 pieceIndex 开始的 window
     * 要从 pieceIndex 开始找接下来 window 大小个未完成的 piece 构成 window
     */
    private fun fillWindow(pieceIndex: Int) {
        // 如果 pieceIndex 以后的 piece 都完成了, 那就没有 piece 要填充到 window 了
        val next = findNextDownloadingPiece(pieceIndex)
        if (next == -1) return

        currentWindowStart = next
        currentWindowEnd = next

        downloadingPieces.add(currentWindowStart)
        for (i in 0..<windowSize - 1) {
            val next = findNextDownloadingPiece(currentWindowEnd + 1)
            if (next != -1) {
                downloadingPieces.add(next)
                currentWindowEnd = next
            } else {
                // findNext 没找到, 说明所有的 piece 都下完了
                break
            }
        }

        addFooterPieces()
    }

    private fun addFooterPieces() {
        for (footerPiece in footerPieces) {
            if (with(pieces) { footerPiece.state } != PieceState.FINISHED) {
                downloadingPieces.addIfNotExist(with(pieces) { footerPiece.pieceIndex })
            }
        }
    }
}

private fun <E> MutableList<E>.addIfNotExist(pieceIndex: E) {
    if (!contains(pieceIndex)) {
        add(pieceIndex)
    }
}


interface PiecePriorities {
    /**
     * 设置仅下载指定的 pieces.
     * @param possibleFooterRange 作为参考的视频尾部元数据 piece index range
     */
    fun downloadOnly(pieceIndexes: List<Int>, possibleFooterRange: IntRange)
}
