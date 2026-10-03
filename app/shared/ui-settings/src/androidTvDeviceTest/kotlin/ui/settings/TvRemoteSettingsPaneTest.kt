/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostState
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostStatus
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.tv.ui.foundation.theme.AniTvTheme
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor

class TvRemoteSettingsPaneTest {
    private val link =
        RemoteSettingsLink(
            "192.168.1.123",
            54321,
            "820f62ee-3a31-4491-b4b7-1e91f6bb12dd",
            "6.0.0-beta01",
            "",
        )
    private var remoteSettings by mutableStateOf(RemoteSettingsHostState(link))
    private var permissionRequests = 0

    @Test
    fun codeIsScannableAndFocusStaysOnTheSection() = runAniComposeUiTest {
        mount()
        onNodeWithTag("tv-remote-settings-qr").assertIsDisplayed()
        val bitmap = onNodeWithTag("tv-remote-settings-qr").captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertEquals(
            link.toUri(),
            MultiFormatReader()
                .decode(
                    BinaryBitmap(
                        HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))
                    )
                )
                .text,
        )
        // The pane has nothing to focus: entering it keeps the focus on the section.
        listOf(Key.DirectionRight, Key.DirectionCenter, Key.DirectionUp, Key.DirectionLeft).forEach {
            key(it)
            awaitFocus("tv-settings-section-Remote")
        }
        onNodeWithTag("tv-remote-settings-qr").assertIsDisplayed()
        key(Key.DirectionDown)
        awaitFocus("tv-settings-section-Appearance")
        onNodeWithTag("tv-remote-settings").assertDoesNotExist()
    }

    @Test
    fun confirmRequestsThePermissionAndTheCodeAppearsWhenGranted() = runAniComposeUiTest {
        remoteSettings = RemoteSettingsHostState(status = RemoteSettingsHostStatus.PERMISSION_REQUIRED)
        mount()
        onNodeWithTag("tv-remote-settings-status").assertIsDisplayed()
        onNodeWithTag("tv-remote-settings-qr").assertDoesNotExist()
        assertEquals(0, permissionRequests)
        key(Key.DirectionCenter)
        assertEquals(1, permissionRequests)
        remoteSettings = RemoteSettingsHostState(link)
        waitForIdle()
        onNodeWithTag("tv-remote-settings-qr").assertIsDisplayed()
        awaitFocus("tv-settings-section-Remote")
    }

    @Test
    fun statusFitsThePaneAtLargeFont() = runAniComposeUiTest {
        remoteSettings = RemoteSettingsHostState(status = RemoteSettingsHostStatus.NO_NETWORK)
        mount(fontScale = 1.3f)
        onNodeWithTag("tv-remote-settings-status").assertIsDisplayed()
        val pane = onNodeWithTag("tv-settings-detail").fetchSemanticsNode().boundsInRoot
        val content = onNodeWithTag("tv-remote-settings").fetchSemanticsNode().boundsInRoot
        assertTrue(content.bottom <= pane.bottom, "Expected $content to fit in $pane")
    }

    private fun AniComposeUiTest.mount(fontScale: Float = 1f) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                AniTvTheme {
                    Box(Modifier.fillMaxSize().background(tvShellBackgroundColor())) {
                        TvSettingsScreen(
                            TvSettingsUiState(loaded = true),
                            {},
                            remoteSettings = remoteSettings,
                            onRequestLocalNetworkPermission = { permissionRequests++ },
                        )
                    }
                }
            }
        }
        awaitFocus("tv-settings-section-Appearance")
        key(Key.DirectionUp)
        awaitFocus("tv-settings-section-Remote")
    }

    private fun AniComposeUiTest.key(key: Key) {
        onAllNodes(isRoot() and hasAnyDescendant(isFocused())).onLast().performKeyInput {
            pressKey(key)
        }
        mainClock.advanceTimeByFrame()
    }

    private fun AniComposeUiTest.awaitFocus(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
