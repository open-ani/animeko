/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.comment

import kotlinx.coroutines.flow.Flow

interface TurnstileState {
    val url: String

    val tokenFlow: Flow<String>

    /**
     * Flow for emitting network errors.
     */
    val webErrorFlow: Flow<Error>

    /**
     * Start requesting token.
     * Calling this method represents that you want to get tokens. [tokenFlow] may produce results any minute.
     */
    fun reload()

    /**
     * Cancel requesting token.
     * [tokenFlow] will not produce any result after calling this method.
     */
    fun cancel()

    companion object {
        /**
         * Callback URI for solving Cloudflare Turnstile at Bangumi.
         * You can intercept the request or register a system-wide URI handler.
         */
        const val CALLBACK_INTERCEPTION_PREFIX = "ani://bangumi-turnstile-callback"
        val CALLBACK_REGEX = Regex("^${CALLBACK_INTERCEPTION_PREFIX}/?\\?token=(.+)$")
    }

    sealed interface Error {
        val code: Int

        data class Network(override val code: Int) : Error
        data class Unknown(override val code: Int) : Error
    }
}