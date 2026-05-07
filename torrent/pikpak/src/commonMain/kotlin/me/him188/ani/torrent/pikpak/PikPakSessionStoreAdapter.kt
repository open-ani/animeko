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

/**
 * Bridges the SDK's [SessionStore] to whatever refresh-token persistence the
 * app already has (typically [PikPakConfig] via DataStore). Only the refresh
 * token is persisted; the short-lived access token is recomputed on each app
 * start via a single refresh round-trip, which is cheaper than the schema
 * migration needed to widen [PikPakConfig] with accessToken/expiresAt/sub.
 *
 * [load] synthesises a [Session] with `expiresAt = 0` so the SDK treats the
 * cached access token as already stale and goes straight into refresh. If
 * [readRefreshToken] yields an empty string, we return `null` and the SDK
 * falls through to full credentials sign-in.
 *
 * [onSessionSaved] is invoked after a successful [save]. It is meant as a
 * hook for platform modules to do post-signin hygiene on the credentials —
 * historically wiping the plaintext password from DataStore. That wipe is
 * currently a no-op (see the `TODO(pikpak-credential-keystore)` comments in
 * the platform Koin modules) because the engine has no OS-keystore fallback
 * yet, so we keep the password on disk to recover when the refresh token
 * gets revoked. The callback contract stays in place so a future keystore
 * migration can restore the wipe without touching this adapter.
 */
class PikPakSessionStoreAdapter(
    private val readRefreshToken: () -> String,
    private val writeRefreshToken: suspend (String) -> Unit,
    private val onSessionSaved: suspend () -> Unit = {},
) : SessionStore {

    override suspend fun load(account: String): Session? {
        val rt = readRefreshToken()
        if (rt.isEmpty()) return null
        return Session(
            accessToken = "",
            refreshToken = rt,
            sub = "",
            expiresAt = 0L,
        )
    }

    override suspend fun save(account: String, session: Session) {
        writeRefreshToken(session.refreshToken)
        onSessionSaved()
    }

    override suspend fun clear(account: String) {
        writeRefreshToken("")
    }
}
