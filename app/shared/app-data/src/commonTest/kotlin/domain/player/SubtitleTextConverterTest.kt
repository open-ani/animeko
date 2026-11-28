/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.SubtitleTextConversion

class SubtitleTextConverterTest {
    @Test
    fun `conversion preserves original text and line breaks when targets change`() = runTest {
        val converter = SubtitleTextConverter()
        val original = "软件與網絡\nHello, world!"
        assertEquals("软件与网络\nHello, world!", converter.convert(original, SubtitleTextConversion.SIMPLIFIED))
        assertEquals("軟件與網絡\nHello, world!", converter.convert(original, SubtitleTextConversion.TRADITIONAL))
        assertEquals(original, converter.convert(original, SubtitleTextConversion.ORIGINAL))
        assertEquals("软件与网络\nHello, world!", converter.convert(original, SubtitleTextConversion.SIMPLIFIED))
        assertEquals("", converter.convert("", SubtitleTextConversion.TRADITIONAL))
    }
}
