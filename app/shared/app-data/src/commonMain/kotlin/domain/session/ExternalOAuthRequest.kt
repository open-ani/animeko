/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 表示一个外部 OAuth 授权请求.
 * 发起外部浏览器等进行 OAuth. 适用于第一次登录, 或者 refresh token 已经过期的情况.
 *
 * 此请求在需要用户登录时创建, 封装 OAuth 的逻辑.
 */
interface ExternalOAuthRequest {
    val state: StateFlow<State>

    /**
     * OAuth 成功回调 code 时调用.
     */
    fun onCallback(code: Result<OAuthResult>)

    /**
     * @param reason for debugging
     */
    fun cancel(reason: CancellationException? = null)

    /**
     * Does not throw
     */
    suspend fun invoke()

    sealed class State {
        data object Launching : State()
        data object AwaitingCallback : State()

        /**
         * Processing callback. E.g. obtaining access token using the code
         */
        data object Processing : State()

        sealed class Result : State()

        data class Cancelled(
            val cause: CancellationException
        ) : Result()

        /**
         * Failed to obtain access token using the code
         */
        data class Failed(
            val throwable: Throwable
        ) : Result()

        data object Success : Result()
    }
}

data class OAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Duration,
)

class ExternalOAuthRequestImpl(
    private val onLaunch: suspend () -> Unit,
    private val onSuccess: suspend (NewSession) -> Unit,
) : ExternalOAuthRequest {
    override val state: MutableStateFlow<ExternalOAuthRequest.State> =
        MutableStateFlow(ExternalOAuthRequest.State.Launching)

    private val result = CompletableDeferred<OAuthResult>()

    /**
     * OAuth 成功回调 code 时调用.
     */
    override fun onCallback(code: Result<OAuthResult>) {
        this.result.completeWith(code)
    }

    override fun cancel(reason: CancellationException?) {
        result.cancel(reason)
    }

    override suspend fun invoke() {
        state.value = ExternalOAuthRequest.State.Launching
        try {
            onLaunch()
        } catch (e: CancellationException) {
            state.value = ExternalOAuthRequest.State.Cancelled(e)
            return
        } catch (e: Throwable) {
            state.value = ExternalOAuthRequest.State.Failed(e)
            return
        }
        check(state.value == ExternalOAuthRequest.State.Launching) {
            "onLaunch must not change state"
        }
        state.value = ExternalOAuthRequest.State.AwaitingCallback
        try {
            val result = result.await()
            state.value = ExternalOAuthRequest.State.Processing

            onSuccess(
                NewSession(
                    result.accessToken,
                    (currentTimeMillis().milliseconds + result.expiresIn).inWholeMilliseconds,
                    result.refreshToken,
                ),
            )
        } catch (e: CancellationException) {
            state.value = ExternalOAuthRequest.State.Cancelled(e)
            return
        } catch (e: Throwable) {
            state.value = ExternalOAuthRequest.State.Failed(e)
            return
        }
        state.value = ExternalOAuthRequest.State.Success
    }
}
