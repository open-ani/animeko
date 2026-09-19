/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import me.him188.ani.app.data.models.subject.SubjectInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectPresentationTest {
    @Test
    fun `toPresentation sets title and originalTitle from displayName and name`() {
        val info = SubjectInfo.Empty.copy(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！")
        val presentation = info.toPresentation()
        assertEquals("孤独摇滚！", presentation.title)
        assertEquals("ぼっち・ざ・ろっく！", presentation.originalTitle)
    }

    @Test
    fun `originalTitle falls back to title when name is blank`() {
        val info = SubjectInfo.Empty.copy(name = "", nameCn = "孤独摇滚！")
        val presentation = info.toPresentation()
        assertEquals("孤独摇滚！", presentation.title)
        assertEquals("孤独摇滚！", presentation.originalTitle)
    }
}
