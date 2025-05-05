/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.domain.media.fetch.AtomicInteger
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.days

class BangumiSessionManagerRequireAuthorizeTest : AbstractBangumiSessionManagerTest() {

    ///////////////////////////////////////////////////////////////////////////
    // requireAuthorize
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `requireAuthorize checks self info`() = runTest {
        val manager = createManager(
            getSelfInfo = { SUCCESS_USER_INFO },
            refreshAccessToken = { noCall() },
        )
        setValidToken()
        manager.requireAuthorizeTest({})
        runCoroutines()

        assertEquals(1, getSelfInfoCalled.value)
    }

    @Test
    fun `requireAuthorize guest with skipOnGuest is true`() = runTest {
        val manager = createManager(
            getSelfInfo = { SUCCESS_USER_INFO },
            refreshAccessToken = { noCall() },
        )
        manager.setSession(GuestSession)
        manager.requireAuthorizeTest({ fail() }, skipOnGuest = true)
        runCoroutines()
        assertEquals(0, getSelfInfoCalled.value)
        assertEquals(SessionStatus.Guest, manager.statePass.first())
    }

    @Test
    fun `requireAuthorize guest with skipOnGuest is false`() = runTest {
        val manager = createManager(
            getSelfInfo = { SUCCESS_USER_INFO },
            refreshAccessToken = { noCall() },
        )
        manager.setSession(GuestSession)
        assertFailsWith<AuthorizationFailedException> {
            manager.requireAuthorizeTest({ throw IndexOutOfBoundsException() }, skipOnGuest = false)
        }
        runCoroutines()
        assertEquals(0, getSelfInfoCalled.value)
        assertEquals(SessionStatus.Guest, manager.statePass.first())
    }

