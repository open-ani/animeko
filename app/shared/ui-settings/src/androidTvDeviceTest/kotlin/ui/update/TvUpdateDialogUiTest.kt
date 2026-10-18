/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TvUpdateDialogUiTest {
    private var state by mutableStateOf(
        TvUpdateUiState(visible = true, phase = TvUpdatePhase.Available, newVersion = "5.4.0", changes = listOf("Feature A")),
    )
    private val intents = mutableListOf<TvUpdateIntent>()

    private fun AniComposeUiTest.mount() {
        setContent {
            TvApplicationTheme(ThemeSettings.Default.seedColor, "en") {
                Box(Modifier.fillMaxSize().background(tvShellBackgroundColor())) {
                    if (state.visible) {
                        TvUpdateDialog(state, { intent ->
                            intents += intent
                            state = when (intent) {
                                TvUpdateIntent.Download -> state.copy(phase = TvUpdatePhase.Downloading, downloadProgress = .5f)
                                TvUpdateIntent.Dismiss -> state.copy(visible = false)
                                else -> state
                            }
                        })
                    }
                }
            }
        }
    }

    @Test
    fun downloadTakesInitialFocusAndTheNextPhaseKeepsIt() = runAniComposeUiTest {
        mount()
        awaitFocus("tv-update-download")
        key(Key.DirectionCenter)
        assertEquals(TvUpdateIntent.Download, intents.last())
        awaitFocus("tv-update-cancel-download")
    }

    @Test
    fun backDismissesAnOptionalUpdate() = runAniComposeUiTest {
        mount()
        awaitFocus("tv-update-download")
        key(Key.Back)
        assertEquals(TvUpdateIntent.Dismiss, intents.last())
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            onAllNodes(hasTestTag("tv-update-dialog")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun expiredVersionCannotBeDismissed() = runAniComposeUiTest {
        state = state.copy(expired = true, expiredLatestVersion = "5.4.0")
        mount()
        awaitFocus("tv-update-download")
        onNodeWithTag("tv-update-dismiss").assertDoesNotExist()
        key(Key.Back)
        assertFalse(TvUpdateIntent.Dismiss in intents)
        onNodeWithTag("tv-update-dialog").assertIsDisplayed()
        awaitFocus("tv-update-download")
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
            val focused = onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("Expected focus on $tag; actual: $focused", failure)
        }
    }
}
