/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangelogTest {
    @Test
    fun `removes Full Changelog`() {
        assertEquals(
            """
                - 修复一些弱网环境下的细节问题
                - 修复启动时可能的崩溃
                - 修复识别电影剧集
            """.trimIndent(),
            Changelog(
                "", "",
                """
                - 修复一些弱网环境下的细节问题
                - 修复启动时可能的崩溃
                - 修复识别电影剧集

                **Full Changelog**: https://github.com/open-ani/animeko/compare/v4.0.0-beta04...v4.0.0-beta05
                """.trimIndent(),
            ).changes,
        )
    }

    @Test
    fun `removes Full Changelog no match`() {
        assertEquals(
            """
                - 修复一些弱网环境下的细节问题
                - 修复启动时可能的崩溃
                - 修复识别电影剧集
            """.trimIndent(),
            Changelog(
                "", "",
                """
                - 修复一些弱网环境下的细节问题
                - 修复启动时可能的崩溃
                - 修复识别电影剧集
                """.trimIndent(),
            ).changes,
        )
    }
}