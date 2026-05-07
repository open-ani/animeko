/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.PikPakClient
import io.github.nihildigit.pikpak.Session
import io.github.nihildigit.pikpak.SessionStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException

/**
 * Throwaway login probe: builds a [PikPakClient] with the given credentials and
 * calls [PikPakClient.login]. Returns `true` only if login succeeded.
 *
 * If [refreshToken] is non-empty the probe's [SessionStore] hands it to the
 * SDK so login can take the refresh-token shortcut — important once the real
 * engine has wiped the plaintext password from disk. Probe writes are
 * discarded so a failed probe never corrupts the user's live session.
 */
suspend fun testPikPakLogin(
    username: String,
    password: String,
    refreshToken: String,
    httpClient: HttpClient,
): Boolean {
    val client = PikPakClient(
        account = username,
        password = password,
        sessionStore = ProbeSessionStore(refreshToken),
        httpClient = httpClient,
    )
    return try {
        client.login()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        false
    } finally {
        runCatching { client.close() }
    }
}

private class ProbeSessionStore(private val refreshToken: String) : SessionStore {
    override suspend fun load(account: String): Session? {
        if (refreshToken.isEmpty()) return null
        return Session(
            accessToken = "",
            refreshToken = refreshToken,
            sub = "",
            expiresAt = 0L,
        )
    }
    override suspend fun save(account: String, session: Session) {}
    override suspend fun clear(account: String) {}
}
