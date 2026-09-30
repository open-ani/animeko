/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.domain.episode.SubjectRecommendation
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_recommendations_empty
import me.him188.ani.app.ui.lang.subject_episode_recommendations_loading
import me.him188.ani.tv.ui.episode.presentation.TvPlaybackCommand
import me.him188.ani.tv.ui.episode.presentation.TvPlaybackSnapshot
import me.him188.ani.tv.ui.episode.presentation.TvPlayerPresentationState
import me.him188.ani.tv.ui.foundation.theme.AniTvTheme
import me.him188.ani.tv.ui.watchtogether.TvTogetherState
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlayerRecommendationsUiTest {
    private class Fixture(val options: TvPlayerOptionsState = TvPlayerOptionsState()) {
        val commands = mutableListOf<TvPlaybackCommand>()
        val openedRecommendations = mutableListOf<SubjectRecommendation>()
        val machine = TvPlayerPresentationState({ TvPlaybackSnapshot(PlayerState(MediaStatus.Ready, true, false), 20_000, 60_000) }, commands::add)
        lateinit var backDispatcher: OnBackPressedDispatcher
        var panel by mutableStateOf(TvPlayerPanelState())
        var episodes by mutableStateOf(listOf(
            TvStripEpisode(1, "1", "第 1 集", false, isKnownBroadcast = true),
            TvStripEpisode(2, "2", "第 2 集", false, isKnownBroadcast = true),
        ))

        fun onIntent(intent: TvEpisodeIntent): Boolean {
            if (intent is TvEpisodeIntent.OpenRecommendation) {
                openedRecommendations += intent.recommendation
                return true
            }
            return true
        }
    }

    @Test
    fun unavailableNextEpisodeKeepsPlayPauseAsBottomRowEntry() = runAniComposeUiTest {
        val fixture = Fixture().apply { episodes = episodes.take(1) }
        showPlayer(fixture)
        onNodeWithTag("tv-next-episode-button").assertDoesNotExist()
        key(Key.DirectionDown)
        onNodeWithTag("tv-play-pause-button").assertIsFocused()
        key(Key.DirectionDown)
        onNodeWithTag("tv-recommendations-empty").assertIsFocused()
        key(Key.DirectionUp)
        assertControllerHasFocus()
    }

    @Test
    fun nextEpisodeAvailabilityUpdatesTheSeekBarsDownTarget() = runAniComposeUiTest {
        val fixture = Fixture()
        val episodes = fixture.episodes
        fixture.episodes = episodes.take(1)
        showPlayer(fixture)
        key(Key.DirectionDown)
        onNodeWithTag("tv-play-pause-button").assertIsFocused()
        key(Key.DirectionUp)
        onNodeWithTag("tv-player-seekbar").assertIsFocused()
        runOnIdle { fixture.episodes = episodes }
        key(Key.DirectionDown)
        onNodeWithTag("tv-next-episode-button").assertIsFocused()
        key(Key.DirectionLeft)
        onNodeWithTag("tv-play-pause-button").assertIsFocused()
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        onNodeWithTag("tv-next-episode-button").assertIsFocused()
        runOnIdle { fixture.episodes = fixture.episodes.take(1) }
        onNodeWithTag("tv-next-episode-button").assertDoesNotExist()
        onNodeWithTag("tv-play-pause-button").assertIsFocused()
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        onNodeWithTag("tv-play-pause-button").assertIsFocused()
    }

    @Test
    fun recommendationRowReplacesBottomControlsKeepsTitleAndRestoresTimeline() = runAniComposeUiTest {
        val fixture = Fixture().apply {
            panel = TvPlayerPanelState(recommendations = listOf(recommendation(1), recommendation(2)))
        }
        showPlayer(fixture)
        val title = onNodeWithTag("tv-player-title-bar").fetchSemanticsNode()
        onNodeWithTag("tv-player-seekbar").assertIsFocused()
        onNodeWithTag("tv-recommendations-hint")
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        key(Key.DirectionDown)
        onNodeWithTag("tv-next-episode-button").assertIsFocused()
        val scrim = bottomScrim()
        key(Key.DirectionDown)
        onNodeWithTag("tv-player-controller").assertDoesNotExist()
        onNodeWithTag("tv-recommendations-row").assertIsDisplayed()
        onNodeWithTag("tv-recommendation-${fixture.panel.recommendations[0].uniqueId}").assertIsFocused()
        onNodeWithTag("tv-player-title-bar").assertIsDisplayed()
        val recommendationTitle = onNodeWithTag("tv-player-title-bar").fetchSemanticsNode()
        assertEquals(title.id, recommendationTitle.id)
        assertEquals(title.boundsInRoot, recommendationTitle.boundsInRoot)
        onNodeWithText("测试番剧").assertIsDisplayed()
        assertSameScrim(scrim)

        key(Key.DirectionRight)
        onNodeWithTag("tv-recommendation-${fixture.panel.recommendations[1].uniqueId}").assertIsFocused()
        key(Key.DirectionUp)
        assertControllerHasFocus()
        openRecommendations()
        onNodeWithTag("tv-recommendations-row").assertIsDisplayed()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        assertControllerHasFocus()
        assertSameScrim(scrim)
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun downFromEveryBottomButtonOpensRecommendationsWithoutConfirm() = runAniComposeUiTest {
        val fixture = Fixture(options = TvPlayerOptionsState(supportsSubtitles = true)).apply {
            panel = TvPlayerPanelState(recommendations = listOf(recommendation(1)))
        }
        showPlayer(fixture)
        bottomButtons().assertCountEquals(8)
        listOf(
            "tv-play-pause-button", "tv-next-episode-button", "tv-episodes-button", "tv-danmaku-toggle", "tv-source-button",
            "tv-speed-button", "tv-subtitles-button", "tv-aspect-button",
        ).forEachIndexed { index, tag ->
            key(Key.DirectionDown)
            onNodeWithTag("tv-next-episode-button").assertIsFocused()
            key(Key.DirectionLeft)
            repeat(index) { key(Key.DirectionRight) }
            onNodeWithTag(tag).assertIsFocused()
            assertFalse(fixture.machine.states.value.recommendationsVisible)
            key(Key.DirectionDown)
            onNodeWithTag("tv-recommendation-${fixture.panel.recommendations[0].uniqueId}").assertIsFocused()
            onNodeWithTag("tv-player-title-bar").assertIsDisplayed()
            key(Key.DirectionUp)
            assertControllerHasFocus()
        }
        assertTrue(fixture.commands.isEmpty())
        assertTrue(fixture.openedRecommendations.isEmpty())
    }

    @Test
    fun advertisingCardsAreVisibleAndClickingThemDoesNothing() = runAniComposeUiTest {
        // A URL must win over an optional subject id, just as on the other platforms.
        val ad = recommendation(123, uri = "https://example.invalid/ad")
        val subject = recommendation(456)
        val fixture = Fixture().apply { panel = TvPlayerPanelState(recommendations = listOf(ad, subject)) }
        showPlayer(fixture)
        openRecommendations()
        onNodeWithTag("tv-recommendation-${ad.uniqueId}").assertIsFocused()
        key(Key.DirectionCenter)
        onNodeWithText("广告").assertIsDisplayed()
        assertTrue(fixture.openedRecommendations.isEmpty())
        onNodeWithTag("tv-player-controller").assertDoesNotExist()
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(listOf(subject), fixture.openedRecommendations)
    }

    @Test
    fun loadingAndEmptyRowsCanReturnAndLoadedItemsAcquireFocus() = runAniComposeUiTest {
        val fixture = Fixture().apply { panel = TvPlayerPanelState(recommendationsLoading = true) }
        showPlayer(fixture)
        openRecommendations()
        onNodeWithTag("tv-recommendations-loading").assertIsFocused().assertHasNoClickAction()
        onNodeWithTag("tv-recommendation-placeholder-1").assertIsDisplayed().assertHasNoClickAction()
        onNodeWithText(playerTestString(Lang.subject_episode_recommendations_loading)).assertDoesNotExist()
        onNodeWithText(playerTestString(Lang.subject_episode_recommendations_empty)).assertDoesNotExist()
        key(Key.DirectionRight)
        onNodeWithTag("tv-recommendations-loading").assertIsFocused()
        key(Key.DirectionUp)
        assertControllerHasFocus()
        openRecommendations()
        runOnIdle { fixture.panel = TvPlayerPanelState() }
        onNodeWithText(playerTestString(Lang.subject_episode_recommendations_empty)).assertIsDisplayed()
        onNodeWithTag("tv-recommendations-empty").assertIsFocused()
        onNodeWithTag("tv-recommendations-loading").assertDoesNotExist()
        key(Key.DirectionUp)
        assertControllerHasFocus()
        openRecommendations()
        val subject = recommendation(789)
        runOnIdle { fixture.panel = TvPlayerPanelState(recommendations = listOf(subject)) }
        onNodeWithTag("tv-recommendation-${subject.uniqueId}").assertIsFocused()
        onNodeWithTag("tv-recommendations-empty").assertDoesNotExist()
    }

    @Test
    fun recommendationsLoadIntoTheRowWithoutStealingFocusAfterReturn() = runAniComposeUiTest {
        val fixture = Fixture().apply { panel = TvPlayerPanelState(recommendationsLoading = true) }
        val subject = recommendation(789)
        showPlayer(fixture)
        openRecommendations()
        runOnIdle { fixture.panel = TvPlayerPanelState(recommendations = listOf(subject)) }
        onNodeWithTag("tv-recommendation-${subject.uniqueId}").assertIsFocused()
        onNodeWithTag("tv-recommendations-loading").assertDoesNotExist()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        assertControllerHasFocus()

        runOnIdle { fixture.panel = TvPlayerPanelState(recommendationsLoading = true) }
        openRecommendations()
        onNodeWithTag("tv-recommendations-loading").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        runOnIdle { fixture.panel = TvPlayerPanelState(recommendations = listOf(subject)) }
        assertControllerHasFocus()
        assertTrue(fixture.openedRecommendations.isEmpty())
    }

    @Test
    fun returningDuringAnimationDoesNotLeaveFocusOnDisappearingContent() = runAniComposeUiTest {
        val fixture = Fixture().apply {
            panel = TvPlayerPanelState(recommendations = listOf(recommendation(1)))
        }
        showPlayer(fixture)
        onNodeWithTag("tv-player-seekbar").assertIsFocused()
        val titleBounds = onNodeWithTag("tv-player-title-bar").fetchSemanticsNode().boundsInRoot
        val scrim = bottomScrim()
        mainClock.autoAdvance = false
        openRecommendations()
        mainClock.advanceTimeBy(80)
        assertSameScrim(scrim)
        onNodeWithTag("tv-player-title-bar").assertIsDisplayed()
        assertEquals(titleBounds, onNodeWithTag("tv-player-title-bar").fetchSemanticsNode().boundsInRoot)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        mainClock.advanceTimeBy(500)
        mainClock.autoAdvance = true
        assertControllerHasFocus()
        assertFalse(fixture.machine.states.value.recommendationsVisible)
        assertTrue(fixture.openedRecommendations.isEmpty())
        assertTrue(fixture.commands.isEmpty())
    }

    private fun AniComposeUiTest.showPlayer(fixture: Fixture) {
        setContent {
            AniTvTheme {
                val backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                SideEffect { fixture.backDispatcher = backDispatcher }
                TvEpisodeScreen(
                    uiState = TvEpisodeUiState(
                        title = TvEpisodeTitle("测试番剧", "1"),
                        loadingState = VideoLoadingState.Succeed(null),
                        positionMillis = 20_000,
                        durationMillis = 60_000,
                        episodes = fixture.episodes,
                        currentEpisodeId = 1,
                        panel = fixture.panel,
                        options = fixture.options,
                    ),
                    togetherState = TvTogetherState(),
                    onTogetherIntent = {},
                    commentsPager = emptyFlow(),
                    presentationState = fixture.machine,
                    actionEvents = emptyFlow(),
                    onIntent = fixture::onIntent,
                    video = { Box(it.background(Color(0xFF1E2A38))) },
                    resolver = {},
                    danmaku = {},
                    modifier = Modifier.testTag("tv-player-test"),
                )
            }
        }
    }

    private fun AniComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun AniComposeUiTest.openRecommendations() {
        key(Key.DirectionDown)
        onNodeWithTag("tv-next-episode-button").assertIsFocused()
        key(Key.DirectionDown)
    }

    private fun AniComposeUiTest.bottomButtons() =
        onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("tv-player-icon-row")))

    private fun AniComposeUiTest.assertControllerHasFocus() {
        onNodeWithTag("tv-player-controller").assertIsDisplayed()
        onNodeWithTag("tv-player-title-bar").assertIsDisplayed()
        onNodeWithTag("tv-player-recommendations").assertDoesNotExist()
        onNodeWithTag("tv-player-seekbar").assertIsFocused()
    }

    private fun AniComposeUiTest.bottomScrim(): SemanticsNode = onNodeWithTag("tv-player-bottom-scrim").fetchSemanticsNode()

    /** The controller and the recommendation row slide over one stationary scrim. */
    private fun AniComposeUiTest.assertSameScrim(expected: SemanticsNode) {
        val scrim = bottomScrim()
        assertEquals(expected.id, scrim.id, "Both views must share the scrim")
        assertEquals(expected.boundsInRoot, scrim.boundsInRoot)
    }

    private fun recommendation(id: Long, uri: String? = null) = SubjectRecommendation(
        subjectId = id,
        name = "推荐条目 $id",
        nameCn = null,
        desc1 = "推荐说明",
        desc2 = if (uri != null) "广告" else "2026 年 9 月",
        imageUrl = "",
        uri = uri,
    )
}
