/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionCodeTest {
    @Test
    fun `versionCode pattern`() {
        val versionCode = currentAniBuildConfig.fourDigitVersionCode
        assertEquals(4, versionCode.length)
        assertEquals(
            true,
            versionCode matches Regex("""[34][0-9]{3}"""),
            message = "$versionCode is not a valid version code",
        )
    }
}