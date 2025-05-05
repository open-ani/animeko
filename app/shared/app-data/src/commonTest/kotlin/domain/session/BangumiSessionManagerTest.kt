/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.coroutines.flow.toList
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class BangumiSessionManagerTest : AbstractBangumiSessionManagerTest() {

    ///////////////////////////////////////////////////////////////////////////
    // Preconditions
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `preconditions - no sharing and replay in tests`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        setValidToken("testToken")
        assertEquals(createVerifying("testToken"), manager.awaitState())
        runCoroutines()
        assertEquals(createVerifying("testToken"), manager.awaitState()) // should rerun flow
    }

    ///////////////////////////////////////////////////////////////////////////
    // Token state
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `no token`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        assertEquals(SessionStatus.Guest, manager.awaitState())
    }

    @Test
    fun `token expired`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { noCall() },
        )
        setExpiredToken()
        assertIs<SessionStatus.Expired>(manager.awaitState())
    }

    @Test
    fun `valid token needs to be verified`() = runTest {
        val manager = createManager(
            getSelfInfo = { SUCCESS_USER_INFO },
            refreshAccessToken = { noCall() },
        )
        setValidToken()
        assertEquals(createVerifying("testToken"), manager.awaitState())
        assertEquals(createVerified(ACCESS_TOKEN, SUCCESS_USER_INFO), manager.awaitState(1))
    }

    @Test
    fun `valid token - unauthorized verify - successful refresh - successful verify`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                if (getSelfInfoCalled.value == 0) {
                    throw RepositoryAuthorizationException()
                } else {
                    SUCCESS_USER_INFO
                }
            },
            refreshAccessToken = {
                newSession()
            },
        )
        setValidToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        val states = manager.statePass.toList()
        assertEquals(createVerifying(ACCESS_TOKEN), states[0])
        assertEquals(SessionStatus.Refreshing, states[1])
        assertEquals(createVerifying(ACCESS_TOKEN), states[2])
        assertEquals(createVerified(ACCESS_TOKEN, SUCCESS_USER_INFO), states[3])
        assertEquals(2, getSelfInfoCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
    }

    @Test
    fun `valid token - unauthorized verify - successful refresh - unauthorized verify`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                when (getSelfInfoCalled.value) {
                    0, 1 -> throw RepositoryAuthorizationException()
                    else -> fail()
                }
            },
            refreshAccessToken = {
                newSession()
            },
        )
        setValidToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        val states = manager.statePass.toList()
        assertEquals(createVerifying(ACCESS_TOKEN), states[0])
        assertEquals(SessionStatus.Refreshing, states[1])
        assertEquals(createVerifying(ACCESS_TOKEN), states[2])
        assertIs<SessionStatus.Expired>(states[3])
        assertEquals(2, getSelfInfoCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
    }

    @Test
    fun `valid token - unauthorized verify - successful refresh - NetworkError verify`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                when (getSelfInfoCalled.value) {
                    0 -> throw RepositoryAuthorizationException()
                    1 -> throw RepositoryNetworkException()
                    else -> fail()
                }
            },
            refreshAccessToken = {
                newSession()
            },
        )
        setValidToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        val states = manager.statePass.toList()
        assertEquals(createVerifying(ACCESS_TOKEN), states[0])
        assertEquals(SessionStatus.Refreshing, states[1])
        assertEquals(createVerifying(ACCESS_TOKEN), states[2])
        assertEquals(SessionStatus.NetworkError, states[3])
        assertEquals(2, getSelfInfoCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
    }

    @Test
    fun `valid token - unauthorized verify - unauthorized refresh`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                throw RepositoryAuthorizationException()
            },
            refreshAccessToken = {
                throw RepositoryAuthorizationException()
            },
        )
        setValidToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        val states = manager.statePass.toList()
        assertEquals(createVerifying(ACCESS_TOKEN), states[0])
        assertEquals(SessionStatus.Refreshing, states[1])
        assertIs<SessionStatus.Expired>(states[2])
        assertEquals(1, getSelfInfoCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
    }

    @Test
    fun `valid token - unauthorized verify - NetworkError refresh`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                throw RepositoryAuthorizationException()
            },
            refreshAccessToken = {
                throw RepositoryNetworkException()
            },
        )
        setValidToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        val states = manager.statePass.toList()
        assertEquals(createVerifying(ACCESS_TOKEN), states[0])
        assertEquals(SessionStatus.Refreshing, states[1])
        assertEquals(SessionStatus.NetworkError, states[2])
        assertEquals(1, getSelfInfoCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
    }

    @Test
    fun `valid token - unauthorized verify - ServiceUnavailable refresh`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                throw RepositoryAuthorizationException()
            },
            refreshAccessToken = {
                throw RepositoryServiceUnavailableException()
            },
        )
        setValidToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        val states = manager.statePass.toList()
        assertEquals(createVerifying(ACCESS_TOKEN), states[0])
        assertEquals(SessionStatus.Refreshing, states[1])
        assertEquals(SessionStatus.ServiceUnavailable, states[2])
        assertEquals(1, getSelfInfoCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
    }

    @Test
    fun `valid token - NetworkError verify - no refresh`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                throw RepositoryNetworkException()
            },
            refreshAccessToken = {
                newSession()
            },
        )
        setValidToken()
        assertEquals(SessionStatus.NetworkError, manager.awaitState(1))
        assertEquals(1, getSelfInfoCalled.value)
        assertEquals(0, refreshAccessTokenCalled.value)
    }

    @Test
    fun `valid token - ServiceUnavailable verify - no refresh`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                throw RepositoryServiceUnavailableException()
            },
            refreshAccessToken = {
                newSession()
            },
        )
        setValidToken()
        assertEquals(SessionStatus.ServiceUnavailable, manager.awaitState(1))
        assertEquals(1, getSelfInfoCalled.value)
        assertEquals(0, refreshAccessTokenCalled.value)
    }


    ///////////////////////////////////////////////////////////////////////////
    // Wrap exceptions
    ///////////////////////////////////////////////////////////////////////////

    @Suppress("ComplexRedundantLet")
    @Test
    fun `wraps exception in getSelfInfo`() = runTest {
        val myException = IndexOutOfBoundsException()
        val manager = createManager(
            getSelfInfo = { throw myException },
            refreshAccessToken = { noCall() },
        )
        setValidToken()
        val state = manager.awaitState(1)
        assertIs<SessionStatus.UnknownError>(state)
            .let {
                assertIs<RepositoryUnknownException>(it.exception)
            }
            .let {
                assertIs<IndexOutOfBoundsException>(it.cause)
            }
    }

    @Test
    fun `wraps exception in refreshAccessToken`() = runTest {
        val manager = createManager(
            getSelfInfo = { noCall() },
            refreshAccessToken = { throw IndexOutOfBoundsException() },
        )
        setExpiredToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        val state = manager.awaitState(1)
        @Suppress("ComplexRedundantLet")
        assertIs<SessionStatus.UnknownError>(state)
            .let {
                assertIs<RepositoryUnknownException>(it.exception)
            }
            .let {
                assertIs<IndexOutOfBoundsException>(it.cause)
            }
    }


    private fun newSession(): NewSession = NewSession(
        AccessTokenPair(ACCESS_TOKEN, ACCESS_TOKEN, expiresAtMillis = validExpiresAtMillis),
        validExpiresAtMillis, REFRESH_TOKEN,
    )

    private fun createVerifying(token: String): SessionStatus.Verifying =
        SessionStatus.Verifying(AccessTokenPair(token, token, expiresAtMillis = validExpiresAtMillis))

    private fun createVerified(token: String, userInfo: UserInfo): SessionStatus.Verified =
        SessionStatus.Verified(AccessTokenPair(token, token, expiresAtMillis = validExpiresAtMillis), userInfo)

}
