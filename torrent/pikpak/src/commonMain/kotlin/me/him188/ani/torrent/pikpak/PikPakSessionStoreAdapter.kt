/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.Session
import io.github.nihildigit.pikpak.SessionStore

// Only the refresh token persists. Recomputing the access token through one refresh round-trip per
// start is cheaper than widening PikPakConfig with accessToken/expiresAt/sub, so the restored session
// carries expiresAt = 0 and the SDK refreshes before its first use. An empty refresh token yields null,
// which sends the SDK to a full credentials sign-in.
// Callbacks are account-aware to prevent an old request overwriting a newly selected account.
// onSessionSaved is a post-signin hook for wiping the stored plaintext password. No platform module
// uses it yet: without an OS keystore the password is the only way back from a revoked refresh token.
class PikPakSessionStoreAdapter(
    private val readRefreshToken: (account: String) -> String,
    private val writeRefreshToken: suspend (account: String, token: String) -> Unit,
    private val onSessionSaved: suspend () -> Unit = {},
) : SessionStore {

    override suspend fun load(account: String): Session? {
        val rt = readRefreshToken(account)
        if (rt.isEmpty()) return null
        return Session(
            accessToken = "",
            refreshToken = rt,
            sub = "",
            expiresAt = 0L,
        )
    }

    override suspend fun save(account: String, session: Session) {
        writeRefreshToken(account, session.refreshToken)
        onSessionSaved()
    }

    override suspend fun clear(account: String) {
        writeRefreshToken(account, "")
    }
}
