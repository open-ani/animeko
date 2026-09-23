/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.session.auth.QrLoginProgress
import me.him188.ani.app.ui.login.EmailLoginViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin
import kotlin.time.Clock

/** Reuses the shared OTP session; request ownership and TV step transitions live here. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvLoginViewModel(
    koin: Koin,
    private val clock: Clock = Clock.System,
    deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
) : EmailLoginViewModel(koin) {
    private val qrLoginPoller = createQrLoginPoller(deviceName)
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

    // 只在登录页可见 (uiState 被收集) 时轮询; 每次 RefreshQr 重新开始.
    private val qrRefreshes = MutableStateFlow(0)
    private val qr = qrRefreshes.flatMapLatest { qrLoginPoller.run() }
        .onEach { if (it == QrLoginProgress.Approved) navigation.emit(TvNavigationEvent.LoggedIn) }
        .map { it.toUiState() }

    val uiState = combine(state, fields, ticker, qr) { login, fields, now, qr ->
        fields.copy(
            email = login.email,
            isExistingAccount = login.isExistingAccount,
            resendRemainSec = (login.nextResendTime - now).inWholeSeconds.coerceAtLeast(0),
            qr = qr,
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
            TvLoginIntent.RefreshQr -> qrRefreshes.update { it + 1 }
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

private fun QrLoginProgress.toUiState(): TvQrLoginUiState = when (this) {
    QrLoginProgress.Loading -> TvQrLoginUiState.Loading
    is QrLoginProgress.Waiting -> TvQrLoginUiState.Waiting(qrContent, remaining.inWholeSeconds)
    is QrLoginProgress.Scanned -> TvQrLoginUiState.Scanned(qrContent, nickname)
    QrLoginProgress.Approved -> TvQrLoginUiState.Success
    QrLoginProgress.Rejected -> TvQrLoginUiState.Invalid(TvQrLoginUiState.Invalid.Reason.Rejected)
    is QrLoginProgress.Failed -> TvQrLoginUiState.Invalid(TvQrLoginUiState.Invalid.Reason.Failed)
}
