/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.main

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.pressKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import me.him188.ani.tv.ui.settings.TvSettingsScreen
import me.him188.ani.tv.ui.settings.TvSettingsUiState

class TvSettingsNavigationUiTest {
    @Test
    fun settingsIsFullScreenAndBackRestoresItsLauncherAndThePreviousHomePage() = runAniComposeUiTest {
        var currentPage = TvShellContent.Schedule
        lateinit var backDispatcher: OnBackPressedDispatcher
        setContent {
            val dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SideEffect { backDispatcher = dispatcher }
            TvApplicationTheme(ThemeSettings.Default.seedColor, languageTag = "en") {
                val stack = rememberNavBackStack(NavRoutes.Main(MainScreenPage.Exploration))
                val memory = remember { TvFocusMemory() }
                NavDisplay(
                    backStack = stack,
                    onBack = { stack.removeLastOrNull() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = entryProvider {
                        entry<NavRoutes.Main> {
                            var page by rememberSaveable { mutableStateOf(TvShellContent.Schedule) }
                            currentPage = page
                            TvMainShell(
                                TvMainUiState(), page, { page = it },
                                onOpenSettings = { stack.add(NavRoutes.Settings()) },
                                onLogout = {},
                                focusMemory = memory,
                            ) {
                                val focus = rememberTvFocusScope()
                                val entry = TvFocusKey("test-page")
                                focus.Resolver()
                                focus.InitialFocus(entry)
                                TvOptionRow(
                                    "Schedule",
                                    modifier = Modifier.testTag("home-content")
                                        .tvFocusAnchor(focus, entry).tvFocusMemorable("test-page"),
                                ) {}
                            }
                        }
                        entry<NavRoutes.Settings> {
                            TvSettingsScreen(TvSettingsUiState(loaded = true), {})
                        }
                    },
                )
            }
        }
        awaitFocus("home-content")
        key(Key.Menu)
        listOf("Search", "Explore", "Schedule", "Collection", "Settings", "Sign In").forEach {
            onNode(hasText(it) and hasAnyAncestor(hasTestTag("tv-main-navigation"))).assertExists()
        }
        repeat(2) { key(Key.DirectionDown) }
        onNodeWithText("Settings").assertIsFocused()
        key(Key.DirectionCenter)
        awaitFocus("tv-settings-section-Appearance")
        mainClock.advanceTimeBy(500)
        onNodeWithTag("tv-main-shell").assertDoesNotExist()
        onNodeWithTag("tv-main-navigation").assertDoesNotExist()
        val settings = onNodeWithTag("tv-settings").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, settings.left)
        key(Key.DirectionRight)
        awaitFocus("tv-settings-item-language")
        runOnIdle { backDispatcher.onBackPressed() }
        awaitFocus("tv-settings-section-Appearance")
        // Navigation 3 receives system Back through the Activity dispatcher.
        runOnIdle { backDispatcher.onBackPressed() }
        try {
            waitUntil(timeoutMillis = 5_000) {
                mainClock.advanceTimeByFrame()
                onAllNodes(hasText("Settings") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError(onAllNodes(isRoot()).onLast().printToString(), failure)
        }
        assertEquals(TvShellContent.Schedule, currentPage)
        onNodeWithTag("tv-settings").assertDoesNotExist()
        key(Key.DirectionRight)
        awaitFocus("home-content")
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
    }
}
