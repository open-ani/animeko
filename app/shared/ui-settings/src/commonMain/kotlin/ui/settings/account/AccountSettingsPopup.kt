/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.him188.ani.app.ui.settings.account

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetDialog
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.effects.onPointerEventMultiplatform
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.user.SelfInfoUiState

@Composable
fun AccountSettingsPopup(
    vm: AccountSettingsViewModel,
    onDismiss: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccountSettings: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = DrawerDefaults.scrimColor,
    maxWidth: Dp = 360.dp,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    ModalBottomSheetDialog(
        onDismissRequest = onDismiss,
        properties = ModalBottomSheetProperties(),
        predictiveBackProgress = remember { Animatable(initialValue = 0f) },
    ) {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            ) {
                drawRect(color = scrimColor)
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 24.dp),
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = maxWidth),
                    shape = shape,
                    color = containerColor,
                    contentColor = contentColor,
                    tonalElevation = tonalElevation,
                ) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = { },
                            actions = {
                                IconButton(onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "Close account sheet")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .padding(top = 8.dp)
                                .fillMaxWidth(),
                        )

                        AccountSettingsPopupLayout(
                            state,
                            onClickLogin = onNavigateToLogin,
                            onClickEditAvatar = onNavigateToAccountSettings,
                            onClickEditProfile = onNavigateToAccountSettings,
                            onClickSettings = onNavigateToSettings,
                            { showLogoutDialog = true },
                            modifier,
                        )
                    }
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
fun AccountLogoutDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onCancel,
        icon = { Icon(Icons.AutoMirrored.Outlined.Logout, "Logout dialog icon") },
        text = { Text("确定要退出登录吗?") },
        confirmButton = {
            TextButton(onConfirm) {
                Text("确定", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onCancel) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AccountSettingsPopupLayout(
    state: AccountSettingsState,
    onClickLogin: () -> Unit,
    onClickEditAvatar: () -> Unit,
    onClickEditProfile: () -> Unit,
    onClickSettings: () -> Unit,
    onClickLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLogin = remember(state) { state.selfInfo.isSessionValid == true }
    Column(modifier) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            EditableSelfAvatar(state.selfInfo, onClickEditAvatar)
        }
        Text(
            if (isLogin) {
                state.selfInfo.selfInfo?.nickname?.takeIf { it.isNotBlank() } ?: "无用户名"
            } else {
                "未登录"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(
                    top = 8.dp,
                    bottom = if (isLogin) 2.dp else 8.dp,
                )
                .fillMaxWidth(),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.MiddleEllipsis,
        )
        if (isLogin) {
            Text(
                remember(state) {
                    state.selfInfo.selfInfo?.email ?: "NO EMAIL"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 2.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }

        SettingsTab(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
        ) {
            Column {
                if (isLogin) {
                    TextItem(
                        icon = { Icon(Icons.Outlined.Edit, contentDescription = "Edit profile settings") },
                        onClick = onClickEditProfile,
                    ) {
                        Text("编辑个人资料")
                    }
                } else {
                    TextItem(
                        icon = { Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = "Login") },
                        onClick = onClickLogin,
                    ) {
                        Text("登录 / 注册")
                    }
                }

                TextItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    onClick = onClickSettings,
                ) {
                    Text("设置")
                }

                if (isLogin) {
                    TextItem(
                        icon = {
                            ProvideContentColor(MaterialTheme.colorScheme.error) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Logout",
                                )
                            }
                        },
                        onClick = onClickLogout,
                    ) {
                        ProvideContentColor(MaterialTheme.colorScheme.error) {
                            Text("退出登录")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EditableSelfAvatar(
    selfInfo: SelfInfoUiState,
    onClickEditAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(80.dp, 80.dp),
) {
    var showEditAvatarScrim by remember { mutableStateOf(false) }

    Box(
        modifier
            .size(size)
            .onPointerEventMultiplatform(PointerEventType.Enter) { showEditAvatarScrim = true }
            .onPointerEventMultiplatform(PointerEventType.Exit) { showEditAvatarScrim = false },
    ) {
        AvatarImage(
            url = selfInfo.selfInfo?.avatarUrl,
            Modifier
                .size(size)
                .clip(CircleShape)
                .placeholder(selfInfo.isLoading),
        )
        AniAnimatedVisibility(
            showEditAvatarScrim,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(DrawerDefaults.scrimColor),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    IconButton(onClickEditAvatar) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit avatar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}