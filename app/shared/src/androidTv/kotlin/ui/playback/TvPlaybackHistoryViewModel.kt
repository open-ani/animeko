/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.playback.PlaybackHistorySyncStatusUiItem
import me.him188.ani.app.ui.playback.PlaybackHistoryViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents

/**
 * TV 播放记录页状态层: 复用手机端的 [PlaybackHistoryViewModel] (记录、待同步队列、删除与同步),
 * 只补充 TV 展示需要的映射与同步错误上报.
 */
class TvPlaybackHistoryViewModel : PlaybackHistoryViewModel() {
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    private val syncing = MutableStateFlow(false)
    private val errors = Channel<LoadError>(Channel.BUFFERED)
    val syncErrors = errors.receiveAsFlow()

    val uiState = combine(stateFlow, pendingSyncEpisodesFlow) { histories, pendingSync ->
        TvPlaybackHistoryUiState(
            histories = histories.toTvHistoryItems(),
            pendingSync = pendingSync.map { it.toTvSyncItem() },
        )
    }.stateInBackground(TvPlaybackHistoryUiState.Empty)

    fun onIntent(intent: TvPlaybackHistoryIntent) {
        when (intent) {
            TvPlaybackHistoryIntent.Sync -> requestSync()
            is TvPlaybackHistoryIntent.OpenEpisode ->
                navigation.emit(TvNavigationEvent.Episode(intent.subjectId, intent.episodeId))

            is TvPlaybackHistoryIntent.Delete -> delete(intent.episodeIds)
            is TvPlaybackHistoryIntent.DeletePending -> deletePendingItems(listOf(intent.item.toSyncStatusItem()))
            TvPlaybackHistoryIntent.DeleteAllPending ->
                deletePendingItems(uiState.value.pendingSync.map { it.toSyncStatusItem() })
        }
    }

    private fun requestSync() {
        if (syncing.value) return
        backgroundScope.launch {
            syncing.value = true
            try {
                syncOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.send(LoadError.fromException(e))
            } finally {
                syncing.value = false
            }
        }
    }

    private fun TvPlaybackSyncItem.toSyncStatusItem() = PlaybackHistorySyncStatusUiItem(
        episodeId = episodeId,
        // 手机端同步状态页用它展示操作名; TV 只需要 id 来删除队列项.
        operationNames = emptyList(),
        subjectName = subjectName,
        episodeName = episodeName,
        versionMillis = versionMillis,
        playbackOpId = playbackOpId,
        collectionOpId = collectionOpId,
    )
}
