/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.user

import androidx.datastore.core.DataStore
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.domain.session.AccessTokenPair
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.UserAniApi
import me.him188.ani.client.apis.UserAuthenticationAniApi
import me.him188.ani.client.apis.UserProfileAniApi
import me.him188.ani.client.models.AniAniSelfUser
import me.him188.ani.client.models.AniAuthenticationResponse
import me.him188.ani.client.models.AniEditEmailRequest
import me.him188.ani.client.models.AniRegisterOrLoginByEmailOtpRequest
import me.him188.ani.client.models.AniSendEmailOtpRequest
import me.him188.ani.client.models.AniUpdateProfileRequest
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.catching
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

class UserRepository(
    private val dataStore: DataStore<SelfInfo?>,
    private val sessionStateProvider: SessionStateProvider,
    private val userApi: ApiInvoker<UserAniApi>,
    private val authApi: ApiInvoker<UserAuthenticationAniApi>,
    private val profileApi: ApiInvoker<UserProfileAniApi>,
    private val bangumiApi: ApiInvoker<me.him188.ani.client.apis.BangumiAniApi>,
    private val sessionManager: SessionManager,
    coroutineContext: CoroutineContext = Dispatchers.Default,
) {
    private val logger = logger<UserRepository>()
    private val scope = CoroutineScope(coroutineContext)

    private val selfInfoRefresher = FlowRestarter()

    /**
     * 先读缓存, 然后网络.
     */
    val selfInfoFlow: Flow<SelfInfo?> = sessionStateProvider.stateFlow.transformLatest { state ->
        when (state) {
            is SessionState.Invalid -> {
                when (state.reason) {
                    InvalidSessionReason.NETWORK_ERROR -> {
                        emit(dataStore.data.firstOrNull())
                    }

                    InvalidSessionReason.NO_TOKEN,
                    InvalidSessionReason.UNKNOWN -> {
                        emit(null)
                    }
                }
            }

            is SessionState.Valid -> {
                emit(dataStore.data.firstOrNull())
                suspend {
                    userApi.invoke { getUser().body() }.toSelfInfo()
                }
                    .asFlow()
                    // 首次登录这里的 http client 可能还是旧的, 添加重试机制确保 user info 能够正确获取.
                    .retryWhen { e, attempt ->
                        val wrapped = RepositoryException.wrapOrThrowCancellation(e)
                        (wrapped is RepositoryAuthorizationException && attempt < 3).also {
                            if (it) {
                                logger.warn(wrapped) { "Failed to get user info, retried $attempt, max retries: 3" }
                                delay(125L)
                            }
                        }
                    }
                    .catching()
                    .restartable(selfInfoRefresher)
                    .collectLatest { result ->
                        result
                            .onSuccess { self ->
                                coroutineScope {
                                    launch { dataStore.updateData { self } }
                                    emit(self)
                                }
                            }
                            .onFailure { e ->
                                logger.error(RepositoryException.wrapOrThrowCancellation(e)) {
                                    "Failed to refresh user profile info."
                                }
                            }
                    }
            }
        }
    }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    sealed class SendOtpResult {
        data class Success(
            val user: SelfInfo,
        ) : SendOtpResult()

        data object InvalidOtp : SendOtpResult()
        data object EmailAlreadyExist : SendOtpResult()
    }

    /**
     * @throws me.him188.ani.app.data.repository.RepositoryRateLimitedException
     */
    suspend fun registerOrLoginByEmailOtp(
        otpId: String,
        otp: String,
    ): SendOtpResult = withContext(Dispatchers.Default) {
        requestEmailOtpAuth {
            registerOrLoginByEmailOtp(AniRegisterOrLoginByEmailOtpRequest(otpId = otpId, otpValue = otp)).body()
        }
    }

    suspend fun bindOrReBindEmail(
        otpId: String,
        otp: String,
    ): SendOtpResult = withContext(Dispatchers.Default) {
        requestEmailOtpAuth {
            editEmail(AniEditEmailRequest(otpId = otpId, otpValue = otp)).body()
        }
    }

    private suspend fun requestEmailOtpAuth(block: suspend UserAuthenticationAniApi.() -> AniAuthenticationResponse): SendOtpResult {
        return authApi.invoke {
            try {
                val data = block()

                sessionManager.setSession(
                    AccessTokenSession(
                        AccessTokenPair(
                            aniAccessToken = data.tokens.accessToken,
                            expiresAtMillis = data.tokens.expiresAtMillis,
                            bangumiAccessToken = data.tokens.bangumiAccessToken,
                        ),
                    ),
                    refreshToken = data.tokens.refreshToken,
                )

                SendOtpResult.Success(
                    user = data.user.toSelfInfo(),
                )
            } catch (e: Exception) {
                if (e is ClientRequestException) {
                    when (e.response.status) {
                        HttpStatusCode.Conflict -> {
                            return@invoke SendOtpResult.EmailAlreadyExist
                        }

                        HttpStatusCode.BadRequest -> {
                            return@invoke SendOtpResult.InvalidOtp
                        }

                        HttpStatusCode.UnprocessableEntity -> {
                            return@invoke SendOtpResult.InvalidOtp
                        }
                    }
                }
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }

    data class SendEmailOtpInfo(
        val otpId: String,
        val hasExistingUser: Boolean?,
    )

    suspend fun sendEmailOtpForLogin(
        email: String,
    ): SendEmailOtpInfo = withContext(Dispatchers.Default) {
        authApi.invoke {
            try {
                val resp = this.sendEmailOtp(
                    AniSendEmailOtpRequest(
                        email = email,
                    ),
                ).body()

                SendEmailOtpInfo(
                    otpId = resp.otpId,
                    hasExistingUser = resp.hasExistingUser,
                )
            } catch (e: Exception) {
                if (e is ClientRequestException) {
                    if (e.response.status == HttpStatusCode.UnprocessableEntity) {
                        // 邮箱不正确
                        throw RepositoryRequestError(
                            localizedMessage = "邮箱格式不正确", // TODO: localize
                            cause = e,
                        )
                    }
                }
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }

    /**
     * 所有参数都是 `nullable`, 传入 `null` 则表示不修改对应的字段.
     */
    suspend fun updateProfile(
        nickname: String?
    ) = withContext(Dispatchers.Default) {
        // 所有参数为 null 表示什么也不更新
        if (nickname == null) {
            return@withContext
        }
        profileApi.invoke {
            try {
                this.updateProfile(AniUpdateProfileRequest(nickname)).body()
                    // 更新 profile 后刷新一下
                    .also { selfInfoRefresher.restart() }
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }

    suspend fun uploadAvatar(
        avatar: ByteArray,
    ) = withContext(Dispatchers.Default) {
        profileApi.invoke {
            try {
                // openapi generator 生成的 OctetByteArray 有问题，改成 OutgoingContent
                // 每次生成 API 都要把参数类型改成 OutgoingContent
                // todo: write patch in build scripts.
                this.uploadAvatar(
                    object : OutgoingContent.ByteArrayContent() {
                        override fun bytes(): ByteArray {
                            return avatar
                        }
                    },
                ).body()

                // 上传成功后刷新用户信息
                selfInfoRefresher.restart()
                UploadAvatarResult.SUCCESS
            } catch (e: ClientRequestException) {
                when (e.response.status) {
                    HttpStatusCode.PayloadTooLarge -> UploadAvatarResult.TOO_LARGE
                    HttpStatusCode.UnprocessableEntity -> UploadAvatarResult.INVALID_FORMAT
                    else -> throw RepositoryException.wrapOrThrowCancellation(e)
                }
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }

    suspend fun clearSelfInfo() {
        dataStore.updateData {
            null
        }
        sessionManager.clearSession()
    }

    suspend fun unbindBangumi() = withContext(Dispatchers.Default) {
        bangumiApi.invoke {
            try {
                val resp = this.unbind().body()

                sessionManager.setSession(
                    AccessTokenSession(
                        AccessTokenPair(
                            aniAccessToken = resp.tokens.accessToken,
                            expiresAtMillis = resp.tokens.expiresAtMillis,
                            bangumiAccessToken = resp.tokens.bangumiAccessToken,
                        ),
                    ),
                    refreshToken = resp.tokens.refreshToken,
                )

                // update local self info cache
                val self = resp.user.toSelfInfo()
                dataStore.updateData { self }
                selfInfoRefresher.restart()
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }
}

enum class UploadAvatarResult {
    SUCCESS, TOO_LARGE, INVALID_FORMAT
}

private fun AniAniSelfUser.toSelfInfo(): SelfInfo {
    return SelfInfo(
        id = Uuid.parse(id),
        nickname = nickname,
        email = email,
        hasPassword = hasPassword,
        avatarUrl = largeAvatar,
        bangumiUsername = bangumiUsername,
    )
}
