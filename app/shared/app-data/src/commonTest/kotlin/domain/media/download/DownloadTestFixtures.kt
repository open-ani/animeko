/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation

internal fun testDownload(id: Int, subjectId: Int = 1, range: EpisodeRange? = null): TestMediaCache {
    val media = TestMediaList.first().copy(mediaId = "media-$id", episodeRange = range)
    return TestMediaCache(
        CachedMedia(media, "test-storage", ResourceLocation.LocalFile("/download-$id")),
        MediaCacheMetadata(subjectId.toString(), id.toString(), subjectNames = listOf("Subject"), episodeSort = EpisodeSort(id), episodeName = "Episode $id"),
    )
}

internal class DownloadTestStorage(
    override val engine: MediaCacheEngine = DummyMediaCacheEngine("test-storage"),
) : MediaCacheStorage {
    override val mediaSourceId = "test-storage"
    override val cacheMediaSource: MediaSource get() = error("Not used")
    override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())
    override val stats = MutableStateFlow(MediaStats.Zero)
    var create: suspend () -> MediaCache = { error("Not used") }
    override suspend fun restorePersistedCaches() = Unit
    override suspend fun cache(media: Media, metadata: MediaCacheMetadata, episodeMetadata: EpisodeMetadata, resume: Boolean) = create()
    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        val selected = listFlow.value.firstOrNull(predicate) ?: return false
        listFlow.value -= selected
        return true
    }
    override fun close() = Unit
}
