/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleCuesTest {
    @Test
    fun `parses SRT cues and strips formatting tags`() {
        val cues = parseSubtitleCues(
            """1
                |00:00:01,250 --> 00:00:03,500
                |{\\an8}<i>Hello</i> &amp; goodbye
                |
                |2
                |00:00:04.000 --> 00:00:05.000
                |Next cue
            """.trimMargin(),
        )

        assertEquals(listOf(1250L, 4000L), cues.map { it.startMillis })
        assertEquals("Hello & goodbye", cues.first().text)
        assertEquals("", SubtitleCueTimeline(cues).textAt(1250L - 1))
        assertEquals("Hello & goodbye", SubtitleCueTimeline(cues).textAt(1250L))
        assertEquals("", SubtitleCueTimeline(cues).textAt(3500L))
        assertEquals("Next cue", SubtitleCueTimeline(cues).textAt(4500L))
    }

    @Test
    fun `parses WebVTT cues without numeric indexes`() {
        val cues = parseSubtitleCues(
            """WEBVTT

                |00:00:01.000 --> 00:00:02.000 align:start
                |<c.yellow>Caption</c>
            """.trimMargin(),
        )

        assertEquals(1, cues.size)
        assertEquals("Caption", cues.single().text)
    }
    @Test
    fun `overlapping cues remain visible after forward and backward seeking`() {
        val timeline = SubtitleCueTimeline(
            listOf(SubtitleCue(0, 10_000, "Long cue"), SubtitleCue(2_000, 3_000, "Short cue")),
        )
        assertEquals("Long cue", timeline.textAt(5_000))
        assertEquals("Long cue\nShort cue", timeline.textAt(2_500))
        assertEquals("", timeline.textAt(10_000))
        assertEquals("Long cue", timeline.textAt(1_000))
    }

}
