/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.TestMediaCache
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaFetchRequest
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

internal fun testDownloadSpec(id: Int, media: Media = testDownload(id).origin): EpisodeDownloadSpec {
    val subject = SubjectInfo.Empty.copy(subjectId = 1)
    val episode = EpisodeInfo.Empty.copy(episodeId = id, sort = EpisodeSort(id), ep = EpisodeSort(id), name = "Episode $id")
    return EpisodeDownloadSpec(subject, episode, media, MediaCacheMetadata(MediaFetchRequest.create(subject, episode)))
}

internal fun downloadFor(spec: EpisodeDownloadSpec) = TestMediaCache(
    CachedMedia(spec.media, "test-storage", ResourceLocation.LocalFile("/download-${spec.episode.episodeId}")),
    spec.metadata,
)

internal fun testDownloadSelection(
    id: Int,
    requestFlow: Flow<MediaFetchRequest>? = null,
): DownloadMediaSelection {
    val spec = testDownloadSpec(id)
    return DownloadMediaSelection(
        EpisodeDownloadRequest(spec.subject, spec.episode),
        object : MediaFetchSession {
            override val request = requestFlow ?: flowOf(MediaFetchRequest.create(spec.subject, spec.episode))
            override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()
            override val cumulativeResults = flowOf(TestMediaList)
            override val hasCompleted = flowOf(CompletedConditions.AllCompleted)
            override fun setFetchRequest(request: MediaFetchRequest) = Unit
        },
        DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
            mediaListNotCached = flowOf(TestMediaList),
            savedUserPreference = flowOf(MediaPreference.Empty),
            savedDefaultPreference = flowOf(MediaPreference.Empty),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
            enableCaching = false,
        ),
    )
}
