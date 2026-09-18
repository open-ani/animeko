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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PikPakSessionStoreAdapterTest {
    @Test
    fun `restored sessions force token refresh and clearing does not signal signin`() = runTest {
        var token = ""
        val savedTokens = mutableListOf<String>()
        val store = PikPakSessionStoreAdapter(
            readRefreshToken = { token }, writeRefreshToken = { _, value -> token = value },
            onSessionSaved = { savedTokens += token },
        )
        val account = "user@example.com"
        assertNull(store.load(account))
        store.save(account, Session("access", "refresh", "subject", 9999))
        assertEquals(listOf("refresh"), savedTokens)
        assertEquals(Session("", "refresh", "", 0), store.load(account))
        store.clear(account)
        assertNull(store.load(account))
        assertEquals(listOf("refresh"), savedTokens)
    }

    @Test
    fun `tokens are read and written for the requesting account`() = runTest {
        val tokens = mutableMapOf("old" to "old-token", "new" to "new-token")
        val store = PikPakSessionStoreAdapter(
            readRefreshToken = { tokens[it].orEmpty() },
            writeRefreshToken = { account, token -> tokens[account] = token },
        )
        store.save("old", Session("access", "refreshed-old", "subject", 9999))
        assertEquals("new-token", store.load("new")?.refreshToken)
        store.clear("old")
        assertNull(store.load("old"))
        assertEquals("new-token", store.load("new")?.refreshToken)
    }

    @Test
    fun `failed persistence must not signal a saved session`() = runTest {
        var notified = false
        val store = PikPakSessionStoreAdapter(
            readRefreshToken = { "" },
            writeRefreshToken = { _, _ -> error("disk unavailable") },
            onSessionSaved = { notified = true },
        )
        assertFailsWith<IllegalStateException> {
            store.save("user@example.com", Session("access", "refresh", "subject", 9999))
        }
        assertEquals(false, notified)
    }
}
