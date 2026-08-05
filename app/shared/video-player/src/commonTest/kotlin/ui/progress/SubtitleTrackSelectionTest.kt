/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import me.him188.ani.app.data.models.preference.SubtitleTrackPreference
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackLabel
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalMediampApi::class)
private fun track(
    id: String,
    language: String? = null,
    labels: List<String> = emptyList(),
) = SubtitleTrack(
    id = id,
    internalId = id,
    language = language,
    labels = labels.map { TrackLabel(null, it) },
)

class ToSubtitleTrackPreferenceTest {
    @Test
    fun `null track means off`() {
        assertEquals(SubtitleTrackPreference(off = true), (null as SubtitleTrack?).toSubtitleTrackPreference())
    }

    @Test
    fun `records language and label`() {
        assertEquals(
            SubtitleTrackPreference(off = false, label = "简日双语", language = "chi"),
            track("a", language = "chi", labels = listOf("简日双语")).toSubtitleTrackPreference(),
        )
    }

    @Test
    fun `blank language and label are dropped`() {
        assertEquals(
            SubtitleTrackPreference(off = false, label = null, language = null),
            track("a", language = "  ", labels = listOf("  ")).toSubtitleTrackPreference(),
        )
    }
}

class SubtitleTrackSelectionForTest {
    private val chs = track("chs", language = "chi", labels = listOf("简体"))
    private val cht = track("cht", language = "zh-Hant", labels = listOf("繁體"))
    private val jpn = track("jpn", language = "jpn")

    @Test
    fun `no recorded preference keeps current`() {
        assertEquals(
            SubtitleTrackSelection.KeepCurrent,
            subtitleTrackSelectionFor(listOf(chs, jpn), SubtitleTrackPreference.Default),
        )
    }

    @Test
    fun `off preference turns subtitles off even when candidates match`() {
        assertEquals(
            SubtitleTrackSelection.Off,
            subtitleTrackSelectionFor(listOf(chs, jpn), SubtitleTrackPreference(off = true)),
        )
    }

    @Test
    fun `exact label match wins over language match`() {
        assertEquals(
            SubtitleTrackSelection.Select(cht),
            subtitleTrackSelectionFor(
                listOf(chs, cht),
                SubtitleTrackPreference(label = "繁體", language = "chi"),
            ),
        )
    }

    @Test
    fun `label match is case insensitive and trimmed`() {
        val eng = track("eng", language = "eng", labels = listOf("Full SDH"))
        assertEquals(
            SubtitleTrackSelection.Select(eng),
            subtitleTrackSelectionFor(listOf(jpn, eng), SubtitleTrackPreference(label = "  full sdh ")),
        )
    }

    @Test
    fun `normalized language match across differing codes`() {
        assertEquals(
            SubtitleTrackSelection.Select(jpn),
            subtitleTrackSelectionFor(listOf(chs, jpn), SubtitleTrackPreference(language = "ja")),
        )
    }

    @Test
    fun `falls back to another chinese variant`() {
        assertEquals(
            SubtitleTrackSelection.Select(cht),
            subtitleTrackSelectionFor(listOf(jpn, cht), SubtitleTrackPreference(language = "zh-Hans")),
        )
    }

    @Test
    fun `exact language beats chinese variant fallback`() {
        val hant = track("hant", language = "cht")
        assertEquals(
            SubtitleTrackSelection.Select(hant),
            subtitleTrackSelectionFor(listOf(chs, hant), SubtitleTrackPreference(language = "zh-Hant")),
        )
    }

    @Test
    fun `no match keeps current`() {
        assertEquals(
            SubtitleTrackSelection.KeepCurrent,
            subtitleTrackSelectionFor(listOf(jpn), SubtitleTrackPreference(language = "eng")),
        )
    }

    @Test
    fun `empty candidates keeps current`() {
        assertEquals(
            SubtitleTrackSelection.KeepCurrent,
            subtitleTrackSelectionFor(emptyList(), SubtitleTrackPreference(language = "chi")),
        )
    }

    @Test
    fun `ties resolve to the first candidate`() {
        val a = track("a", language = "chi")
        val b = track("b", language = "zho")
        assertEquals(
            SubtitleTrackSelection.Select(a),
            subtitleTrackSelectionFor(listOf(a, b), SubtitleTrackPreference(language = "chi")),
        )
    }

    @Test
    fun `unrecognized language code matches literally`() {
        val eo = track("eo", language = "epo")
        assertEquals(
            SubtitleTrackSelection.Select(eo),
            subtitleTrackSelectionFor(listOf(jpn, eo), SubtitleTrackPreference(language = "EPO")),
        )
    }
}
