/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api.pieces

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

private class Test(
    pieceListSize: Long,
    pieceListInitialDataOffset: Long = 0,
    pieceListInitialPieceIndex: Int = 0,
    tdcWindowSize: Int,
    tdcHeaderSize: Long,
    tdcFooterSize: Long,
    tdcPossibleFooterSize: Long,
    private val onDownloadOnly: (pieceIndexes: List<Int>, possibleFooterRange: IntRange) -> Unit
) {
    private val pieceList: MutablePieceList = PieceList.create(
        totalSize = pieceListSize,
        pieceSize = 1,
        initialDataOffset = pieceListInitialDataOffset,
        initialPieceIndex = pieceListInitialPieceIndex,
    )

    private val controller: TorrentDownloadController = TorrentDownloadController(
        pieces = pieceList,
        priorities = object : PiecePriorities {
            override fun downloadOnly(pieceIndexes: List<Int>, possibleFooterRange: IntRange) {
                onDownloadOnly(pieceIndexes, possibleFooterRange)
            }
        },
        windowSize = tdcWindowSize,
        headerSize = tdcHeaderSize,
        footerSize = tdcFooterSize,
        possibleFooterSize = tdcPossibleFooterSize,
    )

    fun resume() {
        controller.onTorrentResumed()
    }

    fun seek(pieceIndex: Int) {
        controller.onSeek(pieceIndex)
    }

    fun finishPiece(vararg pieceIndex: Int) {
        pieceIndex.toTypedArray().forEach {
            with(pieceList) { getByPieceIndex(it).state = PieceState.FINISHED }
            controller.onPieceDownloaded(it)
        }
    }
}

internal class TorrentDownloadControllerTest {
    @Test
    fun `initial download list is empty`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        assertEquals(emptyList(), currentDownloadingPieces)
    }

    @Test
    fun `resume requests to download footer and windows size of head`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()
        // resume 后立刻请求 windowSize 大小的 header 和 footer
        assertEquals(13, currentDownloadingPieces.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999), currentDownloadingPieces)
        assertEquals(988..999, currentPossibleFooterRange)
    }

    @Test
    fun `case sequence download will move window`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()

        // resume window 内的 piece 会使 window 向后滑动
        test.finishPiece(0)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10), currentDownloadingPieces)

        test.finishPiece(1)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10, 11), currentDownloadingPieces)

        test.finishPiece(5)
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 997, 998, 999, 10, 11, 12), currentDownloadingPieces)

        test.finishPiece(12)
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 997, 998, 999, 10, 11, 13), currentDownloadingPieces)
    }

    @Test
    fun `case sequence download will not move window`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10), currentDownloadingPieces)

        // resume window 外的 piece 不会使 window 向后滑动
        test.finishPiece(100)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10), currentDownloadingPieces)
    }

    @Test
    fun `sequence download don't request downloaded piece`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10), currentDownloadingPieces)

        // resume window 外的 piece 不会使 window 向后滑动
        test.finishPiece(100)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10), currentDownloadingPieces)

        // 不会重复请求已经完成的 piece
        (10..99).forEach { test.finishPiece(it) }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 101), currentDownloadingPieces)

        test.seek(200)
        assertEquals(listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 997, 998, 999), currentDownloadingPieces)

        (200..220).forEach { test.finishPiece(it) }
        assertEquals(listOf(997, 998, 999, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230), currentDownloadingPieces)
    }

    @Test
    fun `seek overlap`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10), currentDownloadingPieces)

        test.seek(200)
        assertEquals(listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 997, 998, 999), currentDownloadingPieces)

        test.seek(198)
        assertEquals(listOf(198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 997, 998, 999), currentDownloadingPieces)

        test.seek(200)
        assertEquals(listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 997, 998, 999), currentDownloadingPieces)

        test.finishPiece(210)
        test.seek(202)
        assertEquals(listOf(202, 203, 204, 205, 206, 207, 208, 209, 211, 212, 997, 998, 999), currentDownloadingPieces)
    }

    @Test
    fun `seek at right edge`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10), currentDownloadingPieces)

        test.seek(200)
        assertEquals(listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 997, 998, 999), currentDownloadingPieces)

        // seek 到 possible footer range 里, 只添加此 footer 到 最前面
        test.seek(991)
        assertEquals(
            listOf(991, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 997, 998, 999),
            currentDownloadingPieces,
        )

        test.seek(985)
        assertEquals(
            listOf(985, 986, 987, 988, 989, 990, 991, 992, 993, 994, 997, 998, 999),
            currentDownloadingPieces,
        )
    }

    @Test
    fun `download move window at right edge`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()

        // 完成很多 piece
        (0..994).forEach { test.finishPiece(it) }
        // TODO: not a critical bug.
        assertEquals(listOf(997, 998, 999, 995, 996, 997, 998, 999), currentDownloadingPieces)

        test.seek(100)
        assertEquals(listOf(995, 996, 997, 998, 999), currentDownloadingPieces)

        // 测试边界 piece
        (998..999).forEach { test.finishPiece(it) }
        test.seek(100)
        assertEquals(listOf(995, 996, 997), currentDownloadingPieces)

        test.finishPiece(996)
        test.seek(100)
        assertEquals(listOf(995, 997), currentDownloadingPieces)
    }

    @Test
    fun `resume with piece list with initial piece index offset`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val test = Test(
            pieceListSize = 1000L,
            pieceListInitialPieceIndex = 17,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()
        // resume 后立刻请求 windowSize 大小的 header 和 footer
        assertEquals(13, currentDownloadingPieces.size)
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999).map { it + 17 },
            currentDownloadingPieces,
        )
        assertEquals((988 + 17)..(999 + 17), currentPossibleFooterRange)
    }

    @Test
    fun `sequence download don't request downloaded piece with initial piece index offset`() {
        var currentDownloadingPieces: List<Int> = emptyList()
        var currentPossibleFooterRange: IntRange = IntRange.EMPTY

        val random = Random.nextInt()

        val test = Test(
            pieceListSize = 1000L,
            pieceListInitialPieceIndex = random,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { pieceIndices, possibleFooterRange ->
            currentDownloadingPieces = pieceIndices
            currentPossibleFooterRange = possibleFooterRange
        }

        test.resume()

        test.finishPiece(0 + random)
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10).map { it + random },
            currentDownloadingPieces,
        )

        // resume window 外的 piece 不会使 window 向后滑动
        test.finishPiece(100 + random)
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 10).map { it + random },
            currentDownloadingPieces,
        )

        // 不会重复请求已经完成的 piece
        ((10 + random)..(99 + random)).forEach { test.finishPiece(it) }
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 997, 998, 999, 101).map { it + random },
            currentDownloadingPieces,
        )

        test.seek(200 + random)
        assertEquals(
            listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 997, 998, 999).map { it + random },
            currentDownloadingPieces,
        )

        ((200 + random)..(220 + random)).forEach { test.finishPiece(it) }
        assertEquals(
            listOf(997, 998, 999, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230).map { it + random },
            currentDownloadingPieces,
        )
    }
}