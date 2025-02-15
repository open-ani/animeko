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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.utils.platform.currentTimeMillis
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.days

@Stable
class BangumiTokenAuthViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()

    fun onCancel(reason: String? = null) {
        sessionManager.processingRequest.value?.cancel(CancellationException(reason))
    }

    suspend fun authByToken(token: String) {
        sessionManager.setSession(AccessTokenSession(token, currentTimeMillis() + 365.days.inWholeMilliseconds))
    }

    override fun onCleared() {
        super.onCleared()
        onCancel("BangumiTokenAuthPage BackHandler")
    }
}

@Composable
fun BangumiTokenAuthScreen(
    vm: BangumiTokenAuthViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    var token by rememberSaveable { mutableStateOf("") }
    val navigator = LocalNavigator.current
    BackHandler {
        navigator.popUntilNotAuth()
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Scaffold(
            Modifier.widthIn(max = 600.dp),
            topBar = {
                TopAppBar(
                    title = { Text("Bangumi 授权") },
                    navigationIcon = navigationIcon,
                    colors = AniThemeDefaults.topAppBarColors(),
                    windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                )
            },
            contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        ) { contentPadding ->
            Column(
                Modifier.padding(contentPadding).padding(16.dp).widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("1. 你将前往 Bangumi 开发者测试页面")
                Text("2. 如果提示输入邮箱 (Email), 请使用你的 Bangumi 账号登录")
                Text("3. 创建一个令牌 (token), 名称随意, 有效期 365 天")
                Text("4. 复制创建好的 token, 回到本页面")

                val context = LocalContext.current
                Button(
                    {
                        KoinPlatform.getKoin().get<BrowserNavigator>()
                            .openBrowser(context, "https://next.bgm.tv/demo/access-token/create")
                    },
                    Modifier.align(Alignment.End),
                ) {
                    Text("前往创建令牌")
                }

                Text("在下方粘贴你刚刚复制的 token")

                val onTokenChange = { it: String -> token = it }
                val clipboard by rememberUpdatedState(LocalClipboardManager.current)
                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    label = { Text("Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        IconButton(
                            {
                                clipboard.getText()?.text?.let {
                                    onTokenChange(it.trim())
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.ContentPaste, "粘贴")
                        }
                    },
                )
                val tokenNotEmpty by remember {
                    derivedStateOf {
                        token.isNotEmpty()
                    }
                }

                Button(
                    onClick = {
                        vm.onCancel("BangumiTokenAuthPage 用令牌登录")
                        vm.launchInBackground {
                            authByToken(token)
                            withContext(Dispatchers.Main) {
                                navigator.popUntilNotAuth()
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = tokenNotEmpty,
                ) {
                    Text("用令牌登录")
                }
            }
        }
    }
}

