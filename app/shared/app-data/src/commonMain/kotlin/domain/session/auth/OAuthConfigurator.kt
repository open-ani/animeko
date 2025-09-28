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
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.checkAccessAniApiNow
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
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
) {
    private val logger = logger<OAuthConfigurator>()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /**
     * 进行 OAuth 验证, 操作时会更新 [state]. 这个函数可能会持续很久, 直到用户完成 OAuth 授权并返回结果.
     *
     * does not throw
     */
    suspend fun auth(isRegister: Boolean, onOpenUrl: suspend (String) -> Unit): State {
        val requestId = Uuid.random(random).toString()
        val tokenDeferred = CompletableDeferred<OAuthResult>()

        logger.info { "OAuth started, request id: $requestId" }
        _state.value = State.AwaitingResult(requestId, tokenDeferred)

        try {
            val externalUrl = if (!isRegister) {
                logger.info { "Request bind, request id: $requestId" }
                sessionStateProvider.checkAccessAniApiNow()
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
                // 日志里已经有 HTTP 日志了, 可以看到 425, 不需要这个
//                logger.info { "Check oauth result of request id $requestId: ${oAuthResult != null}" }
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
            _state.value = State.Idle
            throw ex
        } catch (ex: Exception) {
            val re = RepositoryException.wrapOrThrowCancellation(ex)
            val loadError = LoadError.fromException(re)
            if (loadError is LoadError.UnknownError) {
                logger.error(re) { "OAuth failed with unknown error, request id: $requestId" }
            } else {
                logger.warn { "OAuth failed, request id: $requestId, $loadError" }
            }
            _state.value = State.Failed(loadError)
        }
        return _state.value
    }

    sealed interface State {
        data object Idle : State
        class AwaitingResult(val requestId: String, val deferred: CompletableDeferred<OAuthResult>) : State
        class Success(val requestId: String, val result: OAuthResult) : State
        class Failed(val error: LoadError) : State
    }
}