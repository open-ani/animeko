package me.him188.ani.app.ui.user

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

@Immutable
data class SelfInfoUiState(
    val selfInfo: SelfInfo?,
    val isLoading: Boolean,
    /**
     * `null` means loading
     */
    val isSessionValid: Boolean?,
)

@TestOnly
val TestSelfInfoUiState
    get() = SelfInfoUiState(
        SelfInfo(
            id = Uuid.random(),
            nickname = "TestUser",
            email = "test@animeko.org",
            hasPassword = false,
            avatarUrl = null,
        ),
        isLoading = false,
        isSessionValid = true,
    )

class SelfInfoStateProducer(
    flowContext: CoroutineContext = Dispatchers.Default,
    koin: Koin = GlobalKoin,
) {
    private val sessionStateProvider: SessionStateProvider by koin.inject()
    private val userRepository: UserRepository by koin.inject()

    /**
     * 如果重新 collect 这个 flow, 会导致多次网络请求.
     */
    val flow = combine(sessionStateProvider.stateFlow, userRepository.selfInfoFlow()) { sessionState, selfInfo ->
        val isSessionValid = sessionState is SessionState.Valid
        SelfInfoUiState(
            selfInfo = selfInfo,
            isLoading = false,
            isSessionValid = isSessionValid,
        )
    }.stateIn(
        CoroutineScope(flowContext),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SelfInfoUiState(
            selfInfo = null,
            isLoading = true,
            isSessionValid = null,
        ),
    )
}
