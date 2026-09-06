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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.TvTextField
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor

/** 登录页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvLoginFocus : TvFocusKey {
    /** 当前步骤的输入框 (进入各步骤时的初始焦点). */
    Field,
    Submit,
}

/**
 * TV 邮箱 OTP 登录页。只渲染状态、发送 Intent；请求、校验、倒计时与步骤切换由 ViewModel 决定。
 */
@Composable
fun TvLoginScreen(
    uiState: TvLoginUiState,
    onIntent: (TvLoginIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = uiState.step
    val busy = uiState.busy
    val error = uiState.error
    val resendRemainSec = uiState.resendRemainSec
    val focus = rememberTvFocusScope()
    focus.Resolver()
    LaunchedEffect(step) { focus.request(TvLoginFocus.Field) }

    TvLoginPageLayout(focus = focus, modifier = modifier) {
        when (step) {
            TvLoginStep.Email -> TvLoginStepSection(
                title = "登录 Animeko",
                subtitle = "输入邮箱, 我们将发送 6 位验证码",
                field = {
                    TvTextField(
                        value = uiState.email,
                        onValueChange = { onIntent(TvLoginIntent.ChangeEmail(it)) },
                        modifier = Modifier.fillMaxWidth(0.55f)
                            .tvFocusAnchor(focus, TvLoginFocus.Field)
                            .tvFocusHotkey(focus, Key.DirectionDown to TvLoginFocus.Submit),
                        placeholder = "邮箱地址",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { onIntent(TvLoginIntent.SendOtp) }),
                    )
                },
                buttons = {
                    TvHeroButton(
                        text = when {
                            busy -> "发送中…"
                            resendRemainSec > 0 -> "重新发送 (${resendRemainSec}s)"
                            else -> "发送验证码"
                        },
                        icon = Icons.AutoMirrored.Rounded.Send,
                        filled = true,
                        onClick = { onIntent(TvLoginIntent.SendOtp) },
                        onFocused = {},
                        modifier = Modifier.tvFocusAnchor(focus, TvLoginFocus.Submit),
                    )
                },
            )

            TvLoginStep.Otp -> {
                val otp = uiState.otp
                TvLoginStepSection(
                    title = "输入验证码",
                    subtitle = buildString {
                        append("已发送至 ${uiState.email}")
                        when (uiState.isExistingAccount) {
                            true -> append(" · 登录已有账号")
                            false -> append(" · 将注册新账号")
                            null -> {}
                        }
                    },
                    field = {
                        TvTextField(
                            value = otp,
                            onValueChange = { onIntent(TvLoginIntent.ChangeOtp(it)) },
                            modifier = Modifier.fillMaxWidth(0.35f)
                                .tvFocusAnchor(focus, TvLoginFocus.Field)
                                .tvFocusHotkey(focus, Key.DirectionDown to TvLoginFocus.Submit),
                            placeholder = "6 位验证码",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onIntent(TvLoginIntent.SubmitOtp) }),
                        )
                    },
                    buttons = {
                        TvHeroButton(
                            text = if (busy) "验证中…" else "登录",
                            icon = Icons.Rounded.Done,
                            filled = true,
                            onClick = { onIntent(TvLoginIntent.SubmitOtp) },
                            onFocused = {},
                            modifier = Modifier.tvFocusAnchor(focus, TvLoginFocus.Submit),
                        )
                        TvHeroButton(
                            text = "重新输入邮箱",
                            icon = Icons.Rounded.Undo,
                            filled = false,
                            onClick = {
                                onIntent(TvLoginIntent.ReenterEmail)
                            },
                            onFocused = {},
                        )
                    },
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

/**
 * 登录页骨架 (对齐手机 EmailLoginScreenLayout 的 slot 模式):
 * 统一焦点接线 + 垂直居中列, [content] 填充当前步骤区块与错误提示.
 */
@Composable
private fun TvLoginPageLayout(
    focus: TvFocusScope,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .tvFocusNavSignal(focus)
            .padding(horizontal = TvLoginDefaults.HorizontalPadding),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * 单个登录步骤的通用区块: 大标题 + 说明行 + 输入框 + 按钮行.
 * 直接向父列平铺子项 (不包容器), 保持与手写布局一致的树结构.
 */
@Composable
private fun TvLoginStepSection(
    title: String,
    subtitle: String,
    field: @Composable () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    Text(title, style = MaterialTheme.typography.displaySmall, color = tvHeroContentColor())
    Text(
        subtitle,
        Modifier.padding(top = 8.dp, bottom = 20.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = tvHeroSecondaryContentColor(),
    )
    field()
    Row(
        Modifier.padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = buttons,
    )
}

/** 登录页默认值/调参. */
private object TvLoginDefaults {
    /** 内容水平留白 (= overscan 安全边距 48). */
    val HorizontalPadding = 48.dp
}
