/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PassResultTest {
    @Test
    fun `anyChanged and allFinished`() {
        PassResult(anyChanged = true, allFinished = true).run {
            assertEquals(true, anyChanged)
            assertEquals(true, allFinished)
        }
    }

    @Test
    fun `anyChanged and not allFinished`() {
        PassResult(anyChanged = true, allFinished = false).run {
            assertEquals(true, anyChanged)
            assertEquals(false, allFinished)
        }
    }

    @Test
    fun `not anyChanged and allFinished`() {
        PassResult(anyChanged = false, allFinished = true).run {
            assertEquals(false, anyChanged)
            assertEquals(true, allFinished)
        }
    }

    @Test
    fun `not anyChanged and not allFinished`() {
        PassResult(anyChanged = false, allFinished = false).run {
            assertEquals(false, anyChanged)
            assertEquals(false, allFinished)
        }
    }
}