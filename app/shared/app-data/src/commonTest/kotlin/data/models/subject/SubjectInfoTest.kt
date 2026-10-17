/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @see me.him188.ani.app.data.models.subject.SubjectInfo
 */
class SubjectInfoTest {
    private fun subject(name: String, nameCn: String) =
        SubjectInfo.Empty.copy(name = name, nameCn = nameCn)

    @Test
    fun `nameOrNameCn_prefers_original_name`() {
        assertEquals("ぼっち・ざ・ろっく！", subject(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！").nameOrNameCn)
    }

    @Test
    fun `nameOrNameCn_falls_back_to_nameCn_when_name_is_blank`() {
        assertEquals("孤独摇滚！", subject(name = "", nameCn = "孤独摇滚！").nameOrNameCn)
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleTrue_returns_nameOrNameCn`() {
        val info = subject(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！")
        assertEquals(info.nameOrNameCn, info.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleFalse_returns_displayName`() {
        val info = subject(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！")
        assertEquals(info.displayName, info.preferredDisplayName(useOriginalTitle = false))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleTrue_falls_back_to_nameCn_when_name_is_blank`() {
        val info = subject(name = "", nameCn = "孤独摇滚！")
        assertEquals("孤独摇滚！", info.preferredDisplayName(useOriginalTitle = true))
    }
}
