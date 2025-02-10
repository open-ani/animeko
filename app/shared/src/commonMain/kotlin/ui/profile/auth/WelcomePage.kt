/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.icons.GithubMark
import me.him188.ani.app.ui.foundation.icons.QqRoundedOutline
import me.him188.ani.app.ui.foundation.icons.Telegram
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.settings.tabs.AniHelpNavigator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WelcomeViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()
    val authState: AuthState = AuthState()

    fun cancelRequest() {
        sessionManager.processingRequest.value?.cancel("WelcomeViewModel")
    }

    suspend fun logInAsGuest() {
        withContext(Dispatchers.Default) {
            sessionManager.setSession(GuestSession)
        }
    }
}

@Composable
fun WelcomeScene(
    vm: WelcomeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val navigator = LocalNavigator.current
    val goBack = {
        vm.cancelRequest()
        navigator.popUntilNotWelcome()
    }
    BackHandler(onBack = goBack)
    if (vm.authState.isKnownLoggedIn) {
        SideEffect(goBack)
    }
    val scope = rememberCoroutineScope()
    WelcomePage(
        onClickLogin = { vm.authState.launchAuthorize(navigator) },
        onClickGuest = {
            scope.launch {
                vm.logInAsGuest() // needs to be done before goBack. goBack cancels the oauth request.
                goBack()
            }
        },
        modifier = modifier,
        contentWindowInsets = windowInsets,
    )
}

@Composable
fun WelcomePage(
    onClickLogin: () -> Unit,
    onClickGuest: () -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Scaffold(
            Modifier.widthIn(max = 600.dp),
            topBar = {
//            TopAppBar(title = { Text("欢迎") })
            },
            bottomBar = {
                if (showDetails) {
                    Column(Modifier.windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                        HorizontalDivider(Modifier.padding(horizontal = 4.dp))
                        Column(
                            Modifier.padding(all = 16.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilledTonalButton(onClickLogin, Modifier.fillMaxWidth()) {
                                Text("登录 / 注册")
                            }

                            Button(
                                onClick = onClickGuest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("以游客身份免登录进入")
                            }
                            Text(
                                "游客可使用除追番进度管理外的所有功能", Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            },
            contentWindowInsets = contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        ) { contentPadding ->
            var isContentReady by rememberSaveable {
                mutableStateOf(false)
            }
//            if (LocalIsPreviewing.current) {
//                isContentReady = true
//            }
            LaunchedEffect(true) {
                isContentReady = true
            }
            AniAnimatedVisibility(
                isContentReady,
                Modifier.wrapContentSize(),
                // 从中间往上滑
                enter = LocalAniMotionScheme.current.animatedVisibility.screenEnter,
                exit = LocalAniMotionScheme.current.animatedVisibility.screenExit,
            ) {
                LazyColumn(
                    Modifier.padding(contentPadding).widthIn(max = 1000.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    item {
                        Column(Modifier.animateItem()) {
                            Text("欢迎使用 Animeko", style = MaterialTheme.typography.headlineMedium)

                            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                                Row(Modifier.padding(top = 8.dp).align(Alignment.Start)) {
                                    Text(
                                        """一站式在线弹幕追番平台 (简称 Ani)""",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Column(
                                Modifier.padding(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    Text("""Ani 目前由爱好者组成的组织 open-ani 和社区贡献者维护，完全免费，在 GitHub 上开源。""")

                                    Text("""Ani 的目标是提供尽可能简单且舒适的追番体验。""")
                                }
                            }

                            AniContactList()

                            if (!showDetails) {
                                Button({ showDetails = true }, Modifier.padding(top = 16.dp).fillParentMaxWidth()) {
                                    Text("继续")
                                }
                            }
                        }
                    }

                    if (showDetails) {
                        item {
                            Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    "资源来自于网络",
                                    Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                )

                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    Text("""Ani 本身不提供资源保存和下载等服务。Ani 只会从互联网上搜索番剧资源，本质上为一个整合了进度管理、视频播放、弹幕、互联网资源搜索引擎等功能的工具。""")

                                    Text("""Ani 也支持自建的 Jellyfin 等媒体服务。""")
                                }
                                //            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                //                Text("""· 注册时建议使用常见邮箱，例如 QQ, 网易, Outlook""")
                                //                Text("""· 如果提示激活失败，尝试删除激活码的最后一个字再手动输入""")
                                //                Text("""· 如果有其他问题，可加群获取帮助或在 GitHub 上提交 issue""")
                                //            }
                            }

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AniContactList(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.Start),
    ) {
        SuggestionChip(
            { AniHelpNavigator.openGitHubHome(context) },
            icon = {
                Icon(AniIcons.GithubMark, null, Modifier.size(24.dp))
            },
            label = { Text("GitHub") },
        )

        SuggestionChip(
            { AniHelpNavigator.openAniWebsite(context) },
            icon = {
                Icon(
                    Icons.Rounded.Public, null,
                    Modifier.size(24.dp),
                )
            },
            label = { Text("官网") },
        )

        SuggestionChip(
            { AniHelpNavigator.openJoinQQGroup(context) },
            icon = {
                Icon(
                    AniIcons.QqRoundedOutline, null,
                    Modifier.size(24.dp),
                )
            },
            label = { Text("QQ 群") },
        )

        SuggestionChip(
            { AniHelpNavigator.openTelegram(context) },
            icon = {
                Image(
                    AniIcons.Telegram, null,
                    Modifier.size(24.dp),
                )
            },
            label = { Text("Telegram") },
        )
    }
}
