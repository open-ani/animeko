/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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

class BangumiOAuthUrlTest {
    @Test
    fun `normalizes host-only value as https url`() {
        assertEquals("https://bangumi.rdd.moe", normalizeBangumiWebBaseUrl("bangumi.rdd.moe"))
    }

    @Test
    fun `normalizes blank value to default Bangumi host`() {
        assertEquals("https://bgm.tv", normalizeBangumiWebBaseUrl("  "))
    }

    @Test
    fun `rejects unsupported scheme`() {
        assertNull(normalizeBangumiWebBaseUrl("javascript:alert(1)"))
    }

    @Test
    fun `rewrites Bangumi OAuth host and preserves query`() {
        val original = "https://bgm.tv/oauth/authorize?client_id=ani&redirect_uri=https%3A%2F%2Fapi.myani.org%2Fcb&state=req"

        assertEquals(
            "https://bangumi.rdd.moe/oauth/authorize?client_id=ani&redirect_uri=https%3A%2F%2Fapi.myani.org%2Fcb&state=req",
            rewriteBangumiOAuthUrl(original, "bangumi.rdd.moe"),
        )
    }

    @Test
    fun `rewrites bangumi tv OAuth host`() {
        assertEquals(
            "https://bangumi.rdd.moe/oauth/authorize",
            rewriteBangumiOAuthUrl("https://bangumi.tv/oauth/authorize", "https://bangumi.rdd.moe"),
        )
    }

    @Test
    fun `does not rewrite non-Bangumi urls`() {
        assertEquals(
            "https://api.myani.org/v1/login/bangumi/oauth/callback?code=abc",
            rewriteBangumiOAuthUrl(
                "https://api.myani.org/v1/login/bangumi/oauth/callback?code=abc",
                "https://bangumi.rdd.moe",
            ),
        )
    }
}
