/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

object HttpTokenChecker {
    /**
     * Check if the token is syntactically valid.
     */
    fun isValidToken(token: String): Boolean {
        // Define the allowed characters for a valid token
        val allowedCharsRegex = Regex("^[A-Za-z0-9\\-_.~]*\$")

        // Check if the token is empty or contains forbidden characters
        if (token.isBlank() || !allowedCharsRegex.matches(token)) {
            return false
        }

        // Tokens must not exceed reasonable length (commonly 255 characters for headers)
        if (token.length > 255) {
            return false
        }

        return true
    }
}