/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package me.him188.ani.app.domain.session

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.app.domain.media.fetch.AtomicInteger
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.days

sealed class AbstractBangumiSessionManagerTest {
    internal companion object {
        internal const val ACCESS_TOKEN = "testToken"
        internal const val REFRESH_TOKEN = "refreshToken"
        internal val SUCCESS_USER_INFO = UserInfo.EMPTY
    }

    // default state:
    // - no session (access token)
    // - no refresh token

    internal val tokenRepository = TokenRepository(MemoryDataStore(TokenSave.Initial))

    internal val getSelfInfoCalled = AtomicInteger(0)
    internal val refreshAccessTokenCalled = AtomicInteger(0)

    internal fun TestScope.createManager(
        getSelfInfo: suspend (accessToken: String) -> UserInfo,
        refreshAccessToken: suspend (refreshToken: String) -> NewSession,
        tokenRepository: TokenRepository = this@AbstractBangumiSessionManagerTest.tokenRepository,
        parentCoroutineContext: CoroutineContext = testScheduler,
    ) = BangumiSessionManager(
        tokenRepository = tokenRepository,
        getBangumiSelfInfo = {
            try {
                getSelfInfo(it)
            } finally {
                getSelfInfoCalled.incrementAndGet()
            }
        },
        refreshAccessToken = {
            try {
                refreshAccessToken(it)
            } finally {
                refreshAccessTokenCalled.incrementAndGet()
            }
        },
        parentCoroutineContext = parentCoroutineContext,
        enableSharing = false,
    )

    internal suspend fun BangumiSessionManager.awaitState(drop: Int = 0): SessionStatus {
        return statePass.drop(drop).first()
    }

    internal fun <T> noCall(): T {
        // 必须要返回一个, 因为 flow 实际上还会在跑一会
        throw RepositoryAuthorizationException()
    }

    internal fun refreshTokenSuccess(
        accessToken: String = ACCESS_TOKEN,
        expiresAtMillis: Long = Long.MAX_VALUE,
        refreshToken: String = REFRESH_TOKEN,
    ): NewSession {
        return NewSession(
            AccessTokenPair(
                accessToken, accessToken,
                expiresAtMillis,
            ),
            expiresAtMillis, refreshToken,
        )
    }

    internal suspend fun setExpiredToken() {
        tokenRepository.setSession(
            AccessTokenSession(
                tokens = AccessTokenPair(ACCESS_TOKEN, ACCESS_TOKEN, 0),
                // expired
            ),
        )
    }

    protected val validExpiresAtMillis = currentTimeMillis() + 100.days.inWholeMilliseconds
    internal suspend fun setValidToken(token: String = ACCESS_TOKEN, expiresAtMillis: Long = validExpiresAtMillis) {
        tokenRepository.setSession(
            AccessTokenSession(
                tokens = AccessTokenPair(token, token, expiresAtMillis),
                // not too large, avoid overflow.
            ),
        )
    }

    internal fun TestScope.runCoroutines() {
        testScheduler.runCurrent()
    }

    fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        testBody: suspend TestScope.() -> Unit
    ): TestResult = kotlinx.coroutines.test.runTest(context, testBody = testBody)
}