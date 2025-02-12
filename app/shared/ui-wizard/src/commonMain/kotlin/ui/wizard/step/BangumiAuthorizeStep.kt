/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import me.him188.ani.app.domain.session.AuthStateNew
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AnimatedVisibilityMotionScheme
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.icons.BangumiNextIconColor
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.wizard.HeroIcon
import me.him188.ani.app.ui.wizard.WizardLayoutParams
import me.him188.ani.utils.platform.isAndroid

@Composable
internal fun BangumiAuthorizeStep(
    authorizeState: AuthStateNew,
    showTokenAuthorizePage: Boolean,
    contactActions: @Composable () -> Unit,
    onSetShowTokenAuthorizePage: (Boolean) -> Unit,
    onClickAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    onClickNavigateToBangumiDev: () -> Unit,
    onAuthorizeViaToken: (String) -> Unit,
    layoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier,
    onScrollToTop: () -> Unit = { }
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
                        onScrollToTop()
                        onSetShowTokenAuthorizePage(true)
                    },
                    layoutParams = layoutParams,
                )
            } else {
                TokenAuthorize(
                    onClickNavigateToBangumiDev = onClickNavigateToBangumiDev,
                    onAuthorizeViaToken = { token ->
                        onAuthorizeViaToken(token)
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
    authorizeState: AuthStateNew,
    onClickAuthorize: () -> Unit,
    onClickTokenAuthorize: () -> Unit,
    contactActions: @Composable () -> Unit,
    layoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier,
) {
    val motionScheme = LocalAniMotionScheme.current
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(SettingsScope.itemVerticalSpacing),
    ) {
        HeroIcon(layoutParams) {
            Icon(
                imageVector = Icons.Default.BangumiNext,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = BangumiNextIconColor,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Ani 的追番进度管理服务由 Bangumi 提供",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Bangumi 番组计划 是一个中文 ACGN 互联网分享与交流项目，不提供资源下载。" +
                            "登录 Bangumi 账号方可使用收藏、记录观看进度等功能。",
                    modifier = Modifier.padding(horizontal = layoutParams.descHorizontalPadding),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Bangumi 注册提示",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier.padding(horizontal = layoutParams.descHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val currentPlatform = LocalPlatform.current
                    remember {
                        buildList {
                            add("尽可能使用常见邮箱注册，例如 QQ, 网易, Outlook")
                            add("如果提示激活失败，请尝试删除激活码的最后一个字，然后手动输入删除的字")
                            if (currentPlatform.isAndroid()) {
                                add("如果浏览器提示网站被屏蔽或登录成功后无法跳转，请尝试在系统设置中更换默认浏览器")
                            }
                        }
                    }.forEach {
                        RegisterTip(it, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding)
                    .fillMaxWidth(),
            ) {
                AuthorizeButton(
                    authorizeState,
                    onClick = onClickAuthorize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                )
                AuthorizeStateText(
                    authorizeState,
                    modifier = Modifier.padding(
                        horizontal = layoutParams.descHorizontalPadding,
                        vertical = 8.dp,
                    ),
                    animatedVisibilityMotionScheme = motionScheme.animatedVisibility,
                )
            }
            Spacer(Modifier.height(8.dp))
            AuthorizeHelpQA(
                onClickTokenAuthorize = onClickTokenAuthorize,
                contactActions = contactActions,
                layoutParams = layoutParams,
            )
        }
    }
}

@Composable
private fun RegisterTip(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AuthorizeButton(
    authorizeState: AuthStateNew,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable RowScope.() -> Unit = remember(authorizeState) {
        {
            AnimatedContent(
                targetState = authorizeState,
                transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
            ) {
                when (authorizeState) {
                    is AuthStateNew.Initial, is AuthStateNew.Error -> {
                        Text("启动浏览器授权")
                    }

                    is AuthStateNew.AwaitingResult -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 3.dp,
                            )
                            Text("等待授权结果")
                        }
                    }

                    is AuthStateNew.Success -> {
                        Text("重新授权其他账号")
                    }
                }
            }
        }
    }

    if (authorizeState is AuthStateNew.Success) OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
    ) else Button(
        onClick = onClick,
        enabled = authorizeState !is AuthStateNew.AwaitingResult,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun AuthorizeStateText(
    authorizeState: AuthStateNew,
    modifier: Modifier = Modifier,
    animatedVisibilityMotionScheme: AnimatedVisibilityMotionScheme = LocalAniMotionScheme.current.animatedVisibility,
) {

    AnimatedVisibility(
        visible = authorizeState is AuthStateNew.Success || authorizeState is AuthStateNew.Error,
        enter = animatedVisibilityMotionScheme.columnEnter,
        exit = animatedVisibilityMotionScheme.columnExit,
        modifier = modifier,
    ) {
        Text(
            remember(authorizeState) {
                when (authorizeState) {
                    is AuthStateNew.Initial, is AuthStateNew.AwaitingResult -> ""
                    is AuthStateNew.Success -> {
                        if (authorizeState.isGuest) {
                            "使用游客模式"
                        } else {
                            "授权登录成功: ${authorizeState.username}"
                        }
                    }

                    is AuthStateNew.Error -> "授权登录失败: ${authorizeState.message}"
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (authorizeState) {
                is AuthStateNew.Success -> MaterialTheme.colorScheme.primary
                is AuthStateNew.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Stable
private enum class HelpOption {
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
        HelpOption.WEBSITE_BLOCKED -> "浏览器提示网站被屏蔽或禁止访问"
        HelpOption.BANGUMI_REGISTER_CHOOSE -> "注册时应该选择哪一项"
        HelpOption.REGISTER_TYPE_WRONG_CAPTCHA -> "注册或登录时一直提示验证码错误"
        HelpOption.CANT_RECEIVE_REGISTER_EMAIL -> "无法收到邮箱验证码"
        HelpOption.REGISTER_ACTIVATION_FAILED -> "注册时一直激活失败"
        HelpOption.LOGIN_SUCCESS_NO_RESPONSE -> "网页显示登录成功后没有反应"
        HelpOption.OTHERS -> "其他问题"
    }
}

@Composable
private fun SettingsScope.AuthorizeHelpQA(
    onClickTokenAuthorize: () -> Unit,
    contactActions: @Composable () -> Unit,
    layoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier,
) {
    val motionScheme = LocalAniMotionScheme.current
    var currentSelected by rememberSaveable { mutableStateOf<HelpOption?>(null) }

    Column(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding, vertical = 16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                "帮助",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            val contentModifier = Modifier
                .padding(horizontal = layoutParams.horizontalPadding, vertical = 8.dp)
                .fillMaxWidth()
            HelpOption.entries.forEachIndexed { index, option ->
                TextItem(
                    title = {
                        Text(
                            renderHelpOptionTitle(option),
                            fontWeight = if (currentSelected == option) FontWeight.SemiBold else null,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    action = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier
                                .rotate(if (currentSelected == option) 180f else 0f),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentSelected = if (currentSelected == option) null else option },
                )
                AnimatedVisibility(
                    currentSelected == option,
                    enter = motionScheme.animatedVisibility.columnEnter,
                    exit = motionScheme.animatedVisibility.columnExit,
                ) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        when (option) {
                            HelpOption.WEBSITE_BLOCKED ->
                                Text(
                                    "请在系统设置中更换默认浏览器，推荐按使用 Google Chrome，Microsoft Edge " +
                                            "或 Mozilla Firefox 浏览器",
                                    contentModifier,
                                )
                            HelpOption.BANGUMI_REGISTER_CHOOSE ->
                                Text("管理 ACG 收藏与收视进度，分享交流", contentModifier)

                            HelpOption.REGISTER_TYPE_WRONG_CAPTCHA ->
                                Text(
                                    "如果没有验证码的输入框，可以尝试多点几次密码输入框，" +
                                            "如果输错了验证码，需要刷新页面再登录",
                                    contentModifier,
                                )

                            HelpOption.CANT_RECEIVE_REGISTER_EMAIL ->
                                Text("请检查垃圾箱，并且尽可能使用常见邮箱注册，例如 QQ, 网易, Outlook", contentModifier)

                            HelpOption.REGISTER_ACTIVATION_FAILED ->
                                Text("删除激活码的最后一个字，然后手动输入删除的字，或更换其他浏览器", contentModifier)

                            HelpOption.LOGIN_SUCCESS_NO_RESPONSE -> Row(contentModifier) {
                                Text("可以尝试使用")
                                Text(
                                    "令牌登录",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable(onClick = onClickTokenAuthorize),
                                )
                            }
                            
                            HelpOption.OTHERS -> Column(
                                contentModifier,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("无法解决你的问题？还可以通过以下渠道获取帮助")
                                contactActions()
                            }
                        }
                    }
                }
                if (index != HelpOption.entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = layoutParams.horizontalPadding))
                }
            }
        }
    }
}

@Composable
private fun SettingsScope.TokenAuthorize(
    onClickNavigateToBangumiDev: () -> Unit,
    onAuthorizeViaToken: (String) -> Unit,
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
                onClick = { onAuthorizeViaToken(token) },
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