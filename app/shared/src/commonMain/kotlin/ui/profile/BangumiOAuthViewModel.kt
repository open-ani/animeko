/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile

import androidx.annotation.UiThread
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.domain.session.AniAuthClient
import me.him188.ani.app.domain.session.ExternalOAuthRequest
import me.him188.ani.app.domain.session.OAuthResult
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStatus
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.feedback.ErrorMessage
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.platform.Uuid
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

@Stable
class BangumiOAuthViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()
    private val client: AniAuthClient by inject()

    /**
     * 需要进行授权
     */
    val needAuth by sessionManager.state
        .map { it !is SessionStatus.Verified }
        .produceState(true)

    private var requestIdFlow = MutableStateFlow(Uuid.randomString())

    /**
     * 当前是第几次尝试
     */
    val requestId by requestIdFlow.produceState()

    fun makeOAuthUrl(
        requestId: String,
    ): String {
        val base = currentAniBuildConfig.aniAuthServerUrl.removeSuffix("/")
        return "${base}/v1/login/bangumi/oauth?requestId=${requestId.encodeURLParameter()}"
    }

    val oauthUrl by derivedStateOf {
        makeOAuthUrl(requestId)
    }

    /**
     * 展示登录失败的错误
     */
    val authError: MutableStateFlow<ErrorMessage?> = sessionManager.processingRequest.map { request ->
        (request?.state?.value as? ExternalOAuthRequest.State.Failed)?.throwable?.let {
            ErrorMessage.simple("登录失败, 请重试", it)
        }
    }.localCachedStateFlow(null)

    suspend fun doCheckResult() {
        withContext(backgroundScope.coroutineContext) {
            while (true) {
                val resp = client.getResult(requestIdFlow.value)
                logger.info { "Check OAuth result: $resp" }
                resp.fold(
                    onSuccess = { result ->
                        if (result == null) {
                            return@fold
                        }
                        val request = sessionManager.processingRequest.value
                        logger.info {
                            "Check OAuth result success, request is $request, " +
                                    "token expires in ${result.expiresIn.seconds}"
                        }
                        request?.onCallback(
                            Result.success(
                                OAuthResult(
                                    accessToken = result.accessToken,
                                    refreshToken = result.refreshToken,
                                    expiresIn = result.expiresIn.seconds,
                                ),
                            ),
                        )
                        return@withContext
                    },
                    onKnownFailure = {
                        logger.info { "Check OAuth result failed: $it" }
                    },
                )

                delay(1000)
            }
        }
    }

    @UiThread
    fun dismissError() {
        logger.debug { "dismissError" }
        authError.value = null
        refresh()
    }

    @UiThread
    fun refresh() {
        logger.debug { "refresh" }
        requestIdFlow.value = Uuid.randomString()
    }

    private fun onCancel(reason: String?) {
        sessionManager.processingRequest.value?.cancel(CancellationException(reason))
    }

    override fun onCleared() {
        super.onCleared()
        onCancel("BangumiOAuthScene disposed")
    }
}
