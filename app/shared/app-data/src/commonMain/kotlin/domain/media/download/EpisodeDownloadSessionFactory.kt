/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.cache.storage.contains
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.datasources.api.topic.isSingleEpisode
import me.him188.ani.datasources.api.unwrapCached

class EpisodeDownloadSessionFactory(
    private val subjects: SubjectCollectionRepository,
    private val episodes: EpisodeCollectionRepository,
    private val preferences: EpisodePreferencesRepository,
    private val sources: MediaSourceManager,
    private val selectors: MediaSelectorFactory,
    private val caches: MediaCacheManager,
    private val createDownload: CreateEpisodeDownloadUseCase,
) {
    fun create(subjectId: Int, scope: CoroutineScope) = AddEpisodeDownloadSession(
        parentScope = scope,
        prepare = { episodeId, requestScope -> prepare(subjectId, episodeId, requestScope) },
        findReusableDownload = ::findReusableDownload,
        availableStorages = ::availableStorages,
        createDownload = { createDownload(it) },
    )

    private suspend fun prepare(subjectId: Int, episodeId: Int, scope: CoroutineScope): DownloadMediaSelection {
        val request = EpisodeDownloadRequest(
            subjects.subjectCollectionFlow(subjectId).first().subjectInfo,
            episodes.episodeCollectionInfoFlow(subjectId, episodeId).first().episodeInfo,
        )
        val fetchSession = sources.mediaFetcher.first().newSession(
            MediaFetchRequest.create(request.subject, request.episode),
            scope.coroutineContext,
        )
        val selector = selectors.create(subjectId, episodeId, fetchSession.cumulativeResults, scope.coroutineContext)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            selector.events.onChangePreference.collect { preference ->
                caches.backgroundScope.async { preferences.setMediaPreference(subjectId, preference) }.await()
            }
        }
        // Keep an explicitly started search alive while its picker is temporarily hidden.
        scope.launch { fetchSession.cumulativeResults.collect {} }
        return DownloadMediaSelection(request, fetchSession, selector) { media ->
            selectMediaAndSavePreference(selector, media) { preference ->
                caches.backgroundScope.async { preferences.setMediaPreference(subjectId, preference) }.await()
            }
        }
    }

    private suspend fun findReusableDownload(selection: DownloadMediaSelection): ReusableDownload? {
        val request = selection.request
        val existing = findReusableSeasonDownload(request.episode, caches.listCacheForSubject(request.subject.subjectId).first()) ?: return null
        val media = existing.origin.unwrapCached()
        val storage = availableStorages(media).firstOrNull { it.contains(existing) }
        return ReusableDownload(media, storage)
    }

    private suspend fun availableStorages(media: Media): List<MediaCacheStorage> =
        downloadStoragesFor(media, caches.enabledStorages.first())
}

/** Capture the selection event before selecting and persist it before submitting the download. */
internal suspend fun selectMediaAndSavePreference(
    selector: MediaSelector,
    media: Media,
    save: suspend (MediaPreference) -> Unit,
) = coroutineScope {
    val preference = async(start = CoroutineStart.UNDISPATCHED) { selector.events.onChangePreference.first() }
    try {
        if (selector.select(media)) save(preference.await())
    } finally {
        preference.cancel()
    }
}

internal fun findReusableSeasonDownload(episode: EpisodeInfo, downloads: List<MediaCache>): MediaCache? =
    downloads.firstOrNull { download ->
        val range = download.origin.episodeRange
        range != null && !range.isSingleEpisode() &&
                (episode.ep?.let { range.contains(it) } == true || range.contains(episode.sort))
    }

internal fun downloadStoragesFor(media: Media, storages: List<MediaCacheStorage>): List<MediaCacheStorage> {
    val supported = storages.filter { it.engine.supports(media) }
    // PikPak routes BT resources through the HTTP download engine when enabled.
    return if (media.kind == MediaSourceKind.BitTorrent && supported.any { it.engine.engineKey == MediaCacheEngineKey.WebM3u }) {
        supported.filter { it.engine.engineKey == MediaCacheEngineKey.WebM3u }
    } else supported
}
