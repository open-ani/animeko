/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * 从 [SessionManager] 获取当前的 Bangumi 授权状态.
 *
 * 通常在 UI 层使用.
 *
 * ```
 * // In your ViewModel.
 * val authStateProvider: AniAuthStateProvider by inject()
 * // In your UI.
 * val authState by authStateProvider.state.collectAsState(initial = AuthState.Idle)
 * ```
 *
 * @see AniAuthConfigurator
 */
interface AniAuthStateProvider {
    val state: SharedFlow<AuthState>
}

/**
 * Wrapper for [SessionManager] and [AniAuthClient] to handle authorization.
 *
 * Effectively a mutable version of [AniAuthStateProvider], which allows you to start and cancel authorization requests.
 *
 * This class does:
 * * Directly read session states from [SessionManager] by subscribing [state].
 * * Handle procedure of starting and canceling authorization requests.
 *
 * 通常在 UI 层使用.
 *
 * ```
 * // In your ViewModel.
 * val sessionManager: SessionManager by inject()
 * val authClient: AniAuthClient by inject()
 *
 * val authConfigurator = AniAuthConfigurator(
 *    sessionManager = sessionManager,
 *    authClient = authClient,
 *    onLaunchAuthorize = { requestId ->
 *        // open browser or other actions
 *    }
 * )
 *
 * val authState = authConfigurator.state
 *    .map { /* Your UI state */ }
 *    .stateIn(...)
 *
 * // In your UI.
 * val authState by viewModel.authState.collectAsState(MyOwnUIState.Loading)
 * ```
 */
class AniAuthConfigurator(
    private val sessionManager: SessionManager,
    private val authClient: AniAuthClient,
    private val onLaunchAuthorize: suspend (requestId: String) -> Unit,
    private val awaitRetryInterval: Duration = 1.seconds,
    parentCoroutineContext: CoroutineContext = Dispatchers.Default,
) : AniAuthStateProvider {
    private val logger = logger<AniAuthConfigurator>()
    private val scope = parentCoroutineContext.childScope()

    private val authorizeTasker = MonoTasker(scope)
    private val currentRequestAuthorizeId: MutableStateFlow<String?> = MutableStateFlow(REFRESH)

    private val launchedExternalRequests = MutableStateFlow<PersistentList<String>>(persistentListOf())
    private val lastAuthException: MutableStateFlow<Throwable?> = MutableStateFlow(null)

    override val state: SharedFlow<AuthState> = currentRequestAuthorizeId
        .transformLatest { requestId ->
            if (requestId == null) return@transformLatest emit(AuthState.NotAuthed)

            combine(
                sessionManager.state,
                sessionManager.processingRequest.flatMapConcat { it?.state ?: flowOf(null) },
                lastAuthException,
            ) { sessionState, requestState, lastAuthEx ->
                val combinedReqState = requestState ?: (lastAuthEx?.let { ExternalOAuthRequest.State.Failed(it) })
                logger.debug {
                    "[AuthState][${requestId.idStr}] sessionStatus: $sessionState, requestState: $combinedReqState"
                }
                convertCombinedAuthState(requestId, sessionState, combinedReqState)
            }
                .collectLatest { authStateNew -> emit(authStateNew) }
        }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    /**
     * 启动授权请求检查循环.
     *
     * 会启动两个协程:
     * * [checkAuthorizeRequestLoop] 用于检查授权请求状态.
     * * [requireAuthorizeStarterTaskLoop] 用于启动 [SessionManager.requireAuthorize].
     */
    suspend fun authorizeRequestCheckLoop() = coroutineScope {
        launch(start = CoroutineStart.UNDISPATCHED) { checkAuthorizeRequestLoop() }
        launch { requireAuthorizeStarterTaskLoop() }
    }

    /**
     * 通过 [authorizeTasker] 来启动 [SessionManager.requireAuthorize].
     *
     * 根据 [currentRequestAuthorizeId] 执行不同的任务:
     * * 为 null 时什么都不做, 并且重置 [lastAuthException] 和 [launchedExternalRequests].
     * * 为 REFRESH 时, 会阻止 requireAuthorize 启动 external oauth, 由 [lastAuthException] 保存异常信息.
     * * 为 真实 requestId 时, 会启动 external oauth.
     *
     * 这个函数支持 cancellation. 如果 requestId 没变, 重启此函数不会重复调用 requireAuthorize.
     */
    private suspend fun requireAuthorizeStarterTaskLoop() {
        currentRequestAuthorizeId.collectLatest { requestAuthorizeId ->
            if (requestAuthorizeId == null) {
                lastAuthException.update { null }
                launchedExternalRequests.update { clear() }
                return@collectLatest
            }

            // 避免重复启动
            if (launchedExternalRequests.value.contains(requestAuthorizeId)) {
                return@collectLatest
            }

            lastAuthException.update { null }
            // 记录 requestAuthorizeId 为避免重复启动 external oauth
            launchedExternalRequests.update { add(requestAuthorizeId) }

            // requireAuthorize 会在后台一直运行, 直到正常结束或出现异常
            authorizeTasker.launch {
                try {
                    sessionManager.requireAuthorize(
                        onLaunch = {
                            // 只有实际的授权请求才会调用 onLaunchAuthorize,
                            // REFRESH 的情况下又要启动授权请求, 那说明刷新 token 失败了, 直接抛异常
                            // UI state 直接捕获这个异常并提示用户重新授权
                            if (requestAuthorizeId != REFRESH) {
                                onLaunchAuthorize(requestAuthorizeId)
                            } else {
                                throw RefreshTokenFailedException()
                            }
                        },
                        // 游客模式下不能启动 external oauth 授权, 因为肯定是用户手动设置的游客模式
                        skipOnGuest = true,
                    )
                } catch (_: AuthorizationCancelledException) {
                } catch (e: AuthorizationFailedException) {
                    lastAuthException.update { e.cause }
                } catch (e: CancellationException) {
                    throw e // don't prevent cancellation
                } catch (e: Throwable) {
                    throw IllegalStateException("Unknown exception during requireAuthorize, see cause", e)
                }
            }
        }
    }

    /**
     * loop 获取授权状态.
     * 如果授权成功了, 会调用 [ExternalOAuthRequest.onCallback] 来恢复 [SessionManager.requireAuthorize] 继续执行.
     * 如果出现了未知异常, 会调用 [ExternalOAuthRequest.completeExceptionally] 来终止 [SessionManager.requireAuthorize].
     */
    private suspend fun checkAuthorizeRequestLoop() {
        currentRequestAuthorizeId
            .flatMapLatest { requestAuthorizeId ->
                if (requestAuthorizeId == null) return@flatMapLatest flowOf(null)
                if (requestAuthorizeId == REFRESH) return@flatMapLatest flowOf(null)

                sessionManager.processingRequest
                    .filterNotNull()
                    .map { requestAuthorizeId to it }
            }
            .collectLatest { pair ->
                if (pair == null) return@collectLatest
                val (requestAuthorizeId, processingRequest) = pair
                logger.debug {
                    "[AuthCheckLoop][${requestAuthorizeId.idStr}] Current processing request: $processingRequest"
                }

                suspend {
                    val result = getAccessTokenFromAniServer(requestAuthorizeId)
                    logger.debug {
                        "[AuthCheckLoop][$requestAuthorizeId] " +
                                "Check OAuth result success, request is $processingRequest, " +
                                "token expires in ${result.expiresIn}"
                    }
                    // resume sessionManager.requireAuthorize
                    processingRequest.onCallback(Result.success(result))
                }
                    .asFlow()
                    .retry { e ->
                        when (e) {
                            is NotAuthorizedException -> {
                                delay(awaitRetryInterval)
                                true
                            }

                            is CancellationException -> {
                                false
                            }

                            else -> {
                                logger.error(e) {
                                    "[AuthCheckLoop][${requestAuthorizeId.idStr}] Failed to check authorize status."
                                }
                                // cancel processingRequest 会间接 cancel 整个 requireAuthorize
                                processingRequest.completeExceptionally(GetAuthTokenFromAniServerException(e))
                                false
                            }
                        }
                    }
                    .firstOrNull()
            }
    }

    suspend fun startAuthorize() {
        sessionManager.clearSession()
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
    suspend fun setAuthorizationToken(token: String) {
        sessionManager.setSession(
            AccessTokenSession(
                accessToken = token,
                expiresAtMillis = currentTimeMillis() + 365.days.inWholeMilliseconds,
            ),
        )
        // trigger ui update
        currentRequestAuthorizeId.value = REFRESH
    }

    suspend fun setGuestSession() {
        sessionManager.setSession(GuestSession)
        currentRequestAuthorizeId.value = REFRESH
    }

    /**
     * 验证成功了返回 [OAuthResult], 否则抛出异常,
     * 若异常不是 [NotAuthorizedException] 则视为出现了意外问题.
     *
     * 这个函数支持 cancellation
     *
     * @throws NotAuthorizedException 还未完成验证, 需要捕获并重试
     * @return [OAuthResult]
     */
    @Throws(NotAuthorizedException::class, CancellationException::class)
    private suspend fun getAccessTokenFromAniServer(
        requestId: String,
    ): OAuthResult {
        val token = authClient
            .getResult(requestId)
            .fold(
                onSuccess = { resp -> resp ?: throw NotAuthorizedException() },
                // 已知 API 错误总是抛出 NotAuthorizedException, 
                // caller 捕获这个错误并重试 checkAuthorizeStatus
                onKnownFailure = { throw NotAuthorizedException() },
            )

        return OAuthResult(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresIn = token.expiresIn.seconds,
        )
    }

    /**
     * Combine [SessionStatus] and [ExternalOAuthRequest.State] to [AuthState]
     */
    private fun convertCombinedAuthState(
        requestId: String,
        sessionState: SessionStatus,
        requestState: ExternalOAuthRequest.State?,
    ): AuthState {
        return when (sessionState) {
            is SessionStatus.Verified -> {
                val userInfo = sessionState.userInfo
                AuthState.Success(
                    username = userInfo.run { nickname ?: username ?: id.toString() },
                    avatarUrl = userInfo.avatarUrl,
                    isGuest = false,
                )
            }

            is SessionStatus.Refreshing,
            is SessionStatus.Verifying -> {
                AuthState.AwaitingUserInfo(requestId)
            }

            SessionStatus.NetworkError,
            SessionStatus.ServiceUnavailable -> {
                AuthState.NetworkError
            }

            SessionStatus.Expired -> {
                AuthState.TokenExpired
            }

            SessionStatus.NoToken -> when (requestState) {
                ExternalOAuthRequest.State.Launching,
                ExternalOAuthRequest.State.AwaitingCallback -> {
                    AuthState.AwaitingToken(requestId)
                }

                is ExternalOAuthRequest.State.Failed -> {
                    when (requestState.throwable) {
                        // 为什么是 Idle?
                        // SessionManager.requireAuthorize 在 SessionStatus.NoToken 时会启动 external oauth,
                        // 如果是在 REFRESH (检查授权状态) 的情况下, 我们不应该实际启动浏览器去获取 token, 所以抛出了 RefreshTokenFailedException
                        // 整个流程是:
                        //     检查授权状态 ->
                        //     NoToken -> 
                        //     SessionManager 尝试启动 external oauth -> 
                        //     因为是检查, 直接中断启动, 抛出 RefreshTokenFailedException ->
                        //     authorizeTasker 捕获 RefreshTokenFailedException, 放到 lastAuthException ->
                        //     convertCombinedAuthState 获取 lastAuthException ->
                        //     when 分支到这里, 这里返回 Idle

                        is RefreshTokenFailedException -> {
                            AuthState.NotAuthed
                        }

                        else -> {
                            AuthState.UnknownError(requestState.throwable)
                        }
                    }
                }

                is ExternalOAuthRequest.State.Cancelled -> {
                    AuthState.NotAuthed
                }

                // oauth 成功并不代表所有流程结束了, 还会继续进行 session 验证
                // null 表示还未开始 oauth, 也是进行中的动作
                ExternalOAuthRequest.State.Processing,
                ExternalOAuthRequest.State.Success -> {
                    AuthState.AwaitingUserInfo(requestId)
                }

                null -> {
                    AuthState.NotAuthed
                }
            }

            SessionStatus.Guest -> AuthState.Success("", null, isGuest = true)
        }
    }

    companion object {
        private const val REFRESH = "-1"
        private val String.idStr get() = if (equals(REFRESH)) "REFRESH" else this
    }
}

/**
 * 还未完成验证, API 返回 null 或 [ApiFailure.Unauthorized]
 */
private class NotAuthorizedException : Exception()

/**
 * getAccessTokenFromAniServer 时出现了未知问题
 */
private class GetAuthTokenFromAniServerException(cause: Throwable? = null) : Exception(null, cause)

/**
 * [currentRequestAuthorizeId][AniAuthConfigurator.currentRequestAuthorizeId] 为 [REFRESH][AniAuthConfigurator.REFRESH] 时,
 * [SessionStatus.NoToken] 和 [SessionStatus.Expired], 需要 launch external oauth.
 * 但我们不 launch, 而是直接抛出异常, 统一处理为没有 token.
 */
private class RefreshTokenFailedException : Exception()