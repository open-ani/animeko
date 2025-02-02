/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.feedback.ErrorDialogHost
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.profile.BangumiOAuthViewModel
import org.koin.mp.KoinPlatform

@Composable
fun BangumiOAuthScreen(
    vm: BangumiOAuthViewModel,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val nav = LocalNavigator.current
    if (!vm.needAuth) {
        SideEffect {
            nav.popUntilNotAuth()
        }
    }
    BangumiOAuthPage(
        vm,
        onClickTokenAuth = {
            nav.navigateBangumiTokenAuth()
        },
        modifier,
        windowInsets = windowInsets,
        navigationIcon,
    )
}

@Composable
private fun Hint(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Lightbulb, null)
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Box(Modifier.padding(top = 2.dp)) {
                content()
            }
        }
    }
}

/**
 * bangumi 授权
 */
@Composable
fun BangumiOAuthPage(
    vm: BangumiOAuthViewModel,
    onClickTokenAuth: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier
            .systemBarsPadding()
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = navigationIcon,
                actions = {},
                title = { Text(text = "Bangumi 授权") },
                colors = AniThemeDefaults.topAppBarColors(),
                windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            )
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { contentPadding ->
        var showHelp by rememberSaveable {
            mutableStateOf(false)
        }

        LaunchedEffect(true) {
            vm.doCheckResult()
        }

        LazyColumn(
            modifier = Modifier.fillMaxHeight().padding(contentPadding).padding(all = 16.dp),
            // 居中
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            item {
                Column(
                    Modifier.animateItem(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        """追番进度管理服务由 Bangumi 提供""",
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Text("""Bangumi 番组计划 是一个中文 ACGN 互联网分享与交流项目，不提供资源下载。""")

                            Text(
                                """登录 Bangumi 账号方可使用收藏、记录观看进度等功能。""",
                            )
                        }
                    }

                    Text("Bangumi 注册提示", style = MaterialTheme.typography.titleLarge)

                    Hint { Text("""建议使用常见邮箱，例如 QQ, 网易, Outlook""") }
                    Hint { Text("""如果提示激活失败，尝试删除激活码的最后一个字再手动输入""") }

                    key(vm.requestId) {
//                        val launcherState = rememberExternalBrowserLauncherState()

                        val context = LocalContext.current
                        Column(
                            Modifier.padding(top = 8.dp).fillParentMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.titleMedium) {
//                                if (launcherState.isFailed) {
//                                    SideEffect { showHelp = true }
//                                    Text(text = "启动浏览器失败", color = MaterialTheme.colorScheme.error)
//                                } else {
                                Text(text = "请在浏览器中完成授权")
//                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    KoinPlatform.getKoin().get<BrowserNavigator>().openBrowser(context, vm.oauthUrl)
                                },
                                Modifier.fillMaxWidth(),
//                                enabled = !launcherState.isLaunching,
                            ) {
                                Text(text = "启动浏览器")
                            }
                            if (!showHelp) {
                                TextButton({ showHelp = true }, Modifier.fillMaxWidth()) {
                                    Text("无法登录？点击获取帮助")
                                }
                            }
                        }
                    }
                }
            }
            if (showHelp) {
                item {
                    Column(
                        Modifier.animateItem(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HorizontalDivider()

                        Text("获取帮助", style = MaterialTheme.typography.titleLarge)

                        Text("登录失败？可尝试令牌登录", style = MaterialTheme.typography.bodyMedium)

                        OutlinedButton(
                            onClick = onClickTokenAuth,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("令牌登录")
                        }

                        Hint { Text("""如果有其他问题，可加群获取帮助或在 GitHub 上提交 issue""") }

                        AniContactList()
                    }
                }
            }
        }

        ErrorDialogHost(
            vm.authError,
            onConfirm = {
                vm.dismissError()
            },
        )
    }
}
