/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.EpisodeRange

class DownloadSelectionPolicyTest {
    @Test
    fun `reuse requires a season range matching episode sort or ep`() {
        val season = testDownload(1, range = EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
        val single = testDownload(2, range = EpisodeRange.single(EpisodeSort(2)))
        val unknown = testDownload(3)
        val episode = EpisodeInfo.Empty.copy(sort = EpisodeSort(14), ep = EpisodeSort(2))
        assertSame(season, findReusableSeasonDownload(episode, listOf(single, unknown, season)))
        assertSame(season, findReusableSeasonDownload(episode.copy(sort = EpisodeSort(2), ep = null), listOf(season)))
        assertNull(findReusableSeasonDownload(episode.copy(ep = null), listOf(season)))
        assertNull(findReusableSeasonDownload(episode, listOf(single, unknown)))
        assertNull(findReusableSeasonDownload(episode, emptyList()))
    }

    @Test
    fun `default storage prefers supported HTTP engine for BT media`() = runTest {
        val torrent = storage(MediaCacheEngineKey.Anitorrent)
        val web = storage(MediaCacheEngineKey.WebM3u)
        val unsupported = storage(MediaCacheEngineKey.WebM3u, supports = false)
        val otherWeb = storage(MediaCacheEngineKey.WebM3u)
        val media = TestMediaList.first().copy(kind = MediaSourceKind.BitTorrent)
        val manager = MediaDownloadManager(listOf(torrent, unsupported, web, otherWeb), backgroundScope)

        assertSame(web, manager.defaultStorageFor(media))
    }

    @Test
    fun `default storage uses torrent engine when HTTP cannot handle BT media`() = runTest {
        val torrent = storage(MediaCacheEngineKey.Anitorrent)
        val web = storage(MediaCacheEngineKey.WebM3u, supports = false)
        val manager = MediaDownloadManager(listOf(web, torrent), backgroundScope)

        assertSame(torrent, manager.defaultStorageFor(TestMediaList.first().copy(kind = MediaSourceKind.BitTorrent)))
    }

    @Test
    fun `multiple compatible storages use the first registered default`() = runTest {
        val unsupported = storage(MediaCacheEngineKey.Anitorrent, supports = false)
        val first = storage(MediaCacheEngineKey.WebM3u)
        val second = storage(MediaCacheEngineKey.WebM3u)
        val manager = MediaDownloadManager(listOf(unsupported, first, second), backgroundScope)

        assertSame(first, manager.defaultStorageFor(TestMediaList.first().copy(kind = MediaSourceKind.WEB)))
    }

    @Test
    fun `missing compatible storage fails instead of choosing an unsupported engine`() = runTest {
        val media = TestMediaList.first()
        val unsupported = storage(MediaCacheEngineKey.WebM3u, supports = false)

        assertFailsWith<IllegalStateException> {
            MediaDownloadManager(listOf(unsupported), backgroundScope).defaultStorageFor(media)
        }
        assertFailsWith<IllegalStateException> {
            MediaDownloadManager(emptyList(), backgroundScope).defaultStorageFor(media)
        }
    }

    private fun storage(key: MediaCacheEngineKey, supports: Boolean = true) = DownloadTestStorage(
        object : MediaCacheEngine by DummyMediaCacheEngine("test") {
            override val engineKey = key
            override fun supports(media: Media) = supports
        },
    )
}
