/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaFetcherSameSourceIdTest {
    private val sharedSourceId = "local-fs"

    private val request = MediaFetchRequest(
        subjectId = "1",
        episodeId = "1",
        subjectNames = listOf("测试"),
        episodeSort = EpisodeSort("01"),
        episodeName = "测试剧集",
    )

    private fun sourceWithOneMedia(mediaId: String) = TestHttpMediaSource(
        mediaSourceId = sharedSourceId,
        fetch = {
            SinglePagePagedSource {
                listOf(
                    MediaMatch(
                        createTestDefaultMedia(
                            mediaId = mediaId,
                            mediaSourceId = sharedSourceId,
                            originalUrl = "https://example.com/$mediaId",
                            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:$mediaId"),
                            originalTitle = mediaId,
                            publishedTime = 1,
                            properties = createTestMediaProperties(),
                            episodeRange = EpisodeRange.single(EpisodeSort(1)),
                            location = MediaSourceLocation.Local,
                            kind = MediaSourceKind.BitTorrent,
                        ),
                        MatchKind.EXACT,
                    ),
                ).asFlow()
            }
        },
    )

    private suspend fun createSession() = MediaSourceMediaFetcher(
        { MediaFetcherConfig.Default },
        listOf(
            createTestMediaSourceInstance(sourceWithOneMedia("first"), instanceId = "instance-1"),
            createTestMediaSourceInstance(sourceWithOneMedia("second"), instanceId = "instance-2"),
        ),
        currentCoroutineContext()[ContinuationInterceptor] ?: EmptyCoroutineContext,
    ).newSession(request)

    @Test
    fun `results from both instances reach cumulativeResults`() = runTest {
        val session = createSession()
        assertEquals(
            listOf("first", "second"),
            session.awaitCompletedResults().map { it.mediaId }.sorted(),
        )
    }
}
