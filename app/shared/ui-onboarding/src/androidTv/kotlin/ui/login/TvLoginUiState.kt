/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

enum class TvLoginStep { Email, Otp }

data class TvLoginUiState(
    val email: String = "",
    val otp: String = "",
    val step: TvLoginStep = TvLoginStep.Email,
    val busy: Boolean = false,
    val error: String? = null,
    val resendRemainSec: Long = 0,
    val isExistingAccount: Boolean? = null,
    val qr: TvQrLoginUiState = TvQrLoginUiState.Loading,
)

/** 登录页二维码区域的状态. 二维码过期时自动换新, 只有 [Invalid] 需要用户手动刷新. */
sealed interface TvQrLoginUiState {
    data object Loading : TvQrLoginUiState
    data class Waiting(val qrContent: String, val remainSec: Long) : TvQrLoginUiState

    /** 已扫码, 等待用户在手机上确认. */
    data class Scanned(val qrContent: String, val nickname: String?) : TvQrLoginUiState
    data object Success : TvQrLoginUiState
    data class Invalid(val reason: Reason) : TvQrLoginUiState {
        enum class Reason { Rejected, Failed }
    }
}

sealed interface TvLoginIntent {
    data class ChangeEmail(val value: String) : TvLoginIntent
    data class ChangeOtp(val value: String) : TvLoginIntent
    data object SendOtp : TvLoginIntent
    data object SubmitOtp : TvLoginIntent
    data object ReenterEmail : TvLoginIntent
    data object RefreshQr : TvLoginIntent
}
