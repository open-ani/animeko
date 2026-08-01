/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * TV 邮箱 OTP 登录薄 VM (atv-architecture.md §7.7, 拷手机 EmailLoginViewModel 语义):
 * 邮箱 -> sendEmailOtpForLogin -> 6 位验证码 -> registerOrLoginByEmailOtp.
 * Bangumi OAuth / 资料编辑已裁剪 (§1.2), 仅邮箱登录.
 */
@Stable
class TvLoginViewModel : AbstractViewModel(), KoinComponent {
    private val userRepository: UserRepository by inject()

    /** 登录态 (抽屉头像与登录页共用) */
    val selfInfo: StateFlow<SelfInfo?> = userRepository.selfInfoFlow
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed class Step {
        data object Email : Step()
        data class Otp(val email: String, val isExistingAccount: Boolean?) : Step()
        data class Done(val user: SelfInfo) : Step()
    }

    private val _step = MutableStateFlow<Step>(Step.Email)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var otpId = ""

    fun sendOtp(email: String) {
        if (_busy.value) return
        backgroundScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val info = userRepository.sendEmailOtpForLogin(email)
                otpId = info.otpId
                _step.value = Step.Otp(email, info.hasExistingUser)
            } catch (e: RepositoryRateLimitedException) {
                _error.value = "发送太频繁, 请稍后再试"
            } catch (e: Exception) {
                logger.warn(e) { "sendEmailOtpForLogin failed" }
                _error.value = "发送失败: ${RepositoryException.wrapOrThrowCancellation(e).message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun submitOtp(otp: String) {
        if (_busy.value) return
        backgroundScope.launch {
            _busy.value = true
            _error.value = null
            try {
                when (val result = userRepository.registerOrLoginByEmailOtp(otpId, otp)) {
                    is UserRepository.SendOtpResult.Success -> _step.value = Step.Done(result.user)
                    UserRepository.SendOtpResult.InvalidOtp -> _error.value = "验证码不正确"
                    UserRepository.SendOtpResult.EmailAlreadyExist -> _error.value = "邮箱已被占用"
                }
            } catch (e: Exception) {
                logger.warn(e) { "registerOrLoginByEmailOtp failed" }
                _error.value = "登录失败: ${RepositoryException.wrapOrThrowCancellation(e).message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun backToEmail() {
        _step.value = Step.Email
        _error.value = null
    }

    private companion object {
        private val logger = logger<TvLoginViewModel>()
    }
}
