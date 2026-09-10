/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.watch_together_cancel
import me.him188.ani.app.ui.lang.watch_together_confirm_disband
import me.him188.ani.app.ui.lang.watch_together_connection_degraded
import me.him188.ani.app.ui.lang.watch_together_connection_reconnecting
import me.him188.ani.app.ui.lang.watch_together_error_wrong_password
import me.him188.ani.app.ui.lang.watch_together_follow_host_desc
import me.him188.ani.app.ui.lang.watch_together_host_desc
import me.him188.ani.app.ui.lang.watch_together_host_idle
import me.him188.ani.app.ui.lang.watch_together_join
import me.him188.ani.app.ui.lang.watch_together_join_failed
import me.him188.ani.app.ui.lang.watch_together_join_subtitle
import me.him188.ani.app.ui.lang.watch_together_login_description
import me.him188.ani.app.ui.lang.watch_together_login_required
import me.him188.ani.app.ui.watchtogether.WatchTogetherConnectionPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresence
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherPlaybackPresentation
import me.him188.ani.leanback.ui.episode.presentation.TvPlaybackCommand
import me.him188.ani.leanback.ui.episode.presentation.TvPlaybackSnapshot
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanel
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPresentationState
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.leanback.ui.watchtogether.TvTogetherError
import me.him188.ani.leanback.ui.watchtogether.TvTogetherIntent
import me.him188.ani.leanback.ui.watchtogether.TvTogetherState
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlayerState
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvPlayerTogetherUiTest {
    private class Fixture(initial: TvTogetherState) {
        var together by mutableStateOf(initial)
        val intents = mutableListOf<TvTogetherIntent>()
        val playbackCommands = mutableListOf<TvPlaybackCommand>()
        val presentation = TvPlayerPresentationState({ TvPlaybackSnapshot(PlayerState(MediaStatus.Ready, true, false), 620_000, 1_440_000) }, playbackCommands::add)
        lateinit var back: OnBackPressedDispatcher
        var logins = 0

        fun onIntent(intent: TvTogetherIntent) {
            intents += intent
            together = when (intent) {
                is TvTogetherIntent.RoomName -> together.copy(roomName = intent.value, error = null)
                is TvTogetherIntent.Password -> together.copy(password = intent.value, error = null)
                TvTogetherIntent.Join -> together.copy(joining = true, error = null)
                TvTogetherIntent.CancelJoin -> together.copy(joining = false)
                TvTogetherIntent.ToggleFollowing -> together.copy(following = !together.following)
                TvTogetherIntent.Leave -> together.copy(joined = false, password = "")
                else -> together
            }
        }
    }

    @Test
    fun joinFormKeepsInputAndSeparatesFailureFromTheCancellableProgress() = runAniComposeUiTest {
        val fixture = Fixture(TvTogetherState())
        showPlayer(fixture)
        openTogether()
        onNodeWithText(playerTestString(Lang.watch_together_join_subtitle)).assertIsDisplayed()
        onNodeWithTag("tv-together-name").assertIsFocused().performTextInput("周末放映室")
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-password").assertIsFocused().performTextInput("animeko")
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-submit").assertIsFocused()
        onNodeWithTag("tv-player-sidebar-title").assertIsDisplayed()
        assertEquals("周末放映室", fixture.together.roomName)
        assertEquals("animeko", fixture.together.password)
        saveScreenshot("join")
        key(Key.DirectionCenter)
        onNodeWithTag("tv-together-submit").assertIsFocused().assertTextContains(playerTestString(Lang.watch_together_cancel))
        saveScreenshot("joining")
        key(Key.DirectionCenter)
        assertEquals(1, fixture.intents.count { it == TvTogetherIntent.CancelJoin })
        runOnIdle { fixture.together = fixture.together.copy(error = TvTogetherError.Join(WatchTogetherJoinFailure.WRONG_PASSWORD)) }
        onNodeWithTag("tv-together-error").assertIsDisplayed().assertTextContains(
            playerTestString(Lang.watch_together_join_failed, playerTestString(Lang.watch_together_error_wrong_password)),
        )
        onNodeWithTag("tv-together-submit").assertIsFocused().assertTextContains(playerTestString(Lang.watch_together_join))
        saveScreenshot("join-error")
        key(Key.DirectionUp)
        onNodeWithTag("tv-together-password").assertIsFocused()
        key(Key.DirectionLeft)
        assertClosed()
        assertTrue(fixture.playbackCommands.isEmpty())
    }

    @Test
    fun guestControlsStayVisibleAndReadingCardsHaveNoClickAction() = runAniComposeUiTest {
        val fixture = Fixture(room())
        showPlayer(fixture)
        openTogether()
        onNodeWithTag("tv-together-follow").assertIsFocused()
            .assertTextContains(playerTestString(Lang.watch_together_follow_host_desc))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        onNodeWithTag("tv-together-leave").assertIsDisplayed()
        saveScreenshot("room")
        key(Key.DirectionCenter)
        assertEquals(false, fixture.together.following)
        onNodeWithTag("tv-together-follow")
            .assertTextContains(playerTestString(Lang.watch_together_follow_host_desc))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        key(Key.DirectionUp)
        onNodeWithTag("tv-together-playback").assertIsFocused()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        key(Key.DirectionCenter)
        assertTrue(fixture.playbackCommands.isEmpty())
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-member-host").assertIsFocused()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        saveScreenshot("member-focused")
        key(Key.DirectionLeft)
        assertClosed()
    }

    @Test
    fun memberTicksInsertionsAndDepartureKeepFocusWithinTheRoom() = runAniComposeUiTest {
        val fixture = Fixture(room().copy(members = (0..9).map { member("member-$it") }))
        showPlayer(fixture)
        openTogether()
        key(Key.DirectionUp)
        repeat(9) { key(Key.DirectionDown) }
        onNodeWithTag("tv-together-member-member-8").assertIsFocused()
        onNodeWithTag("tv-together-follow").assertIsDisplayed()
        onNodeWithTag("tv-together-leave").assertIsDisplayed()
        saveScreenshot("members-scrolled")
        runOnIdle {
            fixture.together = fixture.together.copy(
                members = fixture.together.members.map { it.copy(watching = playback().copy(positionMillis = 630_000)) },
            )
        }
        onNodeWithTag("tv-together-member-member-8").assertIsFocused()
        runOnIdle { fixture.together = fixture.together.copy(members = listOf(member("new")) + fixture.together.members) }
        onNodeWithTag("tv-together-member-member-8").assertIsFocused()
        runOnIdle { fixture.together = fixture.together.copy(members = fixture.together.members.filter { it.userId != "member-8" }) }
        onNodeWithTag("tv-together-member-member-9").assertIsFocused()
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-follow").assertIsFocused()
        onNodeWithTag("tv-player-seekbar").assertIsNotFocused()
        key(Key.DirectionUp)
        onNodeWithTag("tv-together-member-member-9").assertIsFocused()
        runOnIdle { fixture.together = fixture.together.copy(members = emptyList()) }
        onNodeWithTag("tv-together-follow").assertIsFocused()
        key(Key.DirectionUp)
        onNodeWithTag("tv-together-playback").assertIsFocused()
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-follow").assertIsFocused()
        assertTrue(fixture.playbackCommands.isEmpty())
    }

    @Test
    fun loadingAndConnectionStatesKeepSharedPlaybackSemantics() = runAniComposeUiTest {
        val fixture = Fixture(room().copy(playback = playback().copy(loading = true)))
        showPlayer(fixture)
        openTogether()
        onNodeWithTag("tv-together-playback-loading", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("tv-together-progress", useUnmergedTree = true).assertDoesNotExist()
        saveScreenshot("playback-loading")
        runOnIdle {
            fixture.together = fixture.together.copy(
                playback = playback().copy(buffering = true),
                connection = WatchTogetherConnectionPresentation.RECONNECTING,
            )
        }
        onNodeWithTag("tv-together-playback-loading", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("tv-together-progress", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(playerTestString(Lang.watch_together_connection_reconnecting)).assertIsDisplayed()
        runOnIdle {
            fixture.together = fixture.together.copy(
                playback = null,
                roomName = "一个很长很长的房间名称用于确认电视侧栏不会因为文本而溢出",
                connection = WatchTogetherConnectionPresentation.DEGRADED,
            )
        }
        onNodeWithTag("tv-together-playback").assertTextContains(playerTestString(Lang.watch_together_host_idle))
        onNodeWithText(playerTestString(Lang.watch_together_connection_degraded)).assertIsDisplayed()
        val name = onNodeWithTag("tv-together-room-name")
        val layouts = mutableListOf<TextLayoutResult>()
        name.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(layouts.single().isLineEllipsized(0), "Long room names must leave room for connection status")
        val nameBounds = name.fetchSemanticsNode().boundsInRoot
        val statusBounds = onNodeWithTag("tv-together-connection").fetchSemanticsNode().boundsInRoot
        assertTrue(nameBounds.right < statusBounds.left, "Room name must not overlap connection status")
        assertTrue(abs(nameBounds.center.y - statusBounds.center.y) < 1f, "Room name and status must share a row")
        onNodeWithTag("tv-together-follow").assertIsFocused()
        saveScreenshot("room-idle-reconnecting")
    }

    @Test
    fun hostConfirmationDefaultsToStayAndCancelRestoresTheLeaveAction() = runAniComposeUiTest {
        val fixture = Fixture(room().copy(isHost = true, members = emptyList()))
        showPlayer(fixture)
        openTogether()
        val playbackCard = onNodeWithTag("tv-together-playback").assertIsFocused()
        val hostHint = onNodeWithText(playerTestString(Lang.watch_together_host_desc)).assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        assertTrue(
            hostHint.fetchSemanticsNode().boundsInRoot.top > playbackCard.fetchSemanticsNode().boundsInRoot.bottom,
            "Host explanation must sit below the playback card",
        )
        saveScreenshot("host")
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-leave").assertIsFocused()
        key(Key.DirectionCenter)
        onNodeWithTag("tv-together-stay").assertIsFocused().assertTextContains(playerTestString(Lang.watch_together_cancel))
        onNodeWithText(playerTestString(Lang.watch_together_confirm_disband)).assertIsDisplayed()
        saveScreenshot("confirm-disband")
        key(Key.DirectionCenter)
        onNodeWithTag("tv-together-leave").assertIsFocused()
        assertTrue(fixture.intents.none { it == TvTogetherIntent.Leave })
        key(Key.DirectionCenter)
        runOnIdle { fixture.back.onBackPressed() }
        onNodeWithTag("tv-together-leave").assertIsFocused()
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-confirm-leave").assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals(1, fixture.intents.count { it == TvTogetherIntent.Leave })
        onNodeWithTag("tv-together-name").assertIsFocused()
    }

    @Test
    fun loginKeepsThePanelRootUnfocusableAndUsesTheExistingLoginFlow() = runAniComposeUiTest {
        val fixture = Fixture(TvTogetherState(requiresLogin = true))
        showPlayer(fixture)
        openTogether()
        onNodeWithTag("tv-together-login").assertIsFocused()
        onNodeWithText(playerTestString(Lang.watch_together_login_required)).assertIsDisplayed()
        onNodeWithText(playerTestString(Lang.watch_together_login_description)).assertIsDisplayed()
        saveScreenshot("login")
        key(Key.DirectionCenter)
        assertEquals(1, fixture.logins)
        runOnIdle { fixture.back.onBackPressed() }
        assertClosed()
    }

    @Test
    fun imeNextAndDoneFollowTheFormWithoutSubmittingIt() = runAniComposeUiTest {
        val fixture = Fixture(TvTogetherState())
        showPlayer(fixture)
        openTogether()
        onNodeWithTag("tv-together-name").performTextInput("遥控器输入")
        onNodeWithTag("tv-together-name").performImeAction()
        onNodeWithTag("tv-together-password").assertIsFocused().performTextInput("animeko")
        onNodeWithTag("tv-together-password").performImeAction()
        onNodeWithTag("tv-together-submit").assertIsFocused()
        assertTrue(fixture.intents.none { it == TvTogetherIntent.Join })
        key(Key.DirectionCenter)
        assertEquals(1, fixture.intents.count { it == TvTogetherIntent.Join })
        onNodeWithTag("tv-together-submit").assertIsFocused().assertTextContains(playerTestString(Lang.watch_together_cancel))
    }

    @Test
    fun largerTypeAndLongNamesKeepActionsSafeAndTheFocusedMemberReadable() = runAniComposeUiTest {
        val name = "旅途中的魔法使与她的伙伴们一起看"
        val fixture = Fixture(room().copy(members = listOf(member("host", name), member("self"))))
        showPlayer(fixture, fontScale = 1.3f)
        openTogether()
        onNodeWithTag("tv-together-follow").assertIsFocused()
        onNodeWithTag("tv-together-leave").assertIsDisplayed()
        val screen = onRoot().fetchSemanticsNode().boundsInRoot
        val action = onNodeWithTag("tv-together-follow").fetchSemanticsNode().boundsInRoot
        assertTrue(screen.right - action.right >= screen.width * .05f, "Focused controls must remain inside TV overscan safety")
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-member-host").assertIsFocused().assertTextContains(name)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        saveScreenshot("large-type-member")
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-member-self").assertIsFocused()
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-follow").assertIsFocused()
        key(Key.DirectionLeft)
        assertClosed()
    }

    private fun AniComposeUiTest.showPlayer(fixture: Fixture, fontScale: Float = 1f) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                AniTvTheme {
                    val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    SideEffect { fixture.back = dispatcher }
                    TvEpisodeScreen(
                        uiState = TvEpisodeUiState(
                            title = TvEpisodeTitle("葬送的芙莉莲", "5", "死者的幻影"),
                            loadingState = VideoLoadingState.Succeed(false),
                            positionMillis = 620_000, durationMillis = 1_440_000,
                        ),
                        togetherState = fixture.together,
                        onTogetherIntent = fixture::onIntent,
                        commentsPager = emptyFlow(),
                        presentationState = fixture.presentation,
                        actionEvents = emptyFlow(),
                        onIntent = { if (it == TvEpisodeIntent.OpenLogin) fixture.logins++; true },
                        video = { Box(it.background(Color(0xFF1E2A38))) },
                        resolver = {}, danmaku = {},
                        modifier = Modifier.testTag("tv-together-test"),
                    )
                }
            }
        }
    }

    private fun AniComposeUiTest.openTogether() {
        onNodeWithTag("tv-player-seekbar").assertIsFocused()
        key(Key.DirectionUp)
        repeat(TvPlayerPanel.entries.indexOf(TvPlayerPanel.Together)) { key(Key.DirectionRight) }
        key(Key.DirectionCenter)
        onNodeWithTag("tv-player-sidebar").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        onNodeWithTag("tv-sidebar-back").assertDoesNotExist()
    }

    private fun AniComposeUiTest.assertClosed() {
        onNodeWithTag("tv-player-sidebar").assertDoesNotExist()
        onNodeWithTag("tv-player-chip-Together").assertIsFocused()
    }

    private fun AniComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    // Android's assertScreenshot is a no-op; retain real renders for visual review alongside semantic assertions.
    private fun AniComposeUiTest.saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun save(bitmap: Bitmap, suffix: String) {
            val file = File(context.getExternalFilesDir("screenshots"), "together-$name$suffix.png")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        save(onNodeWithTag("tv-player-sidebar").captureToImage().asAndroidBitmap(), "")
        if (name == "room" || name == "join") save(onRoot().captureToImage().asAndroidBitmap(), "-full")
    }

    private fun playback() = WatchTogetherPlaybackPresentation(
        subjectName = "葬送的芙莉莲", episodeSort = "5", episodeName = "死者的幻影",
        positionMillis = 620_000, durationMillis = 1_440_000, paused = false,
    )

    private fun member(id: String, name: String = "观众 $id") = WatchTogetherMemberPresentation(
        userId = id, nickname = name, avatarUrl = null, isHost = id == "host", isSelf = id == "self",
        following = true, state = WatchTogetherMemberPresence.WATCHING, watching = playback(),
    )

    private fun room() = TvTogetherState(
        joined = true, roomName = "周末放映室", playback = playback(),
        members = listOf(
            member("host", "星河"), member("self", "追番的我"),
            member("friend", "旅途中的魔法使").copy(following = false, state = WatchTogetherMemberPresence.IDLE),
            member("offline", "晚风").copy(state = WatchTogetherMemberPresence.DISCONNECTED, disconnectedMinutes = 3),
        ),
    )
}
