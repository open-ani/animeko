/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlin.test.Test
import kotlin.test.assertEquals
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.playback.PlaybackHistoryUiItem
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme

class TvPlaybackHistoryUiTest {

    @Test
    fun emptyHistoryShowsEmptyStateWithoutSelectionEntry() = runAniComposeUiTest {
        mount(TvPlaybackHistoryUiState())
        onNodeWithTag("tv-playback-history-empty").assertExists()
        onNodeWithTag("tv-playback-history-select").assertDoesNotExist()
    }

    @Test
    fun historyRowOpensEpisodeAndSelectionDeletes() = runAniComposeUiTest {
        val intents = mutableListOf<TvPlaybackHistoryIntent>()
        mount(
            TvPlaybackHistoryUiState(histories = listOf(history(1), history(2))),
            { intents += it },
        )

        // 进页焦点落在列表首条; 确认键直接续播.
        awaitFocus("tv-playback-history-item-1")
        key(Key.DirectionCenter)
        assertEquals(listOf<TvPlaybackHistoryIntent>(TvPlaybackHistoryIntent.OpenEpisode(1, 1)), intents)

        // 首行按上到操作行, 右移到"选择"并进入多选.
        key(Key.DirectionUp)
        awaitFocus("tv-playback-history-sync")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-history-refresh")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-history-select")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-history-item-1")

        // 选中两集后删除.
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        awaitFocus("tv-playback-history-item-2")
        key(Key.DirectionCenter)
        key(Key.DirectionUp)
        awaitFocus("tv-playback-history-item-1")
        key(Key.DirectionUp)
        awaitFocus("tv-playback-history-select-all")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-history-delete")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-history-delete-cancel")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-history-delete-confirm")
        key(Key.DirectionCenter)

        val delete = intents.last() as TvPlaybackHistoryIntent.Delete
        assertEquals(setOf(1, 2), delete.episodeIds.toSet())
        onNodeWithTag("tv-playback-history-selection-chips").assertDoesNotExist()
    }

    @Test
    fun syncPanelListsPendingItemsAndDeletes() = runAniComposeUiTest {
        val intents = mutableListOf<TvPlaybackHistoryIntent>()
        val pending = TvPlaybackSyncItem(
            episodeId = 10,
            subjectName = "Subject",
            episodeName = "Episode 1",
            versionMillis = 0,
            operations = listOf(TvPlaybackSyncOperation.ProgressUpsert),
            playbackOpId = 1,
            collectionOpId = null,
        )
        mount(TvPlaybackHistoryUiState(pendingSync = listOf(pending)), { intents += it })

        // 无记录时焦点在操作行首项 (同步状态), 进入待同步面板后落在第一条.
        awaitFocus("tv-playback-history-sync")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-sync-item-10")
        key(Key.DirectionCenter)
        assertEquals(TvPlaybackHistoryIntent.DeletePending(pending), intents.last())

        // 首行按上到面板操作行 (返回), 右移全部删除, 再返回列表.
        key(Key.DirectionUp)
        awaitFocus("tv-playback-sync-back")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-sync-delete-all")
        key(Key.DirectionCenter)
        assertEquals(TvPlaybackHistoryIntent.DeleteAllPending, intents.last())

        key(Key.DirectionLeft)
        awaitFocus("tv-playback-sync-back")
        key(Key.DirectionCenter)
        onNodeWithTag("tv-playback-sync-list").assertDoesNotExist()
        onNodeWithTag("tv-playback-history-chips").assertExists()
    }

    private fun history(id: Int) = PlaybackHistoryUiItem(
        episodeId = id,
        subjectId = 1,
        episodeSort = id.toFloat(),
        subjectName = "Subject $id",
        subjectImageUrl = null,
        episodeName = "Episode $id",
        positionMillis = 60_000,
        durationMillis = 1_440_000,
        updatedAtMillis = 100L * id,
    )

    private fun AniComposeUiTest.mount(
        state: TvPlaybackHistoryUiState,
        onIntent: (TvPlaybackHistoryIntent) -> Unit = {},
    ) {
        val timeFormatter = TimeFormatter()
        setContent {
            CompositionLocalProvider(LocalTimeFormatter provides timeFormatter) {
                TvApplicationTheme(ThemeSettings.Default.seedColor, languageTag = "en") {
                    TvPlaybackHistoryScreen(
                        state = state,
                        onIntent = onIntent,
                        modifier = Modifier.fillMaxSize().testTag("tv-playback-history-test"),
                    )
                }
            }
        }
    }

    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
    }

    private fun AniComposeUiTest.awaitFocus(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(tag).assertIsFocused()
    }
}
