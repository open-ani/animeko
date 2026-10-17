/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.player

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvBufferOptionsTest {
    @Test
    fun `forward buffer is expressed in seconds`() {
        assertEquals("60", mpvBufferOptions()["cache-secs"])
    }

    @Test
    fun `byte caps are symmetric and match the policy`() {
        val options = mpvBufferOptions()
        assertEquals((96L * 1024 * 1024).toString(), options["demuxer-max-bytes"])
        assertEquals(options["demuxer-max-bytes"], options["demuxer-max-back-bytes"])
    }
}
