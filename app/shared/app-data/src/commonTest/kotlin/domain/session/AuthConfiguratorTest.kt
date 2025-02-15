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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.networkError
import me.him188.ani.app.data.models.unauthorized
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.client.models.AniAnonymousBangumiUserToken
import me.him188.ani.client.models.AniBangumiUserToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AuthConfiguratorTest : AbstractBangumiSessionManagerTest() {
    @Test
    fun `test initial state should be Idle`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        val authClient = createTestAuthClient(getResult = { ApiResponse.success(null) })
        
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply { 
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { 
                authorizeRequestCheckLoop()
            }
        }
        
        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")
            
            advanceUntilIdle()
            expectNoEvents()
        }
    }
    
    @Test
    fun `test initial check - no existing session`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        val authClient = createTestAuthClient(getResult = { ApiResponse.success(null) })

        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }
        
        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")

            advanceUntilIdle()
            expectNoEvents()
            
            configurator.checkAuthorizeState()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "Start check should change state to AwaitingResult.")
            assertIs<AuthStateNew.Idle>(awaitItem(), "No session exists, after checking should change state to Idle.")

            advanceUntilIdle()
            expectNoEvents()
        }
    }
    
    @Test
    fun `test initial check - session existed - refresh succeeded`() = runTest {
        val manager = createManager(
            getSelfInfo = { ApiResponse.success(UserInfo(123, "TestUser")) },
            refreshAccessToken = { ApiResponse.success(NewSession(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN)) },
        ).apply { 
            setSession(AccessTokenSession(ACCESS_TOKEN, Long.MAX_VALUE))
        }
        val authClient = createTestAuthClient(getResult = { ApiResponse.success(null) })

        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }
        
        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")

            advanceUntilIdle()
            expectNoEvents()
            
            configurator.checkAuthorizeState()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "Start check should change state to AwaitingResult.")
            assertIs<AuthStateNew.Success>(awaitItem(), "Session existed, after checking should change state to Success.")

            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `test initial check - session existed - refresh failed`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { ApiResponse.unauthorized() },
        ).apply {
            setSession(AccessTokenSession(ACCESS_TOKEN, 0))
        }
        val authClient = createTestAuthClient(getResult = { ApiResponse.success(null) })

        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }

        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")

            advanceUntilIdle()
            expectNoEvents()

            configurator.checkAuthorizeState()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "Start check should change state to AwaitingResult.")
            assertIs<AuthStateNew.TokenExpired>(awaitItem(), "Session existed, after checking should change state to TokenExpired.")

            advanceUntilIdle()
            expectNoEvents()
        }
    }
    
    @Test
    fun `test await timeout`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        val authClient = createTestAuthClient(getResult = { ApiResponse.success(null) })
        
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            maxAwaitRetries = 1,
            awaitRetryInterval = 5.seconds,
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }

        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")
            
            configurator.startAuthorize()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "startAuthorize should change state to AwaitingResult.")
            
            advanceTimeBy(10.seconds)
            assertIs<AuthStateNew.Timeout>(awaitItem(), "Awaiting timeout should change state to Timeout.")

            advanceUntilIdle()
            expectNoEvents()
        }
    }
    
    @Test
    fun `test success`() = runTest {
        val manager = createManager(
            getSelfInfo = { ApiResponse.success(UserInfo(123, "TestUser")) },
            refreshAccessToken = { ApiResponse.success(NewSession(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN)) },
        )
        val authClient = createTestAuthClient(
            getResult = { ApiResponse.success(AniBangumiUserToken(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN, 123)) }
        )
        
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }

        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")
            
            configurator.startAuthorize()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "startAuthorize should change state to AwaitingResult.")
            
            val successState = awaitItem()
            assertIs<AuthStateNew.Success>(successState, "Should success.")
            assertEquals("TestUser", successState.username)

            advanceUntilIdle()
            expectNoEvents()
        }
    }
    
    @Test
    fun `test guest`() = runTest {
        val manager = createManager(
            getSelfInfo = { ApiResponse.success(UserInfo(123, "TestUser")) },
            refreshAccessToken = { ApiResponse.success(NewSession(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN)) },
        )
        val authClient = createTestAuthClient(
            getResult = { ApiResponse.success(AniBangumiUserToken(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN, 123)) }
        )
        
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }

        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")
            
            configurator.setGuestSession()
            
            advanceUntilIdle()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "Set guest session, should change state to AwaitingResult.")
            
            val successState = awaitItem()
            assertIs<AuthStateNew.Success>(successState, "Should success if set guest session.")
            assertTrue(successState.isGuest, "Set guest session, should be guest.")

            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `test cancel`() = runTest {
        val manager = createManager(
            getSelfInfo = { ApiResponse.success(UserInfo(123, "TestUser")) },
            refreshAccessToken = { ApiResponse.success(NewSession(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN)) },
        )
        val authClient = createTestAuthClient(getResult = { ApiResponse.success(null) })

        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }

        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")
            
            configurator.startAuthorize()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "startAuthorize should change state to AwaitingResult.")
            
            advanceTimeBy(10.seconds)
            configurator.cancelAuthorize()
            assertIs<AuthStateNew.Idle>(awaitItem(), "Cancel authorize should change state to Idle.")

            advanceUntilIdle()
            expectNoEvents()
        }
    }
    
    @Test
    fun `test authorize via session - succeeded`() = runTest {
        val manager = createManager(
            getSelfInfo = { ApiResponse.success(UserInfo(123, "TestUser")) },
            refreshAccessToken = { ApiResponse.success(NewSession(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN)) },
        )
        val authClient = createTestAuthClient(getResult = { ApiResponse.success(null) })

        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }

        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")

            configurator.setAuthorizationToken(ACCESS_TOKEN)
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "setAuthorizationToken should change state to AwaitingResult.")

            val successState = awaitItem()
            assertIs<AuthStateNew.Success>(successState, "Should success.")
            assertEquals("TestUser", successState.username)

            advanceUntilIdle()
            expectNoEvents()
        }
    }
    
    @Test
    fun `test authorize via session - failed`() = runTest {
        val manager = createManager(
            getSelfInfo = { ApiResponse.unauthorized() },
            refreshAccessToken = { ApiResponse.unauthorized() },
        )
        val authClient = createTestAuthClient(getResult = { ApiResponse.success(null) })

        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }

        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")

            configurator.setAuthorizationToken(ACCESS_TOKEN)
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "setAuthorizationToken should change state to AwaitingResult.")
            assertIs<AuthStateNew.TokenExpired>(awaitItem(), "Token is invalid, should change state to TokenExpired.")

            advanceUntilIdle()
            expectNoEvents()
        }
    }
    
    @Test
    fun `test authorize - network error`() = runTest {
        val manager = createManager(
            getSelfInfo = { ApiResponse.networkError() },
            refreshAccessToken = { ApiResponse.networkError() },
        )
        val authClient = createTestAuthClient(
            getResult = { 
                ApiResponse.success(AniBangumiUserToken(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN, 123))
            }
        )

        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        ).apply {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                authorizeRequestCheckLoop()
            }
        }

        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "Initially should be Idle.")

            configurator.startAuthorize()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "setAuthorizationToken should change state to AwaitingResult.")
            assertIs<AuthStateNew.NetworkError>(awaitItem(), "Network error, should change state to Network.")

            advanceUntilIdle()
            expectNoEvents()
        }
    }

    private fun createTestAuthClient(
        getResult: suspend () -> ApiResponse<AniBangumiUserToken?>,
        refreshAccessToken: suspend () -> AniAnonymousBangumiUserToken? = { 
            AniAnonymousBangumiUserToken(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN)
        },
    ): AniAuthClient {
        return object : AniAuthClient {
            override suspend fun getResult(requestId: String): ApiResponse<AniBangumiUserToken?> {
                return getResult()
            }
            
            override suspend fun refreshAccessToken(refreshToken: String): ApiResponse<AniAnonymousBangumiUserToken> {
                val result = refreshAccessToken()
                return if (result != null) {
                    ApiResponse.success(result)
                } else {
                    ApiResponse.networkError()
                }
            }
        }
    }
}