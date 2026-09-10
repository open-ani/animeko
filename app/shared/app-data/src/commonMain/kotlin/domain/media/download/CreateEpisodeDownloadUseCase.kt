/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.danmaku.DanmakuRepository
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.CacheCreate
import me.him188.ani.utils.analytics.recordEvent

/** Persists one frozen plan item; application ownership and batching belong to [SubmitDownloadsUseCase]. */
class CreateEpisodeDownloadUseCase(
    private val downloadManager: MediaDownloadManager,
    private val danmakuRepository: DanmakuRepository,
) {
    suspend operator fun invoke(spec: EpisodeDownloadSpec): MediaCache {
        val storage = downloadManager.defaultStorageFor(spec.media)
        val cache = storage.cache(spec.media, spec.metadata, spec.episode.toEpisodeMetadata())

        // Persistence is the success boundary. Ancillary work cannot turn a saved download into a failure.
        downloadManager.backgroundScope.launch {
            danmakuRepository.cacheDanmakuIfNeeded(
                DanmakuFetchRequest(
                    subjectId = spec.subject.subjectId,
                    subjectPrimaryName = spec.subject.displayName,
                    subjectNames = spec.subject.allNames,
                    subjectPublishDate = spec.subject.airDate,
                    episodeId = spec.episode.episodeId,
                    episodeSort = spec.episode.sort,
                    episodeEp = spec.episode.ep,
                    episodeName = spec.episode.name,
                    filename = spec.media.originalTitle,
                    fileSize = cache.fileStats.first().totalSize.inBytes,
                    fileHash = null,
                    videoDuration = Duration.ZERO,
                ),
            )
        }
        downloadManager.backgroundScope.launch {
            Analytics.recordEvent(CacheCreate) {
                put("subject_id", spec.subject.subjectId)
                put("episode_id", spec.episode.episodeId)
                put("media_source_name", when (spec.media.kind) {
                    MediaSourceKind.WEB -> "web"
                    MediaSourceKind.BitTorrent -> "bt"
                    MediaSourceKind.LocalCache -> null
                })
            }
        }
        return cache
    }
}
