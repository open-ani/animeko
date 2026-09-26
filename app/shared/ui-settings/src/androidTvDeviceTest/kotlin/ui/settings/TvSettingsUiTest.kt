/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.abs
import kotlin.test.assertTrue
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor

class TvSettingsUiTest {
    private var state by mutableStateOf(TvSettingsUiState(loaded = true))
    private val intents = mutableListOf<TvSettingsIntent>()
    private val openedUrls = mutableListOf<String>()

    private fun AniComposeUiTest.mount(language: String = "zh-CN", fontScale: Float = 1f) {
        val previous = LocaleList.getDefault()
        state = state.copy(appearance = state.appearance.copy(appLanguage = ComposeLocale(language)))
        setContent {
            val activity = LocalContext.current.findActivity()
            DisposableEffect(Unit) { onDispose { LocaleList.setDefault(previous) } }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                TvApplicationTheme(state.theme.seedColor, state.appearance.appLanguage?.toLanguageTag()) {
                    val localizedActivity = LocalContext.current.findActivity()
                    SideEffect { assertEquals(activity, localizedActivity) }
                    Box(Modifier.fillMaxSize().background(tvShellBackgroundColor())) {
                        TvSettingsScreen(state, { intent ->
                            intents += intent
                            state = when (intent) {
                                is TvSettingsIntent.Appearance -> state.copy(appearance = intent.update(state.appearance))
                                is TvSettingsIntent.Theme -> state.copy(theme = intent.update(state.theme))
                                is TvSettingsIntent.Torrent -> state.copy(torrent = intent.update(state.torrent))
                                is TvSettingsIntent.Video -> state.copy(video = intent.update(state.video))
                                is TvSettingsIntent.Filter -> state.copy(filter = state.filter.copy(enableRegexFilter = intent.enabled))
                                is TvSettingsIntent.Preference -> state.copy(preference = intent.update(state.preference))
                                is TvSettingsIntent.Selector -> state.copy(selector = intent.update(state.selector))
                                is TvSettingsIntent.Resolver -> state.copy(resolver = intent.update(state.resolver))
                                is TvSettingsIntent.SourceEnabled -> state.copy(sources = state.sources.map {
                                    if (it.id == intent.id) it.copy(enabled = intent.enabled) else it
                                })
                                is TvSettingsIntent.SubscriptionEnabled -> state.copy(
                                    subscriptions = state.subscriptions.map { if (it.id == intent.id) it.copy(enabled = intent.enabled) else it },
                                    sources = state.sources.map { if (it.subscription == intent.id) it.copy(enabled = intent.enabled) else it },
                                )
                                is TvSettingsIntent.SaveRegex -> state.copy(regexFilters = if (intent.isNew) {
                                    state.regexFilters + intent.filter
                                } else state.regexFilters.map { if (it.id == intent.filter.id) intent.filter else it })
                                is TvSettingsIntent.RemoveRegex -> state.copy(regexFilters = state.regexFilters - intent.filter)
                                else -> state
                            }
                        }, onOpenUrl = { openedUrls += it })
                    }
                }
            }
        }
        awaitFocus("tv-settings-section-Appearance")
    }

    @Test
    fun sectionFirstNavigationKeepsBothPanesStationaryAndReusesThePalette() = runAniComposeUiTest {
        mount()
        val screen = bounds("tv-settings")
        val sections = bounds("tv-settings-sections")
        val title = bounds("tv-settings-title")
        assertEquals(screen.height, sections.height)
        assertTrue(title.left >= sections.left && title.right <= sections.right)
        onNodeWithText("Animeko TV").assertDoesNotExist()
        val previewWidth = bounds("tv-settings-detail").width
        val sectionSize = bounds("tv-settings-section-Appearance").size
        capture("sections")
        key(Key.DirectionDown)
        awaitFocus("tv-settings-section-Theme")
        assertEquals(sectionSize, bounds("tv-settings-section-Appearance").size)
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-palette-5")
        mainClock.advanceTimeBy(400)
        assertEquals(previewWidth, bounds("tv-settings-detail").width)
        assertEquals(sections, bounds("tv-settings-sections"))
        assertTrue(abs(bounds("tv-settings-detail").left - sections.width) <= 1f)
        capture("palette")
        key(Key.DirectionLeft)
        awaitFocus("tv-settings-item-palette-4")
        key(Key.DirectionCenter)
        assertTrue(intents.last() is TvSettingsIntent.Theme)
        repeat(4) { key(Key.DirectionLeft) }
        awaitFocus("tv-settings-item-palette-0")
        key(Key.DirectionLeft)
        awaitFocus("tv-settings-section-Theme")
        mainClock.advanceTimeBy(400)
        assertEquals(previewWidth, bounds("tv-settings-detail").width)
    }

    @Test
    fun selectingNsfwAndWatchedFilterUpdatesOnlyTheChosenPreferences() = runAniComposeUiTest {
        mount()
        key(Key.DirectionRight)
        awaitFocus("tv-settings-item-language")
        key(Key.DirectionDown)
        awaitFocus("tv-settings-item-nsfw")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-choice-1")
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-nsfw")
        assertEquals(NsfwMode.HIDE, state.appearance.searchSettings.nsfwMode)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        onNodeWithTag("tv-settings-item-hide-watched").assertIsOn()
        assertTrue(state.appearance.searchSettings.ignoreDoneAndDroppedSubjects)
        assertEquals(NsfwMode.HIDE, state.appearance.searchSettings.nsfwMode)
        capture("appearance-detail")
        key(Key.Back)
        awaitFocus("tv-settings-section-Appearance")
        assertEquals(2, intents.size)
    }

    @Test
    fun quickBackCancelsDetailFocusAndDoesNotReopenIt() = runAniComposeUiTest {
        mount()
        mainClock.autoAdvance = false
        key(Key.DirectionRight)
        awaitFocus("tv-settings-item-language")
        mainClock.advanceTimeBy(64)
        key(Key.Back)
        mainClock.advanceTimeBy(500)
        mainClock.autoAdvance = true
        awaitFocus("tv-settings-section-Appearance")
        key(Key.DirectionDown)
        awaitFocus("tv-settings-section-Theme")
        mainClock.advanceTimeBy(500)
        onNodeWithTag("tv-settings-section-Theme").assertIsFocused()
    }

    @Test
    fun subscriptionsAndIndividualSourcesToggleWithoutOpeningSourceDialogs() = runAniComposeUiTest {
        state = state.copy(
            sources = listOf(TvSettingsSource("test", "Example source", "Source description", "https://example.com", false, "web-selector", "group")),
            subscriptions = listOf(TvSettingsSubscription("group", "https://example.com/sources.json", true)),
        )
        mount()
        repeat(3) { key(Key.DirectionDown) }
        awaitFocus("tv-settings-section-Sources")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-subscriptions")
        key(Key.DirectionDown)
        awaitFocus("tv-settings-item-source-test")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-source-test")
        onNodeWithTag("tv-settings-item-source-test").assertIsOn()
        onNodeWithTag("tv-settings-info").assertDoesNotExist()
        capture("sources")
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        awaitFocus("tv-settings-item-subscription-group")
        assertTrue(bounds("tv-settings-item-subscriptions").right <= bounds("tv-settings-extra").left)
        key(Key.DirectionCenter)
        onNodeWithTag("tv-settings-item-subscription-group").assertIsOff()
        awaitFocus("tv-settings-item-subscription-group")
        assertEquals(false, state.sources.single().enabled)
        capture("subscriptions")
        val focusedSubscription = onNodeWithTag("tv-settings-item-subscription-group").captureToImage().asAndroidBitmap()
        val background = focusedSubscription.getPixel(focusedSubscription.width / 2, focusedSubscription.height / 10)
        assertTrue((background ushr 16 and 0xff) > 200, "The focused subscription must retain its visible focus fill after toggling")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-subscription-group")
        onNodeWithTag("tv-settings-item-subscription-group").assertIsOn()
        assertEquals(true, state.sources.single().enabled)
        key(Key.DirectionLeft)
        awaitFocus("tv-settings-item-subscriptions")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        onNodeWithTag("tv-settings-item-source-test").assertIsOff()
        assertEquals(true, state.subscriptions.single().enabled)
        key(Key.Back)
        awaitFocus("tv-settings-section-Sources")
    }

    @Test
    fun regexValidationEditingAndDeletionKeepPredictableFocus() = runAniComposeUiTest {
        state = state.copy(regexFilters = listOf(DanmakuRegexFilter("rule", "Rule", "spoiler")))
        mount()
        repeat(2) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-player-Playback")
        select("tv-settings-item-player-Danmaku")
        awaitFocus("tv-settings-item-danmaku")
        select("tv-settings-item-regex-rule")
        awaitFocus("tv-settings-input")
        onNodeWithTag("tv-settings-input").performTextReplacement("[")
        onNodeWithTag("tv-settings-save").assertIsNotEnabled()
        onNodeWithTag("tv-settings-input").performTextReplacement("spoiler.*")
        select("tv-settings-save")
        awaitFocus("tv-settings-item-regex-rule")
        assertEquals("spoiler.*", state.regexFilters.single().regex)
        select("tv-settings-item-regex-rule")
        select("tv-settings-regex-delete")
        awaitFocus("tv-settings-delete-cancel")
        capture("delete-confirmation")
        assertEquals(1, state.regexFilters.size)
        key(Key.Back)
        awaitFocus("tv-settings-regex-delete")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-delete-cancel")
        key(Key.DirectionRight)
        awaitFocus("tv-settings-delete-confirm")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-regex-add")
        assertTrue(state.regexFilters.isEmpty())
    }

    @Test
    fun subtitlePriorityCanBeReorderedAndDeselectedWithTheRemote() = runAniComposeUiTest {
        mount()
        repeat(4) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-subtitles")
        val original = state.preference.fallbackSubtitleLanguageIds!!
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-order-${original.first()}")
        select("tv-settings-reorder")
        awaitFocus("tv-settings-order-${original.first()}")
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        awaitFocus("tv-settings-order-${original.first()}")
        assertTrue(bounds("tv-settings-order-${original.first()}").top > bounds("tv-settings-order-${original.last()}").top)
        capture("subtitle-moving")
        // Back cancels only the active move; the editor remains open.
        key(Key.Back)
        awaitFocus("tv-settings-order-${original.first()}")
        assertTrue(bounds("tv-settings-order-${original.first()}").top < bounds("tv-settings-order-${original.last()}").top)
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        key(Key.Back)
        awaitFocus("tv-settings-reorder")
        assertTrue(intents.isEmpty())
        select("tv-settings-save")
        awaitFocus("tv-settings-item-subtitles")
        assertEquals(original.reversed(), state.preference.fallbackSubtitleLanguageIds)
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-order-${original.last()}")
        key(Key.DirectionCenter)
        onNodeWithTag("tv-settings-order-${original.last()}").assertIsOff()
        capture("subtitle-order")
        select("tv-settings-save")
        awaitFocus("tv-settings-item-subtitles")
        assertEquals(listOf(original.first()), state.preference.fallbackSubtitleLanguageIds)
        onNodeWithTag("tv-settings-item-prefer-bt").assertDoesNotExist()
    }

    @Test
    fun aboutReturnsOneLevelAtATimeToTheOriginalEntry() = runAniComposeUiTest {
        state = state.copy(libraries = listOf(TvSettingsLibrary("lib", "Library", "1.0", null, "MIT", "Permission is hereby granted.")))
        mount()
        repeat(6) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-version")
        select("tv-settings-item-developers")
        capture("developers")
        key(Key.Back)
        awaitFocus("tv-settings-item-developers")
        select("tv-settings-item-acknowledgements")
        awaitFocus("tv-settings-item-bangumi")
        select("tv-settings-item-licenses")
        awaitFocus("tv-settings-item-license-lib")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-info")
        key(Key.Back)
        awaitFocus("tv-settings-item-license-lib")
        key(Key.Back)
        awaitFocus("tv-settings-item-licenses")
        key(Key.Back)
        awaitFocus("tv-settings-item-acknowledgements")
        key(Key.Back)
        awaitFocus("tv-settings-section-About")
    }

    @Test
    fun englishLargeTextKeepsSettingsWithinTheDetailPane() = runAniComposeUiTest {
        mount("en", 1.3f)
        repeat(2) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-player-Playback")
        val pane = bounds("tv-settings-detail")
        val row = bounds("tv-settings-item-player-Playback")
        assertTrue(row.left >= pane.left && row.right <= pane.right)
        capture("player-english-large")
        select("tv-settings-item-player-Advanced")
        awaitFocus("tv-settings-item-hls-filter")
        val parentEntry = onNodeWithTag("tv-settings-item-player-Advanced", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val parentViewport = onNodeWithTag("tv-settings-detail-items", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(parentEntry.top >= parentViewport.top && parentEntry.bottom <= parentViewport.bottom,
            "The selected parent menu must remain completely visible")
        capture("player-extra-english-large")
        select("tv-settings-item-preinit-effects")
        assertTrue(intents.last() is TvSettingsIntent.Kernel)
    }

    @Test
    fun extraPaneKeepsItsParentMenuVisibleAndLeftReturnsToItsEntry() = runAniComposeUiTest {
        mount()
        repeat(2) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        select("tv-settings-item-player-Playback")
        awaitFocus("tv-settings-item-auto-next")
        assertTrue(bounds("tv-settings-item-player-Playback").right <= bounds("tv-settings-extra").left)
        assertTrue(bounds("tv-settings-item-auto-next").left >= bounds("tv-settings-extra").left)
        capture("player-extra")
        key(Key.DirectionLeft)
        awaitFocus("tv-settings-item-player-Playback")
        onNodeWithTag("tv-settings-extra").assertDoesNotExist()
        key(Key.DirectionRight)
        awaitFocus("tv-settings-item-auto-next")
        key(Key.Back)
        awaitFocus("tv-settings-item-player-Playback")
        key(Key.DirectionLeft)
        awaitFocus("tv-settings-section-Player")
    }

    @Test
    fun watchingAdvancedUsesAnExtraPaneWithAnAnimatedWorkflowAndPreservesItsParent() = runAniComposeUiTest {
        mount("en")
        repeat(4) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-subtitles")
        onNodeWithTag("tv-settings-item-fast-select").assertDoesNotExist()
        mainClock.autoAdvance = false
        select("tv-settings-item-watching-advanced")
        awaitFocus("tv-settings-item-fast-select")
        assertTrue(bounds("tv-settings-item-watching-advanced").right <= bounds("tv-settings-extra").left)
        val workflow = bounds("tv-settings-selector-workflow")
        val setting = bounds("tv-settings-item-fast-select")
        assertTrue(abs(workflow.width * 2 - setting.width) <= 2f)
        assertTrue(abs(workflow.center.x - setting.center.x) <= 2f)
        val before = onNodeWithTag("tv-settings-selector-workflow").captureToImage().asAndroidBitmap()
        mainClock.advanceTimeBy(1_600)
        val after = onNodeWithTag("tv-settings-selector-workflow").captureToImage().asAndroidBitmap()
        assertTrue(!before.sameAs(after), "The selector workflow must advance with the frame clock")
        val fastSelect = state.selector.fastSelectWebKind
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-fast-select")
        assertEquals(!fastSelect, state.selector.fastSelectWebKind)
        if (!state.selector.fastSelectWebKind) key(Key.DirectionCenter)
        select("tv-settings-item-source-wait")
        navigateTo("tv-settings-choice-4", if (state.selector.fastSelectWebLowTierToleranceDuration.inWholeSeconds > 10) Key.DirectionUp else Key.DirectionDown)
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-source-wait")
        assertEquals(10, state.selector.fastSelectWebLowTierToleranceDuration.inWholeSeconds)
        capture("watching-advanced-workflow")
        key(Key.Back)
        mainClock.autoAdvance = true
        awaitFocus("tv-settings-item-watching-advanced")
        onNodeWithTag("tv-settings-extra").assertDoesNotExist()
    }

    @Test
    fun languageChangesImmediatelyAndKeepsTheCurrentSettingFocused() = runAniComposeUiTest {
        mount()
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-language")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-choice-1")
        select("tv-settings-choice-4")
        awaitFocus("tv-settings-item-language")
        onNodeWithTag("tv-settings-item-language").assertTextContains("Language", substring = true)
        assertEquals("en", state.appearance.appLanguage?.toLanguageTag())
        key(Key.Back)
        awaitFocus("tv-settings-section-Appearance")
    }

    @Test
    fun loadedLicenseListReceivesFocusAndLongLicenseTextScrollsWithTheRemote() = runAniComposeUiTest {
        state = state.copy(librariesLoading = true)
        mount()
        repeat(6) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        select("tv-settings-item-acknowledgements")
        select("tv-settings-item-licenses")
        awaitFocus("tv-settings-item-licenses-loading")
        runOnIdle {
            state = state.copy(
                librariesLoading = false,
                libraries = listOf(TvSettingsLibrary(
                    "lib", "Library", "1.0", null, "MIT",
                    (1..100).joinToString("\n") { "License paragraph $it. Permission is hereby granted." },
                )),
            )
        }
        awaitFocus("tv-settings-item-license-lib")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-info")
        repeat(3) { key(Key.DirectionDown) }
        mainClock.advanceTimeBy(500)
        val scroll = onNodeWithTag("tv-settings-info").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.value() > 0f)
        capture("license")
        key(Key.Back)
        awaitFocus("tv-settings-item-license-lib")
    }

    @Test
    fun returningToASectionKeepsTheLastSettingAndPaletteStartsOnTheAppliedColor() = runAniComposeUiTest {
        mount()
        key(Key.DirectionCenter)
        navigateTo("tv-settings-item-hide-watched")
        key(Key.Back)
        awaitFocus("tv-settings-section-Appearance")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-palette-5")
        key(Key.DirectionLeft)
        key(Key.DirectionCenter)
        key(Key.Back)
        awaitFocus("tv-settings-section-Theme")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-palette-4")
        key(Key.Back)
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-hide-watched")
    }

    @Test
    fun loadingCompletesWithoutLosingTheRemoteFocus() = runAniComposeUiTest {
        state = state.copy(loaded = false)
        mount()
        key(Key.DirectionRight)
        awaitFocus("tv-settings-item-loading")
        runOnIdle { state = state.copy(loaded = true) }
        awaitFocus("tv-settings-item-language")
        key(Key.DirectionDown)
        awaitFocus("tv-settings-item-nsfw")
    }

    @Test
    fun unsavedSelectionAndTextEditsAreDiscardedByBack() = runAniComposeUiTest {
        mount()
        repeat(4) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-subtitles")
        val original = state.preference.fallbackSubtitleLanguageIds
        key(Key.DirectionCenter)
        key(Key.DirectionCenter)
        key(Key.Back)
        awaitFocus("tv-settings-item-subtitles")
        assertEquals(original, state.preference.fallbackSubtitleLanguageIds)
        select("tv-settings-item-alliance")
        awaitFocus("tv-settings-input")
        onNodeWithTag("tv-settings-input").performTextReplacement("Fansub.*")
        key(Key.Back)
        awaitFocus("tv-settings-item-alliance")
        assertTrue(intents.isEmpty())
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-input")
        onNodeWithTag("tv-settings-input").performTextReplacement("Fansub.*")
        key(Key.DirectionDown)
        awaitFocus("tv-settings-save")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-alliance")
        assertEquals(listOf("Fansub.*"), state.preference.alliancePatterns)
    }

    @Test
    fun playerGroupsReturnToTheirEntryAndSpeedRangeOnlySavesOnConfirmation() = runAniComposeUiTest {
        mount()
        repeat(2) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        select("tv-settings-item-player-Picture")
        awaitFocus("tv-settings-item-enhancement")
        select("tv-settings-item-speed-range")
        awaitFocus("tv-settings-speed-min")
        val original = state.video.minPlaybackSpeed
        key(Key.DirectionRight)
        key(Key.Back)
        awaitFocus("tv-settings-item-speed-range")
        assertEquals(original, state.video.minPlaybackSpeed)
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-speed-min")
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        awaitFocus("tv-settings-speed-max")
        key(Key.DirectionDown)
        awaitFocus("tv-settings-save")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-speed-range")
        assertEquals(original + .25f, state.video.minPlaybackSpeed)
        key(Key.Back)
        awaitFocus("tv-settings-item-player-Picture")
        key(Key.Back)
        awaitFocus("tv-settings-section-Player")
    }

    @Test
    fun linksOfferAScannableCodeAndFocusTheActionInsteadOfTheUrlText() = runAniComposeUiTest {
        mount(fontScale = 1.3f)
        repeat(6) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        onNodeWithTag("tv-settings-item-qq").assertDoesNotExist()
        listOf("website", "telegram").forEachIndexed { index, entry ->
            select("tv-settings-item-$entry")
            awaitFocus("tv-settings-open-link")
            onNodeWithTag("tv-settings-link-qr").assertExists()
            capture(entry)
            val bitmap = onNodeWithTag("tv-settings-link-qr").captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val decoded = MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
            ).text
            key(Key.DirectionCenter)
            assertEquals(index + 1, openedUrls.size)
            assertEquals(decoded, openedUrls.last())
            key(Key.Back)
            awaitFocus("tv-settings-item-$entry")
        }
    }

    @Test
    fun bitTorrentSettingsAreReachableAndRestoreFocusAfterEditing() = runAniComposeUiTest {
        mount()
        navigateTo("tv-settings-section-BitTorrent")
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-item-torrent-download")
        select("tv-settings-item-torrent-metered")
        assertEquals(false, state.torrent.limitUploadOnMeteredNetwork)
        navigateTo("tv-settings-item-torrent-ratio", Key.DirectionUp)
        key(Key.DirectionCenter)
        key(Key.Back)
        awaitFocus("tv-settings-item-torrent-ratio")
        capture("bittorrent")
    }

    private fun AniComposeUiTest.navigateTo(tag: String, direction: Key = Key.DirectionDown) {
        repeat(60) {
            if (onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            key(direction)
        }
        val focused = onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
        error("Unable to reach $tag with $direction; focused nodes: $focused")
    }

    private fun AniComposeUiTest.select(tag: String) {
        navigateTo(tag)
        key(Key.DirectionCenter)
    }
    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
    }

    private fun AniComposeUiTest.awaitFocus(tag: String) {
        try {
            waitUntil(timeoutMillis = 5_000) {
                mainClock.advanceTimeByFrame()
                onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            capture("failure-$tag")
            val focused = onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("Expected focus on $tag; actual: $focused", failure)
        }
        onNodeWithTag(tag).assertIsFocused()
    }

    private fun AniComposeUiTest.bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun AniComposeUiTest.capture(name: String) {
        mainClock.advanceTimeBy(400)
        onNodeWithTag("tv-settings").assertScreenshot("tv-settings/$name")
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "tv-settings-$name.png")
        file.outputStream().use {
            onNodeWithTag("tv-settings").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
