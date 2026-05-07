/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [PikPakSessionStoreAdapter] — the bridge between the SDK's
 * [io.github.nihildigit.pikpak.SessionStore] contract and whatever persists
 * the user's refresh token (typically `PikPakConfig` via DataStore).
 *
 * Pure, no SDK networking: the adapter's job is plain read/write indirection
 * plus a small synthesis step on load().
 */
class PikPakSessionStoreAdapterTest {

    private class FakeStore(var refreshToken: String = "") {
        val adapter = PikPakSessionStoreAdapter(
            readRefreshToken = { refreshToken },
            writeRefreshToken = { refreshToken = it },
        )
    }

    @Test
    fun `load returns null when no refresh token persisted`() = runTest {
        val store = FakeStore(refreshToken = "")
        assertNull(store.adapter.load("user@example.com"))
    }

    @Test
    fun `load synthesises a stale session when a refresh token exists`() = runTest {
        val store = FakeStore(refreshToken = "rt-abc")
        val session = store.adapter.load("user@example.com")
        assertEquals("rt-abc", session?.refreshToken)
        // We only persist the refresh token; the rest must be blanked/stale
        // so the SDK immediately goes through its refresh path.
        assertEquals("", session?.accessToken)
        assertEquals("", session?.sub)
        assertEquals(0L, session?.expiresAt)
    }

    @Test
    fun `save writes back the session's refresh token`() = runTest {
        val store = FakeStore(refreshToken = "old")
        store.adapter.save(
            account = "user@example.com",
            session = io.github.nihildigit.pikpak.Session(
                accessToken = "at",
                refreshToken = "rt-new",
                sub = "sub-id",
                expiresAt = 9999L,
            ),
        )
        // Only refreshToken is persisted; the other fields are discarded on purpose.
        assertEquals("rt-new", store.refreshToken)
    }

    @Test
    fun `clear wipes the refresh token`() = runTest {
        val store = FakeStore(refreshToken = "rt-abc")
        store.adapter.clear("user@example.com")
        assertEquals("", store.refreshToken)
    }

    @Test
    fun `load after clear returns null`() = runTest {
        val store = FakeStore(refreshToken = "rt-abc")
        store.adapter.clear("user@example.com")
        assertNull(store.adapter.load("user@example.com"))
    }

    @Test
    fun `load after save round-trips the refresh token`() = runTest {
        val store = FakeStore()
        store.adapter.save(
            account = "user@example.com",
            session = io.github.nihildigit.pikpak.Session(
                accessToken = "ignored",
                refreshToken = "rt-42",
                sub = "sub",
                expiresAt = 123L,
            ),
        )
        val loaded = store.adapter.load("user@example.com")
        assertEquals("rt-42", loaded?.refreshToken)
    }

    @Test
    fun `save fires onSessionSaved so the platform can drop the plaintext password`() = runTest {
        var refreshToken = ""
        var cleared = 0
        val adapter = PikPakSessionStoreAdapter(
            readRefreshToken = { refreshToken },
            writeRefreshToken = { refreshToken = it },
            onSessionSaved = { cleared++ },
        )
        adapter.save(
            account = "user@example.com",
            session = io.github.nihildigit.pikpak.Session(
                accessToken = "at",
                refreshToken = "rt-new",
                sub = "sub",
                expiresAt = 1L,
            ),
        )
        assertEquals(1, cleared)
        assertEquals("rt-new", refreshToken)
    }

    @Test
    fun `clear does not fire onSessionSaved`() = runTest {
        var refreshToken = "rt-existing"
        var cleared = 0
        val adapter = PikPakSessionStoreAdapter(
            readRefreshToken = { refreshToken },
            writeRefreshToken = { refreshToken = it },
            onSessionSaved = { cleared++ },
        )
        // clear() runs when the SDK decides the current refresh token is no
        // longer usable. Wiping the password here would strand the user —
        // the next signin would fail because nothing knows the password.
        adapter.clear("user@example.com")
        assertEquals(0, cleared)
        assertEquals("", refreshToken)
    }
}
