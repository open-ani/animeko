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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor

/** 登录页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvLoginFocus : TvFocusKey {
    /** 当前步骤的输入框 (进入各步骤时的初始焦点). */
    Field,
}

/**
 * TV 邮箱 OTP 登录页 (atv-architecture.md §7.7): 两步式 —— 邮箱 -> 验证码.
 */
@Composable
fun TvLoginScreen(
    viewModel: TvLoginViewModel,
    onLoggedIn: (SelfInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step by viewModel.step.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()

    // 统一焦点框架: 每个步骤 (邮箱/验证码) 进入时初始焦点落输入框
    val focus = rememberTvFocusScope()
    focus.Resolver()
    LaunchedEffect(step) {
        (step as? TvLoginViewModel.Step.Done)?.let { onLoggedIn(it.user) }
        if (step !is TvLoginViewModel.Step.Done) focus.request(TvLoginFocus.Field)
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (val s = step) {
            is TvLoginViewModel.Step.Email -> {
                var email by rememberSaveable { mutableStateOf("") }
                Text("登录 Animeko", style = MaterialTheme.typography.displaySmall)
                Text(
                    "输入邮箱, 我们将发送 6 位验证码",
                    Modifier.padding(top = 8.dp, bottom = 20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                me.him188.ani.tv.ui.foundation.widgets.TvTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(0.55f).tvFocusAnchor(focus, TvLoginFocus.Field),
                    placeholder = "邮箱地址",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendOtp(email) }),
                )
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.sendOtp(email) }) {
                        Text(if (busy) "发送中…" else "发送验证码")
                    }
                }
            }

            is TvLoginViewModel.Step.Otp -> {
                var otp by rememberSaveable { mutableStateOf("") }
                Text("输入验证码", style = MaterialTheme.typography.displaySmall)
                Text(
                    buildString {
                        append("已发送至 ${s.email}")
                        when (s.isExistingAccount) {
                            true -> append(" · 登录已有账号")
                            false -> append(" · 将注册新账号")
                            null -> {}
                        }
                    },
                    Modifier.padding(top = 8.dp, bottom = 20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                me.him188.ani.tv.ui.foundation.widgets.TvTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6) otp = it },
                    modifier = Modifier.fillMaxWidth(0.35f).tvFocusAnchor(focus, TvLoginFocus.Field),
                    placeholder = "6 位验证码",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submitOtp(otp) }),
                )
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.submitOtp(otp) }) {
                        Text(if (busy) "验证中…" else "登录")
                    }
                    Button(onClick = { viewModel.backToEmail() }) {
                        Text("重新输入邮箱")
                    }
                }
            }

            is TvLoginViewModel.Step.Done -> {
                Text(
                    "登录成功",
                    Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.displaySmall,
                )
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
