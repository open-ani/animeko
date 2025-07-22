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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.session.AccessTokenPair
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
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

class UserRepository(
    private val dataStore: DataStore<SelfInfo?>,
    private val sessionStateProvider: SessionStateProvider,
    private val userApi: ApiInvoker<UserAniApi>,
    private val authApi: ApiInvoker<UserAuthenticationAniApi>,
    private val profileApi: ApiInvoker<UserProfileAniApi>,
    private val sessionManager: SessionManager,
    private val flowContext: CoroutineContext = Dispatchers.Default,
) {
    /**
     * 先读缓存, 然后网络. 注意, 重复 collect 会导致多次网络请求.
     */
    fun selfInfoFlow(): Flow<SelfInfo?> = flow {
        emit(dataStore.data.first())

        if (sessionStateProvider.stateFlow.first() is SessionState.Valid) {
            // Update self info when session is valid
            val self = try {
                userApi.invoke {
                    getUser().body()
                }
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }

            val newInfo = self.toSelfInfo()
            dataStore.updateData { newInfo }
            emit(newInfo)
        }
    }.flowOn(flowContext)

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

    suspend fun sendEmailOtpForLogin(
        email: String,
    ) = withContext(Dispatchers.Default) {
        authApi.invoke {
            try {
                this.sendEmailOtp(
                    AniSendEmailOtpRequest(
                        email = email,
                    ),
                ).body().otpId
            } catch (e: Exception) {
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
                this.updateProfile(
                    AniUpdateProfileRequest(nickname),
                ).body()
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
                this.uploadAvatar(
                    me.him188.ani.client.infrastructure.OctetByteArray(avatar),
                ).body()

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
