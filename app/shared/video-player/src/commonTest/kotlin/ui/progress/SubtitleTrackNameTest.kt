/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(InternalMediampApi::class)
private fun track(
    language: String? = null,
    labels: List<String> = emptyList(),
    id: String = "id-$language-$labels",
) = SubtitleTrack(
    id = id,
    internalId = id,
    language = language,
    labels = labels.map { TrackLabel(null, it) },
)

class SubtitleTrackLanguageParseTest {
    @Test
    fun `parse - chinese variants`() {
        assertEquals(SubtitleTrackLanguage.CHINESE, SubtitleTrackLanguage.parse("chi"))
        assertEquals(SubtitleTrackLanguage.CHINESE, SubtitleTrackLanguage.parse("zho"))
        assertEquals(SubtitleTrackLanguage.CHINESE, SubtitleTrackLanguage.parse("zh"))
        assertEquals(SubtitleTrackLanguage.CHINESE_SIMPLIFIED, SubtitleTrackLanguage.parse("zh-Hans"))
        assertEquals(SubtitleTrackLanguage.CHINESE_SIMPLIFIED, SubtitleTrackLanguage.parse("chs"))
        assertEquals(SubtitleTrackLanguage.CHINESE_TRADITIONAL, SubtitleTrackLanguage.parse("zh-Hant"))
        assertEquals(SubtitleTrackLanguage.CHINESE_TRADITIONAL, SubtitleTrackLanguage.parse("cht"))
        assertEquals(SubtitleTrackLanguage.CANTONESE, SubtitleTrackLanguage.parse("yue"))
    }

    @Test
    fun `parse - other languages`() {
        assertEquals(SubtitleTrackLanguage.JAPANESE, SubtitleTrackLanguage.parse("jpn"))
        assertEquals(SubtitleTrackLanguage.JAPANESE, SubtitleTrackLanguage.parse("ja"))
        assertEquals(SubtitleTrackLanguage.ENGLISH, SubtitleTrackLanguage.parse("eng"))
        assertEquals(SubtitleTrackLanguage.ENGLISH, SubtitleTrackLanguage.parse("en"))
        assertEquals(SubtitleTrackLanguage.KOREAN, SubtitleTrackLanguage.parse("kor"))
        assertEquals(SubtitleTrackLanguage.KOREAN, SubtitleTrackLanguage.parse("ko"))
    }

    @Test
    fun `parse - case insensitive and underscore separated`() {
        assertEquals(SubtitleTrackLanguage.CHINESE_SIMPLIFIED, SubtitleTrackLanguage.parse("ZH_HANS"))
        assertEquals(SubtitleTrackLanguage.CHINESE_SIMPLIFIED, SubtitleTrackLanguage.parse("  zh-CN  "))
        assertEquals(SubtitleTrackLanguage.ENGLISH, SubtitleTrackLanguage.parse("EN"))
    }

    @Test
    fun `parse - falls back to a shorter subtag`() {
        assertEquals(SubtitleTrackLanguage.CHINESE_SIMPLIFIED, SubtitleTrackLanguage.parse("zh-Hans-CN"))
        assertEquals(SubtitleTrackLanguage.ENGLISH, SubtitleTrackLanguage.parse("en-US"))
    }

    @Test
    fun `parse - unknown`() {
        assertNull(SubtitleTrackLanguage.parse(null))
        assertNull(SubtitleTrackLanguage.parse(""))
        assertNull(SubtitleTrackLanguage.parse("   "))
        assertNull(SubtitleTrackLanguage.parse("und"))
        assertNull(SubtitleTrackLanguage.parse("简日双语"))
    }
}

class SubtitleTrackNamesOfTest {
    @Test
    fun `empty input`() {
        assertEquals(emptyList(), subtitleTrackNamesOf(emptyList()))
    }

    @Test
    fun `language code is normalized`() {
        assertEquals(
            listOf(
                SubtitleTrackName.Language(SubtitleTrackLanguage.CHINESE),
                SubtitleTrackName.Language(SubtitleTrackLanguage.JAPANESE),
            ),
            subtitleTrackNamesOf(listOf(track(language = "chi"), track(language = "jpn"))),
        )
    }

