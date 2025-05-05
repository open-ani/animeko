/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.network.BangumiProfileService
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.data.repository.user.Session
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.trace
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentTimeMillis
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun BangumiSessionManager(
    koin: Koin,
    parentCoroutineContext: CoroutineContext,
): BangumiSessionManager {
    val tokenRepository: TokenRepository by koin.inject()
    val bangumiProfileService: BangumiProfileService by koin.inject()
    val client: AniAuthClient by koin.inject()

    return BangumiSessionManager(
        tokenRepository,
        getBangumiSelfInfo = { accessToken ->
            bangumiProfileService.getSelfUserInfo(accessToken)
        },
        refreshAccessToken = refreshAccessToken@{ refreshToken ->
            val it = client.refreshAccessToken(refreshToken)
            NewSession(
                it.tokens,
                expiresAtMillis = (currentTimeMillis().milliseconds + it.expiresInSeconds.seconds).inWholeMilliseconds,
                bangumiRefreshToken = it.refreshToken,
            )
        },
        parentCoroutineContext,
        enableSharing = true,
    )
}

class NewSession(
    val accessTokens: AccessTokenPair,
    val expiresAtMillis: Long,
    val bangumiRefreshToken: String,
)

class BangumiSessionManager(
    private val tokenRepository: TokenRepository,
    /**
     * May throw [me.him188.ani.app.data.repository.RepositoryException].
     */
    private val getBangumiSelfInfo: suspend (bangumiAccessToken: String) -> UserInfo,
    /**
     * May throw [me.him188.ani.app.data.repository.RepositoryException].
     */
    private val refreshAccessToken: suspend (refreshToken: String) -> NewSession,
    parentCoroutineContext: CoroutineContext,
    /**
     * Should be `true`. Set to `false` only for testing.
     */
    enableSharing: Boolean
) : SessionManager, HasBackgroundScope by BackgroundScope(parentCoroutineContext) {
    private val logger = logger<SessionManager>()

    private val refreshCounter = MutableStateFlow(0)

    override val state: Flow<SessionStatus> = refreshCounter.transform { _ ->
        // 不跟踪 tokenRepository.session 变化. 每次手动更新 refreshCounter.
        emit(Result.success(null)) // 只要 refreshCounter 变化, 就立即清除缓存

        emitAll(
            flow {
                doSessionPass(tokenRepository.session.first())
            }.map {
                Result.success(it)
            },
        )
    }.run {
        if (enableSharing) {
            // shareIn absorbs exceptions. We need to catch and rethrow inorder to make it transparent
            catch {
                emit(Result.failure(it))
            }.shareInBackground(
                SharingStarted.WhileSubscribed(
                    5000,
                    replayExpirationMillis = 12.hours.inWholeMilliseconds,
                ),
            )
        } else this
    }.map {
        it.getOrThrow() // transparent exception
    }.filterNotNull()

    /**
     * 单元测试专用, 只跑完一个 pass 就 complete. 相比之下, [state] 如果开了 sharing, 就不会完结.
     */
    @TestOnly
    val statePass
        get() = tokenRepository.session.take(1).transform { session ->
            doSessionPass(session)
        }

    private fun shouldStopSessionRefresh(
        failure: RepositoryException
    ): SessionStatus.VerificationFailed? {
        // explicit when to be exhaustive
        when (failure) {
            is RepositoryAuthorizationException -> {
                // 我们肯定登录已经过期, 继续尝试 refresh token
            }

            is RepositoryNetworkException,
            is RepositoryRateLimitedException -> {
                return SessionStatus.NetworkError
            }

            is RepositoryServiceUnavailableException -> {
                return SessionStatus.ServiceUnavailable
            }

            is RepositoryUnknownException -> {
                return SessionStatus.UnknownError(failure)
            }
        }
        return null
    }

    private fun RepositoryException.toSessionState() = when (this) {
        is RepositoryRateLimitedException,
        is RepositoryNetworkException -> SessionStatus.NetworkError

        is RepositoryServiceUnavailableException -> SessionStatus.ServiceUnavailable
        is RepositoryAuthorizationException -> SessionStatus.Expired(cause = this)
        is RepositoryUnknownException -> SessionStatus.UnknownError(this)
    }

    /**
     * 校验 session 并尝试刷新.
     *
     * 如果 [savedSession] 不为 `null`, 则尝试登录. 登录成功时 emit [SessionStatus.Verified].
     * 如果登录失败, 则尝试 refresh token. refresh 后会重试登录.
     *
     * 如果 [savedSession] 为 `null`, 则会跳过登录, 直接尝试 refresh token.
     *
     * 状态很复杂, 建议看 `BangumiSessionManagerTest`
     */
    private suspend fun FlowCollector<SessionStatus>.doSessionPass(
        savedSession: Session?,
    ) {
        // 先用保存的 session 尝试
        when (savedSession) {
            null -> {}
            GuestSession -> {
                emit(SessionStatus.Guest)
                return
            }

            is AccessTokenSession -> {
                if (!savedSession.tokens.isExpired()) {
                    // token 有效, 尝试登录
                    emit(SessionStatus.Verifying(savedSession.tokens))

                    try {
                        val userInfo = getBangumiSelfInfo(savedSession.tokens.bangumiAccessToken)
                        emit(SessionStatus.Verified(savedSession.tokens, userInfo))
                        // First attempt successful, let's return
                        return
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        shouldStopSessionRefresh(RepositoryException.wrapOrThrowCancellation(e))?.let {
                            emit(it)
                            return
                        }
                    }
                }
            }
        }

        // session 无效, 继续尝试 refresh token
        val refreshToken = tokenRepository.refreshToken.first()
        if (refreshToken == null) {
            if (savedSession == null) { // 没有保存的 token 时才 emit NoToken
                emit(SessionStatus.Guest)
            } else {
                emit(
                    SessionStatus.Expired(
                        RepositoryAuthorizationException("session ($savedSession) is invalid and refreshToken is null"),
                    ),
                )
            }
            return
        }

        // 有 refresh token, 尝试刷新
        emit(SessionStatus.Refreshing)
        try {
            val accessTokens = tryRefreshSessionByRefreshToken(refreshToken).accessTokens

            // refresh 成功, 再次尝试登录
            emit(SessionStatus.Verifying(accessTokens))
            val userInfo = getBangumiSelfInfo(accessTokens.bangumiAccessToken)

            // 终于 OK
            emit(SessionStatus.Verified(accessTokens, userInfo))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 刷新 refresh token 失败, 或者刷新成功后登录却失败了, 已经没有更多方法可以尝试了
            emit(RepositoryException.wrapOrThrowCancellation(e).toSessionState())
        }
    }

    private val singleAuthLock = Mutex()
    override val processingRequest: MutableStateFlow<ExternalOAuthRequest?> = MutableStateFlow(null)
    override val events: MutableSharedFlow<SessionEvent> = MutableSharedFlow(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private suspend fun tryRefreshSessionByRefreshToken(refreshToken: String): NewSession {
        logger.trace { "tryRefreshSessionByRefreshToken: start" }
        // session is invalid, refresh it
        val session = refreshAccessToken(refreshToken)
        setSessionAndRefreshToken(
//            session.userId,
            session,
            isNewLogin = false,
        )
        return session
    }

    override suspend fun requireAuthorize(
        onLaunch: suspend () -> Unit,
        skipOnGuest: Boolean
    ) {
        logger.trace { "requireAuthorize is called" }

        singleAuthLock.withLock {
            // 查看当前状态
            val currentStatus = state.filterNot { it is SessionStatus.Loading }.first()
            logger.trace { "requireAuthorize: currentStatus=$currentStatus" }

            // Explicitly check all branches
            when (currentStatus) {
                is SessionStatus.Verified -> return // already verified

                // We did `filterOut` above. Unit testing will ensure this is not reached.
                is SessionStatus.Loading -> throw AssertionError()

                is SessionStatus.Guest -> {
                    // 用户当前以游客登录
                    if (skipOnGuest) return
                }

                // Error kinds
                SessionStatus.NetworkError,
                SessionStatus.ServiceUnavailable
                    -> {
                    check(currentStatus is SessionStatus.VerificationFailed)
                    // can be retried
                    throw AuthorizationFailedException(
                        currentStatus,
                        "Failed to login due to $currentStatus, but this may be recovered by a refresh",
                    )
                }

                is SessionStatus.Expired,
                    -> {
                    // continue, smart casts should work
                }

                is SessionStatus.UnknownError -> {
                    throw AuthorizationFailedException(
                        currentStatus,
                        "Failed to login due to $currentStatus, but this may be recovered by a refresh",
                        cause = currentStatus.exception,
                    )
                }
            }
            logger.trace { "requireAuthorize: Launching ExternalOAuthRequestImpl" }

            // Launch external oauth (e.g. browser)
            val req = ExternalOAuthRequestImpl(
                onLaunch = onLaunch,
                onSuccess = { session ->
                    setSessionAndRefreshToken(session, isNewLogin = true)
                    state.first() // await for change
                },
            )
            processingRequest.value = req
            try {
                req.invoke()
            } catch (e: RepositoryException) {
                logger.trace { "requireAuthorize: ExternalOAuthRequestImpl failed with $e" }
                throw AuthorizationFailedException(
                    currentStatus,
                    "Exception during invoking ExternalOAuthRequestImpl, see cause",
                    cause = e,
                )
            } finally {
                processingRequest.value = null
            }
            logger.trace { "requireAuthorize: ExternalOAuthRequestImpl succeed" }

            // Throw exceptions according to state
            val state = req.state.value
            check(state is ExternalOAuthRequest.State.Result)
            when (state) {
                is ExternalOAuthRequest.State.Cancelled -> {
                    throw AuthorizationCancelledException(null, state.cause)
                }

                is ExternalOAuthRequest.State.Failed -> {
                    throw AuthorizationFailedException(
                        currentStatus,
                        "ExternalOAuthRequest failed: $currentStatus",
                        cause = state.throwable,
                    )
                }

                ExternalOAuthRequest.State.Success -> {
                    // nop
                }
            }
        }
    }

    private val requireAuthorizeAsyncTasker = MonoTasker(backgroundScope)
    override fun requireAuthorizeAsync(
        onLaunch: suspend () -> Unit,
        skipOnGuest: Boolean,
    ) {
        requireAuthorizeAsyncTasker.launch {
            try {
                requireAuthorize(onLaunch, skipOnGuest)
                logger.info { "requireOnline: success" }
            } catch (_: AuthorizationCancelledException) {
                logger.info { "requireOnline: cancelled (hint: there might be another job still running)" }
            } catch (e: AuthorizationException) {
                logger.error(e) { "Authorization failed" }
            } catch (e: Throwable) {
                throw IllegalStateException("Unknown exception during requireAuthorizeAsync, see cause", e)
            }
        }
    }

    /**
     * Can be called either in [state] or in [requireAuthorize].
     *
     * 会触发更新, 但不会等待更新结束.
     */
    private suspend fun setSessionAndRefreshToken(
        newSession: NewSession,
        isNewLogin: Boolean
    ) {
        logger.info { "Bangumi session refreshed, new expiresAtMillis=${newSession.expiresAtMillis}" }

        tokenRepository.setRefreshToken(newSession.bangumiRefreshToken)
        setSessionImpl(AccessTokenSession(newSession.accessTokens))
        if (isNewLogin) {
            events.tryEmit(SessionEvent.Login)
        } else {
            events.tryEmit(SessionEvent.TokenRefreshed)
        }
        refreshCounter.value++ // triggers update
    }

    override suspend fun setSession(session: Session) {
        setSessionImpl(session)
        when (session) {
            is AccessTokenSession -> events.tryEmit(SessionEvent.Login)
            GuestSession -> events.tryEmit(SessionEvent.SwitchToGuest)
        }
        refreshCounter.value++
    }

    override suspend fun retry() {
        singleAuthLock.withLock {
            if (state.first() is SessionStatus.VerificationFailed) {
                refreshCounter.value++
            }
        }
    }

    private suspend fun setSessionImpl(session: Session) {
        tokenRepository.setSession(session)
    }

    override suspend fun clearSession() {
        val curr = tokenRepository.session.first()
        tokenRepository.clear()
        if (curr !is GuestSession) {
            events.tryEmit(SessionEvent.Logout)
        }
        refreshCounter.value++
    }

    @TestOnly
    override suspend fun invalidateSession() {
        tokenRepository.session.first()?.let {
            when (it) {
                is AccessTokenSession -> {
                    tokenRepository.setSession(it.copy())
                }

                GuestSession -> {
                }
            }

        }
    }
}