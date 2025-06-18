/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.LoadErrorCardLayout
import me.him188.ani.app.ui.search.LoadErrorCardRole
import me.him188.ani.app.ui.settings.account.AccountLogoutDialog
import me.him188.ani.app.ui.settings.account.AccountSettingsState
import me.him188.ani.app.ui.settings.account.AccountSettingsViewModel
import me.him188.ani.app.ui.settings.account.EditProfileState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.user.SelfInfoUiState

@Composable
fun SettingsScope.AccountSettingsGroup(
    vm: AccountSettingsViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToBangumiOAuth: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass,
) {
    val state by vm.state.collectAsStateWithLifecycle(initialValue = AccountSettingsState.Empty)
    var showLogoutDialog by remember { mutableStateOf(false) }

    val motionScheme = LocalAniMotionScheme.current
    var editingProfile by rememberSaveable { mutableStateOf(false) }

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

            AnimatedContent(
                editingProfile,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .animateContentSize(),
                transitionSpec = motionScheme.animatedContent.standard,
            ) { editing ->
                if (!editing) {
                    AccountInfo(
                        state.selfInfo,
                        state.boundBangumi,
                        onClickLogin = onNavigateToLogin,
                        onClickLogout = { showLogoutDialog = true },
                        onClickEditProfile = { editingProfile = true },
                        onClickBindBangumi = onNavigateToBangumiOAuth,
                        onClickBindEmail = onNavigateToLogin,
                    )
                } else {
                    EditProfile(
                        state.selfInfo.selfInfo?.nickname ?: "",
                        avatarUploadState = state.avatarUploadState,
                        onSave = {
                            editingProfile = false
                            vm.saveProfile(it)
                        },
                        onCancel = {
                            editingProfile = false
                            vm.refreshState()
                        },
                        onCheckUsername = { vm.validateUsername(it) },
                        onUploadAvatar = { vm.uploadAvatar(it) },
                        onResetAvatarUploadState = { vm.resetAvatarUploadState() },
                    )
                }
            }
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
private fun SettingsScope.AccountInfo(
    selfInfo: SelfInfoUiState,
    boundBangumi: Boolean,
    onClickLogin: () -> Unit,
    onClickLogout: () -> Unit,
    onClickEditProfile: () -> Unit,
    onClickBindBangumi: () -> Unit,
    onClickBindEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentInfo = remember(selfInfo) { selfInfo.selfInfo }
    val isLogin = remember(selfInfo) { selfInfo.isSessionValid == true }

    Column(modifier) {
        if (isLogin) {
            if (currentInfo != null) {
                UserProfileItem("昵称", currentInfo.nickname.takeIf { it.isNotBlank() } ?: "未设置")
                UserProfileItem("邮箱", currentInfo.email ?: "未设置")
                UserProfileItem("用户 ID", currentInfo.id.toString())
                if (boundBangumi && currentInfo.bangumiUsername != null) {
                    UserProfileItem("Bangumi 用户名", currentInfo.bangumiUsername ?: "")
                }
            } else if (selfInfo.isLoading) {
                TextItem {
                    Text("加载中...")
                }
            } else {
                TextItem(
                    icon = {
                        ProvideContentColor(MaterialTheme.colorScheme.error) {
                            Icon(Icons.Default.Warning, contentDescription = "Load failed")
                        }
                    },
                ) {
                    ProvideContentColor(MaterialTheme.colorScheme.error) {
                        Text("加载失败")
                    }
                }
            }
        } else {
            TextItem {
                Text("未登录")
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
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
                FilledTonalButton(
                    onClick = onClickEditProfile,
                    modifier = Modifier,
                ) {
                    Text("编辑资料")
                }
                if (!boundBangumi) {
                    FilledTonalButton(
                        onClick = onClickBindBangumi,
                        modifier = Modifier,
                    ) {
                        Text("绑定 Bangumi")
                    }
                }
                if (selfInfo.selfInfo?.email == null) {
                    FilledTonalButton(
                        onClick = onClickBindEmail,
                        modifier = Modifier,
                    ) {
                        Text("绑定邮箱")
                    }
                }
                Button(
                    onClick = onClickLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier,
                ) {
                    Text("退出登录")
                }
            }
        }
    }
}

@Composable
private fun SettingsScope.EditProfile(
    initialUsername: String,
    avatarUploadState: EditProfileState.UploadAvatarState,
    onSave: (EditProfileState) -> Unit,
    onCancel: () -> Unit,
    onCheckUsername: (String) -> Boolean,
    onUploadAvatar: (PlatformFile) -> Unit,
    modifier: Modifier = Modifier,
    onResetAvatarUploadState: () -> Unit = {},
) {
    var username by rememberSaveable { mutableStateOf(initialUsername) }

    val filePicker = rememberFilePickerLauncher(
        type = FileKitType.Image,
        title = "选择头像",
    ) {
        it?.let { file ->
            onUploadAvatar(file)
        }
    }

    val saveEnabled by remember {
        derivedStateOf {
            onCheckUsername(username)
        }
    }

    Column(modifier) {
        TextItem(
            title = { Text("选择头像") },
            description = { Text("仅支持 jpg, png 和 webp 格式, 长宽限制 1000x1000") },
            onClick = {
                onResetAvatarUploadState()
                filePicker.launch()
            },
        )

        AniAnimatedVisibility(
            avatarUploadState is EditProfileState.UploadAvatarState.Uploading ||
                    avatarUploadState is EditProfileState.UploadAvatarState.Failed,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            when (avatarUploadState) {
                is EditProfileState.UploadAvatarState.Uploading -> {
                    LoadErrorCardLayout(LoadErrorCardRole.Neural) {
                        ListItem(
                            leadingContent = { CircularProgressIndicator(Modifier.size(24.dp)) },
                            headlineContent = { Text("上传中...") },
                            colors = listItemColors,
                        )
                    }
                }

                is EditProfileState.UploadAvatarState.Failed -> {
                    LoadErrorCard(
                        avatarUploadState.loadError,
                        onRetry = { onUploadAvatar(avatarUploadState.file) },
                    )
                }

                else -> {}
            }
        }

        TextFieldItem(
            username,
            title = { Text("昵称") },
            description = {
                Text("最大 20 字符，只能包含中文、日文、英文、数字和下划线，或留空清除昵称")
            },
            onValueChangeCompleted = { username = it },
            isErrorProvider = { !onCheckUsername(it) },
            sanitizeValue = { it.trim() },
            placeholder = { Text("未设置") },
        )

        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(onCancel) {
                Text("抛弃并返回")
            }
            FilledTonalButton(
                {
                    onSave(EditProfileState(initialUsername))
                },
                enabled = saveEnabled,
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun SettingsScope.UserProfileItem(
    title: String,
    content: String,
    modifier: Modifier = Modifier.padding(vertical = 2.dp),
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val clipboardManager = LocalClipboardManager.current
    val toaster = LocalToaster.current

    TextItem(
        title = {
            Text(
                title,
                color = MaterialTheme.colorScheme.secondary,
            )
        },
        description = {
            Text(
                content,
                style = style,
                maxLines = 1,
                textAlign = TextAlign.Start,
                overflow = TextOverflow.MiddleEllipsis,
            )
        },
        onClick = {
            clipboardManager.setText(AnnotatedString(content))
            toaster.toast("已复制到剪切板: $content")
        },
        modifier = modifier,
    )
}