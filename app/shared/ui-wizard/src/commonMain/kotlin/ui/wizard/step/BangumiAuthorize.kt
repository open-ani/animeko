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
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.theme.BangumiNextIconColor
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.rendering.BangumiNext
import me.him188.ani.app.ui.wizard.HeroIconDefaults
import me.him188.ani.app.ui.wizard.HeroIconScaffold
import me.him188.ani.app.ui.wizard.WizardLayoutParams
import me.him188.ani.utils.platform.isAndroid

@Stable
sealed class AuthorizeUIState {
    sealed class Initial : AuthorizeUIState()

    @Immutable
    data object Placeholder : Initial()
    data object Idle : Initial()

    @Stable
    data class AwaitingResult(val requestId: String) : AuthorizeUIState()

    @Stable
    data class Error(val requestId: String, val message: String) : AuthorizeUIState()

    @Stable
    data class Success(val username: String, val avatarUrl: String?) : AuthorizeUIState()
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

@Suppress("UnusedReceiverParameter")
@Composable
private fun SettingsScope.DefaultAuthorize(
    authorizeState: AuthorizeUIState,
    onClickAuthorize: () -> Unit,
    onClickHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(SettingsScope.itemVerticalSpace),
    ) {
        HeroIconScaffold(
            icon = {
                Icon(
                    imageVector = Icons.Default.BangumiNext,
                    contentDescription = null,
                    modifier = Modifier.size(HeroIconDefaults.iconSize),
                    tint = BangumiNextIconColor,
                )
            },
            content = {
                AnimatedVisibility(
                    visible = authorizeState is AuthorizeUIState.Success,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Row(
                        modifier = Modifier
                            .ifThen(authorizeState !is AuthorizeUIState.Success) {
                                Modifier.alpha(0f)
                            },
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarImage(
                            url = (authorizeState as? AuthorizeUIState.Success)?.avatarUrl,
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "授权成功",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                (authorizeState as? AuthorizeUIState.Success)?.username ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                modifier = Modifier
                                    .widthIn(max = 80.dp)
                                    .basicMarquee(),
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(HeroIconDefaults.contentPadding())
                .fillMaxWidth(),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
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
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bangumi 注册提示",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val currentPlatform = LocalPlatform.current
                remember {
                    buildList {
                        add("请使用常见邮箱注册，例如 QQ, 网易, Outlook")
                        add("如果提示激活失败，请尝试删除激活码的最后一个字再手动输入")
                        if (currentPlatform.isAndroid()) {
                            add("如果浏览器提示网站被屏蔽或登录成功后无法跳转，请尝试在系统设置更换默认浏览器")
                        }
                    }
                }.forEach {
                    RegisterTip(it, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onClickAuthorize,
                    enabled = authorizeState !is AuthorizeUIState.AwaitingResult,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    AnimatedContent(authorizeState) {
                        when (authorizeState) {
                            is AuthorizeUIState.Initial, is AuthorizeUIState.Error -> {
                                Text("启动浏览器授权")
                            }

                            is AuthorizeUIState.AwaitingResult -> {
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

                            is AuthorizeUIState.Success -> {
                                Text("重新授权其他账号")
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = authorizeState is AuthorizeUIState.Error,
                    enter = fadeIn(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text(
                        "授权登录失败: ${(authorizeState as? AuthorizeUIState.Error)?.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(
                    onClick = onClickHelp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Text("无法登录？点击获取帮助")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Stable
private enum class HelpOption {
    TOKEN_AUTH, MORE_HELP
}

@Composable
private fun renderHelpOptionTitle(option: HelpOption): String {
    return when (option) {
        HelpOption.TOKEN_AUTH -> "我有账号，但无法授权登录"
        HelpOption.MORE_HELP -> "我没有账号，也无法注册账号"
    }
}

@Composable
private fun TokenAuthorizeStep(
    step: Int,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "$step",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                text()
            }
        }
        content()
    }
}

@Composable
private fun TokenAuthorizeHelp(
    modifier: Modifier = Modifier,
    onClickNavigateToBangumiDev: () -> Unit,
    onAuthorizeViaToken: (String) -> Unit,
) {
    var token by rememberSaveable { mutableStateOf("") }
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Text("可以尝试使用令牌登录，请按如下步骤操作")
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TokenAuthorizeStep(1, { Text("前往 Bangumi 开发者测试页面") }) {
                    Button(
                        onClickNavigateToBangumiDev,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("点击打开浏览器")
                    }
                }
                TokenAuthorizeStep(2, { Text("如果提示输入邮箱 (Email), 请使用你的 Bangumi 账号登录") }) { }
                TokenAuthorizeStep(3, { Text("创建一个令牌 (token), 名称随意, 有效期 365 天") }) { }
                TokenAuthorizeStep(4, { Text("复制创建好的 token, 粘贴至下方输入框，点击授权登录") }) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("粘贴 token 到此处") },
                        )
                        Button(
                            onClick = { onAuthorizeViaToken(token) },
                            enabled = token.isNotBlank(),
                        ) {
                            Text("授权登录")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreHelp(
    contactActions: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "如果有其他问题，可加群获取帮助或在 GitHub 上提交 issue",
            style = MaterialTheme.typography.bodyMedium,
        )
        contactActions()
    }
}

@Composable
private fun SettingsScope.AlternativeHelp(
    contactActions: @Composable () -> Unit,
    onClickBack: () -> Unit,
    onClickNavigateToBangumiDev: () -> Unit,
    onAuthorizeViaToken: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentSelected by rememberSaveable { mutableStateOf<HelpOption?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            TextButton(
                onClick = onClickBack,
                Modifier,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    Icons.Rounded.ArrowBackIosNew,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("返回授权页", softWrap = false)
            }
        }
        Text(
            "你遇到了什么问题？",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            HelpOption.entries.forEach { option ->
                TextItem(
                    title = {
                        Text(
                            renderHelpOptionTitle(option),
                            fontWeight = if (currentSelected == option) FontWeight.SemiBold else null,
                        )
                    },
                    action = {
                        Icon(
                            Icons.Default.ArrowBackIosNew,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(if (currentSelected == option) 90f else -90f),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentSelected = if (currentSelected == option) null else option },
                )
                AnimatedVisibility(currentSelected == option) {
                    when (option) {
                        HelpOption.TOKEN_AUTH -> TokenAuthorizeHelp(
                            onAuthorizeViaToken = onAuthorizeViaToken,
                            onClickNavigateToBangumiDev = onClickNavigateToBangumiDev,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )

                        HelpOption.MORE_HELP -> MoreHelp(
                            contactActions = contactActions,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun BangumiAuthorize(
    authorizeState: AuthorizeUIState,
    contactActions: @Composable () -> Unit,
    onClickAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    onRefreshAuthorizeStatus: () -> Unit,
    onClickNavigateToBangumiDev: () -> Unit,
    onAuthorizeViaToken: (String) -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: WizardLayoutParams = WizardLayoutParams.Default
) {
    var showHelpPage by remember { mutableStateOf(false) }

    SettingsTab(modifier) {
        AnimatedContent(
            showHelpPage,
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90))
                    .togetherWith(fadeOut(animationSpec = tween(90)))
            },
        ) {
            if (it) AlternativeHelp(
                contactActions = contactActions,
                onClickBack = {
                    showHelpPage = false
                    onRefreshAuthorizeStatus()
                },
                onClickNavigateToBangumiDev = onClickNavigateToBangumiDev,
                onAuthorizeViaToken = { token ->
                    onAuthorizeViaToken(token)
                    showHelpPage = false
                },
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding),
            ) else DefaultAuthorize(
                authorizeState = authorizeState,
                onClickAuthorize = onClickAuthorize,
                onClickHelp = {
                    onCancelAuthorize()
                    showHelpPage = true
                },
                modifier = Modifier
                    .padding(horizontal = layoutParams.horizontalPadding),
            )
        }
    }
}