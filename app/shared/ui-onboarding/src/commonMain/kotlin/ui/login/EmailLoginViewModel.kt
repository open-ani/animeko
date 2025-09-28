/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.login

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

@Stable
class EmailLoginViewModel : AbstractViewModel(), KoinComponent {
    private val userRepository: UserRepository by inject()
    private val sessionManager: SessionManager by inject()

    private val stateFields = MutableStateFlow(EmailLoginUiState.Initial)

    val state = combine(
        stateFields,
        sessionManager.stateProvider.stateFlow,
        userRepository.selfInfoFlow,
    ) { state, sessionState, selfInfo ->
        state.copy(
            mode = when {
                sessionState is SessionState.Invalid && sessionState.reason == InvalidSessionReason.NO_TOKEN -> EmailLoginUiState.Mode.LOGIN
                sessionState is SessionState.Valid && selfInfo?.email.isNullOrEmpty() -> EmailLoginUiState.Mode.BIND
                else -> EmailLoginUiState.Mode.REBIND
            },
        )
    }.stateInBackground(EmailLoginUiState.Initial)

    private var otpId = ""

    private inline fun updateState(block: EmailLoginUiState.() -> EmailLoginUiState) {
        stateFields.value = stateFields.value.block()
    }

    fun setEmail(email: String) {
        updateState { copy(email = email) }
    }

    suspend fun sendEmailOtp() {
        if (Clock.System.now() < stateFields.value.nextResendTime) {
            // fail fast
            throw RepositoryRateLimitedException()
        }
        val info = withContext(Dispatchers.Default) {
            userRepository.sendEmailOtpForLogin(stateFields.value.email)
        }
        otpId = info.otpId
        updateState {
            copy(
                nextResendTime = Clock.System.now() + 30.seconds,
                isExistingAccount = info.hasExistingUser,
            )
        }
    }

    suspend fun submitEmailOtp(otp: String) = withContext(Dispatchers.Default) {
        userRepository.registerOrLoginByEmailOtp(otpId, otp)
    }

    suspend fun bindOrRebind(otp: String) = withContext(Dispatchers.Default) {
        userRepository.bindOrReBindEmail(otpId, otp)
    }
}

@Immutable
data class EmailLoginUiState(
    val email: String,
    val nextResendTime: Instant,
    val mode: Mode,
    // null means unknown; true -> existing user; false -> new registration
    val isExistingAccount: Boolean? = null,
) {
    companion object {
        val Initial = EmailLoginUiState(
            "",
            Instant.DISTANT_PAST,
            Mode.LOGIN,
            isExistingAccount = null,
        )
    }

    enum class Mode { LOGIN, BIND, REBIND }
}
