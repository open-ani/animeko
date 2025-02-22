/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

@Composable
fun SelfAvatar(
    authState: AuthState,
    selfInfo: UserInfo?,
    size: DpSize, // = DpSize(48.dp, 48.dp)
    onClickLogin: () -> Unit,
    onClickRetryRefreshSession: () -> Unit,
    modifier: Modifier = Modifier,
    handler: SelfAvatarActionHandler = rememberSelfAvatarActionHandler(),
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    Box {
        Surface({ showMenu = true }, modifier, shape = CircleShape) {
            if (authState.isLoading || authState.isKnownLoggedIn) {
                // 加载中时展示 placeholder
                AvatarImage(
                    url = selfInfo?.avatarUrl,
                    Modifier.size(size).clip(CircleShape).placeholder(selfInfo == null),
                )
            } else {
                if (authState.isKnownGuest) {
                    TextButton(onClickLogin) {
                        Text("登录")
                    }
                } else {
                    SessionTipsIcon(
                        state = authState,
                        onLogin = onClickLogin,
                        onRetry = onClickRetryRefreshSession,
                        showLabel = false,
                    )
                }
            }
        }

        DropdownMenu(
            showMenu,
            offset = DpOffset(x = 0.dp, y = 8.dp),
            onDismissRequest = { showMenu = false },
        ) {
            SelfAvatarMenus(handler, onClickAny = { showMenu = false })
        }
    }
}

@Stable
interface SelfAvatarActionHandler {
    fun onClickSettings()
    suspend fun onLogout()
}

private class DefaultSelfAvatarActionHandler(
    private val navigator: AniNavigator,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : SelfAvatarActionHandler, KoinComponent {
    private val sessionManager: SessionManager by inject()
    override fun onClickSettings() {
        navigator.navigateSettings()
    }

    override suspend fun onLogout() {
        withContext(dispatcher) {
            sessionManager.clearSession()
        }
    }
}

@Composable
fun rememberSelfAvatarActionHandler(): SelfAvatarActionHandler {
    val navigator = LocalNavigator.current
    return remember(navigator) { DefaultSelfAvatarActionHandler(navigator) }
}

@Composable
private fun SelfAvatarMenus(
    handler: SelfAvatarActionHandler,
    onClickAny: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("设置") },
        onClick = {
            handler.onClickSettings()
            onClickAny()
        },
        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
    )

    val logoutTasker = rememberUiMonoTasker()
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    val running by logoutTasker.isRunning.collectAsStateWithLifecycle()
    DropdownMenuItem(
        text = { Text("退出登录", color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
        onClick = { showLogoutConfirmation = true },
        enabled = !running,
    )
    if (showLogoutConfirmation) {
        AlertDialog(
            { showLogoutConfirmation = false },
            text = { Text("确定要退出登录吗?") },
            confirmButton = {
                TextButton(
                    {
                        logoutTasker.launch {
                            handler.onLogout()
                            onClickAny()
                        }
                        showLogoutConfirmation = false
                    },
                ) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showLogoutConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
}
