package me.him188.ani.app.data.repository.user

import androidx.datastore.core.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.UserAniApi
import me.him188.ani.client.models.AniAniSelfUser
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

class UserRepository(
    private val dataStore: DataStore<SelfInfo?>,
    private val sessionStateProvider: SessionStateProvider,
    private val userApi: ApiInvoker<UserAniApi>,
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

    private fun AniAniSelfUser.toSelfInfo(): SelfInfo {
        return SelfInfo(
            id = Uuid.parse(id),
            nickname = nickname,
            email = email,
            hasPassword = hasPassword,
            avatarUrl = largeAvatar,
        )
    }

    suspend fun clearSelfInfo() {
        dataStore.updateData {
            null
        }
    }
}
