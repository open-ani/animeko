/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api.pieces

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
    private val onDownloadOnly: (highPriorityPieces: List<Int>, normalPriorityPieces: List<Int>) -> Unit
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
            override fun downloadOnly(highPriorityPieces: List<Int>, normalPriorityPieces: List<Int>) {
                onDownloadOnly(highPriorityPieces, normalPriorityPieces)
            }
        },
        windowSize = tdcWindowSize,
        headerSize = tdcHeaderSize,
        footerSize = tdcFooterSize,
        possibleFooterSize = tdcPossibleFooterSize,
    )

    fun resume() {
        controller.resume()
    }

    fun seek(pieceIndex: Int) {
        controller.seekTo(pieceIndex)
    }

    fun finishPiece(vararg pieceIndex: Int) {
        pieceIndex.toTypedArray().forEach {
            with(pieceList) { getByPieceIndex(it).state = PieceState.FINISHED }
            controller.onPieceDownloaded(it)
        }
    }

    fun finishPieceRange(range: IntRange) {
        finishPiece(*range.toList().toIntArray())
    }
}

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
internal class TorrentDownloadControllerTest {
    @Test
    @Suppress("UNUSED_VARIABLE")
    fun `initial download list is empty`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        assertEquals(emptyList(), currentDownloadingNormalPriorityPieces)
    }

    @Test
    fun `resume requests to download footer and windows size of head`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        test.resume()
        // resume 后立刻请求 windowSize 大小的 header 和 footer
        assertEquals(10, currentDownloadingNormalPriorityPieces.size)
        assertEquals(listOf(0, 999, 1, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)
    }

    @Test
    fun `case sequence download will moving window`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        test.resume()

        // 下载 window 内的 piece 会使 window 向后滑动
        test.finishPiece(0)
        assertEquals(listOf(999, 1, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        test.finishPiece(1)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        test.finishPiece(5)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(6, 7, 8, 9, 10, 11, 12, 13, 14, 15), currentDownloadingNormalPriorityPieces)

        test.finishPiece(12)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(6, 7, 8, 9, 10, 11, 13, 14, 15, 16), currentDownloadingNormalPriorityPieces)
    }

    @Test
    fun `case sequence download will not moving window`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        test.finishPiece(0)
        assertEquals(listOf(999, 1, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)
        test.finishPiece(1)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        // 下载 window 外的 piece 不会使 window 向后滑动
        test.finishPiece(100)
        test.finishPiece(101)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

    }

    @Test
    fun `sequence download don't request downloaded piece`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(999, 1, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)
        test.finishPiece(1)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        // 下载 window 之外的 piece
        test.finishPiece(16, 17, 19)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        // 下载 window 内的，window 向后滑动，但是不会请求已经下载的 piece
        test.finishPiece(5)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(6, 7, 8, 9, 10, 11, 12, 13, 14, 15), currentDownloadingNormalPriorityPieces)
        test.finishPiece(9)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(6, 7, 8, 10, 11, 12, 13, 14, 15, 18), currentDownloadingNormalPriorityPieces)

        (20..100).forEach { test.finishPiece(it) }
        assertEquals(listOf(6, 7, 8, 10, 11, 12, 13, 14, 15, 18), currentDownloadingNormalPriorityPieces)
        // move 整个大块，超过 window size 的大块
        (7..20).forEach { test.finishPiece(it) }
        assertEquals(listOf(6, 101, 102, 103, 104, 105, 106, 107, 108, 109), currentDownloadingNormalPriorityPieces)

    }

    @Test
    fun `download move window at right edge`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(999, 1, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)
        test.finishPiece(1)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        test.finishPieceRange(0..985)
        assertEquals(listOf(999, 998, 997), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(986, 987, 988, 989, 990, 991, 992, 993, 994, 995), currentDownloadingNormalPriorityPieces)

        test.finishPieceRange(986..994)
        assertEquals(listOf(995, 996), currentDownloadingNormalPriorityPieces)
        assertEquals(listOf(999, 998, 997), currentDownloadingHighPriorityPieces)
    }

    @Test
    fun `seek and move window`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(999, 1, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)
        test.finishPiece(1)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        // seek 到 200
        test.seek(200)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209), currentDownloadingNormalPriorityPieces)

        test.finishPieceRange(200..220)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(221, 222, 223, 224, 225, 226, 227, 228, 229, 230), currentDownloadingNormalPriorityPieces)
    }

    @Test
    fun `seek don't request downloaded piece`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(999, 1, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)
        test.finishPiece(1)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        // seek 到 200
        test.seek(200)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209), currentDownloadingNormalPriorityPieces)

        // 下载 50 - 100 piece, seek 到 50 应该请求 piece 101
        test.finishPieceRange(50..100)
        test.seek(50)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(101, 102, 103, 104, 105, 106, 107, 108, 109, 110), currentDownloadingNormalPriorityPieces)

        // 下载 250 - 300, seek 到 248 应该请求 piece 248, 249 和 301 及以后
        test.finishPieceRange(250..300)
        test.seek(248)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(248, 249, 301, 302, 303, 304, 305, 306, 307, 308), currentDownloadingNormalPriorityPieces)
    }

    @Test
    fun `seek at right edge`() {
        var currentDownloadingHighPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000L,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12,
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        test.resume()

        test.finishPiece(0)
        assertEquals(listOf(999, 1, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)
        test.finishPiece(1)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        test.finishPiece(7)
        assertEquals(listOf(5, 6, 8, 9, 10, 11, 12, 13, 14, 15), currentDownloadingNormalPriorityPieces)

        test.seek(978)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(978, 979, 980, 981, 982, 983, 984, 985, 986, 987), currentDownloadingNormalPriorityPieces)

        test.seek(989)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(989, 990, 991, 992, 993, 994, 995, 996), currentDownloadingNormalPriorityPieces)

        test.seek(995)
        assertEquals(listOf(999, 998, 2, 997, 3, 4), currentDownloadingHighPriorityPieces)
        assertEquals(listOf(995, 996), currentDownloadingNormalPriorityPieces)

        // 末尾的 piece 都下完了, 需要填充前面的 piece
        test.finishPiece(996)
        assertEquals(listOf(995, 5, 6, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        test.finishPiece(15)
        assertEquals(listOf(995, 5, 6, 8, 9, 10, 11, 12, 13, 14), currentDownloadingNormalPriorityPieces)

        test.finishPiece(5)
        assertEquals(listOf(995, 6, 8, 9, 10, 11, 12, 13, 14, 16), currentDownloadingNormalPriorityPieces)

        test.finishPiece(17)
        assertEquals(listOf(995, 6, 8, 9, 10, 11, 12, 13, 14, 16), currentDownloadingNormalPriorityPieces)

        test.finishPiece(995)
        assertEquals(listOf(6, 8, 9, 10, 11, 12, 13, 14, 16, 18), currentDownloadingNormalPriorityPieces)
    }


    /*@Test
    fun `test`() {
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()
        var currentDownloadingNormalPriorityPieces: List<Int> = emptyList()

        val test = Test(
            pieceListSize = 1000,
            tdcWindowSize = 10,
            tdcHeaderSize = 5,
            tdcFooterSize = 3,
            tdcPossibleFooterSize = 12
        ) { highList, normalList ->
            currentDownloadingHighPriorityPieces = highList
            currentDownloadingNormalPriorityPieces = normalList
        }

        assertEquals(emptyList(), currentDownloadingNormalPriorityPieces)

        test.resume()
        assertEquals(13, currentDownloadingNormalPriorityPieces.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), currentDownloadingNormalPriorityPieces)
        assertEquals(988..999, currentDownloadingNormalPriorityPieces)

        // resume window 内的 piece 会使 window 向后滑动
        test.finishPiece(0)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), currentDownloadingNormalPriorityPieces)

        test.finishPiece(1)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11), currentDownloadingNormalPriorityPieces)

        test.finishPiece(5)
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 10, 11, 12), currentDownloadingNormalPriorityPieces)

        test.finishPiece(12)
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 10, 11, 13), currentDownloadingNormalPriorityPieces)

        // resume window 外的 piece 不会使 window 向后滑动
        finishPiece(100)
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 10, 11, 13), currentDownloadingNormalPriorityPieces.sorted())

        // 不会重复请求已经完成的 piece
        (10..99).forEach { finishPiece(it) }
        assertEquals(listOf(2, 3, 4, 6, 7, 8, 9, 101, 102, 103), currentDownloadingNormalPriorityPieces)

        // seek 到 200
        controller.onSeek(200)
        assertEquals(listOf(200, 201, 202, 203, 204, 205, 206, 207, 208, 209), currentDownloadingNormalPriorityPieces)

        (200..220).forEach { finishPiece(it) }
        assertEquals(listOf(997, 998, 999, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230), currentDownloadingNormalPriorityPieces)

        // seek 到前面已经完成的部分, window 应该填充 50 后的前十个未完成的 piece
        controller.onSeek(50)
        assertEquals(listOf(101, 102, 103, 104, 105, 106, 107, 108, 109, 110), currentDownloadingNormalPriorityPieces)

        // 200 - 220 已经完成了
        controller.onSeek(200)
        assertEquals(listOf(221, 222, 223, 224, 225, 226, 227, 228, 229, 230), currentDownloadingNormalPriorityPieces)

        (101..109 step 2).forEach { finishPiece(it) }
        controller.onSeek(50)
        assertEquals(listOf(102, 104, 106, 108, 110, 111, 112, 113, 114, 115), currentDownloadingNormalPriorityPieces)

        // 完成很多 piece
        (0..994).forEach { finishPiece(it) }
        controller.onSeek(100)
        assertEquals(listOf(995, 996), currentDownloadingNormalPriorityPieces)

        // 测试边界 piece
        (998..999).forEach { finishPiece(it) }
        controller.onSeek(100)
        assertEquals(listOf(995, 996, 997), currentDownloadingNormalPriorityPieces.sorted())

        finishPiece(996)
        controller.onSeek(100)
        assertEquals(listOf(995, 997), currentDownloadingNormalPriorityPieces.sorted())
    }*/
}