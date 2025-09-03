/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryServiceUnavailableException
import me.him188.ani.app.data.repository.RepositoryUnknownException
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.data.repository.user.Session
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.domain.session.auth.OAuthResult
import me.him188.ani.app.domain.session.auth.toOAuthResult
import me.him188.ani.client.apis.UserAuthenticationAniApi
import me.him188.ani.client.models.AniLoginWithRefreshTokenRequest
import me.him188.ani.client.models.AniRefreshTokenRequest
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.thisLogger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class AniSessionRefresher(
    private val getUserAuthApi: () -> ApiInvoker<UserAuthenticationAniApi>
) : SessionManager.SessionRefresher {
    override suspend fun refresh(refreshToken: String): OAuthResult {
        return getUserAuthApi().invoke {
            val resp = refreshToken(AniRefreshTokenRequest(refreshToken)).body()
            resp.toOAuthResult()
        }
    }
}

/**
 * 维护 [AccessTokenPair] 的管理器.
 *
 * 它负责持久化 [AccessTokenPair] 和 refreshToken, 以及在 accessToken 过期前使用 refreshToken 刷新两个 token (调用 [refreshSession]).
 * [SessionManager] 不处理登录和登出, 只负责维护 token 的有效性.
 *
 * 注意, [SessionManager] 已经涉及登录的内部逻辑. 如果你只需要知道当前用户是否有登录, 使用 [SessionStateProvider].
 * @since 5.0
 */
