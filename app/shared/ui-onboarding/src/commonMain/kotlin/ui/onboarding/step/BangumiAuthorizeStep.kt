/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.step

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.AniMotionScheme
import me.him188.ani.app.ui.foundation.animation.AnimatedVisibilityMotionScheme
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.icons.BangumiNextIconColor
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.onboarding.HeroIcon
import me.him188.ani.app.ui.onboarding.WizardLayoutParams
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem

/**
 * @param onSkip if not null, a "skip" button will be shown, and this callback will be invoked when the button is clicked.
 */
// this page will be reused.
@Composable
fun BangumiAuthorizeStep(
    authorizeState: AuthState,
    showTokenAuthorizePage: Boolean,
    contactActions: @Composable () -> Unit,
    onSetShowTokenAuthorizePage: (Boolean) -> Unit,
    onClickAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    onClickNavigateToBangumiDev: () -> Unit,
    onAuthorizeByToken: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
    layoutParams: WizardLayoutParams = WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass),
) {
    SettingsTab(modifier) {
        AnimatedContent(
            showTokenAuthorizePage,
            transitionSpec = LocalAniMotionScheme.current.animatedContent.topLevel,
        ) { show ->
            if (!show) {
                DefaultAuthorize(
                    authorizeState = authorizeState,
                    contactActions = contactActions,
                    onClickAuthorize = onClickAuthorize,
                    onClickTokenAuthorize = {
                        onCancelAuthorize()
                        onSetShowTokenAuthorizePage(true)
                    },
                    onSkip = onSkip,
                    onClickCancelAuthorize = onCancelAuthorize,
                    layoutParams = layoutParams,
                )
            } else {
                TokenAuthorize(
                    onClickNavigateToBangumiDev = onClickNavigateToBangumiDev,
                    onAuthorizeByToken = { token ->
                        onAuthorizeByToken(token)
                        onSetShowTokenAuthorizePage(false)
                    },
                    layoutParams = layoutParams,
                )
            }
        }
    }
}

@Composable
private fun SettingsScope.DefaultAuthorize(
    authorizeState: AuthState,
    onClickAuthorize: () -> Unit,
    onClickTokenAuthorize: () -> Unit,
    onClickCancelAuthorize: () -> Unit,
    onSkip: (() -> Unit)?,
    contactActions: @Composable () -> Unit,
    layoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier,
) {
    val motionScheme = LocalAniMotionScheme.current
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(SettingsScope.itemVerticalSpacing),
    ) {
        HeroIcon {
            Icon(
                imageVector = Icons.Default.BangumiNext,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = BangumiNextIconColor,
            )
        }
        Column {
            Column(
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding)
                    .fillMaxWidth(),
            ) {
                Text(
                    "登录后，可以使用收藏、观看进度管理等额外功能",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding)
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
            ) {
                AuthorizeButton(
                    authorizeState,
                    onClick = onClickAuthorize,
                    onClickCancel = onClickCancelAuthorize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                )
                onSkip?.let {
                    TextButton(
                        onClick = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                    ) {
                        Text("跳过")
                    }
                }
                AuthorizeStateText(
                    authorizeState,
                    modifier = Modifier.padding(
                        horizontal = layoutParams.descHorizontalPadding,
                        vertical = 8.dp,
                    ),
                    animatedVisibilityMotionScheme = motionScheme.animatedVisibility,
                )
            }
        }
        AuthorizeHelpQA(
            onClickTokenAuthorize = onClickTokenAuthorize,
            contactActions = contactActions,
            layoutParams = layoutParams,
            Modifier.padding(top = 36.dp),
        )
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthorizeButton(
    authorizeState: AuthState,
    onClick: () -> Unit,
    onClickCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable RowScope.() -> Unit = remember(authorizeState) {
        {
            AnimatedContent(
                targetState = authorizeState,
                transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
            ) {
                when (authorizeState) {
                    is AuthState.NotAuthed, is AuthState.Error -> {
                        Text("登录 / 注册")
                    }

                    is AuthState.AwaitingResult -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 3.dp,
                            )
                            when (authorizeState) {
                                is AuthState.AwaitingToken -> {
                                    Text("正在等待登录结果")
                                }

                                is AuthState.AwaitingUserInfo -> {
                                    Text("正在获取用户信息")
                                }
                            }
                        }
                    }

                    is AuthState.Success -> {
                        Text(if (authorizeState.isGuest) "登录 / 注册" else "登录其他账号")
                    }
                }
            }
        }
    }

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val awaitingResult = authorizeState is AuthState.AwaitingResult
        if (authorizeState is AuthState.Success && !authorizeState.isGuest) OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            content = content,
        ) else Button(
            onClick = onClick,
            enabled = authorizeState !is AuthState.AwaitingResult,
            modifier = Modifier.weight(1f),
            content = content,
            shape = if (awaitingResult) SplitButtonDefaults.leadingButtonShapes().shape else ButtonDefaults.shape,
        )
        AniAnimatedVisibility(
            visible = awaitingResult,
            enter = LocalAniMotionScheme.current.animatedVisibility.rowEnter,
            exit = LocalAniMotionScheme.current.animatedVisibility.rowExit,
        ) {
            FilledTonalButton(
                onClick = onClickCancel,
                content = { Text("取消") },
                shape = SplitButtonDefaults.trailingButtonShapes().shape,
            )
        }
    }
}

