/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.oauth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.AniMotionScheme
import me.him188.ani.app.ui.foundation.animation.AnimatedVisibilityMotionScheme
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.icons.BangumiNextIconColor
import me.him188.ani.app.ui.foundation.widgets.HeroIcon
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem

sealed interface AuthState {
    data class LoggedInAni(val bound: Boolean) : Idle

    data object NoAniAccount : Idle

    sealed interface Idle : AuthState

    data object AwaitingResult : AuthState

    data object Success : AuthState
    class Failed(val error: LoadError, val loggedIn: Boolean) : AuthState
}

@Composable
fun BangumiAuthorizeLayout(
    authorizeState: AuthState,
    contactActions: @Composable () -> Unit,
    onClickAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    SettingsTab(modifier) {
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
                        .fillMaxWidth(),
                ) {
                    Text(
                        "授权 Bangumi 账号，可以同步你的观看记录到 Bangumi 或便捷登录 Ani",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                ) {
                    AuthorizeButton(
                        authorizeState,
                        onClick = onClickAuthorize,
                        onClickCancel = onCancelAuthorize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                    )
                    AuthorizeStateText(
                        authorizeState,
                        modifier = Modifier.padding(vertical = 8.dp),
                        animatedVisibilityMotionScheme = motionScheme.animatedVisibility,
                    )
                }
            }
            AuthorizeHelpQA(
                contactActions = contactActions,
                scrollState = scrollState,
                Modifier.padding(top = 36.dp),
            )
        }
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
                when (it) {
                    is AuthState.LoggedInAni -> {
                        Text("绑定 Bangumi 账号")
                    }

                    is AuthState.Idle, is AuthState.Failed -> {
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
                            Text("正在等待结果")
                        }
                    }

                    is AuthState.Success -> {
                        Text("已授权")
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
        if (authorizeState is AuthState.Success) {
            OutlinedButton(
                onClick = onClick,
                enabled = false,
                modifier = Modifier.weight(1f),
                content = content,
            )
        } else {
            Button(
                onClick = onClick,
                enabled = !awaitingResult,
                modifier = Modifier.weight(1f),
                content = content,
                shape = if (awaitingResult) SplitButtonDefaults.leadingButtonShapes().shape else ButtonDefaults.shape,
            )
        }
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
        visible = authorizeState is AuthState.Success || authorizeState is AuthState.Failed,
        enter = animatedVisibilityMotionScheme.columnEnter,
        exit = animatedVisibilityMotionScheme.columnExit,
        modifier = modifier,
    ) {
        Text(
            when (authorizeState) {
                is AuthState.Failed -> renderLoadErrorMessage(authorizeState.error)
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (authorizeState) {
                is AuthState.Success -> MaterialTheme.colorScheme.primary
                is AuthState.Failed -> MaterialTheme.colorScheme.error
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
        HelpOption.OTHERS -> "其他问题"
    }
}

@Composable
private fun renderHelpOptionContent(
    option: HelpOption,
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
    contactActions: @Composable () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var currentSelected by rememberSaveable { mutableStateOf<HelpOption?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Column(
            modifier = Modifier.padding(top = 8.dp),
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
                    content = { renderHelpOptionContent(option, contactActions) },
                    expanded = currentSelected == option,
                    showDivider = index != HelpOption.entries.lastIndex,
                    onClick = { 
                        val wasExpanded = currentSelected == option
                        currentSelected = if (wasExpanded) null else option
                        if (!wasExpanded) {
                            scope.launch {
                                // 帮助栏展开自动下拉以展示内容
                                delay(100)
                                scrollState.animateScrollBy(300f)
                            }
                        }
                    },
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
            modifier = Modifier.fillMaxWidth(),
            enter = motionScheme.animatedVisibility.columnEnter,
            exit = motionScheme.animatedVisibility.columnExit,
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
        }
        if (showDivider) {
            HorizontalDivider()
        }
    }
}
