/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.playback

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
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
import me.him188.ani.tv.ui.main.TvMainShell
import me.him188.ani.tv.ui.main.TvMainUiState
import me.him188.ani.tv.ui.main.TvShellContent
import kotlin.test.assertTrue

class TvPlaybackHistoryUiTest {

    @Test
    fun emptyHistoryShowsEmptyStateWithoutSelectionEntry() = runAniComposeUiTest {
        mount(TvPlaybackHistoryUiState())
        onNodeWithTag("tv-playback-history-empty").assertExists()
        onNodeWithTag("tv-playback-history-sync-status").assertExists()
        onNodeWithTag("tv-playback-history-sync").assertDoesNotExist()
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
    fun syncDialogListsPendingItemsAndDeletes() = runAniComposeUiTest {
        val intents = mutableListOf<TvPlaybackHistoryIntent>()
        lateinit var backDispatcher: OnBackPressedDispatcher
        val pending = TvPlaybackSyncItem(
            episodeId = 10,
            subjectName = "Subject",
            episodeName = "Episode 1",
            versionMillis = 0,
            operations = listOf(TvPlaybackSyncOperation.ProgressUpsert),
            playbackOpId = 1,
            collectionOpId = null,
        )
        mount(
            TvPlaybackHistoryUiState(pendingSync = listOf(pending)),
            { intents += it },
            onBackDispatcher = { backDispatcher = it },
        )

        // 一级页面的同步状态按钮打开弹窗, 初始焦点在第一条待同步项.
        awaitFocus("tv-playback-history-sync")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-sync-item-10")
        onNodeWithTag("tv-playback-sync-dialog").assertExists()

        key(Key.DirectionCenter)
        assertEquals(TvPlaybackHistoryIntent.DeletePending(pending), intents.last())

        onNodeWithTag("tv-playback-sync-delete-all").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(TvPlaybackHistoryIntent.DeleteAllPending, intents.last())

        // 「关闭」按钮和遥控器返回键都能退出弹窗.
        onNodeWithTag("tv-playback-sync-close").performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithTag("tv-playback-sync-list").assertDoesNotExist()

        awaitFocus("tv-playback-history-sync")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-sync-item-10")
        runOnIdle { backDispatcher.onBackPressed() }
        onNodeWithTag("tv-playback-sync-list").assertDoesNotExist()
    }

    @Test
    fun menuKeyDoesNotLeaveTheDeleteDialogInTheShell() = runAniComposeUiTest {
        val intents = mutableListOf<TvPlaybackHistoryIntent>()
        mountInShell(
            TvPlaybackHistoryUiState(histories = listOf(history(1))),
            { intents += it },
        )

        // 进入多选并打开删除确认弹窗.
        awaitFocus("tv-playback-history-item-1")
        key(Key.DirectionUp)
        awaitFocus("tv-playback-history-refresh")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-history-select")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-history-item-1")
        key(Key.DirectionCenter)
        key(Key.DirectionUp)
        awaitFocus("tv-playback-history-select-all")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-history-delete")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-history-delete-cancel")

