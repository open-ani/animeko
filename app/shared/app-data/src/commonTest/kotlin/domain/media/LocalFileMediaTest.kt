/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileUriMediaDataProvider
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFileMediaTest {
    @Test
    fun `recognizes video files by extension ignoring case`() {
        assertTrue(LocalFileMedia.isVideoFile(Path("/videos/episode-01.mkv")))
        assertTrue(LocalFileMedia.isVideoFile(Path("/videos/EPISODE-01.MP4")))
        assertFalse(LocalFileMedia.isVideoFile(Path("/videos/episode-01.ass")))
        assertFalse(LocalFileMedia.isVideoFile(Path("/videos/mkv")))
        assertFalse(LocalFileMedia.isVideoFile(Path("/downloads/Ani-4.12.0.dmg")))
    }

    @Test
    fun `finds the first video file among the given files`() {
        val video = Path("/videos/episode-01.mkv")
        assertEquals(
            video.inSystem,
            LocalFileMedia.findVideoFile(listOf(Path("/videos/episode-01.ass"), video, Path("/videos/episode-02.mkv"))),
        )
        assertNull(LocalFileMedia.findVideoFile(listOf(Path("/videos/episode-01.ass"))))
        assertNull(LocalFileMedia.findVideoFile(emptyList()))
    }

    @Test
    fun `creates a local media that the local file resolver can play`() {
        val file = Path("/videos/episode-01.mkv").inSystem
        val media = LocalFileMedia.create(file)

        assertTrue(LocalFileMedia.isLocalFile(media))
        assertEquals("episode-01.mkv", media.originalTitle)
        assertEquals(MediaSourceLocation.Local, media.location)
        val download = assertIs<ResourceLocation.LocalFile>(media.download)
        assertEquals(file.absolutePath, download.filePath)
        assertNull(download.fileType)
        assertTrue(LocalFileMediaResolver().supports(media))
    }

    @Test
    fun `hints the player about MPEG-TS files`() {
        val download = assertIs<ResourceLocation.LocalFile>(
            LocalFileMedia.create(Path("/videos/episode-01.TS").inSystem).download,
        )
        assertEquals(ResourceLocation.LocalFile.FileType.MPTS, download.fileType)
    }

    @Test
    fun `different files are different media and the same file is the same media`() {
        val first = LocalFileMedia.create(Path("/videos/episode-01.mkv").inSystem)
        assertEquals(first, LocalFileMedia.create(Path("/videos/episode-01.mkv").inSystem))
        assertNotEquals(first.mediaId, LocalFileMedia.create(Path("/videos/episode-02.mkv").inSystem).mediaId)
    }

    @Test
    fun `content uri media is resolved as uri media data`() = runTest {
        val uri = "content://com.android.providers.media.documents/document/video%3A1"
        val media = LocalFileMedia.createFromContentUri(uri, "episode-01.mkv")

        assertTrue(LocalFileMedia.isLocalFile(media))
        assertEquals("episode-01.mkv", media.originalTitle)
        assertEquals(MediaSourceLocation.Local, media.location)
        val download = assertIs<ResourceLocation.LocalFile>(media.download)
        assertEquals(uri, download.filePath)
        assertEquals(uri, download.originalUri)
        assertNull(download.fileType)

        val provider = LocalFileMediaResolver().resolve(
            media,
            EpisodeMetadata(title = "episode", ep = null, sort = EpisodeSort(1)),
        )
        val uriProvider = assertIs<LocalFileUriMediaDataProvider>(provider)
        assertEquals(uri, uriProvider.uri)
    }
}
