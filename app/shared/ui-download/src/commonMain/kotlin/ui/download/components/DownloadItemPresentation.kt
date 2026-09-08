/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.components

import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.download.DownloadSnapshot
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

internal fun DownloadSnapshot.toDownloadItem(
    collectionType: UnifiedCollectionType?,
    history: EpisodeHistory?,
): DownloadItem {
    val subjectId = metadata.subjectId.toIntOrNull() ?: 0
    val episodeId = metadata.episodeId.toIntOrNull() ?: 0
    return DownloadItem(
        subjectId = subjectId,
        episodeId = episodeId,
        id = id,
        sort = metadata.episodeSort,
        subjectName = metadata.subjectNameCN ?: metadata.subjectNames.firstOrNull().orEmpty(),
        displayName = metadata.episodeName,
        creationTime = metadata.creationTime,
        stats = DownloadItem.Stats(downloadSpeed, progress, totalSize),
        status = toDownloadStatus(status),
        playbackProgress = history.toPlaybackProgress(),
        engineKey = engineKey,
        subjectCollectionType = collectionType,
        playability = when {
            subjectId == 0 || episodeId == 0 -> DownloadItem.Playability.INVALID_SUBJECT_EPISODE_ID
            !canPlay -> DownloadItem.Playability.STREAMING_NOT_SUPPORTED
            else -> DownloadItem.Playability.PLAYABLE
        },
        mediaSourceId = sourceId,
    )
}

internal fun EpisodeHistory?.toPlaybackProgress(): Progress {
    if (this == null || isDeleted || positionMillis <= 0L) return Progress.Unspecified
    val duration = durationMillis?.takeIf { it > 0L } ?: return Progress.Unspecified
    return (positionMillis.toDouble() / duration.toDouble()).toFloat().toProgress()
}

internal fun toDownloadStatus(state: MediaCacheState): DownloadStatus {
    return when (state) {
        MediaCacheState.IN_PROGRESS -> DownloadStatus.IN_PROGRESS
        MediaCacheState.PAUSED -> DownloadStatus.PAUSED
        MediaCacheState.FAILED -> DownloadStatus.FAILED
        MediaCacheState.COMPLETED -> DownloadStatus.COMPLETED
    }
}
