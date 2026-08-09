/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.api.MediaPreviewThumbnails
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MediaPreviewThumbnailsTileFetcherDesktopTest {
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testFetchUsesInjectedClientHeadersAndTileCache() = runTest {
        var requestCount = 0
        val client = HttpClient(MockEngine { request ->
            requestCount++
            assertEquals("https://example.com/tiles/0.jpg", request.url.toString())
            assertEquals("token", request.headers["Authorization"])
            respond(Base64.decode(ONE_PIXEL_PNG))
        })
        try {
            val fetcher = MediaPreviewThumbnailsTileFetcher(
                thumbnails(
                    width = 1,
                    height = 1,
                    intervalMillis = 10_000,
                    totalCount = 1,
                    columns = 1,
                    rows = 1,
                    headers = mapOf("Authorization" to "token"),
                ),
                client.asScopedHttpClient(),
            )

            assertNotNull(fetcher.fetchFrame(0))
            assertNotNull(fetcher.fetchFrame(0))
            assertEquals(1, requestCount)
        } finally {
            client.close()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testFetchUsesMediaSourceRequesterAndTileCache() = runTest {
        var requestCount = 0
        val client = HttpClient(MockEngine { error("Direct HTTP request is not expected") })
        try {
            val fetcher = MediaPreviewThumbnailsTileFetcher(
                thumbnails(
                    width = 1,
                    height = 1,
                    intervalMillis = 10_000,
                    totalCount = 1,
                    columns = 1,
                    rows = 1,
                    requesterMediaSourceId = "jellyfin-instance",
                ),
                client.asScopedHttpClient(),
            ) { mediaSourceId, url ->
                requestCount++
                assertEquals("jellyfin-instance", mediaSourceId)
                assertEquals("https://example.com/tiles/0.jpg", url)
                Base64.decode(ONE_PIXEL_PNG)
            }

            assertNotNull(fetcher.fetchFrame(0))
            assertNotNull(fetcher.fetchFrame(0))
            assertEquals(1, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun testOldMediaRequestCannotRepopulateState() = runTest {
        val firstRequest = CompletableDeferred<ImageBitmap?>()
        val requestStarted = CompletableDeferred<Unit>()
        val image = ImageBitmap(1, 1)
        var requestCount = 0
        val state = MediaProgressFramePreviewState(
            fetchFrame = {
                requestCount++
                if (requestCount == 1) {
                    requestStarted.complete(Unit)
                    firstRequest.await()
                } else {
                    image
                }
            },
            debounceMillis = 0,
        )

        val oldRequest = async { state.requestFrame(0) }
        requestStarted.await()
        state.onMediaChanged()
        firstRequest.complete(image)
        oldRequest.await()
        assertNull(state.frame)

        state.requestFrame(0)
        assertEquals(2, requestCount)
        assertEquals(image, state.frame)
    }

    private fun thumbnails(
        width: Int = 320,
        height: Int = 180,
        intervalMillis: Long = 10_000,
        totalCount: Int = 240,
        columns: Int = 10,
        rows: Int = 10,
        headers: Map<String, String> = emptyMap(),
        requesterMediaSourceId: String? = null,
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
        headers = headers,
        requesterMediaSourceId = requesterMediaSourceId,
    )

    private companion object {
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
