/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.share

import me.him188.ani.app.domain.media.LocalFileMedia
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import org.openani.mediamp.source.UriMediaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MediaShareDataTest {
    private fun media(download: ResourceLocation, originalUrl: String): Media = DefaultMedia(
        mediaId = "test:$originalUrl",
        mediaSourceId = "test",
        originalUrl = originalUrl,
        download = download,
        originalTitle = "test",
        publishedTime = 0,
        properties = MediaProperties(
            subjectName = null,
            episodeName = null,
            subtitleLanguageIds = emptyList(),
            resolution = "",
            alliance = "",
            size = FileSize.Unspecified,
            subtitleKind = null,
        ),
        episodeRange = null,
        location = MediaSourceLocation.Local,
        kind = MediaSourceKind.WEB,
    )

    @Test
    fun `https uri becomes streaming file`() {
        val data = MediaShareData.from(
            media = media(ResourceLocation.WebVideo("https://example.com/page"), "https://example.com/page"),
            mediaData = UriMediaData("https://example.com/video.m3u8"),
        )
        assertEquals("https://example.com/video.m3u8", assertIs<ResourceLocation.HttpStreamingFile>(data.download).uri)
    }

    @Test
    fun `file uri becomes streaming file`() {
        val data = MediaShareData.from(
            media = media(ResourceLocation.LocalFile("/tmp/a.mp4"), "file:///tmp/a.mp4"),
            mediaData = UriMediaData("file:///tmp/a.mp4"),
        )
        assertEquals("file:///tmp/a.mp4", assertIs<ResourceLocation.HttpStreamingFile>(data.download).uri)
    }

    @Test
    fun `content uri does not throw and falls back to media download`() {
        val localFile = ResourceLocation.LocalFile(
            "content://com.android.externalstorage.documents/document/primary%3AMovies%2Fqa.mp4",
        )
        val data = MediaShareData.from(
            media = media(localFile, "content://com.android.externalstorage.documents/document/primary%3AMovies%2Fqa.mp4"),
            mediaData = UriMediaData("content://com.android.externalstorage.documents/document/primary%3AMovies%2Fqa.mp4"),
        )
        assertEquals(localFile, data.download)
    }

    @Test
    fun `content uri without media falls back to null`() {
        val data = MediaShareData.from(
            media = null,
            mediaData = UriMediaData("content://com.android.externalstorage.documents/document/primary%3AMovies%2Fqa.mp4"),
        )
        assertEquals(null, data.download)
    }

    @Test
    fun `local file media created from content uri keeps local file download`() {
        val media = LocalFileMedia.createFromContentUri(
            "content://com.android.externalstorage.documents/document/primary%3AMovies%2Fqa.mp4",
            displayName = "qa.mp4",
        )
        val data = MediaShareData.from(media, UriMediaData(media.originalUrl))
        assertEquals(media.download, data.download)
        assertIs<ResourceLocation.LocalFile>(data.download)
    }
}
