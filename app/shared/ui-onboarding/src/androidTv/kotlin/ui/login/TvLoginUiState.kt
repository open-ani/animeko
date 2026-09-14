/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.login

enum class TvLoginStep { Email, Otp }

data class TvLoginUiState(
    val email: String = "",
    val otp: String = "",
    val step: TvLoginStep = TvLoginStep.Email,
    val busy: Boolean = false,
    val error: String? = null,
    val resendRemainSec: Long = 0,
    val isExistingAccount: Boolean? = null,
)

sealed interface TvLoginIntent {
    data class ChangeEmail(val value: String) : TvLoginIntent
    data class ChangeOtp(val value: String) : TvLoginIntent
    data object SendOtp : TvLoginIntent
    data object SubmitOtp : TvLoginIntent
    data object ReenterEmail : TvLoginIntent
}
