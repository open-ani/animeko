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
    fun `takes content after ben ci geng xin heading and drops subheadings`() {
        assertEquals(
            """
                * 进入应用的新版本提示恢复为带按钮卡片
                * 低端设备性能优化

                * 全新沉浸式追番页
            """.trimIndent(),
            Changelog(
                "", "",
                """
                [github-android]: https://example.com/a.apk

                | 处理器架构 | 下载 |
                |---|---|
                | universal | [GitHub][github-android] |

                ## 本次更新

                ### 更新与通用

                * 进入应用的新版本提示恢复为带按钮卡片
                * 低端设备性能优化

                ### TV 追番页

                * 全新沉浸式追番页

                > Android TV 遥控器使用说明请见 README。
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