        // 弹窗打开时菜单键不应把焦点送进侧栏.
        key(Key.Menu)
        onNodeWithTag("tv-playback-history-delete-cancel").assertIsFocused()
        assertTrue(
            onAllNodes(hasTestTag("tv-main-navigation") and hasAnyDescendant(isFocused()))
                .fetchSemanticsNodes().isEmpty(),
            "Menu key must not move focus into the navigation rail while the page modal is open",
        )
    }

    @Test
    fun backClosesTheDeleteDialogWithoutLeavingThePage() = runAniComposeUiTest {
        val intents = mutableListOf<TvPlaybackHistoryIntent>()
        mountInShell(
            TvPlaybackHistoryUiState(histories = listOf(history(1))),
            { intents += it },
        )

        awaitFocus("tv-playback-history-item-1")
        key(Key.DirectionUp)
        awaitFocus("tv-playback-history-refresh")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-history-select")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-history-item-1")
        key(Key.DirectionCenter)
        key(Key.DirectionUp)
        awaitFocus("tv-playback-history-select-all")
        key(Key.DirectionRight)
        awaitFocus("tv-playback-history-delete")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-history-delete-cancel")

        // 返回键只关弹窗, 不应把内容切回探索页.
        key(Key.Back)
        onNodeWithTag("tv-playback-history-delete-dialog").assertDoesNotExist()
        onNodeWithTag("tv-playback-history").assertExists()

        // 焦点已回到多选操作行, 按键仍然有效.
        awaitFocus("tv-playback-history-select-all")
    }

    @Test
    fun closingTheSyncDialogRestoresFocusAndKeys() = runAniComposeUiTest {
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
        mount(
            TvPlaybackHistoryUiState(histories = listOf(history(1)), pendingSync = listOf(pending)),
            { intents += it },
        )

        // 进页焦点在列表; 上到操作行首项 (同步状态) 并打开弹窗.
        awaitFocus("tv-playback-history-item-1")
        key(Key.DirectionUp)
        awaitFocus("tv-playback-history-sync")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-sync-item-10")

        // 关闭后焦点回到操作行, 且能继续下到列表.
        onNodeWithTag("tv-playback-sync-close").performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithTag("tv-playback-sync-dialog").assertDoesNotExist()
        awaitFocus("tv-playback-history-sync")
        key(Key.DirectionDown)
        awaitFocus("tv-playback-history-item-1")
    }

    @Test
    fun deletingPendingItemsKeepsFocusInTheDialog() = runAniComposeUiTest {
        val first = pendingItem(10)
        val second = pendingItem(11)
        var state by mutableStateOf(TvPlaybackHistoryUiState(pendingSync = listOf(first, second)))
        mountStateful({ state }) { intent ->
            when (intent) {
                is TvPlaybackHistoryIntent.DeletePending ->
                    state = state.copy(pendingSync = state.pendingSync - intent.item)

                TvPlaybackHistoryIntent.DeleteAllPending ->
                    state = state.copy(pendingSync = emptyList())

                else -> Unit
            }
        }

        // 打开弹窗, 焦点在第一条待同步项.
        awaitFocus("tv-playback-history-sync")
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-sync-item-10")

        // 删掉第一条后焦点落到剩下的那条, 按键仍然有效.
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-sync-item-11")

        // 删空后焦点落到「关闭」, 确认退出弹窗并把焦点还给页面.
        key(Key.DirectionCenter)
        awaitFocus("tv-playback-sync-close")
        key(Key.DirectionCenter)
        onNodeWithTag("tv-playback-sync-dialog").assertDoesNotExist()
        awaitFocus("tv-playback-history-refresh")
    }

    private fun pendingItem(id: Int) = TvPlaybackSyncItem(
        episodeId = id,
        subjectName = "Subject $id",
        episodeName = "Episode $id",
        versionMillis = 0,
        operations = listOf(TvPlaybackSyncOperation.ProgressUpsert),
        playbackOpId = id.toLong(),
        collectionOpId = null,
    )

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
        onBackDispatcher: (OnBackPressedDispatcher) -> Unit = {},
    ) {
        val timeFormatter = TimeFormatter()
        setContent {
            val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SideEffect { onBackDispatcher(backDispatcher) }
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

    private fun AniComposeUiTest.mountStateful(
        state: () -> TvPlaybackHistoryUiState,
        onIntent: (TvPlaybackHistoryIntent) -> Unit,
    ) {
        val timeFormatter = TimeFormatter()
        setContent {
            CompositionLocalProvider(LocalTimeFormatter provides timeFormatter) {
                TvApplicationTheme(ThemeSettings.Default.seedColor, languageTag = "en") {
                    TvPlaybackHistoryScreen(
                        state = state(),
                        onIntent = onIntent,
                        modifier = Modifier.fillMaxSize().testTag("tv-playback-history-test"),
                    )
                }
            }
        }
    }

    private fun AniComposeUiTest.mountInShell(
        state: TvPlaybackHistoryUiState,
        onIntent: (TvPlaybackHistoryIntent) -> Unit,
        onBackDispatcher: (OnBackPressedDispatcher) -> Unit = {},
    ) {
        val timeFormatter = TimeFormatter()
        setContent {
            val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SideEffect { onBackDispatcher(backDispatcher) }
            CompositionLocalProvider(LocalTimeFormatter provides timeFormatter) {
                TvApplicationTheme(ThemeSettings.Default.seedColor, languageTag = "en") {
                    TvMainShell(
                        uiState = TvMainUiState(),
                        content = TvShellContent.PlaybackHistory,
                        onContentChange = {},
                        onOpenSettings = {},
                        onLogout = {},
                    ) { _, navigationRailInsets ->
                        TvPlaybackHistoryScreen(
                            state = state,
                            onIntent = onIntent,
                            navigationRailInsets = navigationRailInsets,
                        )
                    }
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
