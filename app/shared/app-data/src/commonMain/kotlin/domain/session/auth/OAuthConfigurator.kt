/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.IOException
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.canAccessAniApiNow
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.platform.Uuid
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Access OAuthClient oAuth
 */
class OAuthConfigurator(
    private val client: OAuthClient,
    private val sessionManager: SessionManager,
    private val sessionStateProvider: SessionStateProvider,
    private val random: Random = Random.Default,
    private val onOpenUrl: suspend (String) -> Unit,
) {
    private val logger = logger<OAuthConfigurator>()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /**
     * 启动 oAuth 验证
     */
    suspend fun start() {
        val requestId = Uuid.random(random).toString()
        val tokenDeferred = CompletableDeferred<OAuthResult>()

        logger.info { "OAuth started, request id: $requestId" }
        _state.value = State.AwaitingResult(requestId, tokenDeferred)

        val hasLogonAni = sessionStateProvider.canAccessAniApiNow()

        try {
            val externalUrl = if (hasLogonAni) {
                logger.info { "Request bind, request id: $requestId" }
                client.getOAuthBindLink(requestId)
            } else {
                logger.info { "Request register, request id: $requestId" }
                client.getOAuthRegisterLink(requestId)
            }

            onOpenUrl(externalUrl)

            var oAuthResult: OAuthResult? = null
            while (oAuthResult == null) {
                delay(1.seconds)
                oAuthResult = client.getResult(requestId)
                logger.info { "Check oauth result of request id $requestId: ${oAuthResult != null}" }
            }

            _state.value = State.Success(requestId, oAuthResult)
            logger.info {
                "Oauth success, request id: $requestId, " +
                        "token hash: ${oAuthResult.tokens.aniAccessToken.hashCode()}"
            }

            sessionManager.setSession(
                AccessTokenSession(oAuthResult.tokens),
                oAuthResult.refreshToken,
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            logger.error(ex) { "OAuth failed, request id: $requestId" }

            when (ex) {
                is AlreadyBoundException ->
                    _state.value = State.KnownError(State.ErrorType.AlreadyBound, ex)

                is InvalidTokenException ->
                    _state.value = State.KnownError(State.ErrorType.InvalidBangumiToken, ex)

                is NotSupportedForRegistration ->
                    _state.value = State.KnownError(State.ErrorType.NotSupportedForRegistration, ex)

                is IOException ->
                    _state.value = State.KnownError(State.ErrorType.NetworkError, ex)

                else -> _state.value = State.UnknownError(ex)
            }
        }
    }

    sealed interface State {
        data object Idle : State
        class AwaitingResult(val requestId: String, val deferred: CompletableDeferred<OAuthResult>) : State
        class Success(val requestId: String, val result: OAuthResult) : State

        interface Error : State
        data class UnknownError(val exception: Throwable) : State

        class KnownError(val type: ErrorType, val exception: Throwable) : State

        enum class ErrorType {
            NotSupportedForRegistration, // 不支持注册
            InvalidBangumiToken,
            AlreadyBound, // 已经绑定了其他的 Ani 账号
            NetworkError,
        }
    }
}