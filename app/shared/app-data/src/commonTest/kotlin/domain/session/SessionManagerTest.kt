/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.data.repository.user.Session
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SessionManagerTest {
    private val nowMillis = 1_000_000L

    @Test
    fun `startup clears expired access token session`() = runTest {
        val store = MemoryDataStore(
            TokenSave(
                refreshToken = "refresh",
                accessTokens = TokenSave.AccessTokens(
                    bangumiAccessToken = "bangumi",
                    aniAccessToken = "ani",
                    expiresAtMillis = nowMillis,
                ),
            ),
        )
        val repository = TokenRepository(store)
        val manager = createSessionManager(repository, backgroundScope)

        manager.clearSessionIfAccessTokenExpired()

        assertEquals(TokenSave.Initial, store.data.value)
        assertEquals(GuestSession, repository.session.value())
    }

    @Test
    fun `startup keeps valid access token session`() = runTest {
        val save = TokenSave(
            refreshToken = "refresh",
            accessTokens = TokenSave.AccessTokens(
                bangumiAccessToken = "bangumi",
                aniAccessToken = "ani",
                expiresAtMillis = nowMillis + 2.hours.inWholeMilliseconds,
            ),
        )
        val store = MemoryDataStore(save)
        val repository = TokenRepository(store)
        val manager = createSessionManager(repository, backgroundScope)

        manager.clearSessionIfAccessTokenExpired()

        assertEquals(save, store.data.value)
        assertEquals(
            AccessTokenSession(
                AccessTokenPair(
                    aniAccessToken = "ani",
                    expiresAtMillis = nowMillis + 2.hours.inWholeMilliseconds,
                    bangumiAccessToken = "bangumi",
                ),
            ),
            repository.session.value(),
        )
    }

    @Test
    fun `login from guest notifies listener before the session is saved`() = runTest {
        val repository = TokenRepository(MemoryDataStore(TokenSave.Initial))
        val calls = mutableListOf<Pair<String, Session>>()
        val manager = createSessionManager(repository, backgroundScope) { userId ->
            calls += userId to repository.session.value()
        }

        manager.setSession(validSession(), "refresh", userId = "u1")

        assertEquals(listOf<Pair<String, Session>>("u1" to GuestSession), calls)
        assertEquals(validSession(), repository.session.value())
    }

    @Test
    fun `setSession while logged in does not notify listener`() = runTest {
        val repository = TokenRepository(MemoryDataStore(TokenSave.Initial))
        repository.setSession(validSession())
        val calls = mutableListOf<String>()
        val manager = createSessionManager(repository, backgroundScope) { calls += it }

        manager.setSession(validSession(), "refresh", userId = "u1")

        assertEquals(emptyList(), calls)
    }

    @Test
    fun `setSession that is not a new login does not notify listener`() = runTest {
        val repository = TokenRepository(MemoryDataStore(TokenSave.Initial))
        val calls = mutableListOf<String>()
        val manager = createSessionManager(repository, backgroundScope) { calls += it }

        manager.setSession(validSession(), "refresh", isNewLogin = false, userId = "u1")

        assertEquals(emptyList(), calls)
    }

    @Test
    fun `listener failure does not affect login`() = runTest {
        val repository = TokenRepository(MemoryDataStore(TokenSave.Initial))
        val manager = createSessionManager(repository, backgroundScope) { error("listener failed") }

        manager.setSession(validSession(), "refresh", userId = "u1")

        assertEquals(validSession(), repository.session.value())
    }

    private fun validSession() = AccessTokenSession(
        AccessTokenPair(
            aniAccessToken = "ani",
            expiresAtMillis = nowMillis + 2.hours.inWholeMilliseconds,
            bangumiAccessToken = null,
        ),
    )

    private fun createSessionManager(
        repository: TokenRepository,
        coroutineScope: CoroutineScope,
        onNewLogin: (suspend (userId: String) -> Unit)? = null,
    ): SessionManager {
        return SessionManager(
            tokenRepository = repository,
            coroutineScope = coroutineScope,
            refreshSession = SessionManager.SessionRefresher {
                error("refresh should not be called")
            },
            clock = FixedClock(nowMillis),
            newLoginListener = onNewLogin?.let { listener ->
                SessionManager.NewLoginListener { userId -> listener(userId) }
            },
        )
    }

    private suspend fun <T> Flow<T>.value(): T {
        return first()
    }

    private class FixedClock(private val millis: Long) : Clock {
        override fun now(): Instant {
            return Instant.fromEpochMilliseconds(millis)
        }
    }
}
