/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import me.him188.ani.app.data.repository.user.UserRepository.SendOtpResult
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import org.jetbrains.compose.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun EmailLoginVerifyScreen(
    onSuccess: () -> Unit,
    onBangumiLoginClick: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EmailLoginViewModel = viewModel<EmailLoginViewModel> { EmailLoginViewModel() },
) {
    val state by vm.state.collectAsStateWithLifecycle(EmailLoginUiState.Initial)
    val asyncHandler = rememberAsyncHandler()
    val toaster = LocalToaster.current
    val emailAlreadyUsedText = stringResource(Lang.login_email_already_used)
    val invalidOtpText = stringResource(Lang.login_invalid_otp)


    EmailLoginVerifyScreenImpl(
        email = state.email,
        nextResendTime = state.nextResendTime,
        isExistingAccount = state.isExistingAccount,
        onCodeSubmit = { otp ->
            asyncHandler.launch {
                val result = if (state.mode == EmailLoginUiState.Mode.LOGIN) {
                    vm.submitEmailOtp(otp)
                } else {
                    vm.bindOrRebind(otp)
                }

                when (result) {
                    SendOtpResult.EmailAlreadyExist -> toaster.show(emailAlreadyUsedText)
                    SendOtpResult.InvalidOtp -> toaster.show(invalidOtpText)
                    is SendOtpResult.Success -> onSuccess()
                }
            }
        },
        onResendClick = {
            asyncHandler.launch {
                vm.sendEmailOtp()
            }
        },
        onBangumiLoginClick,
        onNavigateSettings,
        onNavigateBack,
        modifier = modifier,
        enabled = !asyncHandler.isWorking,
        showThirdPartyLogin = state.mode == EmailLoginUiState.Mode.LOGIN,
        title = { EmailPageTitle(state.mode, state.isExistingAccount) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmailLoginVerifyScreenImpl(
    email: String,
    nextResendTime: Instant,
    isExistingAccount: Boolean?,
    onCodeSubmit: (string: String) -> Unit,
    onResendClick: () -> Unit,
    onBangumiLoginClick: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = { Text(stringResource(Lang.login_sign_in)) },
    enabled: Boolean = true,
    showThirdPartyLogin: Boolean = true,
) {
    EmailLoginScreenLayout(
        onBangumiLoginClick,
        onNavigateSettings,
        onNavigateBack,
        modifier,
        title = title,
        showThirdPartyLogin = showThirdPartyLogin,
    ) {
        CenteredSectionHeader(
            title = { Text(stringResource(Lang.login_verify_title)) },
            description = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Lang.login_verify_check_email, email))
                    if (isExistingAccount != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (isExistingAccount) {
                                stringResource(Lang.login_verify_signing_in_existing)
                            } else {
                                stringResource(Lang.login_verify_signing_up_new)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        var code by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            code,
            {
                code = it.trim()
                if (code.length == 6) {
                    onCodeSubmit(code)
                }
            },
            Modifier.fillMaxWidth(),
            label = {
                Text(stringResource(Lang.login_verification_code))
            },
            isError = code.any { !it.isDigit() },
            placeholder = {
                Text(stringResource(Lang.login_six_digit_number))
            },
            keyboardOptions = KeyboardOptions.Default.copy(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions {
                if (code.length == 6) {
                    onCodeSubmit(code)
                }
            },
            singleLine = true,
            trailingIcon = if (code.isNotEmpty()) {
                {
                    IconButton({ code = "" }) {
                        Icon(Icons.Outlined.Close, stringResource(Lang.login_clear))
                    }
                }
            } else null,
            enabled = enabled,
        )

        Spacer(Modifier.height(8.dp)) // actually 16, 按钮有 8dp topPadding

        // 计算距离下次发送的时间
        val currentTime by produceState(Clock.System.now()) {
            while (true) {
                value = Clock.System.now()
                delay(1000)
            }
        }
        val timeLeft = nextResendTime - currentTime
        val canResend = timeLeft < 0.seconds

        TextButton(
            onResendClick,
            Modifier.align(Alignment.CenterHorizontally),
            enabled = enabled && canResend,
        ) {
            if (canResend) {
                Text(stringResource(Lang.login_resend_otp))
            } else {
                Text(stringResource(Lang.login_resend_after_seconds, timeLeft.inWholeSeconds))
            }
        }
    }
}

@Composable
@Preview
private fun PreviewEmailLoginVerifyScreen() = ProvideCompositionLocalsForPreview {
    EmailLoginVerifyScreenImpl(
        "test@openani.org",
        nextResendTime = Clock.System.now() + 16.seconds,
        isExistingAccount = true,
        onCodeSubmit = {},
        onResendClick = {},
        {},
        {},
        {},
    )
}
