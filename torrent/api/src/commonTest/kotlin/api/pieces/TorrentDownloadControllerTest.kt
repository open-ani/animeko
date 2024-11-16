/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api.pieces

import me.him188.ani.datasources.api.topic.FileSize.Companion.kiloBytes
import kotlin.test.Test
import kotlin.test.assertEquals

internal class TorrentDownloadControllerTest {
    internal val Int.kb: Long get() = kiloBytes.inBytes

    @Test
    fun `test sequence download and seek`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val priorities = object : PiecePriorities {
            override fun downloadOnly(pieceIndexes: List<Int>, possibleFooterRange: IntRange) {
                currentDownloadingPieces = pieceIndexes
                currentPossibleFooterRange = possibleFooterRange
            }
        }

        val pieceList = PieceList.create(
            totalSize = 1000.kb,
            pieceSize = 1.kb,
            initialDataOffset = 0,
            initialPieceIndex = 0,
        )

        val controller = TorrentDownloadController(
            pieces = pieceList,
            priorities = priorities,
            windowSize = 10,
            headerSize = 5.kb,
            footerSize = 3.kb,
            possibleFooterSize = 12.kb,
        )

        fun finishPiece(index: Int) {
            with(pieceList) { getByPieceIndex(index).state = PieceState.FINISHED }
            controller.onPieceDownloaded(index)
        }

        controller.onTorrentResumed()
        // resume 后立刻请求 windowSize 大小的 header 和 footer
        assertEquals(13, currentDownloadingPieces.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999), currentDownloadingPieces)
        assertEquals(988..999, currentPossibleFooterRange)

        // resume window 内的 piece 会使 window 向后滑动
        finishPiece(0)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 997, 998, 999), currentDownloadingPieces.sorted())

        finishPiece(1)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 997, 998, 999), currentDownloadingPieces.sorted())

        finishPiece(5)
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 997, 998, 999), currentDownloadingPieces.sorted())

        finishPiece(12)
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 10, 11, 13, 997, 998, 999), currentDownloadingPieces.sorted())

        // resume window 外的 piece 不会使 window 向后滑动
        finishPiece(100)
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 10, 11, 13, 997, 998, 999), currentDownloadingPieces.sorted())

        // 不会重复请求已经完成的 piece
        (10..99).forEach { finishPiece(it) }
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 101, 102, 103, 997, 998, 999), currentDownloadingPieces.sorted())

        // seek 到 200, seek 后面的地方不会请求 footer
        controller.onSeek(200)
        assertEquals(listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209), currentDownloadingPieces.sorted())

        (200..220).forEach { finishPiece(it) }
        assertEquals(listOf(221, 222, 223, 224, 225, 226, 227, 228, 229, 230), currentDownloadingPieces.sorted())

        // seek 到前面已经完成的部分, window 应该填充 50 后的前十个未完成的 piece
        controller.onSeek(50)
        assertEquals(listOf(101, 102, 103, 104, 105, 106, 107, 108, 109, 110), currentDownloadingPieces.sorted())

        // 200 - 220 已经完成了
        controller.onSeek(200)
        assertEquals(listOf(221, 222, 223, 224, 225, 226, 227, 228, 229, 230), currentDownloadingPieces.sorted())

        (101..109 step 2).forEach { finishPiece(it) }
        controller.onSeek(50)
        assertEquals(listOf(102, 104, 106, 108, 110, 111, 112, 113, 114, 115), currentDownloadingPieces.sorted())

        // 完成很多 piece
        (0..994).forEach { finishPiece(it) }
        controller.onSeek(100)
        assertEquals(listOf(995, 996, 997, 998, 999), currentDownloadingPieces.sorted())

        // 测试边界 piece
        (998..999).forEach { finishPiece(it) }
        controller.onSeek(100)
        assertEquals(listOf(995, 996, 997), currentDownloadingPieces.sorted())

        finishPiece(996)
        controller.onSeek(100)
        assertEquals(listOf(995, 997), currentDownloadingPieces.sorted())
    }
}