    @Test
    fun `human label wins over language code`() {
        assertEquals(
            listOf(SubtitleTrackName.Label("简日双语")),
            subtitleTrackNamesOf(listOf(track(language = "chi", labels = listOf("简日双语")))),
        )
    }

    @Test
    fun `label that is only a language code is normalized instead of shown raw`() {
        assertEquals(
            listOf(SubtitleTrackName.Language(SubtitleTrackLanguage.CHINESE_SIMPLIFIED)),
            subtitleTrackNamesOf(listOf(track(language = null, labels = listOf("CHS")))),
        )
    }

    @Test
    fun `blank labels are ignored`() {
        assertEquals(
            listOf(SubtitleTrackName.Language(SubtitleTrackLanguage.ENGLISH)),
            subtitleTrackNamesOf(listOf(track(language = "eng", labels = listOf("   ")))),
        )
    }

    @Test
    fun `unnamed tracks are numbered by position`() {
        assertEquals(
            listOf(
                SubtitleTrackName.Unnamed(1),
                SubtitleTrackName.Language(SubtitleTrackLanguage.CHINESE),
                SubtitleTrackName.Unnamed(3),
            ),
            subtitleTrackNamesOf(listOf(track(), track(language = "zh"), track())),
        )
    }

    @Test
    fun `duplicate names get a numeric suffix`() {
        assertEquals(
            listOf(
                SubtitleTrackName.Language(SubtitleTrackLanguage.CHINESE, index = 1),
                SubtitleTrackName.Language(SubtitleTrackLanguage.CHINESE, index = 2),
                SubtitleTrackName.Language(SubtitleTrackLanguage.JAPANESE),
            ),
            subtitleTrackNamesOf(
                listOf(track(language = "chi", id = "a"), track(language = "zho", id = "b"), track(language = "jpn")),
            ),
        )
    }

    @Test
    fun `language designator in label is stripped into a note`() {
        assertEquals(
            listOf(
                SubtitleTrackName.LanguageWithNote(SubtitleTrackLanguage.CHINESE_SIMPLIFIED, "KitaujiSub"),
                SubtitleTrackName.LanguageWithNote(SubtitleTrackLanguage.CHINESE_TRADITIONAL, "Sakurato"),
                SubtitleTrackName.LanguageWithNote(SubtitleTrackLanguage.JAPANESE, "Netflix"),
            ),
            subtitleTrackNamesOf(
                listOf(
                    track(language = "chi", labels = listOf("chs[KitaujiSub]")),
                    track(language = "chi", labels = listOf("cht[Sakurato]")),
                    track(language = "jpn", labels = listOf("jpn[Netflix]")),
                ),
            ),
        )
    }

    @Test
    fun `metadata refines a generic designator, group name with language letters is kept intact`() {
        assertEquals(
            listOf(
                SubtitleTrackName.LanguageWithNote(SubtitleTrackLanguage.CHINESE_SIMPLIFIED, "UHA-WINGS"),
            ),
            subtitleTrackNamesOf(
                listOf(track(language = "zh-Hans", labels = listOf("chi [UHA-WINGS]"))),
            ),
        )
    }

    @Test
    fun `extractFromTitle - no designator returns title unchanged`() {
        assertEquals(null to "简日双语", SubtitleTrackLanguage.extractFromTitle("简日双语"))
        assertEquals(
            SubtitleTrackLanguage.CHINESE_SIMPLIFIED to "",
            SubtitleTrackLanguage.extractFromTitle("CHS"),
        )
    }

    @Test
    fun `duplicate labels get a numeric suffix`() {
        assertEquals(
            listOf(
                SubtitleTrackName.Label("简日双语", index = 1),
                SubtitleTrackName.Label("简日双语", index = 2),
            ),
            subtitleTrackNamesOf(
                listOf(
                    track(labels = listOf("简日双语"), id = "a"),
                    track(labels = listOf("简日双语"), id = "b"),
                ),
            ),
        )
    }
}
