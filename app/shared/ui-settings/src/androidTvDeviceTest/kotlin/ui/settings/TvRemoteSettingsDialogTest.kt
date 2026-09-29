/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostState
import me.him188.ani.app.domain.settings.remote.RemoteSettingsHostStatus
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.tv.ui.foundation.theme.AniTvTheme

class TvRemoteSettingsDialogTest {
    @Test
    fun englishDialogAtLargeFont() {
        val previous = LocaleList.getDefault()
        try {
            LocaleList.setDefault(LocaleList(Locale.ENGLISH))
            runAniComposeUiTest {
                mount(
                    RemoteSettingsHostState(
                        RemoteSettingsLink(
                            "192.168.1.123",
                            54321,
                            "820f62ee-3a31-4491-b4b7-1e91f6bb12dd",
                            "6.0.0-beta01",
                            "",
                        )
                    ),
                    fontScale = 1.3f,
                    onClose = {},
                )
                onNodeWithText("Set up your TV with your phone").assertIsDisplayed()
                onNodeWithTag("tv-remote-settings-close").assertIsDisplayed().assertIsFocused()
                onNodeWithTag("tv-remote-settings-qr").assertIsDisplayed()
                capture("en-large-font")
            }
        } finally {
            LocaleList.setDefault(previous)
        }
    }

    @Test
    fun codeIsScannableAndDpadFocusStaysOnClose() = runAniComposeUiTest {
        val link =
            RemoteSettingsLink(
                "192.168.1.123",
                54321,
                "820f62ee-3a31-4491-b4b7-1e91f6bb12dd",
                "6.0.0-beta01",
                "",
            )
        var closed = 0
        mount(RemoteSettingsHostState(link), onClose = { closed++ })
        onNodeWithTag("tv-remote-settings-close").assertIsFocused()
        onNodeWithTag("tv-remote-settings-qr").assertIsDisplayed()
        capture("ready")
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
        listOf(Key.DirectionUp, Key.DirectionRight, Key.DirectionDown, Key.DirectionLeft).forEach {
            key ->
            onNodeWithTag("tv-remote-settings-close").performKeyInput { pressKey(key) }
            onNodeWithTag("tv-remote-settings-close").assertIsFocused()
        }
        onNodeWithTag("tv-remote-settings-close").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, closed)
    }

    @Test
    fun unavailableStateFitsAtLargeFontAndBackClosesIt() = runAniComposeUiTest {
        var closed = 0
        mount(
            RemoteSettingsHostState(status = RemoteSettingsHostStatus.NO_NETWORK),
            fontScale = 1.3f,
            onClose = { closed++ },
        )
        onNodeWithTag("tv-remote-settings-unavailable").assertIsDisplayed()
        onNodeWithTag("tv-remote-settings-qr").assertDoesNotExist()
        onNodeWithTag("tv-remote-settings-close").assertIsFocused().assertIsDisplayed()
        capture("unavailable-large-font")
        onNodeWithTag("tv-remote-settings-close").performKeyInput { pressKey(Key.Back) }
        assertEquals(1, closed)
    }

    private fun AniComposeUiTest.mount(
        state: RemoteSettingsHostState,
        fontScale: Float = 1f,
        onClose: () -> Unit,
    ) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                AniTvTheme {
                    Box(Modifier.fillMaxSize()) { TvRemoteSettingsDialog(state, onClose) }
                }
            }
        }
        mainClock.advanceTimeBy(400)
        waitForIdle()
    }

    private fun AniComposeUiTest.capture(name: String) {
        val dialog = onNodeWithTag("tv-remote-settings-dialog")
        dialog.assertScreenshot("tv-remote-settings/$name")
        val bounds = dialog.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= 0 && bounds.top >= 0)
        val file =
            File(
                InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .getExternalFilesDir(null),
                "tv-remote-settings-$name.png",
            )
        file.outputStream().use {
            dialog.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
