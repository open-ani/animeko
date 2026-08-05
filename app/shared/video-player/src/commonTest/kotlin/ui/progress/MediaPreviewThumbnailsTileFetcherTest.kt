/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.api.MediaPreviewThumbnails
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MediaPreviewThumbnailsTileFetcherTest {
    @Test
    fun testTileIndexAndCoordinatesCalculation() {
        val thumbnails = thumbnails(
            width = 320,
            height = 180,
            intervalMillis = 10_000L,
            totalCount = 240,
        )
        val layout = thumbnails.layout as MediaPreviewThumbnails.Layout.SpriteTile

        assertEquals(SpriteTileFrame(tileIndex = 0, cropX = 0, cropY = 0), calculateSpriteTileFrame(thumbnails, layout, 0))
        assertEquals(
            SpriteTileFrame(tileIndex = 0, cropX = 1_600, cropY = 0),
            calculateSpriteTileFrame(thumbnails, layout, 55_000),
        )
        assertEquals(
            SpriteTileFrame(tileIndex = 1, cropX = 0, cropY = 0),
            calculateSpriteTileFrame(thumbnails, layout, 1_000_000),
        )
        assertEquals(
            SpriteTileFrame(tileIndex = 1, cropX = 1_600, cropY = 0),
            calculateSpriteTileFrame(thumbnails, layout, 1_050_000),
        )
        assertEquals(
            SpriteTileFrame(tileIndex = 2, cropX = 2_880, cropY = 540),
            calculateSpriteTileFrame(thumbnails, layout, Long.MAX_VALUE),
        )
    }

    @Test
    fun testInvalidAndOverflowingMetadataIsRejected() {
        val zeroCount = thumbnails(totalCount = 0)
        assertNull(calculateSpriteTileFrame(zeroCount, zeroCount.layout as MediaPreviewThumbnails.Layout.SpriteTile, 0))

        val overflowing = thumbnails(
            width = Int.MAX_VALUE,
            totalCount = 3,
            columns = Int.MAX_VALUE,
            rows = 1,
        )
        assertNull(
            calculateSpriteTileFrame(
                overflowing,
                overflowing.layout as MediaPreviewThumbnails.Layout.SpriteTile,
                20_000,
            ),
        )
    }

    @Test
    fun testFetchPropagatesCancellation() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            requestStarted.complete(Unit)
            awaitCancellation()
        })
        try {
            val fetcher = MediaPreviewThumbnailsTileFetcher(thumbnails(), client.asScopedHttpClient())
            val fetch = async { fetcher.fetchFrame(0) }
            requestStarted.await()
            fetch.cancel()
            assertFailsWith<CancellationException> { fetch.await() }
        } finally {
            client.close()
        }
    }

    private fun thumbnails(
        width: Int = 320,
        height: Int = 180,
        intervalMillis: Long = 10_000,
        totalCount: Int = 240,
        columns: Int = 10,
        rows: Int = 10,
    ) = MediaPreviewThumbnails(
        width = width,
        height = height,
        intervalMillis = intervalMillis,
        totalCount = totalCount,
        layout = MediaPreviewThumbnails.Layout.SpriteTile(
            columns = columns,
            rows = rows,
            urlPattern = "https://example.com/tiles/{tileIndex}.jpg",
        ),
    )
}
