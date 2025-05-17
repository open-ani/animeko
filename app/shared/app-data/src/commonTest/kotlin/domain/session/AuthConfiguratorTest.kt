/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.client.models.AniAnonymousBangumiUserToken
import me.him188.ani.client.models.AniBangumiUserToken
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest as runCoroutineTest

class AuthConfiguratorTest : AbstractBangumiSessionManagerTest() {
    @Test
    fun `test initial state should be Idle`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        val authClient = createTestAuthClient(getResult = { null })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(launchedAuthorize, "Initial state should not trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test initial check - no existing session`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        val authClient = createTestAuthClient(getResult = { null })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(launchedAuthorize, "Check state should not trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test initial check - session existed - refresh succeeded`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { getSelfInfoSuccess() },
            refreshAccessToken = { refreshTokenSuccess() },
        ).apply {
            setSession(AccessTokenSession(ACCESS_TOKEN, Long.MAX_VALUE))
        }
        val authClient = createTestAuthClient(getResult = { null })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.AwaitingResult>(awaitItem(), "Start check should change state to AwaitingResult.")
            assertIs<AuthState.Success>(
                awaitItem(),
                "Session existed, after checking should change state to Success.",
            )

            advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(launchedAuthorize, "Check state should not trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test initial check - session existed - refresh failed`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { getSelfInfoSuccess() },
            refreshAccessToken = { noCall() },
        ).apply {
            setSession(AccessTokenSession(ACCESS_TOKEN, 0))
        }
        val authClient = createTestAuthClient(getResult = { null })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.TokenExpired>(
                awaitItem(),
                "Session existed, after checking should change state to TokenExpired.",
            )

            advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(launchedAuthorize, "Check state should not trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test initial check - session existed - get self info failed`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { refreshTokenSuccess() },
        ).apply {
            setSession(AccessTokenSession(ACCESS_TOKEN, 0))
        }
        val authClient = createTestAuthClient(getResult = { null })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.TokenExpired>(
                awaitItem(),
                "Session existed, after checking should change state to TokenExpired.",
            )

            advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(launchedAuthorize, "Check state should not trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test success`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { getSelfInfoSuccess() },
            refreshAccessToken = { refreshTokenSuccess() },
        )
        val authClient = createTestAuthClient(
            getResult = { checkAuthorizeResultSuccess() },
        )

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.startAuthorize()
            assertIs<AuthState.AwaitingResult>(awaitItem(), "startAuthorize should change state to AwaitingResult.")

            val successState = awaitItem()
            assertIs<AuthState.Success>(successState, "Should success.")
            assertEquals(TestUserInfo.username, successState.username)

            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(launchedAuthorize, "Start authorize should trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test guest`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { getSelfInfoSuccess() },
            refreshAccessToken = { refreshTokenSuccess() },
        )
        val authClient = createTestAuthClient(
            getResult = { checkAuthorizeResultSuccess() },
        )

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.setGuestSession()

            // 本来就是 GuestSession (NotAuthed), 不会 emit 新的

            advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(launchedAuthorize, "Set guest session should not trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test cancel`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { getSelfInfoSuccess() },
            refreshAccessToken = { refreshTokenSuccess() },
        )
        val authClient = createTestAuthClient(getResult = { null })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.startAuthorize()
            assertIs<AuthState.AwaitingResult>(awaitItem(), "startAuthorize should change state to AwaitingResult.")

            configurator.cancelAuthorize()
            assertIs<AuthState.NotAuthed>(awaitItem(), "Cancel authorize should change state to Idle.")

            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(launchedAuthorize, "Start authorize should trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test authorize by session - succeeded`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { getSelfInfoSuccess() },
            refreshAccessToken = { refreshTokenSuccess() },
        )
        val authClient = createTestAuthClient(getResult = { null })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.authorizeByBangumiToken(ACCESS_TOKEN)
            assertIs<AuthState.AwaitingUserInfo>(awaitItem())

            val successState = awaitItem()
            assertIs<AuthState.Success>(successState, "Should success.")
            assertEquals(TestUserInfo.username, successState.username)

            advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(launchedAuthorize, "Set authorization token should not trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test authorize by session - failed`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        val authClient = createTestAuthClient(getResult = { null })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.authorizeByBangumiToken(ACCESS_TOKEN)
            assertIs<AuthState.AwaitingUserInfo>(awaitItem())
            assertIs<AuthState.TokenExpired>(awaitItem(), "Token is invalid, should change state to TokenExpired.")

            advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(launchedAuthorize, "Set authorization token should not trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test authorize - network error - in check authorize status`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        val authClient = createTestAuthClient(getResult = { throw RepositoryNetworkException() })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.startAuthorize()
            assertIs<AuthState.AwaitingResult>(
                awaitItem(),
                "setAuthorizationToken should change state to AwaitingResult.",
            )

            expectNoEvents()
        }
        assertTrue(launchedAuthorize, "Start authorize should trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test authorize - network error initially error but later ok`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { getSelfInfoSuccess() },
            refreshAccessToken = { refreshTokenSuccess() },
        )

        var retriesThenNetworkOk = 5
        val authClient = createTestAuthClient(
            getResult = {
                if (retriesThenNetworkOk == 0) {
                    checkAuthorizeResultSuccess()
                } else {
                    retriesThenNetworkOk--
                    throw RepositoryNetworkException()
                }
            },
        )

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.startAuthorize()
            assertIs<AuthState.AwaitingResult>(
                awaitItem(),
                "setAuthorizationToken should change state to AwaitingResult.",
            )
            assertIs<AuthState.AwaitingUserInfo>(awaitItem())
            assertIs<AuthState.Success>(awaitItem(), "Network store, should change state to Success.")

            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(launchedAuthorize, "Start authorize should trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test authorize - network error - in refresh token`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { throw RepositoryNetworkException() },
            refreshAccessToken = { throw RepositoryNetworkException() },
        )
        val authClient = createTestAuthClient(getResult = { checkAuthorizeResultSuccess() })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.startAuthorize()
            assertIs<AuthState.AwaitingResult>(
                awaitItem(),
                "setAuthorizationToken should change state to AwaitingResult.",
            )
            assertIs<AuthState.NetworkError>(awaitItem(), "Network error, should change state to Network.")

            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(launchedAuthorize, "Start authorize should trigger launch authorize.")
        loopJob.cancel()
    }

    @Test
    fun `test authorize - network error - in refresh token getSelfInfo`() = runCoroutineTest {
        val manager = createManager(
            getSelfInfo = { throw RepositoryNetworkException() },
            refreshAccessToken = { refreshTokenSuccess() },
        )
        val authClient = createTestAuthClient(getResult = { checkAuthorizeResultSuccess() })

        var launchedAuthorize = false
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = { launchedAuthorize = true },
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val loopJob = launch(start = CoroutineStart.UNDISPATCHED) {
            configurator.authorizeRequestCheckLoop()
        }

        configurator.state.test {
            assertIs<AuthState.NotAuthed>(awaitItem(), "Initially should be Idle.")

            configurator.startAuthorize()
            assertIs<AuthState.AwaitingResult>(
                awaitItem(),
                "setAuthorizationToken should change state to AwaitingResult.",
            )
            assertIs<AuthState.NetworkError>(awaitItem(), "Network error, should change state to Network.")

            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(launchedAuthorize, "Start authorize should trigger launch authorize.")
        loopJob.cancel()
    }

    private fun getSelfInfoSuccess(): UserInfo {
        return TestUserInfo
    }

    private fun checkAuthorizeResultSuccess(): AniBangumiUserToken {
        return AniBangumiUserToken(TestUserInfo.id, 100.days.inWholeMilliseconds, ACCESS_TOKEN, REFRESH_TOKEN)
    }

    private fun createTestAuthClient(
        getResult: suspend () -> AniBangumiUserToken?,
        refreshAccessToken: suspend () -> AniAnonymousBangumiUserToken? = {
            AniAnonymousBangumiUserToken(ACCESS_TOKEN, REFRESH_TOKEN, 100.days.inWholeMilliseconds)
        },
    ): AniAuthClient {
        return object : AniAuthClient {
            override suspend fun getResult(requestId: String): AniAuthResult? {
                return getResult()?.let {
                    AniAuthResult(
                        AccessTokenPair(
                            it.accessToken,
                            it.accessToken,
                            expiresAtMillis = it.expiresIn.seconds.inWholeMilliseconds + currentTimeMillis(),
                        ),
                        it.expiresIn,
                        it.refreshToken,
                    )
                }
            }

            override suspend fun refreshAccessToken(refreshToken: String): AniAuthResult {
                val result = refreshAccessToken()
                return if (result != null) {
                    AniAuthResult(
                        AccessTokenPair(
                            result.accessToken, result.accessToken,
                            expiresAtMillis = result.expiresIn.seconds.inWholeMilliseconds + currentTimeMillis(),
                        ),
                        result.expiresIn,
                        result.refreshToken,
                    )
                } else {
                    throw RepositoryNetworkException()
                }
            }

            override suspend fun getAccessTokensByBangumiToken(bangumiAccessToken: String): String {
                return bangumiAccessToken
            }
        }
    }

    private companion object {
        val TestUserInfo = UserInfo(123, "TestUser")
    }

    @Suppress("SameParameterValue", "TestFunctionName")
    private fun AccessTokenSession(
        accessToken: String, // bangumi
        expiresAtMillis: Long,
    ) = AccessTokenSession(tokens = AccessTokenPair(accessToken, accessToken, expiresAtMillis))
}
