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
import kotlin.math.min

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
    private val pieceOffsetStart = with(pieces) { pieces.first().dataStartOffset }
    private val totalPieceSize: Long = if (pieces.isEmpty()) 0 else
        with(pieces) { last().dataEndOffset - pieceOffsetStart }
    private val totalPieceCount = pieces.sizes.size

    // 获取 metadata piece 数量
    private val metadataPieces = pieces.parseMetadataPieceCount()

    /**
     * metadata 的 pieceIndex, 下载完后移除对应 index, 传递给 [priorities].
     * metadata piece 的顺序和数量是固定的. 不需要额外的 list 来存储当前需要下载的 piece.
     */
    private val highPieces = with(metadataPieces) { pieces.getMetadataPieces().toMutableList() }

    /**
     * 其他 piece 的 pieceIndex, 使用 [downloadingNormalPieces] 维护下载窗口
     */
    private val normalPieces: List<Int> =
        DelegateStrippedMetadataPieceList(pieces, metadataPieces.headerPieceCount, metadataPieces.footerPieceCount)

    private val bodyPieceIndexRange =
        if (normalPieces.isEmpty()) IntRange.EMPTY else normalPieces.first()..normalPieces.last()

    /**
     * 正在下载的 normal pieces.
     */
    private val downloadingNormalPieces = normalPieces.take(windowSize).toMutableList()

    /**
     * [normalPieces] 中的窗口索引. 注意是 [normalPieces] 的 index 不是 pieceIndex
     */
    private var currentWindowStartIndex = 0

    // 有可能 normalPriorityPieces 的数量比 windowSize 小
    private var currentWindowEndIndex = min(min(normalPieces.size, windowSize) - 1, 0)

    /**
     * 返回此 pieceIndex 在 [normalPieces] 中对应 piece 的列表索引.
     * 接收者必须是 [normalPieces] 中的 Piece 的 pieceIndex.
     */
    private val Int.indexInNormalPieceList: Int
        get() {
            require(this in bodyPieceIndexRange)
            return this - normalPieces.first()
        }

    /**
     * 是否所有 normal piece 都下载完了, 如果都下载完了就不再处理
     */
    private var allNormalPieceDownloaded = false
    
    fun isDownloading(pieceIndex: Int): Boolean = synchronized(this) {
        return downloadingNormalPieces.contains(pieceIndex) ||
                highPieces.contains(pieceIndex)
    }

    fun resume() = synchronized(this) {
        if (normalPieces.isEmpty()) {
            priorities.downloadOnly(highPieces, emptyList())
            return@synchronized
        }
        seekTo(normalPieces.first())
    }

    fun seekTo(pieceIndex: Int) = synchronized(this) {
        if (normalPieces.isEmpty()) {
            priorities.downloadOnly(highPieces, emptyList())
            return@synchronized
        }
        
        val coercedBodyPieceIndex = pieceIndex.coerceIn(bodyPieceIndexRange)
        downloadingNormalPieces.clear()
        fillNormalPieceWindow(coercedBodyPieceIndex.indexInNormalPieceList)
        priorities.downloadOnly(highPieces, downloadingNormalPieces)
    }

    /**
     * 在 [normalPieces] 中找从 [pieceIndex] 接下来最近的还未完成的 piece
     *
     * @return [normalPieces] 的 index, 如果没有返回 -1
     */
    private fun findNextDownloadingNormalPiece(indexInList: Int): Int {
        val list = normalPieces
        for (index in (indexInList..list.lastIndex)) {
            if (with(pieces) { pieces.getByPieceIndex(list[index]).state } != PieceState.FINISHED) {
                return index
            }
        }
        return -1
    }

    fun onPieceDownloaded(pieceIndex: Int) = synchronized(this) {
        // 完成了首尾 metadata 的 piece, 不移动窗口
        if (highPieces.isNotEmpty() && highPieces.remove(pieceIndex)) {
            priorities.downloadOnly(highPieces, downloadingNormalPieces)
            return@synchronized
        }

        // (1) normal piece 是空的, 不关 normal piece 的事情, 不处理 window
        // (2) 所有 normal piece 都下载完了, 不处理 window
        if (normalPieces.isEmpty() || allNormalPieceDownloaded) {
            return@synchronized
        }

        // (3) 下载完了窗口之外的 piece, 不关 window 内的 piece 的事, 不处理 window
        if (!downloadingNormalPieces.remove(pieceIndex)) {
            return@synchronized
        }

        if (pieceIndex.indexInNormalPieceList == currentWindowStartIndex) {
            // 移动 window start
            currentWindowStartIndex = findNextDownloadingNormalPiece(currentWindowStartIndex + 1)

            if (currentWindowStartIndex == -1) {
                // 往后再找不到需要下载的 piece 了, 说明已经全部下完了
                allNormalPieceDownloaded = true
                return@synchronized
            }
        }


        val newWindowEnd = findNextDownloadingNormalPiece(currentWindowEndIndex + 1)

        if (newWindowEnd != -1) {
            downloadingNormalPieces.addIfNotExist(normalPieces[newWindowEnd])
            currentWindowEndIndex = newWindowEnd
        } else {
            // 如果找不到了, 那说明 currentWidowEnd 到最后的 normal piece 已经下载完了
            // 此时有两种情况
            //   1) 所有的 normal piece 都下载完了
            //   2) 前面没下完，seek 到后面, 后面的下完了
            // 从 initialPieceIndex 开始寻找下一个没 finish 的, 返回 -1 了, 那就是全都下完了

            val nextFromStart = findNextDownloadingNormalPiece(0)
            if (nextFromStart == -1) {
                // 标记全下完了, 不再尝试 fill window
                allNormalPieceDownloaded = true
            } else {
                // 继续填充前面没下完的 normal piece
                fillNormalPieceWindow(nextFromStart) // 不用 coerceIn, findNextDownloadingNormalPiece 保证
            }
        }

        priorities.downloadOnly(highPieces, downloadingNormalPieces)
    }

    /**
     * seek 到 pieceIndex 不一定会重构从 pieceIndex 开始的 window, 因为这个 piece 可能已经下载完成了
     * 要从 pieceIndex 开始找接下来 window 大小个未完成的 piece 构成 window.
     *
     * 此函数会重置 window 边界.
     */
    private fun fillNormalPieceWindow(listIndex: Int) {
        // 如果 pieceIndex 以后的 piece 都完成了, 那就没有 piece 要填充到 window 了
        val nextIndex = findNextDownloadingNormalPiece(listIndex)
        if (nextIndex == -1) return

        currentWindowStartIndex = nextIndex
        currentWindowEndIndex = nextIndex

        downloadingNormalPieces.addIfNotExist(normalPieces[currentWindowStartIndex])
        // windowSize - downloadingNormalPieces.size 是为了保证 window 内的 piece 数量总为 windowSize
        for (i in 0..<(windowSize - downloadingNormalPieces.size)) { 
            val next = findNextDownloadingNormalPiece(currentWindowEndIndex + 1)
            if (next != -1) {
                downloadingNormalPieces.addIfNotExist(normalPieces[next])
                currentWindowEndIndex = next
            } else {
                // findNext 没找到, 说明所有的 piece 都下完了
                break
            }
        }
    }

    private fun PieceList.parseMetadataPieceCount(): MetadataPieceCount {
        // 如果 piece 总大小比 header 和 footer 小, 那所有 piece 都判定为 normal piece
        if (headerSize >= totalPieceSize || footerSize >= totalPieceSize) return MetadataPieceCount.Zero
        // 如果总 piece 数量只有 1, 那也是所有 piece 判定为 normal piece
        if (totalPieceCount == 1) return MetadataPieceCount.Zero
        // 如果总 piece 数量有 2, 那 header 和 footer 各一个
        if (totalPieceCount == 2) return MetadataPieceCount(1, 1)
       
        val headerCount: Int
        val footerCount: Int

        // 第一个在 header size 外的 piece
        val firstPieceIndexAfterHeader = pieceIndexOfFirst { it.dataStartOffset >= pieceOffsetStart + headerSize }
        if (firstPieceIndexAfterHeader == -1) {
            // 如果没找到, 那说明所有 piece 都是 header piece
            // 因此所有 piece 都判定为 normal piece 也没问题
            return MetadataPieceCount.Zero
        } else {
            headerCount = firstPieceIndexAfterHeader - initialPieceIndex
        }

        // 第一个在 footer size 之外的 piece
        val firstPieceIndexBeforeFooter =
            pieceIndexOfLast { it.dataEndOffset <= pieceOffsetStart + totalPieceSize - footerSize }

        if (firstPieceIndexBeforeFooter == -1) {
            // 如果没找到, 那说明所有 piece 都是 footer piece
            // 因此所有 piece 都判定为 normal piece 也没问题
            return MetadataPieceCount.Zero
        } else {
            footerCount = initialPieceIndex + totalPieceCount - firstPieceIndexBeforeFooter - 1
        }

        // 如果 header 和 footer piece 数量大于 piece 总数，通常意味着它们重叠了, 那就均分
        if (headerCount + footerCount > totalPieceCount) {
            val halfTotal = totalPieceCount / 2
            return MetadataPieceCount(halfTotal, totalPieceCount - halfTotal)
        }

        return MetadataPieceCount(headerCount, footerCount)
    }
}

