/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.playback

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.repository.player.PlaybackHistoryPendingOp
import me.him188.ani.app.ui.playback.PendingSyncEpisode
import me.him188.ani.app.ui.playback.PlaybackHistoryUiItem
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@Immutable
data class TvPlaybackHistoryUiState(
    val histories: List<PlaybackHistoryUiItem> = emptyList(),
    val pendingSync: List<TvPlaybackSyncItem> = emptyList(),
    /** 手动同步进行中; 刷新入口据此避免重复请求. */
    val syncing: Boolean = false,
) {
    companion object {
        val Empty = TvPlaybackHistoryUiState()
    }
}

/** 待同步的一集及其操作, 已转换成界面可直接展示的数据. */
@Immutable
data class TvPlaybackSyncItem(
    val episodeId: Int,
    val subjectName: String?,
    val episodeName: String?,
    val versionMillis: Long,
    val operations: List<TvPlaybackSyncOperation>,
    val playbackOpId: Long?,
    val collectionOpId: Long?,
)

/** 待同步操作类型, 界面按当前语言映射文案. */
enum class TvPlaybackSyncOperation {
    ProgressUpsert,
    ProgressDelete,
    MarkWatched,
    UnmarkWatched,
}

sealed interface TvPlaybackHistoryIntent {
    data object Sync : TvPlaybackHistoryIntent

    data class OpenEpisode(val subjectId: Int, val episodeId: Int) : TvPlaybackHistoryIntent

    data class Delete(val episodeIds: Collection<Int>) : TvPlaybackHistoryIntent

    data class DeletePending(val item: TvPlaybackSyncItem) : TvPlaybackHistoryIntent

    data object DeleteAllPending : TvPlaybackHistoryIntent
}

/** 列表过滤已删除记录, 并按最近播放时间降序, 与手机端播放记录页一致. */
internal fun List<EpisodeHistory>.toTvHistoryItems(): List<PlaybackHistoryUiItem> =
    asSequence()
        .filterNot(EpisodeHistory::isDeleted)
        .sortedByDescending(EpisodeHistory::updatedAtMillis)
        .map {
            PlaybackHistoryUiItem(
                episodeId = it.episodeId,
                subjectId = it.subjectId,
                episodeSort = it.episodeSort,
                subjectName = it.subjectName,
                subjectImageUrl = it.subjectImageUrl,
                episodeName = it.episodeName,
                positionMillis = it.positionMillis,
                durationMillis = it.durationMillis,
                updatedAtMillis = it.updatedAtMillis,
            )
        }
        .toList()

/** 操作名按播放进度、看过状态的顺序列出, 与手机端同步状态页一致. */
internal fun PendingSyncEpisode.toTvSyncItem(): TvPlaybackSyncItem = TvPlaybackSyncItem(
    episodeId = episodeId,
    subjectName = subjectName,
    episodeName = episodeName,
    versionMillis = versionMillis,
    operations = buildList {
        when (playbackOp) {
            is PlaybackHistoryPendingOp.Upsert -> add(TvPlaybackSyncOperation.ProgressUpsert)
            is PlaybackHistoryPendingOp.Delete -> add(TvPlaybackSyncOperation.ProgressDelete)
            null -> Unit
        }
        collectionOp?.let { op ->
            add(
                if (op.collectionType == UnifiedCollectionType.DONE) {
                    TvPlaybackSyncOperation.MarkWatched
                } else {
                    TvPlaybackSyncOperation.UnmarkWatched
                },
            )
        }
    },
    playbackOpId = playbackOp?.id,
    collectionOpId = collectionOp?.id,
)
