/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.watch_together_members_label
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchTogetherDialogTest {
    @Test
    fun `not-in-room form renders and dispatches join`() = runAniComposeUiTest {
        var received: WatchTogetherIntent? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                WatchTogetherDialog(
                    state = WatchTogetherUiState.Initial.copy(
                        featureEnabled = true,
                        joinForm = WatchTogetherJoinFormState(lastRoomName = "Friday"),
                    ),
                    onIntent = { received = it },
                    onLogin = {},
                    onDismissRequest = {},
                )
            }
        }

        onNodeWithTag(WATCH_TOGETHER_DIALOG_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(WATCH_TOGETHER_ROOM_NAME_TEST_TAG).performTextClearance()
        onNodeWithTag(WATCH_TOGETHER_ROOM_NAME_TEST_TAG).performTextInput("Friday Night")
        onNodeWithTag(WATCH_TOGETHER_PASSWORD_TEST_TAG).performTextInput("secret")
        onNodeWithTag(WATCH_TOGETHER_JOIN_TEST_TAG).performClick()

        runOnIdle {
            assertEquals(
                WatchTogetherIntent.JoinRoom("Friday Night", "secret"),
                received,
            )
        }
    }

    @Test
    fun `in-room state renders playback and dispatches follow change`() = runAniComposeUiTest {
        val membersLabel = runBlocking { getString(Lang.watch_together_members_label, 2) }
        var received: WatchTogetherIntent? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                WatchTogetherDialog(
                    state = inRoomState(),
                    onIntent = { received = it },
                    onLogin = {},
                    onDismissRequest = {},
                )
            }
        }

        onNodeWithTag(WATCH_TOGETHER_DIALOG_TEST_TAG).assertIsDisplayed()
        onNodeWithText("葬送的芙莉莲").assertIsDisplayed()
        onNodeWithText(membersLabel).assertIsDisplayed()
        onNodeWithText("Alice").assertIsDisplayed()
        onNodeWithTag(WATCH_TOGETHER_FOLLOW_TEST_TAG).performClick()

        runOnIdle {
            assertEquals(WatchTogetherIntent.SetFollowing(false), received)
        }
    }

    private fun inRoomState() = WatchTogetherUiState.Initial.copy(
        featureEnabled = true,
        phase = WatchTogetherPhase.IN_ROOM,
        following = true,
        room = WatchTogetherRoomCardState(
            roomName = "Friday Night",
            connection = WatchTogetherConnectionPresentation.CONNECTED,
            playback = playback(),
            members = listOf(
                WatchTogetherMemberPresentation(
                    userId = "host",
                    nickname = "Alice",
                    avatarUrl = null,
                    isHost = true,
                    following = true,
                    state = WatchTogetherMemberPresence.WATCHING,
                    watching = playback(),
                ),
                WatchTogetherMemberPresentation(
                    userId = "member",
                    nickname = "成员",
                    avatarUrl = null,
                    isHost = false,
                    following = true,
                    state = WatchTogetherMemberPresence.WATCHING,
                    watching = playback(),
                ),
            ),
        ),
    )

    private fun playback() = WatchTogetherPlaybackPresentation(
        subjectName = "葬送的芙莉莲",
        episodeSort = "12",
        episodeName = "真正的勇者",
        positionMillis = 90_000L,
        durationMillis = 1_500_000L,
        paused = false,
    )
}