private class MetadataPieceCount(val headerPieceCount: Int, val footerPieceCount: Int) {
    /**
     * 获取 metadata 的 piece index, 靠近边缘的排在前面.
     * 例如 如果 piece index 从 `0 - 99`, 返回 `0, 99, 1, 98, 2, 97, 3, 96, 95 ...`
     */
    fun PieceList.getMetadataPieces(): List<Int> {
        val totalPieceCount = sizes.size

        require(headerPieceCount <= totalPieceCount) {
            "headerCount should be smaller than piece list size"
        }
        require(footerPieceCount <= totalPieceCount) {
            "footerCount should be smaller than piece list size"
        }

        var headIndex = 0
        var tailIndex = 0

        return buildList {
            while (headIndex < headerPieceCount || tailIndex < footerPieceCount) {
                if (headIndex < headerPieceCount) {
                    add(initialPieceIndex + headIndex)
                    headIndex += 1
                }
                if (tailIndex < footerPieceCount) {
                    add(initialPieceIndex + totalPieceCount - 1 - tailIndex)
                    tailIndex += 1
                }
            }
        }
    }

    companion object {
        val Zero = MetadataPieceCount(0, 0)
    }
}

/**
 * Delegate list of pieceIndex without metadata pieces.
 */
private class DelegateStrippedMetadataPieceList(
    private val delegate: PieceList,
    private val headerCount: Int,
    private val footerCount: Int,
) : AbstractList<Int>() {
    private val pieceCount = delegate.sizes.size

    init {
        require(headerCount <= pieceCount) { "headerCount should be smaller than piece list size" }
        require(footerCount <= pieceCount) { "footerCount should be smaller than piece list size" }
        require(pieceCount - headerCount - footerCount >= 0) {
            "headerCount + footerCount is bigger than total pieceCount."
        }
    }

    override val size: Int = pieceCount - headerCount - footerCount

    override fun get(index: Int): Int {
        val targetIndex = headerCount + index
        if (index >= size) {
            throw IndexOutOfBoundsException(
                "Accessing delegate[$index] which is a footer piece or out of bounds. stripped size = $size",
            )
        }
        val result = delegate.first().pieceIndex + targetIndex

        check(result >= delegate.initialPieceIndex && result < delegate.endPieceIndex)
        return result
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
     *
     * 总体的下载优先级是按照先 [highPriorityPieces] 后 [normalPriorityPieces] 排序的.
     *
     * @param highPriorityPieces 高优先级的 piece, 需要考虑最先下载.
     *  通常是视频的首尾 metadata piece, 需要下完首尾 piece 才能边下边播.
     *
     *  按照元素顺序决定优先级, 例如 首尾 piece 是 `0 1 2 3 97 98 99`.
     *  那建议将按照头尾两侧的顺序排序. 例如 `[0, 99, 1, 98, 2, 97, 3]` 保证最靠近边缘的最先下载.
     *
     * @param normalPriorityPieces 正常优先级的 piece.
     *  通常是不包含首尾的 piece.
     *
     *  按照元素顺序决定优先级, 建议按照 piece 顺序排序. 例如 `[15, 16, 17, 18, 19, ...]`
     */
    fun downloadOnly(
        highPriorityPieces: List<Int>,
        normalPriorityPieces: List<Int>,
    )
}
