/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.app.videoplayer.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import me.him188.ani.app.videoplayer.subtitle.SubtitleAppearance
import me.him188.ani.app.videoplayer.subtitle.SubtitleSafeArea
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.textStyleRules
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.setFrame

class IosSubtitleOverlayTest {
    @Test
    fun `native subtitle tracks receive the selected size and safe line position`() {
        val item = AVPlayerItem(uRL = NSURL.fileURLWithPath("/missing-native-style-test.mp4"))
        val style = IosNativeSubtitleStyle(AVPlayer(playerItem = item))
        style.update(SubtitleAppearance(2f), SubtitleSafeArea(bottom = 96.0), 600.0)
        assertEquals(1, item.textStyleRules?.size)
        style.update(SubtitleAppearance(0.75f), SubtitleSafeArea(), 600.0)
        assertEquals(1, item.textStyleRules?.size)
    }

    @Test
    fun `larger subtitles increase the native font and preserve separate lines`() {
        val overlay = IosSubtitleOverlay().apply { setFrame(CGRectMake(0.0, 0.0, 1000.0, 600.0)) }
        overlay.update("第一行\nSecond line", SubtitleAppearance(0.75f), SubtitleSafeArea())
        overlay.layoutIfNeeded()
        val small = overlay.label.font.pointSize
        overlay.update("第一行\nSecond line", SubtitleAppearance(2f), SubtitleSafeArea())
        overlay.layoutIfNeeded()
        assertEquals(16.5, small)
        assertEquals(44.0, overlay.label.font.pointSize)
        assertEquals("第一行\nSecond line", overlay.label.text)
    }

    @Test
    fun `long large subtitles stay above controls and inside a narrow viewport`() {
        val overlay = IosSubtitleOverlay().apply { setFrame(CGRectMake(0.0, 0.0, 320.0, 240.0)) }
        overlay.update((1..20).joinToString("\n") { "字幕 $it A long second language caption" }, SubtitleAppearance(2f),
            SubtitleSafeArea(left = 24.0, top = 48.0, right = 24.0, bottom = 80.0))
        overlay.layoutIfNeeded()
        overlay.label.frame.useContents {
            assertTrue(origin.x >= 32)
            assertTrue(origin.x + size.width <= 288)
            assertTrue(origin.y >= 56)
            assertTrue(origin.y + size.height <= 148)
        }
        assertTrue(overlay.label.numberOfLines in 1..6)
    }
}
