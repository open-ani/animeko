/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.details

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.TestMediaSourceInfo
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaCacheMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MediaCacheDetailsScreenStateTest {
    @Test
    fun `falls back to origin details when cached media is unavailable`() = runTest {
        val mediaOrigin = TestMediaList.first()
        val cache = object : TestMediaCache(
            media = CachedMedia(mediaOrigin, "local-cache", mediaOrigin.download),
            metadata = MediaCacheMetadata(
                subjectId = "1",
                episodeId = "1",
                subjectNameCN = "subject",
                subjectNames = listOf("subject"),
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                episodeName = "episode",
            ),
        ) {
            override val origin = mediaOrigin

            override suspend fun getCachedMedia() = error("Download not completed")
        }

        val state = createMediaCacheDetailsScreenState(cache, TestMediaSourceInfo)

        val details = assertNotNull(state.details)
        assertEquals(mediaOrigin.originalTitle, details.originalTitle)
        assertEquals(mediaOrigin.originalUrl, details.originalUrl)
        assertEquals(TestMediaSourceInfo, details.sourceInfo)
        assertNull(details.localCacheFilePath)
        assertNull(details.contentDownloadUri)
    }

    @Test
    fun `returns placeholder when cache is missing`() = runTest {
        val state = createMediaCacheDetailsScreenState(mediaCache = null, sourceInfo = TestMediaSourceInfo)
        assertNull(state.details)
    }
}
