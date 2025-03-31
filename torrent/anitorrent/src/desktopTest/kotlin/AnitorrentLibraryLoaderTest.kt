/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent

import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test

class AnitorrentLibraryLoaderTest {
    @Test
    fun `can load in debug mode`() { // 注意, 这个 test 无法测试打包之后的
        assertDoesNotThrow {
            AnitorrentLibraryLoader.loadLibraries()
        }
    }
}
