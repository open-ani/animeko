/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OAuthPlatformTest {
    @Test
    fun `ids match the provider ids of the server`() {
        assertEquals(OAuthPlatform.BANGUMI, OAuthPlatform.fromId("bangumi"))
        assertEquals(OAuthPlatform.GITHUB, OAuthPlatform.fromId("github"))
    }

    @Test
    fun `unknown provider from a newer server is null instead of failing`() {
        assertNull(OAuthPlatform.fromId("google"))
        assertNull(OAuthPlatform.fromId(""))
        assertNull(OAuthPlatform.fromId("GitHub")) // id 区分大小写
    }
}
