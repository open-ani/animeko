/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_read_full
import me.him188.ani.app.ui.lang.media_captcha_image
import me.him188.ani.app.ui.lang.media_captcha_required_message
import me.him188.ani.app.ui.lang.media_subtitle_language_chinese_simplified
import me.him188.ani.app.ui.lang.settings_framework_timeout
import me.him188.ani.app.ui.lang.subject_episode_default_title
import me.him188.ani.app.ui.lang.subject_episode_video_settings_font_size
import me.him188.ani.app.ui.lang.tv_player_following_host_hint
import me.him188.ani.app.ui.lang.tv_player_scroll_hide_hint
import me.him188.ani.app.ui.lang.tv_player_scroll_reveal_hint
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.app.ui.lang.video_player_performance
import me.him188.ani.app.ui.lang.video_player_quality
import me.him188.ani.app.ui.lang.video_player_video_enhancement
import me.him188.ani.app.ui.lang.watch_together_error_invalid_name
import me.him188.ani.app.ui.lang.watch_together_error_temporary
import me.him188.ani.app.ui.lang.watch_together_error_wrong_password
import me.him188.ani.app.ui.lang.watch_together_follow_host_desc
import me.him188.ani.app.ui.lang.watch_together_host_desc
import me.him188.ani.app.ui.lang.watch_together_join
import me.him188.ani.app.ui.lang.watch_together_join_failed
import me.him188.ani.app.ui.lang.watch_together_title
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.leanback.ui.watchtogether.TvTogetherError
import me.him188.ani.leanback.ui.watchtogether.TvTogetherState
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlayerI18nUiTest {
    private class Fixture(locale: String) {
        var locale by mutableStateOf(locale)
        var width by mutableStateOf(960.dp)
        var fontScale by mutableStateOf(1f)
        var together by mutableStateOf(TvTogetherState(roomName = "Friends", error = TvTogetherError.Join(WatchTogetherJoinFailure.WRONG_PASSWORD)))
        var state by mutableStateOf(TvEpisodeUiState(
            title = TvEpisodeTitle("Frieren", "5", "Phantoms of the Dead"),
            loadingState = VideoLoadingState.Succeed(false), positionMillis = 20_000, durationMillis = 60_000,
            episodes = listOf(
                TvStripEpisode(5, "5", "Phantoms of the Dead", false, isKnownBroadcast = true),
                TvStripEpisode(6, "6", "The Hero of the Village", false, isKnownBroadcast = true),
            ),
            currentEpisodeId = 5,
            options = TvPlayerOptionsState(supportsSubtitles = true),
            sources = TvSourceSelectionState(listOf(TvSourceGroup(
                "captcha", "source", "Test source", null,
                MediaSourceFetchState.CaptchaRequired(
                    SolveRequest("source", "https://example.invalid", WebCaptchaKind.Image, PageExpectation.AnyContent), 0,
                ), emptyList(),
            )), loading = false),
        ))
        val presentation = TvPlayerPresentationState({ TvPlaybackSnapshot(true, 20_000, 60_000) }, {})
        lateinit var back: OnBackPressedDispatcher
        val comments = flowOf(PagingData.from(listOf(EpisodeComment(
            stableId = "i18n", source = EpisodeCommentSource.BANGUMI,
            sourceCommentId = "i18n", commentId = "i18n", episodeId = 5,
            createdAt = 1_788_739_200_000L, content = "A comment [mask]with spoilers[/mask]", author = null,
        ))))
    }

    @Test fun english() = checkPlayerLocale("en-US", "Danmaku on")
    @Test fun simplifiedChinese() = checkPlayerLocale("zh-CN", "弹幕开")
    @Test fun hongKongChinese() = checkPlayerLocale("zh-HK", "彈幕開")
    @Test fun traditionalChinese() = checkPlayerLocale("zh-TW", "彈幕開")

    private fun checkPlayerLocale(locale: String, expectedDanmaku: String) = withPlayerTestLocale(locale) {
        runAniComposeUiTest {
            val fixture = Fixture(locale)
            showPlayer(fixture)
            onNodeWithTag("tv-player-seekbar").assertIsFocused()
            onNodeWithTag("tv-danmaku-toggle").assertContentDescriptionEquals(expectedDanmaku)
            onNodeWithText(playerTestString(Lang.subject_episode_default_title, "5") + " · Phantoms of the Dead").assertIsDisplayed()
            assertControlBounds()
            runOnIdle { fixture.state = fixture.state.copy(options = fixture.state.options.copy(message = TvPlayerMessage.FollowingHost)) }
            onNodeWithText(playerTestString(Lang.tv_player_following_host_hint, playerTestString(Lang.watch_together_title))).assertIsDisplayed()
            runOnIdle { fixture.state = fixture.state.copy(options = fixture.state.options.copy(message = null)) }
            screenshot("controls-$locale")

            openPanel(TvPlayerPanel.Comments)
            onNodeWithTag("tv-comment-i18n").assertIsFocused().assertTextContains(playerTestString(Lang.comment_read_full))
            key(Key.DirectionCenter)
            onNodeWithText(playerTestString(Lang.tv_player_scroll_reveal_hint)).assertIsDisplayed()
            key(Key.DirectionCenter)
            onNodeWithText(playerTestString(Lang.tv_player_scroll_hide_hint)).assertIsDisplayed()
            screenshot("comment-$locale")
            runOnIdle { fixture.back.onBackPressed() }
            runOnIdle { fixture.back.onBackPressed() }

            openPanel(TvPlayerPanel.DanmakuSettings)
            onNodeWithTag("tv-danmaku-property-FontSize").assertIsFocused()
                .assertTextContains(playerTestString(Lang.subject_episode_video_settings_font_size))
            runOnIdle { fixture.back.onBackPressed() }

            openPanel(TvPlayerPanel.Together)
            for ((error, reason) in listOf(
                TvTogetherError.Join(WatchTogetherJoinFailure.WRONG_PASSWORD) to Lang.watch_together_error_wrong_password,
                TvTogetherError.Join(null) to Lang.watch_together_error_temporary,
                TvTogetherError.EmptyName to Lang.watch_together_error_invalid_name,
                TvTogetherError.Timeout to Lang.settings_framework_timeout,
            )) {
                runOnIdle { fixture.together = fixture.together.copy(error = error) }
                onNodeWithTag("tv-together-error").assertTextContains(
                    playerTestString(Lang.watch_together_join_failed, playerTestString(reason)),
                )
            }
            assertTextFits(playerTestString(Lang.watch_together_join))
            key(Key.DirectionDown)
            key(Key.DirectionDown)
            onNodeWithTag("tv-together-submit").assertIsFocused()
            screenshot("together-$locale")
            runOnIdle { fixture.back.onBackPressed() }

            runOnIdle { fixture.presentation.onAction(TvPlayerAction.OpenSourceDialog) }
            onNodeWithTag("tv-source-action-captcha").assertTextContains(
                playerTestString(Lang.media_captcha_required_message, playerTestString(Lang.media_captcha_image)),
            )
            val media = TestMediaList.first().copy(
                originalTitle = "Test episode", properties = createTestMediaProperties(subtitleLanguageIds = listOf("CHS", "custom")),
            )
            runOnIdle {
                fixture.state = fixture.state.copy(sources = TvSourceSelectionState(listOf(
                    fixture.state.sources.groups.single().copy(state = MediaSourceFetchState.Succeed(1), items = listOf(TvSourceItem(media))),
                ), loading = false))
            }
            onNodeWithTag("tv-source-detailed").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            onNodeWithText(playerTestString(Lang.media_subtitle_language_chinese_simplified) + " / custom").assertIsDisplayed()
            screenshot("source-$locale")
        }
    }

    @Test
    fun localeChangesUpdateExistingErrorsWithoutReplacingFocusedControls() = withPlayerTestLocale("en-US") {
        runAniComposeUiTest {
            val fixture = Fixture("en-US")
            showPlayer(fixture)
            openPanel(TvPlayerPanel.Together)
            key(Key.DirectionDown)
            key(Key.DirectionDown)
            val nodeId = onNodeWithTag("tv-together-submit").assertIsFocused().fetchSemanticsNode().id
            for (locale in listOf("zh-CN", "zh-HK", "zh-TW", "en-US")) {
                runOnIdle {
                    LocaleList.setDefault(LocaleList(Locale.forLanguageTag(locale)))
                    fixture.locale = locale
                }
                onNodeWithTag("tv-together-error").assertTextContains(
                    playerTestString(Lang.watch_together_join_failed, playerTestString(Lang.watch_together_error_wrong_password)),
                )
                onNodeWithTag("tv-together-submit").assertIsFocused().assertTextContains(playerTestString(Lang.watch_together_join))
                assertEquals(nodeId, onNodeWithTag("tv-together-submit").fetchSemanticsNode().id)
            }
        }
    }

    @Test
    fun narrowEnglishControlsCollapseWithAccessibleLabelsAndKeepTheirFocus() = withPlayerTestLocale("en-US") {
        runAniComposeUiTest {
            val fixture = Fixture("en-US")
            showPlayer(fixture)
            key(Key.DirectionUp)
            repeat(TvPlayerPanel.entries.indexOf(TvPlayerPanel.VideoSettings)) { key(Key.DirectionRight) }
            val chip = onNodeWithTag("tv-player-chip-VideoSettings")
            val nodeId = chip.assertIsFocused().fetchSemanticsNode().id
            runOnIdle { fixture.width = 720.dp; fixture.fontScale = 1.3f }
            chip.assertIsFocused().assertContentDescriptionEquals(playerTestString(Lang.video_player_video_enhancement))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            assertEquals(nodeId, chip.fetchSemanticsNode().id)
            assertControlBounds()
            screenshot("controls-narrow-en-US")
            openPanel(TvPlayerPanel.Together)
            runOnIdle { fixture.together = fixture.together.copy(error = null) }
            assertTextFits(playerTestString(Lang.watch_together_join))
            screenshot("together-large-type-en-US")
            runOnIdle { fixture.together = fixture.together.copy(joined = true) }
            onNodeWithTag("tv-together-follow").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            screenshot("together-follow-large-type-en-US")
            assertTextFits(playerTestString(Lang.watch_together_follow_host_desc))
            onNodeWithTag("tv-together-leave").assertIsDisplayed()
            runOnIdle { fixture.together = fixture.together.copy(isHost = true) }
            onNodeWithTag("tv-together-playback").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            screenshot("together-host-large-type-en-US")
            assertTextFits(playerTestString(Lang.watch_together_host_desc))
            val explanationBounds = onNodeWithText(playerTestString(Lang.watch_together_host_desc), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val contentBounds = onNodeWithTag("tv-together-content").fetchSemanticsNode().boundsInRoot
            assertTrue(
                explanationBounds.bottom <= contentBounds.bottom - with(density) { 12.dp.toPx() },
                "Host explanation must remain above the list's bottom fade",
            )
            onNodeWithTag("tv-together-leave").assertIsDisplayed()
        }
    }

    @Test
    fun enhancementLabelsWrapWithoutChangingTheSelectedModesFocus() = withPlayerTestLocale("en-US") {
        runAniComposeUiTest {
            val fixture = Fixture("en-US").apply {
                fontScale = 1.3f
                state = state.copy(options = state.options.copy(enhancementMode = VideoEnhancementMode.PERFORMANCE))
            }
            showPlayer(fixture)
            onNodeWithTag("tv-player-chip-VideoSettings").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            key(Key.DirectionCenter)
            onNodeWithTag("tv-enhancement-PERFORMANCE").assertIsFocused()
            for (resource in listOf(Lang.video_player_off, Lang.video_player_performance, Lang.video_player_quality)) {
                assertTextFits(playerTestString(resource), maxLines = 2)
            }
            key(Key.DirectionRight)
            onNodeWithTag("tv-enhancement-QUALITY").assertIsFocused()
            screenshot("enhancement-large-type-en-US")
        }
    }

    @Test
    fun skipCountdownUsesEnglishPluralAndLocalizesMissingChapterNames() = withPlayerTestLocale("en-US") {
        runAniComposeUiTest {
            val fixture = Fixture("en-US").apply {
                state = state.copy(options = state.options.copy(skipPrompt = TvSkipPrompt("OP", 1)))
            }
            showPlayer(fixture)
            onNodeWithTag("tv-auto-skip-popup").assertTextContains("Skip OP in 1 second")
            runOnIdle { fixture.state = fixture.state.copy(options = fixture.state.options.copy(skipPrompt = TvSkipPrompt("OP", 2))) }
            onNodeWithTag("tv-auto-skip-popup").assertTextContains("Skip OP in 2 seconds")
            runOnIdle { fixture.state = fixture.state.copy(options = fixture.state.options.copy(skipPrompt = TvSkipPrompt(null, 2))) }
            onNodeWithTag("tv-auto-skip-popup").assertTextContains("Skip Chapter in 2 seconds")
            onNodeWithTag("tv-player-seekbar").assertIsFocused()
        }
    }

    private fun AniComposeUiTest.showPlayer(fixture: Fixture) {
        // Activity creation can reset the process locale; configure it after the test host exists.
        runOnIdle { LocaleList.setDefault(LocaleList(Locale.forLanguageTag(fixture.locale))) }
        setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(fixture.locale)) }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fixture.fontScale),
            ) {
                AniTvTheme {
                    val back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    SideEffect { fixture.back = back }
                    TvEpisodeScreen(
                        uiState = fixture.state, togetherState = fixture.together, onTogetherIntent = {},
                        commentsPager = fixture.comments, presentationState = fixture.presentation, actionEvents = emptyFlow(),
                        onIntent = { true }, video = { Box(it.background(Color(0xFF1E2A38))) }, resolver = {}, danmaku = {},
                        modifier = Modifier.width(fixture.width).fillMaxHeight().testTag("tv-i18n-player"),
                    )
                }
            }
        }
    }

    private fun AniComposeUiTest.openPanel(panel: TvPlayerPanel) {
        onNodeWithTag("tv-player-chip-${panel.name}").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key(Key.DirectionCenter)
        onNodeWithTag("tv-player-sidebar").assertIsDisplayed()
    }

    private fun AniComposeUiTest.assertControlBounds() {
        val player = onNodeWithTag("tv-i18n-player").fetchSemanticsNode().boundsInRoot
        val chips = TvPlayerPanel.entries.map { onNodeWithTag("tv-player-chip-${it.name}").fetchSemanticsNode().boundsInRoot }
        chips.forEach { assertTrue(it.width > 0 && it.left >= player.left && it.right <= player.right) }
        chips.zipWithNext().forEach { (first, second) -> assertTrue(first.right <= second.left, "Translated chips overlap") }
        onNodeWithTag("tv-source-button").assertIsDisplayed()
        onNodeWithTag("tv-speed-button").assertIsDisplayed()
        onNodeWithTag("tv-danmaku-toggle").assertIsDisplayed()
    }

    private fun AniComposeUiTest.assertTextFits(text: String, maxLines: Int = Int.MAX_VALUE) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val visibleBounds = node.fetchSemanticsNode().boundsInRoot
        assertFalse(layout.hasVisualOverflow, "Translated text overflows its layout: $text")
        assertTrue(layout.size.width <= visibleBounds.width + 1f, "Translated text is clipped horizontally: $text")
        assertTrue(layout.size.height <= visibleBounds.height + 1f, "Translated text is clipped vertically: $text")
        assertTrue(layout.lineCount <= maxLines, "Translated action breaks into too many lines: $text")
    }

    private fun AniComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun AniComposeUiTest.screenshot(name: String) {
        val bitmap = onNodeWithTag("tv-i18n-player").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir("screenshots"), "i18n-$name.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
