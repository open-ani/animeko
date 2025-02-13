/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.trace
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class AniAuthConfigurator(
    private val sessionManager: SessionManager,
    private val authClient: AniAuthClient,
    private val onLaunchAuthorize: suspend (requestId: String) -> Unit,
    private val maxAwaitRetries: Long = 5 * 60,
    private val awaitRetryInterval: Duration = 1.seconds,
    parentCoroutineContext: CoroutineContext,
) {
    private val logger = logger<AniAuthConfigurator>()
    private val scope = parentCoroutineContext.childScope()

    private val authorizeTasker = MonoTasker(scope)
    private val currentRequestAuthorizeId = MutableStateFlow<String?>(null)

    val state: Flow<AuthStateNew> = currentRequestAuthorizeId
        .transformLatest { requestId ->
            if (requestId == null) {
                emit(AuthStateNew.Idle)
                logger.trace { "[AuthState] Got null request id, stopped checking" }
                return@transformLatest
            } else {
                emit(AuthStateNew.AwaitingResult(requestId))
            }

            logger.trace { "[AuthState][$requestId] Start checking authorization request" }
            sessionManager.state
                .collectLatest { sessionState ->
                    // 如果有 token, 直接获取当前 session 的状态即可
                    if (sessionState !is SessionStatus.NoToken)
                        return@collectLatest collectCombinedAuthState(requestId, sessionState, null)

                    sessionManager.processingRequest
                        /*.onEach { processingRequest ->
                            if (processingRequest == null) {
                                logger.trace { "[AuthState][$requestId] No processing request, assume NoToken" }
                                emit(AuthStateNew.Idle)
                            }
                        }*/
                        .filterNotNull()
                        .flatMapLatest {
                            logger.trace { "[AuthState][$requestId] Current processing request: $it" }
                            it.state
                        }
                        .collectLatest { requestState ->
                            logger.trace {
                                "[AuthState][$requestId] " +
                                        "session: ${sessionState::class.simpleName}, " +
                                        "request: ${requestState::class.simpleName}"
                            }
                            collectCombinedAuthState(requestId, sessionState, requestState)
                        }
                }
        }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(),
            AuthStateNew.Idle,
        )

    init {
        // process authorize request
        scope.launch {
            currentRequestAuthorizeId
                .filterNotNull()
                .transform { requestAuthorizeId ->
                    // launch authorize if request id is not REFRESH.
                    if (requestAuthorizeId != REFRESH) {
                        logger.trace {
                            "[AuthCheckLoop] Current requestAuthorizeId: $requestAuthorizeId, checking authorize state."
                        }
                        sessionManager.clearSession()
                        authorizeTasker.launch {
                            try {
                                sessionManager.requireAuthorize(
                                    onLaunch = { onLaunchAuthorize(requestAuthorizeId) },
                                    skipOnGuest = false,
                                )
                            } catch (_: AuthorizationCancelledException) {
                            } catch (_: AuthorizationException) {
                            } catch (e: Throwable) {
                                throw IllegalStateException("Unknown exception during requireAuth, see cause", e)
                            }
                        }
                        emit(requestAuthorizeId)
                        return@transform
                    }
                    logger.trace { "[AuthCheckLoop] Trigger as REFRESH which will not cause checking authorize state." }
                    emit(null)
                }
                .filterNotNull() // filter out REFRESH
                .transformLatest { requestAuthorizeId ->
                    emitAll(
                        sessionManager.processingRequest
                            .filterNotNull()
                            .map { requestAuthorizeId to it },
                    )
                }
                .collectLatest { (requestAuthorizeId, processingRequest) ->
                    logger.trace {
                        "[AuthCheckLoop][$requestAuthorizeId] Current processing request: $processingRequest"
                    }

                    // 最大尝试 300 次, 每次间隔 1 秒
                    suspend { checkAuthorizeStatus(requestAuthorizeId, processingRequest) }
                        .asFlow()
                        .retry(retries = maxAwaitRetries) { e ->
                            (e is NotAuthorizedException).also { if (it) delay(awaitRetryInterval) }
                        }
                        .catch { authorizeTasker.cancel() }
                        .firstOrNull()
                }
        }
    }

    fun startAuthorize() {
        currentRequestAuthorizeId.value = Uuid.random().toString()
    }

    fun cancelAuthorize() {
        authorizeTasker.cancel()
        currentRequestAuthorizeId.value = null
    }

    fun checkAuthorizeState() {
        currentRequestAuthorizeId.value = REFRESH
    }

    /**
     * 通过 token 授权
     */
    fun setAuthorizationToken(token: String) {
        scope.launch {
            sessionManager.setSession(
                AccessTokenSession(
                    accessToken = token,
                    expiresAtMillis = currentTimeMillis() + 365.days.inWholeMilliseconds,
                ),
            )
            // trigger ui update
            currentRequestAuthorizeId.value = REFRESH
        }
    }

    fun setGuestSession() {
        // 因为设置 GuestSession 之后会马上进入主界面, backgroundScope 会被取消
        // 所以这里使用 GlobalScope 确保这个任务能完成, 
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            sessionManager.setSession(GuestSession)
            currentRequestAuthorizeId.value = REFRESH
        }
    }

    /**
     * 只有验证成功了才会正常返回, 否则会抛出异常
     */
    private suspend fun checkAuthorizeStatus(
        requestId: String,
        request: ExternalOAuthRequest,
    ) {
        val token = authClient.getResult(requestId).getOrThrow()
            ?: throw NotAuthorizedException()

        val result = OAuthResult(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresIn = token.expiresIn.seconds,
        )

        logger.trace {
            "[AuthCheckLoop][$requestId] " +
                    "Check OAuth result success, request is $request, " +
                    "token expires in ${token.expiresIn.seconds}"
        }
        request.onCallback(Result.success(result))
    }

    companion object {
        private const val REFRESH = "-1"
    }
}