class SessionManager(
    private val tokenRepository: TokenRepository,
    private val coroutineScope: CoroutineScope,
    private val refreshSession: SessionRefresher,
    private val clock: Clock = Clock.System,
    private val config: Config = Config(),
) {
    fun interface SessionRefresher {
        /**
         * @throws RepositoryException
         */
        suspend fun refresh(refreshToken: String): OAuthResult
    }

    data class Config(
        /**
         * 在 accessToken 过期前多久提前刷新 accessToken.
         *
         * 刷新失败会在一段时间后自动重试. [refreshTokenBefore] 时间长一点可以增加更多重试机会.
         */
        val refreshTokenBefore: Duration = 7.days, // 注意, Ani 服务器会至少给 31 天 accessToken.
        /**
         * 在刷新失败后, 等待多久再尝试刷新.
         */
        val refreshAttemptInterval: Duration = 1.hours,
    )

    private val logger = thisLogger()

    val sessionFlow: Flow<Session> = tokenRepository.session
    val accessTokenSessionFlow: Flow<AccessTokenSession?> = sessionFlow.map {
        when (it) {
            is AccessTokenSession -> it
            is GuestSession -> null
        }
    }


    private val _stateProvider = object : SessionStateProvider {
        override val stateFlow =
            MutableSharedFlow<SessionState>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val eventFlow =
            MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        suspend fun emitEvent(event: SessionEvent) {
            eventFlow.emit(event)
        }
    }

    val stateProvider get() = _stateProvider

    private val backgroundJob by lazy {
        fun emitState(state: SessionState) {
            check(_stateProvider.stateFlow.tryEmit(state))
        }

        /**
         * 维护 accessToken 的有效性. 此函数可在有新的 session 时被 cancel.
         *
         * 只有这里会修改 [stateProvider].
         */
        suspend fun maintainAccessTokenLoop(session: AccessTokenSession) {
            logger.debug {
                "SessionManager: maintainAccessTokenLoop started with session: $session"
            }

            if (session.tokens.isExpired(clock)) {
                // 我们确定此时已经过期了. 但是先别急, 可以刷新

                try {
                    // 目前不支持检查 refreshToken 是否过期, 所以直接请求刷新
                    refreshSession() // This is expected to throw RepositoryException
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RepositoryException) {
                    // 翻译错误为 InvalidSessionReason, emit 给其他人
                    val reason = when (e) {
                        is RepositoryAuthorizationException -> {
                            // 说明 refreshToken 都过期了, 那就真没办法了
                            clearSession()
                            InvalidSessionReason.NO_TOKEN
                        }

                        is RepositoryNetworkException -> InvalidSessionReason.NETWORK_ERROR

                        // 服务器不太可能会返回 429, 就把它当做网络错误了
                        is RepositoryRateLimitedException -> InvalidSessionReason.NETWORK_ERROR

                        is RepositoryServiceUnavailableException -> InvalidSessionReason.NO_TOKEN
                        is RepositoryUnknownException -> InvalidSessionReason.UNKNOWN
                    }

                    if (reason == InvalidSessionReason.UNKNOWN) {
                        logger.error("Refresh session failed with unknown error", e)
                    } else {
                        // 对于已知的错误, 不要记录冗长的堆栈
                        logger.warn { "Refresh session failed with known error: $reason" }
                    }

                    emitState(SessionState.Invalid(reason))
                } catch (e: Exception) {
                    emitState(SessionState.Invalid(InvalidSessionReason.UNKNOWN))
                    logger.error("Refresh session failed", e)
                }
            } else {
                // token 还没有过期, 直接发出有效的状态
                emitState(SessionState.Valid(bangumiConnected = session.tokens.bangumiAccessToken != null))

                // Token 会在未来过期, 所以我们延迟到那个时候
                val ttl = (session.tokens.expiresAtMillis - clock.now().toEpochMilliseconds()).milliseconds
                    .minus(config.refreshTokenBefore) // 提前一小会

                logger.debug {
                    "SessionManager: access token is valid, will refresh in $ttl ms"
                }

                delay(ttl)

                logger.info {
                    "SessionManager: access token is about to expire, refreshing now"
                }

                // 每小时尝试一次
                while (session.tokens.isExpired(clock)) {
                    try {
                        refreshSession()
                    } catch (e: Exception) {
                        // 不管是什么错误, 反正失败了就等
                        val re = RepositoryException.wrapOrThrowCancellation(e)
                        if (re is RepositoryUnknownException) {
                            logger.error(
                                "Refresh session failed with unknown exception, see cause. Retrying in ${config.refreshAttemptInterval}",
                                e,
                            )
                        } else {
                            logger.warn("Refresh session failed with $re. Retrying in ${config.refreshAttemptInterval}")
                        }
                        delay(config.refreshAttemptInterval)
                    }
                }
            }
        }


        // 启动后台任务, 定时刷新 token
        coroutineScope.launch(CoroutineName("SessionManager auto refresh")) {
            migrationResult.await() // 先等迁移
            tokenRepository.session.collectLatest { session ->
                when (session) {
                    is GuestSession -> emitState(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
                    is AccessTokenSession -> maintainAccessTokenLoop(session)
                }
            }
        }

        Unit // 不存储 Job
    }

    fun startBackgroundJob() {
        backgroundJob // lazy init
    }

    /**
     * 登录成功后调用, 设置一个会话. 这也会导致 [stateProvider] [SessionStateProvider.stateFlow] 更新.
     */
    suspend fun setSession(
        session: AccessTokenSession,
        // Ani 登录保证每次登录都返回新的 refreshToken, 所以我们要求都更新
        refreshToken: String,
        isNewLogin: Boolean = true,
    ) {
        tokenRepository.setSession(session)
        tokenRepository.setRefreshToken(refreshToken)
        if (isNewLogin) {
            _stateProvider.emitEvent(SessionEvent.NewLogin)
        }
    }


    /**
     * 设置为未登录状态. 同时清空 accessToken 和 refreshToken. 这也会导致 [stateProvider] [SessionStateProvider.stateFlow] 更新.
     */
    suspend fun clearSession() {
        tokenRepository.clear()
        // 注意, 我们这里不修改公开的 state. background task 会帮我们修改.
    }

    private val refreshSessionLock = Mutex()

    /**
     * 使用 refreshToken 刷新 accessToken. 刷新成功后会自动持久化. 这也会导致 [stateProvider] [SessionStateProvider.stateFlow] 更新.
     *
     * 只有当 [SessionStateProvider.stateFlow] 为网络错误, 并且用户主动点击了刷新按钮时, 才应当调用此函数.
     *
     * @throws RepositoryException
     */
    suspend fun refreshSession() = refreshSessionLock.withLock {
        val refreshToken = tokenRepository.refreshToken.first()
        if (refreshToken == null) {
            return
        }

        try {
            val result = refreshSession.refresh(refreshToken)
            setSession(
                session = AccessTokenSession(
                    tokens = result.tokens,
                ),
                refreshToken = result.refreshToken,
                isNewLogin = false,
            )
            // 注意, 我们这里不修改公开的 state. background task 会帮我们修改.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // refresh 只应该 throw RepositoryException, 但是我们还是保险起见封装
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    sealed interface MigrationResult {
        data object NoExtraAction : MigrationResult
        data object NeedReLogin : MigrationResult
    }

    companion object {
        private val logger = logger<SessionManager>()

        val migrationResult = CompletableDeferred<MigrationResult>()

        @Deprecated("Since 5.0, for migration only")
        suspend fun migrateBangumiToken(koin: Koin): MigrationResult {
            val settings = koin.get<SettingsRepository>()
            val tokenRepository = koin.get<TokenRepository>()

            val session = tokenRepository.session.first()
            val needReLogin = settings.oneshotActionConfig.flow.map { it.needReLoginAfter500 }.first()

            // 如果是 guest (未登录或新用户) 或者已经迁移过了, 就不迁移
            if (session is GuestSession || !needReLogin) {
                if (!needReLogin) {
                    settings.oneshotActionConfig.update { copy(needReLoginAfter500 = false) }
                }
                return MigrationResult.NoExtraAction
            }

            check(session is AccessTokenSession)

            val bgmRefreshToken = tokenRepository.refreshToken.first()
            val client = koin.get<AniApiProvider>().bangumiApi
            val sessionManager = koin.get<SessionManager>()

            // 如果需要迁移, 并且有 bgm refresh token, 则尝试自动迁移
            if (bgmRefreshToken != null) {
                try {
                    val result = client
                        .invoke { loginWithRefreshToken(AniLoginWithRefreshTokenRequest(bgmRefreshToken)).body() }

                    sessionManager.setSession(
                        AccessTokenSession(
                            AccessTokenPair(
                                aniAccessToken = result.tokens.accessToken,
                                expiresAtMillis = result.tokens.expiresAtMillis,
                                bangumiAccessToken = result.tokens.bangumiAccessToken,
                            ),
                        ),
                        refreshToken = result.tokens.refreshToken,
                    )

                    // 迁移完成后清除一次性动作
                    settings.oneshotActionConfig.update { copy(needReLoginAfter500 = false) }
                    return MigrationResult.NoExtraAction
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ClientRequestException) {
                    // 400 表示这个 refresh token 无效, 否则表示出现了其他内部错误
                    if (e.response.status.value == 400) {
                        logger.warn("Bangumi refresh token is invalid, clearing session", e)
                    } else {
                        logger.error("Failed to request bind Bangumi refresh, clearing session", e)
                    }
                }
            }
            // 这时候可能是没有 Bangumi refresh token, 或者请求迁移失败了 
            // 需要清除 session 并在用户第一次点击登录按钮时导航到 bgm 登录, 以免注册多余的 ani 账号
            sessionManager.clearSession()
            settings.oneshotActionConfig.update { copy(needReLoginAfter500 = false) }
            return MigrationResult.NeedReLogin
        }
    }
}
