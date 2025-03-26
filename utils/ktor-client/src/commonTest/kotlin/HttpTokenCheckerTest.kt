/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import me.him188.ani.utils.ktor.HttpTokenChecker.isValidToken
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

class HttpTokenCheckerTest {
    @Test
    fun `test valid token`() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        assertTrue(isValidToken(token))
    }

    @Test
    fun `test valid token with special characters`() {
        val token = "token-123_ABC~xyz.456"
        assertTrue(isValidToken(token))
    }

    @Test
    fun `test empty token`() {
        val token = ""
        assertFalse(isValidToken(token))
    }

    @Test
    fun `test token with forbidden characters`() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9@#$$%%"
        assertFalse(isValidToken(token))
    }

    @Test
    fun `test token with spaces`() {
        val token = "eyJhbGciOiJI UzI1NiIsInR5cCI6IkpXVCJ9"
        assertFalse(isValidToken(token))
    }

    @Test
    fun `test token exceeding max length`() {
        val token = "a".repeat(256) // Exceeds the 255 character limit
        assertFalse(isValidToken(token))
    }

    @Test
    fun `test token at max length`() {
        val token = "a".repeat(255) // Exactly 255 characters
        assertTrue(isValidToken(token))
    }

    @Test
    fun `chinese is not valid`() {
        assertFalse(isValidToken("柚"))
    }

    @Test
    fun `chinese unicode is not valid`() {
        assertFalse(isValidToken("\u67DA")) // https://github.com/open-ani/animeko/issues/1242
    }

    @Test
    fun `test token with only allowed special characters`() {
        val token = "-_.~"
        assertTrue(isValidToken(token))
    }
}