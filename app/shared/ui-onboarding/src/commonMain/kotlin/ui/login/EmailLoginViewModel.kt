package me.him188.ani.app.ui.login

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

@Stable
class EmailLoginViewModel : AbstractViewModel(), KoinComponent {
    private val userRepository: UserRepository by inject()

    var state by mutableStateOf(EmailLoginUiState.Initial)
        private set

    private var otpId = ""

    private inline fun updateState(block: EmailLoginUiState.() -> EmailLoginUiState) {
        state = state.block()
    }

    fun setEmail(email: String) {
        updateState { copy(email = email) }
    }

    suspend fun sendEmailOtp() {
        if (Clock.System.now() < state.nextResendTime) {
            // fail fast
            throw RepositoryRateLimitedException()
        }
        otpId = withContext(Dispatchers.Default) {
            userRepository.sendEmailOtpForLogin(state.email)
        }
        updateState {
            copy(
                nextResendTime = Clock.System.now() + 30.seconds,
            )
        }
    }

    suspend fun submitEmailOtp(otp: String) = withContext(Dispatchers.Default) {
        userRepository.registerOrLoginByEmailOtp(otpId, otp)
    }
}

@Immutable
data class EmailLoginUiState(
    val email: String,
    val nextResendTime: Instant,
) {
    companion object {
        val Initial = EmailLoginUiState("", Instant.DISTANT_PAST)
    }
}
