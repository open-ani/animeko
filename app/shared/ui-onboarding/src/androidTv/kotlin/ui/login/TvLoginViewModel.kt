/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.login

import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.ui.login.EmailLoginViewModel
import me.him188.ani.leanback.ui.foundation.TvNavigationEvent
import me.him188.ani.leanback.ui.foundation.TvNavigationEvents
import org.koin.core.Koin

/** Reuses the shared OTP session; request ownership and TV step transitions live here. */
class TvLoginViewModel(koin: Koin, private val clock: Clock = Clock.System) : EmailLoginViewModel(koin) {
    private val fields = MutableStateFlow(TvLoginUiState())
    private val requestMutex = Mutex()
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    private val ticker = flow {
        while (true) {
            emit(clock.now())
            delay(1_000)
        }
    }
    val uiState = combine(state, fields, ticker) { login, fields, now ->
        fields.copy(
            email = login.email,
            isExistingAccount = login.isExistingAccount,
            resendRemainSec = (login.nextResendTime - now).inWholeSeconds.coerceAtLeast(0),
        )
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvLoginUiState())

    fun onIntent(intent: TvLoginIntent) {
        when (intent) {
            is TvLoginIntent.ChangeEmail -> if (!fields.value.busy) setEmail(intent.value)
            is TvLoginIntent.ChangeOtp -> if (!fields.value.busy && intent.value.length <= 6) {
                fields.update { it.copy(otp = intent.value) }
            }
            TvLoginIntent.ReenterEmail -> if (!fields.value.busy) {
                fields.update { it.copy(step = TvLoginStep.Email, otp = "", error = null) }
            }
            TvLoginIntent.SendOtp -> request(sending = true) {
                sendEmailOtp()
                fields.update { it.copy(step = TvLoginStep.Otp, otp = "") }
            }
            TvLoginIntent.SubmitOtp -> if (fields.value.step == TvLoginStep.Otp) {
                request(sending = false) {
                    when (submitEmailOtp(fields.value.otp)) {
                        is UserRepository.SendOtpResult.Success -> navigation.emit(TvNavigationEvent.LoggedIn)
                        UserRepository.SendOtpResult.InvalidOtp -> fields.update { it.copy(error = "验证码不正确") }
                        UserRepository.SendOtpResult.EmailAlreadyExist -> fields.update { it.copy(error = "邮箱已被占用") }
                    }
                }
            }
        }
    }

    private fun request(sending: Boolean, action: suspend () -> Unit) {
        // Acquire before launching: repeated remote/IME events cannot start duplicate requests.
        if (!requestMutex.tryLock()) return
        fields.update { it.copy(busy = true, error = null) }
        backgroundScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RepositoryRateLimitedException) {
                fields.update { it.copy(error = "发送太频繁, 请稍后再试") }
            } catch (e: Exception) {
                fields.update {
                    it.copy(error = if (sending) "发送失败, 请检查邮箱地址与网络" else "登录失败, 请重试")
                }
            } finally {
                fields.update { it.copy(busy = false) }
                requestMutex.unlock()
            }
        }
    }
}