    @Test
    fun `requireAuthorize invokes onLaunch when failed to verify`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                throw RepositoryAuthorizationException()
            },
            refreshAccessToken = { noCall() },
        )
        setValidToken()
        assertFailsWith<AuthorizationCancelledException> {
            manager.requireAuthorizeTest(
                onLaunch = {
                    throw kotlin.coroutines.cancellation.CancellationException()
                },
            )
        }

        assertEquals(1, onLaunchCalled.value)
    }

    @Test
    fun `requireAuthorize failed to verify followed by successful refresh`() = runTest {
        val initial = "initial"
        val manager = createManager(
            getSelfInfo = {
                if (it == initial) {
                    throw RepositoryAuthorizationException()
                } else {
                    UserInfo.EMPTY
                }
            },
            refreshAccessToken = {
                NewSession(
                    successAccessTokenPair(),
                    currentTimeMillis() + 100.days.inWholeMilliseconds,
                    REFRESH_TOKEN,
                )
            },
        )
        setValidToken(initial)
        tokenRepository.setRefreshToken(REFRESH_TOKEN)

        manager.requireAuthorizeTest(
            onLaunch = { fail() },
        )

        assertEquals(0, onLaunchCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
        assertEquals(2, getSelfInfoCalled.value)

        manager.statePass.toList().run {
            assertEquals(2, size, "Expected 2 states, but got $this")
            assertEquals(createVerifying(ACCESS_TOKEN), get(0))
            assertEquals(createVerified(ACCESS_TOKEN, UserInfo.EMPTY), get(1))
        }

        assertEquals(0, onLaunchCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value) // not changed
        assertEquals(3, getSelfInfoCalled.value) // 2 during requiredAuthorize + 1 during statePass
    }

    @Test
    fun `requireAuthorize failed to verify followed by network error during refresh`() = runTest {
        val initial = "initial"
        val manager = createManager(
            getSelfInfo = {
                if (it == initial) {
                    throw RepositoryAuthorizationException()
                } else {
                    UserInfo.EMPTY
                }
            },
            refreshAccessToken = {
                throw RepositoryNetworkException()
            },
        )
        setValidToken(initial)
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        assertFailsWith<AuthorizationFailedException> {
            manager.requireAuthorizeTest(
                onLaunch = { fail() },
            )
        }
        assertEquals(0, onLaunchCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
        assertEquals(1, getSelfInfoCalled.value)

        manager.statePass.toList().run {
            assertEquals(3, size)
            assertEquals(createVerifying(initial), get(0))
            assertEquals(SessionStatus.Refreshing, get(1))
            assertEquals(SessionStatus.NetworkError, get(2))
        }

        assertEquals(0, onLaunchCalled.value)
        assertEquals(2, refreshAccessTokenCalled.value) // 1 during requiredAuthorize + 1 during statePass
        assertEquals(2, getSelfInfoCalled.value) // 2 during requiredAuthorize + 1 during statePass
    }

    @Test
    fun `requireAuthorize failed to verify followed by unsuccessful refresh then successful oauth`() = runTest {
        val initial = "initial"
        val manager = createManager(
            getSelfInfo = {
                if (it == initial) {
                    throw RepositoryAuthorizationException()
                } else {
                    UserInfo.EMPTY
                }
            },
            refreshAccessToken = {
                throw RepositoryAuthorizationException()
            },
        )
        setValidToken(initial)
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        manager.requireAuthorizeTest(
            onLaunch = {
                manager.processingRequest.value.let {
                    assertNotNull(it)
                    assertEquals(ExternalOAuthRequest.State.Launching, it.state.value)

                    it.onCallback(
                        Result.success(
                            OAuthResult(
                                successAccessTokenPair(),
                                REFRESH_TOKEN,
                                veryLongDuration,
                            ),
                        ),
                    )
                }
            },
        )

        assertEquals(1, refreshAccessTokenCalled.value)
        assertEquals(1, onLaunchCalled.value)
        assertEquals(1, getSelfInfoCalled.value)

        manager.statePass.toList().run {
            assertEquals(2, size, "Expected 2 states, but got $this")
            assertEquals(
                createVerifying(ACCESS_TOKEN), // not initial token
                get(0),
            )
            assertEquals(createVerified(ACCESS_TOKEN, UserInfo.EMPTY), get(1))
        }

        assertEquals(1, onLaunchCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value) // not changed
        assertEquals(2, getSelfInfoCalled.value) // 2 during requiredAuthorize + 1 during statePass
    }

    @Test
    fun `requireAuthorize failed to verify followed by unsuccessful refresh then cancelled oauth`() = runTest {
        val initial = "initial"
        val manager = createManager(
            getSelfInfo = {
                if (it == initial) {
                    throw RepositoryAuthorizationException()
                } else {
                    UserInfo.EMPTY
                }
            },
            refreshAccessToken = {
                throw RepositoryAuthorizationException()
            },
        )
        setValidToken(initial)
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        assertFailsWith<AuthorizationCancelledException> {
            manager.requireAuthorizeTest(
                onLaunch = {
                    manager.processingRequest.value.let {
                        assertNotNull(it)
                        assertEquals(ExternalOAuthRequest.State.Launching, it.state.value)

                        it.cancel()
                    }
                },
            )
        }

        assertEquals(1, onLaunchCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
        assertEquals(1, getSelfInfoCalled.value)

        manager.statePass.toList().run {
            assertEquals(3, size, message = this.toString())
            assertEquals(createVerifying(initial), get(0))
            assertEquals(SessionStatus.Refreshing, get(1))
            assertIs<SessionStatus.Expired>(get(2))
        }

        assertEquals(1, onLaunchCalled.value)
        assertEquals(2, refreshAccessTokenCalled.value) // 1 during requiredAuthorize
        assertEquals(2, getSelfInfoCalled.value) // 2 during requiredAuthorize + 1 during statePass
    }

    @Test
    fun `requireAuthorize failed to verify followed by unsuccessful refresh then exceptional oauth`() = runTest {
        val initial = "initial"
        val manager = createManager(
            getSelfInfo = {
                if (it == initial) {
                    throw RepositoryAuthorizationException()
                } else {
                    UserInfo.EMPTY
                }
            },
            refreshAccessToken = {
                throw RepositoryAuthorizationException()
            },
        )
        setValidToken(initial)
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        assertFailsWith<AuthorizationFailedException> {
            manager.requireAuthorizeTest(
                onLaunch = {
                    manager.processingRequest.value.let {
                        assertNotNull(it)
                        assertEquals(ExternalOAuthRequest.State.Launching, it.state.value)

                        it.onCallback(Result.failure(IndexOutOfBoundsException("a bug")))
                    }
                },
            )
        }

        assertEquals(1, onLaunchCalled.value)
        assertEquals(1, refreshAccessTokenCalled.value)
        assertEquals(1, getSelfInfoCalled.value)

        manager.statePass.toList().run {
            assertEquals(3, size)
            assertEquals(createVerifying(initial), get(0))
            assertEquals(SessionStatus.Refreshing, get(1))
            assertIs<SessionStatus.Expired>(get(2))
        }

        assertEquals(1, onLaunchCalled.value)
        assertEquals(2, refreshAccessTokenCalled.value) // 1 during requiredAuthorize
        assertEquals(2, getSelfInfoCalled.value) // 2 during requiredAuthorize + 1 during statePass
    }

    @Test
    fun `requireAuthorize failed to verify followed by successful oauth`() = runTest {
        val manager = createManager(
            getSelfInfo = {
                if (getSelfInfoCalled.value == 0) {
                    throw RepositoryAuthorizationException()
                } else {
                    SUCCESS_USER_INFO
                }
            },
            refreshAccessToken = { noCall() },
        )
        setValidToken()
        manager.requireAuthorizeTest(
            onLaunch = {
                manager.processingRequest.value.let {
                    assertNotNull(it)
                    assertEquals(ExternalOAuthRequest.State.Launching, it.state.value)

                    it.onCallback(
                        Result.success(
                            OAuthResult(
                                successAccessTokenPair(),
                                REFRESH_TOKEN,
                                veryLongDuration,
                            ),
                        ),
                    )
                }
            },
        )
        runCoroutines()

        assertEquals(1, onLaunchCalled.value)
        assertEquals(1, getSelfInfoCalled.value)
        manager.statePass.toList().run {
            assertEquals(2, size)
            assertEquals(createVerifying(ACCESS_TOKEN), get(0))
            assertEquals(createVerified(ACCESS_TOKEN, UserInfo.EMPTY), get(1))
        }
        assertEquals(1, onLaunchCalled.value)
        assertEquals(2, getSelfInfoCalled.value)
    }

    private fun successAccessTokenPair(): AccessTokenPair = AccessTokenPair(
        ACCESS_TOKEN, ACCESS_TOKEN,
        expiresAtMillis = validExpiresAtMillis,
    )

    @Test
    fun `requireAuthorize guest followed by successful oauth`() = runTest {
        val manager = createManager(
            getSelfInfo = { SUCCESS_USER_INFO },
            refreshAccessToken = {
                noCall()
            },
        )
        manager.setSession(GuestSession)
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        manager.requireAuthorizeTest(
            onLaunch = {
                manager.processingRequest.value.let {
                    assertNotNull(it)
                    assertEquals(ExternalOAuthRequest.State.Launching, it.state.value)

                    it.onCallback(
                        Result.success(
                            OAuthResult(
                                successAccessTokenPair(),
                                REFRESH_TOKEN,
                                veryLongDuration,
                            ),
                        ),
                    )
                }
            },
        )
        runCoroutines()

        assertEquals(1, onLaunchCalled.value)
        assertEquals(0, getSelfInfoCalled.value)
        manager.statePass.toList().run {
            assertEquals(2, size, "Expected 2 states, but got $this")
            assertEquals(createVerifying(ACCESS_TOKEN), get(0))
            assertEquals(createVerified(ACCESS_TOKEN, UserInfo.EMPTY), get(1))
        }
        assertEquals(1, onLaunchCalled.value)
        assertEquals(1, getSelfInfoCalled.value)
    }

    @Test
    fun `requireAuthorize failed to verify followed by cancelled oauth`() = runTest {
        val initialToken = "initial"
        val manager = createManager(
            getSelfInfo = {
                if (it == initialToken) {
                    throw RepositoryAuthorizationException()
                } else {
                    SUCCESS_USER_INFO
                }
            },
            refreshAccessToken = { noCall() },
        )
        setValidToken(token = initialToken)
        assertFailsWith<AuthorizationCancelledException> {
            manager.requireAuthorizeTest(
                onLaunch = {
                    manager.processingRequest.value.let {
                        assertNotNull(it)
                        assertEquals(ExternalOAuthRequest.State.Launching, it.state.value)

                        it.cancel()
                    }
                },
            )
        }
        runCoroutines()

        assertEquals(1, onLaunchCalled.value)
        assertEquals(1, getSelfInfoCalled.value)
        manager.statePass.toList().run {
            assertEquals(2, size)
            assertEquals(createVerifying(initialToken), get(0))
            assertIs<SessionStatus.Expired>(get(1))
        }
        assertEquals(1, onLaunchCalled.value)
        assertEquals(2, getSelfInfoCalled.value)
    }

    @Test
    fun `requireAuthorize failed to verify followed by exceptional oauth`() = runTest {
        val initialToken = "initial"
        val manager = createManager(
            getSelfInfo = {
                if (it == initialToken) {
                    throw RepositoryAuthorizationException()
                } else {
                    SUCCESS_USER_INFO
                }
            },
            refreshAccessToken = { noCall() },
        )
        setValidToken(token = initialToken)
        assertFailsWith<AuthorizationFailedException> {
            manager.requireAuthorizeTest(
                onLaunch = {
                    manager.processingRequest.value.let {
                        assertNotNull(it)
                        assertEquals(ExternalOAuthRequest.State.Launching, it.state.value)

                        it.onCallback(Result.failure(IndexOutOfBoundsException("a bug")))
                    }
                },
            )
        }
        runCoroutines()

        assertEquals(1, onLaunchCalled.value)
        assertEquals(1, getSelfInfoCalled.value)
        manager.statePass.toList().run {
            assertEquals(2, size)
            assertEquals(createVerifying(initialToken), get(0))
            assertIs<SessionStatus.Expired>(get(1))
        }
        assertEquals(1, onLaunchCalled.value)
        assertEquals(2, getSelfInfoCalled.value)
    }

    private val onLaunchCalled = AtomicInteger(0)
    private suspend fun BangumiSessionManager.requireAuthorizeTest(
        onLaunch: suspend () -> Unit,
        skipOnGuest: Boolean = false, // we are more alert during testing
    ) = requireAuthorize(
        onLaunch = {
            try {
                onLaunch()
            } finally {
                onLaunchCalled.incrementAndGet()
            }
        },
        skipOnGuest = skipOnGuest,
    )

    ///////////////////////////////////////////////////////////////////////////
    // requireAuthorize exceptions
    /////////////////////////////////////////////////////////////////////////// 


    @Test
    fun `requireAuthorize wraps exception during getSelfInfo`() = runTest {
        val manager = createManager(
            getSelfInfo = { throw IndexOutOfBoundsException("a bug") },
            refreshAccessToken = { noCall() },
        )
        setValidToken()

        assertFailsWith<AuthorizationFailedException> {
            manager.requireAuthorizeTest({})
        }.let {
            assertIs<RepositoryUnknownException>(it.cause)
        }
    }

    @Test
    fun `state wraps exception during getSelfInfo`() = runTest {
        val manager = createManager(
            getSelfInfo = { throw IndexOutOfBoundsException("a bug") },
            refreshAccessToken = { noCall() },
        )
        setValidToken()

        manager.state.filterNot { it is SessionStatus.Loading }.first().let {
            assertIs<SessionStatus.UnknownError>(it)
            assertIs<RepositoryUnknownException>(it.exception)
            assertIs<IndexOutOfBoundsException>(it.exception.cause)
        }
        manager.statePass.filterNot { it is SessionStatus.Loading }.first().let {
            assertIs<SessionStatus.UnknownError>(it)
            assertIs<RepositoryUnknownException>(it.exception)
            assertIs<IndexOutOfBoundsException>(it.exception.cause)
        }
    }

    @Test
    fun `requireAuthorize wraps exception during refreshAccessToken`() = runTest {
        val manager = createManager(
            getSelfInfo = { throw RepositoryAuthorizationException() },
            refreshAccessToken = { throw IndexOutOfBoundsException("a bug") },
        )
        setValidToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        assertFailsWith<AuthorizationFailedException> {
            manager.requireAuthorizeTest({})
        }
    }

    @Test
    fun `state wraps exception during refreshAccessToken`() = runTest {
        val manager = createManager(
            getSelfInfo = { throw RepositoryAuthorizationException() },
            refreshAccessToken = { throw IndexOutOfBoundsException("a bug") },
        )
        setValidToken()
        tokenRepository.setRefreshToken(REFRESH_TOKEN)
        manager.state.filterNot { it is SessionStatus.Loading }.first().let {
            assertIs<SessionStatus.UnknownError>(it)
            assertIs<RepositoryUnknownException>(it.exception)
            assertIs<IndexOutOfBoundsException>(it.exception.cause)
        }
        // 顺便也检查一下专门为 test 实现的 statePass 也拥有相同的行为
        manager.statePass.filterNot { it is SessionStatus.Loading }.first().let {
            assertIs<SessionStatus.UnknownError>(it)
            assertIs<RepositoryUnknownException>(it.exception)
            assertIs<IndexOutOfBoundsException>(it.exception.cause)
        }
    }

    private val veryLongDuration = 100.days

    private fun createVerifying(token: String): SessionStatus.Verifying =
        SessionStatus.Verifying(
            AccessTokenPair(
                token,
                token,
                expiresAtMillis = validExpiresAtMillis,
            ),
        )

    @Suppress("SameParameterValue")
    private fun createVerified(token: String, userInfo: UserInfo): SessionStatus.Verified =
        SessionStatus.Verified(
            AccessTokenPair(
                token, token,
                expiresAtMillis = validExpiresAtMillis,
            ),
            userInfo,
        )

}