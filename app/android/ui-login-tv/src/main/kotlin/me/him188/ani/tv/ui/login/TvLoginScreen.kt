/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.ui.login.EmailLoginViewModel
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.TvTextField
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor
import kotlin.time.Clock

/** 登录页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvLoginFocus : TvFocusKey {
    /** 当前步骤的输入框 (进入各步骤时的初始焦点). */
    Field,
}

/** TV 登录流程步骤 (UI 流转状态; 数据与操作在共享 [EmailLoginViewModel]). */
private enum class TvLoginStep { Email, Otp }

/**
 * TV 邮箱 OTP 登录页 (atv-architecture.md §7.7): 两步式 —— 邮箱 -> 验证码.
 *
 * 状态层复用手机 [EmailLoginViewModel] (D3): 邮箱状态 / 重发倒计时 (nextResendTime) /
 * 已有账号判定 / OTP 发送与校验. 步骤流转与错误展示为 TV 侧 UI 状态.
 */
@Composable
fun TvLoginScreen(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel<EmailLoginViewModel> { EmailLoginViewModel() }
    val uiState by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(TvLoginStep.Email) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // 重发倒计时读共享状态的 nextResendTime, 每秒刷新剩余秒数
    var resendRemainSec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(uiState.nextResendTime) {
        while (true) {
            resendRemainSec = (uiState.nextResendTime - Clock.System.now()).inWholeSeconds.coerceAtLeast(0)
            delay(1000)
        }
    }

    val focus = rememberTvFocusScope()
    focus.Resolver()
    LaunchedEffect(step) { focus.request(TvLoginFocus.Field) }

    fun sendOtp() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                viewModel.sendEmailOtp()
                step = TvLoginStep.Otp
            } catch (e: RepositoryRateLimitedException) {
                error = "发送太频繁, 请稍后再试"
            } catch (e: Exception) {
                error = "发送失败, 请检查邮箱地址与网络"
            } finally {
                busy = false
            }
        }
    }

    fun submitOtp(otp: String) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                when (viewModel.submitEmailOtp(otp)) {
                    is UserRepository.SendOtpResult.Success -> onLoggedIn()
                    UserRepository.SendOtpResult.InvalidOtp -> error = "验证码不正确"
                    UserRepository.SendOtpResult.EmailAlreadyExist -> error = "邮箱已被占用"
                }
            } catch (e: Exception) {
                error = "登录失败, 请重试"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .tvFocusNavSignal(focus)
            .padding(horizontal = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            TvLoginStep.Email -> {
                Text("登录 Animeko", style = MaterialTheme.typography.displaySmall, color = tvHeroContentColor())
                Text(
                    "输入邮箱, 我们将发送 6 位验证码",
                    Modifier.padding(top = 8.dp, bottom = 20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tvHeroSecondaryContentColor(),
                )
                TvTextField(
                    value = uiState.email,
                    onValueChange = viewModel::setEmail,
                    modifier = Modifier.fillMaxWidth(0.55f).tvFocusAnchor(focus, TvLoginFocus.Field),
                    placeholder = "邮箱地址",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { sendOtp() }),
                )
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvHeroButton(
                        text = when {
                            busy -> "发送中…"
                            resendRemainSec > 0 -> "重新发送 (${resendRemainSec}s)"
                            else -> "发送验证码"
                        },
                        icon = Icons.AutoMirrored.Rounded.Send,
                        filled = true,
                        onClick = { if (resendRemainSec <= 0) sendOtp() },
                        onFocused = {},
                    )
                }
            }

            TvLoginStep.Otp -> {
                var otp by rememberSaveable { mutableStateOf("") }
                Text("输入验证码", style = MaterialTheme.typography.displaySmall, color = tvHeroContentColor())
                Text(
                    buildString {
                        append("已发送至 ${uiState.email}")
                        when (uiState.isExistingAccount) {
                            true -> append(" · 登录已有账号")
                            false -> append(" · 将注册新账号")
                            null -> {}
                        }
                    },
                    Modifier.padding(top = 8.dp, bottom = 20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tvHeroSecondaryContentColor(),
                )
                TvTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6) otp = it },
                    modifier = Modifier.fillMaxWidth(0.35f).tvFocusAnchor(focus, TvLoginFocus.Field),
                    placeholder = "6 位验证码",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitOtp(otp) }),
                )
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvHeroButton(
                        text = if (busy) "验证中…" else "登录",
                        icon = Icons.Rounded.Done,
                        filled = true,
                        onClick = { submitOtp(otp) },
                        onFocused = {},
                    )
                    TvHeroButton(
                        text = "重新输入邮箱",
                        icon = Icons.Rounded.Undo,
                        filled = false,
                        onClick = {
                            step = TvLoginStep.Email
                            error = null
                        },
                        onFocused = {},
                    )
                }
            }
        }

        error?.let {
            Text(
                it,
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
