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

    private fun createSessionManager(repository: TokenRepository, coroutineScope: CoroutineScope): SessionManager {
        return SessionManager(
            tokenRepository = repository,
            coroutineScope = coroutineScope,
            refreshSession = SessionManager.SessionRefresher {
                error("refresh should not be called")
            },
            clock = FixedClock(nowMillis),
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
