/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.settings.account.AccountLogoutDialog
import me.him188.ani.app.ui.settings.account.AccountSettingsState
import me.him188.ani.app.ui.settings.account.AccountSettingsViewModel
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.user.SelfInfoUiState

@Composable
fun SettingsScope.AccountSettingsGroup(
    vm: AccountSettingsViewModel,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass,
) {
    val state by vm.state.collectAsStateWithLifecycle(initialValue = AccountSettingsState.Empty)
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (windowSizeClass.isWidthCompact)
                Alignment.CenterHorizontally else Alignment.Start,
        ) {
            AvatarImage(
                url = state.selfInfo.selfInfo?.avatarUrl,
                Modifier
                    .padding(vertical = 16.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .placeholder(state.selfInfo.isLoading),
            )

            AccountInfo(
                state.selfInfo,
                state.boundBangumi,
                onClickLogin = onNavigateToLogin,
                onClickLogout = { showLogoutDialog = true },
                onClickEditProfile = { },
            )
        }
    }

    if (showLogoutDialog) {
        AccountLogoutDialog(
            {
                vm.logout()
                showLogoutDialog = false
            },
            onCancel = { showLogoutDialog = false },
        )
    }
}

@Composable
private fun AccountInfo(
    selfInfo: SelfInfoUiState,
    boundBangumi: Boolean,
    onClickLogin: () -> Unit,
    onClickLogout: () -> Unit,
    onClickEditProfile: () -> Unit,
) {
    val currentInfo = remember(selfInfo) { selfInfo.selfInfo }
    val isLogin = remember(selfInfo) { selfInfo.isSessionValid == true }

    Column(
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        if (isLogin) {
            if (currentInfo != null) {
                CopiableSingleLineText("ID", currentInfo.id.toString())
                CopiableSingleLineText("昵称", currentInfo.nickname.takeIf { it.isNotBlank() } ?: "未设置")
                CopiableSingleLineText("邮箱", currentInfo.email ?: "未设置")
                CopiableSingleLineText("Bangumi ID", boundBangumi.toString())
            } else if (selfInfo.isLoading) {
                Text("加载中...", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("加载失败", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text("未登录", style = MaterialTheme.typography.bodyMedium)
        }
    }

    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!isLogin) {
            Button(
                onClick = onClickLogin,
                modifier = Modifier,
            ) {
                Text("登录")
            }
        } else {
            Button(
                onClick = onClickEditProfile,
                modifier = Modifier,
            ) {
                Text("编辑资料")
            }
            Button(
                onClick = onClickLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier,
            ) {
                Text("登出")
            }
        }
    }
}

@Composable
private fun CopiableSingleLineText(
    title: String,
    content: String,
    modifier: Modifier = Modifier.padding(vertical = 2.dp),
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val clipboardManager = LocalClipboardManager.current
    val toaster = LocalToaster.current

    val text = remember(title, content) { "$title: $content" }

    Text(
        text,
        style = style,
        modifier = modifier.clickable {
            clipboardManager.setText(AnnotatedString(content))
            toaster.toast("已复制到剪切板: $content")
        },
        maxLines = 1,
        textAlign = TextAlign.Start,
        overflow = TextOverflow.MiddleEllipsis,
    )
}