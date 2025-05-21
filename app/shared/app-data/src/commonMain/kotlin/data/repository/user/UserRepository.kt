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
import me.him188.ani.client.models.AniAniSelfUser
import me.him188.ani.client.models.AniRegisterOrLoginByEmailOtpRequest
import me.him188.ani.client.models.AniSendEmailOtpRequest
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

class UserRepository(
    private val dataStore: DataStore<SelfInfo?>,
    private val sessionStateProvider: SessionStateProvider,
    private val userApi: ApiInvoker<UserAniApi>,
    private val authApi: ApiInvoker<UserAuthenticationAniApi>,
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
        authApi.invoke {
            try {
                val data = this.registerOrLoginByEmailOtp(
                    AniRegisterOrLoginByEmailOtpRequest(
                        otpId = otpId,
                        otpValue = otp,
                    ),
                ).body()

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

    suspend fun clearSelfInfo() {
        dataStore.updateData {
            null
        }
        sessionManager.clearSession()
    }
}

private fun AniAniSelfUser.toSelfInfo(): SelfInfo {
    return SelfInfo(
        id = Uuid.parse(id),
        nickname = nickname,
        email = email,
        hasPassword = hasPassword,
        avatarUrl = largeAvatar,
    )
}
