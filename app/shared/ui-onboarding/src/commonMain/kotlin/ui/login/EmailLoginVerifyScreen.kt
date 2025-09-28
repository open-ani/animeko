/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.him188.ani.app.data.repository.user.UserRepository.SendOtpResult
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Duration.Companion.seconds

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
                    SendOtpResult.EmailAlreadyExist -> toaster.show("该邮箱已被使用")
                    SendOtpResult.InvalidOtp -> toaster.show("验证码无效或已过期，请重新发送")
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
    title: @Composable () -> Unit = { Text("登录") },
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
            title = { Text("输入验证码") },
            description = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("请检查邮箱 $email")
                    if (isExistingAccount != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (isExistingAccount) "正在登录现有账号" else "正在注册新账号",
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
                Text("验证码")
            },
            isError = code.any { !it.isDigit() },
            placeholder = {
                Text("6 位数字")
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
                        Icon(Icons.Outlined.Close, "清空")
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
                Text("重新发送验证码")
            } else {
                Text("${timeLeft.inWholeSeconds} 秒后可重新发送")
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
