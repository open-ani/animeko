/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvOptionsTest {
    private fun lines(text: String) = text.lines()

    @Test
    fun `empty text has no options`() {
        assertEquals(emptyList(), parseMpvOptions(lines("")))
        assertEquals(emptyList(), parseMpvOptions(lines("  \n\n\t\n")))
    }

    @Test
    fun `each line is one option`() {
        assertEquals(
            listOf(MpvOption("hwdec", "auto"), MpvOption("profile", "fast")),
            parseMpvOptions(lines("hwdec=auto\nprofile=fast")),
        )
    }

    @Test
    fun `spaces around key and value are ignored`() {
        assertEquals(
            listOf(MpvOption("hwdec", "auto")),
            parseMpvOptions(lines("   hwdec  =  auto   ")),
        )
    }

    @Test
    fun `comments and blank lines are ignored`() {
        assertEquals(
            listOf(MpvOption("hwdec", "auto")),
            parseMpvOptions(lines("# 硬件解码\n\n  # 另一条注释\nhwdec=auto\n")),
        )
    }

    @Test
    fun `only the first equals sign splits the line`() {
        assertEquals(
            listOf(MpvOption("script-opts", "osc-scalewindowed=2")),
            parseMpvOptions(lines("script-opts=osc-scalewindowed=2")),
        )
    }

    @Test
    fun `command line style dashes are accepted`() {
        assertEquals(
            listOf(MpvOption("hwdec", "auto")),
            parseMpvOptions(lines("--hwdec=auto")),
        )
    }

    @Test
    fun `surrounding quotes are removed from value`() {
        assertEquals(
            listOf(MpvOption("sub-font", "Noto Sans CJK SC")),
            parseMpvOptions(lines("""sub-font="Noto Sans CJK SC"""")),
        )
        assertEquals(
            listOf(MpvOption("sub-font", "Noto Sans CJK SC")),
            parseMpvOptions(lines("sub-font='Noto Sans CJK SC'")),
        )
        // 不成对的引号保持原样
        assertEquals(
            listOf(MpvOption("sub-font", "\"Noto")),
            parseMpvOptions(lines("""sub-font="Noto""")),
        )
    }

    @Test
    fun `value-less line is a flag option`() {
        assertEquals(
            listOf(MpvOption("fs", "yes")),
            parseMpvOptions(lines("fs")),
        )
        assertEquals(
            listOf(MpvOption("audio", "no")),
            parseMpvOptions(lines("no-audio")),
        )
        assertEquals(
            listOf(MpvOption("border", "no")),
            parseMpvOptions(lines("--no-border")),
        )
    }

    @Test
    fun `lines without a key are ignored`() {
        assertEquals(emptyList(), parseMpvOptions(lines("=auto\n--\n  =  ")))
    }

    @Test
    fun `empty value is kept`() {
        assertEquals(
            listOf(MpvOption("sub-file-paths", "")),
            parseMpvOptions(lines("sub-file-paths=")),
        )
    }

    @Test
    fun `splitting keeps comments but trims surrounding blank lines`() {
        assertEquals(
            listOf("# 硬件解码", "hwdec=auto", "", "profile=fast"),
            splitMpvOptionLines("\n\n# 硬件解码\nhwdec=auto\n\nprofile=fast\n\n"),
        )
    }

    @Test
    fun `splitting blank text gives no lines`() {
        assertEquals(emptyList(), splitMpvOptionLines(""))
        assertEquals(emptyList(), splitMpvOptionLines("\n  \n"))
    }

    @Test
    fun `splitting and joining round-trips the edited text`() {
        val text = "# 硬件解码\nhwdec=auto\n\nprofile=fast"
        assertEquals(text, splitMpvOptionLines(text).joinToString("\n"))
    }
}
