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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.tv_login_or
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
import org.jetbrains.compose.resources.stringResource

/** 登录页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvLoginFocus : TvFocusKey {
    /** 当前步骤的输入框 (进入各步骤时的初始焦点). */
    Field,
    Submit,

    /** 二维码失效时出现的刷新按钮. */
    RefreshQr,
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

    // 空间搜索从左侧按钮向右会落到更宽的输入框上, 所以刷新按钮出现时, 给按钮行最右的按钮显式指定向右的去向.
    val toQrRefresh = if (uiState.qr is TvQrLoginUiState.Invalid) {
        Modifier.tvFocusHotkey(focus, Key.DirectionRight to TvLoginFocus.RefreshQr)
    } else Modifier

    TvLoginPageLayout(
        focus = focus,
        modifier = modifier,
        qrPanel = {
            TvQrLoginPanel(
                uiState.qr,
                onRefresh = {
                    // 刷新按钮随即消失, 先把焦点交还给左侧, 否则焦点丢失后遥控器无法继续操作
                    focus.request(TvLoginFocus.Submit)
                    onIntent(TvLoginIntent.RefreshQr)
                },
                refreshButtonModifier = Modifier.tvFocusAnchor(focus, TvLoginFocus.RefreshQr)
                    .tvFocusHotkey(focus, Key.DirectionLeft to TvLoginFocus.Submit),
            )
        },
    ) {
        when (step) {
            TvLoginStep.Email -> TvLoginStepSection(
                title = "登录 Animeko",
                subtitle = "输入邮箱, 我们将发送 6 位验证码",
                field = {
                    TvTextField(
                        value = uiState.email,
                        onValueChange = { onIntent(TvLoginIntent.ChangeEmail(it)) },
                        modifier = Modifier.fillMaxWidth(0.85f)
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
                        modifier = Modifier.tvFocusAnchor(focus, TvLoginFocus.Submit).then(toQrRefresh),
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
                            modifier = Modifier.fillMaxWidth(0.55f)
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
                            modifier = toQrRefresh,
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
 * 登录页骨架: 左侧是邮箱登录的步骤区块 ([content], 垂直居中列, slot 模式对齐手机 EmailLoginScreenLayout),
 * 右侧常驻 [qrPanel]. 两种登录方式同时可用, 扫码不需要遥控器操作, 所以焦点默认留在左侧.
 */
@Composable
private fun TvLoginPageLayout(
    focus: TvFocusScope,
    modifier: Modifier = Modifier,
    qrPanel: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxSize()
            .tvFocusNavSignal(focus)
            .padding(horizontal = TvLoginDefaults.HorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(TvLoginDefaults.ColumnSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, content = content)
        TvLoginMethodDivider(Modifier.fillMaxHeight(0.6f))
        Box(Modifier.width(TvLoginDefaults.QrPanelWidth), contentAlignment = Alignment.Center) { qrPanel() }
    }
}

/** 两种登录方式之间的竖向分隔: 线 · "或" · 线. */
@Composable
private fun TvLoginMethodDivider(modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VerticalDivider(Modifier.weight(1f))
        Text(
            stringResource(Lang.tv_login_or),
            style = MaterialTheme.typography.bodyMedium,
            color = tvHeroSecondaryContentColor(),
        )
        VerticalDivider(Modifier.weight(1f))
    }
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

    /** 邮箱区 / 分隔 / 扫码区之间的间距. */
    val ColumnSpacing = 40.dp
    val QrPanelWidth = 300.dp
}