/**
 * Combine [SessionStatus] and [ExternalOAuthRequest.State] to [AuthStateNew]
 */
private suspend fun FlowCollector<AuthStateNew>.collectCombinedAuthState(
    requestId: String,
    sessionState: SessionStatus,
    requestState: ExternalOAuthRequest.State?,
) {
    when (sessionState) {
        is SessionStatus.Verified -> {
            val userInfo = sessionState.userInfo
            emit(
                AuthStateNew.Success(
                    username = userInfo.username ?: userInfo.id.toString(),
                    avatarUrl = userInfo.avatarUrl,
                    isGuest = false,
                ),
            )
        }

        is SessionStatus.Loading -> {
            emit(AuthStateNew.AwaitingResult(requestId))
        }

        SessionStatus.NetworkError,
        SessionStatus.ServiceUnavailable -> {
            emit(AuthStateNew.Network)
        }

        SessionStatus.Expired -> {
            emit(AuthStateNew.TokenExpired)
        }

        SessionStatus.NoToken -> when (requestState) {
            ExternalOAuthRequest.State.Launching,
            ExternalOAuthRequest.State.AwaitingCallback,
            ExternalOAuthRequest.State.Processing -> {
                emit(AuthStateNew.AwaitingResult(requestId))
            }

            is ExternalOAuthRequest.State.Failed -> {
                emit(AuthStateNew.UnknownError(requestState.throwable.toString()))
            }

            is ExternalOAuthRequest.State.Cancelled -> {
                emit(AuthStateNew.Timeout)

            }

            else -> {}
        }

        SessionStatus.Guest -> emit(AuthStateNew.Success("", null, isGuest = true))
    }
}

// This class is intend to replace current [AuthState]
@Stable
sealed class AuthStateNew {
    sealed class Initial : AuthStateNew()

    @Immutable
    data object Idle : Initial()

    @Stable
    data class AwaitingResult(val requestId: String) : AuthStateNew()

    sealed class Error : AuthStateNew()

    @Immutable
    data object Network : Error()

    @Immutable
    data object TokenExpired : Error()

    @Immutable
    data object Timeout : Error()
    
    @Stable
    data class UnknownError(val message: String) : Error()

    @Stable
    data class Success(
        val username: String,
        val avatarUrl: String?,
        val isGuest: Boolean
    ) : AuthStateNew()
}

/**
 * 还未完成验证
 */
private class NotAuthorizedException : Exception()