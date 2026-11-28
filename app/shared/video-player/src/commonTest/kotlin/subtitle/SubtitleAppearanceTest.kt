/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.him188.ani.app.data.models.preference.SubtitleStyle

class SubtitleAppearanceTest {
    @Test
    fun `position changes preserve bounds and native styling is kept for defaults`() {
        val top = subtitleRegion(1280.0, 720.0, SubtitleSafeArea(), 0f)
        val middle = subtitleRegion(1280.0, 720.0, SubtitleSafeArea(), 0.5f)
        val bottom = subtitleRegion(1280.0, 720.0, SubtitleSafeArea(), 1f)
        assertTrue(top.top < middle.top && middle.top < bottom.top)
        assertEquals(top.height, bottom.height)
        assertTrue(!SubtitleAppearance().requiresTextOverlay)
        assertTrue(SubtitleAppearance(style = SubtitleStyle(textColor = 0xFFFFFF00)).requiresTextOverlay)
        assertEquals(bottom, subtitleRegion(1280.0, 720.0, SubtitleSafeArea(), Float.NaN))
    }

    @Test
    fun `font scales are bounded and invalid values use the default`() {
        assertEquals(SubtitleAppearance.MinFontSize, SubtitleAppearance(-1f).fontSize)
        assertEquals(SubtitleAppearance.MaxFontSize, SubtitleAppearance(99f).fontSize)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(1f, SubtitleAppearance(value).effectiveFontScale)
        }
    }

    @Test
    fun `subtitle regions avoid safe areas and controls in narrow and large viewports`() {
        for (width in listOf(0.0, 32.0, 320.0, 1210.0)) {
            for (height in listOf(0.0, 40.0, 180.0, 834.0)) {
                val region = subtitleRegion(width, height, SubtitleSafeArea(left = 24.0, top = 64.0, right = 24.0, bottom = 96.0))
                assertTrue(region.left >= 0 && region.left + region.width <= width)
                assertTrue(region.top >= 0 && region.bottom <= height)
                assertTrue(region.width >= 0 && region.height >= 0)
                assertTrue(region.height <= height * 0.35)
                if (height >= 180) assertTrue(region.bottom <= height - 108)
            }
        }
    }

    @Test
    fun `duplicate overlapping cues use one line while different cues remain separate`() {
        val timeline = SubtitleCueTimeline(listOf(
            SubtitleCue(0, 4000, "Same"), SubtitleCue(1000, 3000, "Same"), SubtitleCue(1500, 2500, "Other"),
        ))
        assertEquals("Same\nOther", timeline.textAt(2000))
        assertEquals("Same", timeline.textAt(3500))
    }
}
