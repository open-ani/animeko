/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.person

import me.him188.ani.app.data.models.subject.PersonPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonWorkInfoTest {
    private val subject = PersonSubjectSummary(1, "name", "名字", "")

    @Test
    fun `voice acting comes before staff positions`() {
        val work = PersonWorkInfo(
            subject,
            listOf(PersonPosition.ThemeSongPerformance, PersonPosition.InsertedSongPerformance),
            castCount = 1,
        )
        assertEquals(listOf("配音", "主题歌演出", "插入歌演出"), work.roleNames)
    }

    @Test
    fun `work the person only voiced shows voice acting`() {
        assertEquals(listOf("配音"), PersonWorkInfo(subject, emptyList(), castCount = 3).roleNames)
    }

    @Test
    fun `staff only work shows positions without voice acting`() {
        val work = PersonWorkInfo(subject, listOf(PersonPosition.Director, PersonPosition.Director))
        assertEquals(listOf("导演"), work.roleNames)
    }

    @Test
    fun `positions without a known name are skipped`() {
        assertEquals(emptyList(), PersonWorkInfo(subject, listOf(PersonPosition(139))).roleNames)
    }
}
