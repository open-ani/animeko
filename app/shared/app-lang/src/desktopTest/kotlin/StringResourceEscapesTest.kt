/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.lang

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 字符串资源由 Compose Multiplatform 直接读取 (见 build.gradle.kts 的 `customDirectory`), 它只还原 `\uXXXX`、`\n`、`\t` 与 `\\`.
 * Android 风格的 `\'`、`\"`、`\@`、`\?` 在 Android 上正常, 在 Compose 资源里会连同反斜杠原样显示.
 * 撇号与引号直接写成 `’`、`“”` 等字符.
 */
class StringResourceEscapesTest {
    @Test
    fun `strings use only escapes compose resources understand`() {
        val files = File("src/androidMain/res").walk().filter { it.name == "strings.xml" }.toList()
        assertTrue(files.isNotEmpty(), "No strings.xml found under ${File("src/androidMain/res").absolutePath}")

        val offenders = files.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> UNSUPPORTED_ESCAPE.containsMatchIn(line) }
                .map { (index, _) -> "${file.parentFile.name}/${file.name}:${index + 1}" }
        }
        assertEquals(emptyList(), offenders)
    }

    private companion object {
        /**
         * 前面不是反斜杠的 `\'`、`\"`、`\@`、`\?`; `\\` 是已转义的反斜杠, 不算.
         */
        val UNSUPPORTED_ESCAPE = Regex("""(?<!\\)\\['"@?]""")
    }
}
