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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import me.him188.ani.app.data.models.ApiResponse
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.networkError
import me.him188.ani.client.models.AniAnonymousBangumiUserToken
import me.him188.ani.client.models.AniBangumiUserToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class AuthConfiguratorTest : AbstractBangumiSessionManagerTest() {
    @Test
    fun `test initial state - should be Idle`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        val authClient = createTestAuthClient(getResult = { null })
        
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        )
        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "initially should be Idle")
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
        val authClient = createTestAuthClient(getResult = { null })
        
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            maxAwaitRetries = 1,
            awaitRetryInterval = 5.seconds,
            parentCoroutineContext = backgroundScope.coroutineContext
        )
        
        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "initially should be Idle")
            
            configurator.startAuthorize()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "startAuthorize should change state to AwaitingResult")
            
            advanceTimeBy(10.seconds)
            assertIs<AuthStateNew.Timeout>(awaitItem(), "Awaiting timeout should change state to Timeout")
            
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
            getResult = { AniBangumiUserToken(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN, 123) }
        )
        
        val configurator = AniAuthConfigurator(
            sessionManager = manager,
            authClient = authClient,
            onLaunchAuthorize = {},
            parentCoroutineContext = backgroundScope.coroutineContext
        )
        
        configurator.state.test {
            assertIs<AuthStateNew.Idle>(awaitItem(), "initially should be Idle")
            
            configurator.startAuthorize()
            assertIs<AuthStateNew.AwaitingResult>(awaitItem(), "startAuthorize should change state to AwaitingResult")
            
            val successState = awaitItem()
            assertIs<AuthStateNew.Success>(successState, "Should success")
            assertEquals("TestUser", successState.username)
            
            expectNoEvents()
        }
    }

    private fun createTestAuthClient(
        getResult: suspend () -> AniBangumiUserToken?,
        refreshAccessToken: suspend () -> AniAnonymousBangumiUserToken? = {
            AniAnonymousBangumiUserToken(ACCESS_TOKEN, Long.MAX_VALUE, REFRESH_TOKEN)
        },
    ): AniAuthClient {
        return object : AniAuthClient {
            override suspend fun getResult(requestId: String): ApiResponse<AniBangumiUserToken?> {
                return ApiResponse.success(getResult())
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