@Composable
private fun AuthorizeStateText(
    authorizeState: AuthState,
    modifier: Modifier = Modifier,
    animatedVisibilityMotionScheme: AnimatedVisibilityMotionScheme = LocalAniMotionScheme.current.animatedVisibility,
) {

    AnimatedVisibility(
        visible = authorizeState.isKnownLoggedIn || authorizeState is AuthState.Error,
        enter = animatedVisibilityMotionScheme.columnEnter,
        exit = animatedVisibilityMotionScheme.columnExit,
        modifier = modifier,
    ) {
        Text(
            remember(authorizeState) {
                when (authorizeState) {
                    is AuthState.NotAuthed, is AuthState.AwaitingResult -> ""
                    is AuthState.Success -> "已登录: ${authorizeState.username}"
                    is AuthState.NetworkError -> "登录失败：网络错误，请重试"
                    is AuthState.TokenExpired -> "登录失败：Token 已过期，请重新授权"
                    is AuthState.UnknownError -> "登录失败：未知错误，请重试\n" + authorizeState.message
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (authorizeState) {
                is AuthState.Success -> MaterialTheme.colorScheme.primary
                is AuthState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Stable
private enum class HelpOption {
    BANGUMI_DESC,
    WEBSITE_BLOCKED,
    BANGUMI_REGISTER_CHOOSE,
    REGISTER_TYPE_WRONG_CAPTCHA,
    CANT_RECEIVE_REGISTER_EMAIL,
    REGISTER_ACTIVATION_FAILED,
    LOGIN_SUCCESS_NO_RESPONSE,
    OTHERS,
}

@Composable
private fun renderHelpOptionTitle(option: HelpOption): String {
    return when (option) {
        HelpOption.BANGUMI_DESC -> "Bangumi 是什么"
        HelpOption.WEBSITE_BLOCKED -> "浏览器提示网站被屏蔽或禁止访问"
        HelpOption.BANGUMI_REGISTER_CHOOSE -> "注册时应该选择哪一项"
        HelpOption.REGISTER_TYPE_WRONG_CAPTCHA -> "注册或登录时一直提示验证码错误"
        HelpOption.CANT_RECEIVE_REGISTER_EMAIL -> "无法收到邮箱验证码"
        HelpOption.REGISTER_ACTIVATION_FAILED -> "注册时一直激活失败"
        HelpOption.LOGIN_SUCCESS_NO_RESPONSE -> "网页显示登录成功后没有反应"
        HelpOption.OTHERS -> "其他问题"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun renderHelpOptionContent(
    option: HelpOption,
    onClickTokenAuthorize: () -> Unit,
    contactActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (option) {
            HelpOption.BANGUMI_DESC -> {

                Text(
                    "Bangumi 番组计划 是一个中文互联网的 ACGN 内容分享与交流网站，致力于提供一个轻松便捷独特的交流与沟通环境。\n" +
                            "Bangumi 提供了番剧索引、番剧收藏、追番进等功能，Ani 可以将你的观看记录同步至 Bangumi。",
                )
            }

            HelpOption.WEBSITE_BLOCKED -> {
                Text("请在系统设置中更换默认浏览器，推荐按使用 Google Chrome，Microsoft Edge 或 Mozilla Firefox 浏览器")
            }

            HelpOption.BANGUMI_REGISTER_CHOOSE -> {
                Text("管理 ACG 收藏与收视进度，分享交流")
            }

            HelpOption.REGISTER_TYPE_WRONG_CAPTCHA -> {
                Text("如果没有验证码的输入框，可以尝试多点几次密码输入框，如果输错了验证码，需要刷新页面再登录")
            }


            HelpOption.CANT_RECEIVE_REGISTER_EMAIL -> {
                Text("请检查垃圾箱，并且尽可能使用常见邮箱注册，例如 QQ, 网易, Outlook")
            }


            HelpOption.REGISTER_ACTIVATION_FAILED -> {
                Text("删除激活码的最后一个字，然后手动输入删除的字，或更换其他浏览器")
            }

            HelpOption.LOGIN_SUCCESS_NO_RESPONSE -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("可以尝试使用")
                    TextButton(
                        onClick = onClickTokenAuthorize,
                        modifier = Modifier.heightIn(ButtonDefaults.XSmallContainerHeight),
                        contentPadding = ButtonDefaults.XSmallContentPadding,
                    ) {
                        Text(
                            "令牌登录",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(ButtonDefaults.XSmallIconSpacing))
                        Icon(
                            Icons.AutoMirrored.Default.OpenInNew,
                            contentDescription = "Use token login",
                            modifier = Modifier.size(ButtonDefaults.XSmallIconSize), // Text size of medium body
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            HelpOption.OTHERS -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("无法解决你的问题？还可以通过以下渠道获取帮助")
                    contactActions()
                }
            }
        }
    }
}

@Composable
private fun SettingsScope.AuthorizeHelpQA(
    onClickTokenAuthorize: () -> Unit,
    contactActions: @Composable () -> Unit,
    layoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier,
) {
    var currentSelected by rememberSaveable { mutableStateOf<HelpOption?>(null) }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                "帮助",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            HelpOption.entries.forEachIndexed { index, option ->
                ExpandableHelpItem(
                    title = {
                        Text(
                            renderHelpOptionTitle(option),
                            fontWeight = if (currentSelected == option) FontWeight.SemiBold else null,
                        )
                    },
                    content = {
                        renderHelpOptionContent(
                            option,
                            onClickTokenAuthorize,
                            contactActions,
                            modifier = Modifier.ifThen(option != HelpOption.LOGIN_SUCCESS_NO_RESPONSE) {
                                Modifier.padding(vertical = 16.dp)
                            },
                        )
                    },
                    expanded = currentSelected == option,
                    showDivider = index != HelpOption.entries.lastIndex,
                    onClick = { currentSelected = if (currentSelected == option) null else option },
                )
            }
        }
    }
}

@Composable
fun SettingsScope.ExpandableHelpItem(
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    expanded: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    motionScheme: AniMotionScheme = LocalAniMotionScheme.current,
    layoutParams: WizardLayoutParams = WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass),
) {
    Column(modifier) {
        TextItem(
            title = {
                ProvideTextStyle(MaterialTheme.typography.titleMedium, title)
            },
            action = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
        )
        AnimatedVisibility(
            expanded,
            modifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding)
                .fillMaxWidth(),
            enter = motionScheme.animatedVisibility.columnEnter,
            exit = motionScheme.animatedVisibility.columnExit,
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = layoutParams.horizontalPadding))
        }
    }
}

@Composable
private fun SettingsScope.TokenAuthorize(
    onClickNavigateToBangumiDev: () -> Unit,
    onAuthorizeByToken: (String) -> Unit,
    layoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier,
) {
    var token by rememberSaveable { mutableStateOf("") }

    Group(
        modifier = modifier,
        title = { Text("令牌 (token) 登录指南") },
    ) {
        TextItem(
            icon = { TokenAuthorizeStepIcon(1) },
            title = { Text("登录 Bangumi 开发者后台") },
            description = { Text("点击跳转到 Bangumi 开发后台，使用邮箱登录") },
            action = {
                IconButton(onClickNavigateToBangumiDev) {
                    Icon(Icons.Rounded.ArrowOutward, null)
                }
            },
            modifier = Modifier.clickable(onClick = onClickNavigateToBangumiDev),
        )
        TextItem(
            icon = { TokenAuthorizeStepIcon(2) },
            title = { Text("创建令牌 (token)") },
            description = { Text("任意名称，有效期 365 天") },
        )
        TextItem(
            icon = { TokenAuthorizeStepIcon(3) },
            title = { Text("复制令牌到下方输入框中") },
        )
        Column(
            modifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("令牌 (token)") },
            )
            Button(
                onClick = { onAuthorizeByToken(token) },
                enabled = token.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
            ) {
                Text("授权登录")
            }
        }
    }
}

// has fixed size
@Composable
private fun TokenAuthorizeStepIcon(
    step: Int
) {
    Surface(
        modifier = Modifier.size(36.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}