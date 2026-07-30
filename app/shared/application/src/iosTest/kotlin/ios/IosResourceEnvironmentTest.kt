/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ios

import kotlin.test.Test
import kotlin.test.assertEquals

class IosResourceEnvironmentTest {
    @Test
    fun `Chinese scripts take precedence over region`() {
        assertEquals("CN", resolveIosResourceRegion("zh", "Hans", "SG"))
        assertEquals("CN", resolveIosResourceRegion("zh", "Hans", "HK"))
        assertEquals("HK", resolveIosResourceRegion("zh", "Hant", "SG"))
        assertEquals("HK", resolveIosResourceRegion("zh", "Hant", "CN"))
    }

    @Test
    fun `Traditional Chinese regions retain their regional resources`() {
        assertEquals("HK", resolveIosResourceRegion("zh", "Hant", "HK"))
        assertEquals("MO", resolveIosResourceRegion("zh", "Hant", "MO"))
        assertEquals("TW", resolveIosResourceRegion("zh", "Hant", "TW"))
    }

    @Test
    fun `other locales retain their region`() {
        assertEquals("SG", resolveIosResourceRegion("zh", "", "SG"))
        assertEquals("SG", resolveIosResourceRegion("en", "", "SG"))
    }
}
