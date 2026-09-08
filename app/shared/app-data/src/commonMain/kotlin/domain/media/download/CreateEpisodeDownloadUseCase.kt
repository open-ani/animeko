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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.danmaku.DanmakuRepository
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.CacheCreate
import me.him188.ani.utils.analytics.recordEvent

/** A submitted download belongs to the application even if its originating page is closed. */
class CreateEpisodeDownloadUseCase(
    private val downloadManager: MediaDownloadManager,
    private val danmakuRepository: DanmakuRepository,
) {
    suspend operator fun invoke(target: DownloadTarget) {
        downloadManager.backgroundScope.async {
            val request = target.selection.request
            val metadata = MediaCacheMetadata(target.selection.fetchSession.request.first())
            val cache = target.storage.cache(target.media, metadata, request.episode.toEpisodeMetadata())

            // Persistence is the success boundary. Ancillary work cannot turn a saved download into a failure.
            downloadManager.backgroundScope.launch {
                danmakuRepository.cacheDanmakuIfNeeded(
                    DanmakuFetchRequest(
                        subjectId = request.subject.subjectId,
                        subjectPrimaryName = request.subject.displayName,
                        subjectNames = request.subject.allNames,
                        subjectPublishDate = request.subject.airDate,
                        episodeId = request.episode.episodeId,
                        episodeSort = request.episode.sort,
                        episodeEp = request.episode.ep,
                        episodeName = request.episode.name,
                        filename = target.media.originalTitle,
                        fileSize = cache.fileStats.first().totalSize.inBytes,
                        fileHash = null,
                        videoDuration = Duration.ZERO,
                    ),
                )
            }
            downloadManager.backgroundScope.launch {
                Analytics.recordEvent(CacheCreate) {
                    put("subject_id", request.subject.subjectId)
                    put("episode_id", request.episode.episodeId)
                    put("media_source_name", when (target.media.kind) {
                        MediaSourceKind.WEB -> "web"
                        MediaSourceKind.BitTorrent -> "bt"
                        MediaSourceKind.LocalCache -> null
                    })
                }
            }
        }.await()
    }
}
