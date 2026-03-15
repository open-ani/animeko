/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadProgress
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.utils.httpdownloader.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpMediaCacheEngineProgressTest {
    @Test
    fun `uses segment progress for hls downloads`() {
        val progress = testProgress(
            mediaType = MediaType.M3U8,
            totalSegments = 8,
            downloadedSegments = 3,
            downloadedBytes = 1200,
            totalBytes = -1,
        ).toHttpCacheProgress()

        assertEquals((3f / 8f).toProgress(), progress)
    }

    @Test
    fun `uses byte progress for mp4 downloads`() {
        val progress = testProgress(
            mediaType = MediaType.MP4,
            totalSegments = 1,
            downloadedSegments = 0,
            downloadedBytes = 25,
            totalBytes = 100,
        ).toHttpCacheProgress()

        assertEquals(0.25f.toProgress(), progress)
    }

    @Test
    fun `returns unspecified when total bytes are unknown for file downloads`() {
        val progress = testProgress(
            mediaType = MediaType.MP4,
            totalSegments = 1,
            downloadedSegments = 0,
            downloadedBytes = 25,
            totalBytes = -1,
        ).toHttpCacheProgress()

        assertEquals(Progress.Unspecified, progress)
    }

    @Test
    fun `returns complete when downloader reports completed`() {
        val progress = testProgress(
            mediaType = MediaType.M3U8,
            totalSegments = 8,
            downloadedSegments = 7,
            downloadedBytes = 1200,
            totalBytes = -1,
            status = DownloadStatus.COMPLETED,
        ).toHttpCacheProgress()

        assertEquals(1f.toProgress(), progress)
    }

    private fun testProgress(
        mediaType: MediaType,
        totalSegments: Int,
        downloadedSegments: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        status: DownloadStatus = DownloadStatus.DOWNLOADING,
    ) = DownloadProgress(
        downloadId = DownloadId("test"),
        url = "https://example.com/test",
        mediaType = mediaType,
        totalSegments = totalSegments,
        downloadedSegments = downloadedSegments,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        status = status,
    )
}
