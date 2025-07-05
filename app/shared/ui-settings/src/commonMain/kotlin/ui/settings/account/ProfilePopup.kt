/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.rememberAsyncHandler

/**
 * 在右上角显示的个人信息弹窗
 */
@Composable
fun ProfilePopup(
    vm: ProfileViewModel,
    onDismissRequest: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccountSettings: () -> Unit,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass,
) {
    val state by vm.stateFlow.collectAsStateWithLifecycle()
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    val content = @Composable {
        ProfilePopupLayout(
            state,
            onClickLogin = onNavigateToLogin,
            onClickEditAvatar = onNavigateToAccountSettings,
            onClickEditProfile = onNavigateToAccountSettings,
            onClickSettings = onNavigateToSettings,
            { showLogoutDialog = true },
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                .ifThen(windowSizeClass.isWidthAtLeastMedium) {
                    padding(horizontal = 8.dp)
                }
                .ifThen(windowSizeClass.isHeightAtLeastMedium) {
                    padding(vertical = 8.dp)
                },
        )
    }

    if (windowSizeClass.isWidthAtLeastMedium) {
        val density = LocalDensity.current
        Popup(
            alignment = Alignment.TopEnd,
            offset = with(density) {
                IntOffset(0, 32.dp.roundToPx())
            },
            properties = PopupProperties(),
            onDismissRequest = onDismissRequest,
        ) {
            // 模拟点击外面关闭 popup, 否则事件会被广播到下层
            Box(
                Modifier.fillMaxSize()
                    .clickable(interactionSource = null, indication = null, onClick = onDismissRequest)
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.TopEnd,
            ) {

                // 实际内容
                Surface(
                    modifier = Modifier
                        .windowInsetsPadding(AniWindowInsets.safeDrawing)
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 360.dp)
                        .clickable(interactionSource = null, indication = null, onClick = {}), // 避免触发 onDismissRequest
                    shape = MaterialTheme.shapes.extraLarge,
                    color = BottomSheetDefaults.ContainerColor,
                    contentColor = contentColorFor(BottomSheetDefaults.ContainerColor),
                    tonalElevation = 0.dp,
                ) {
                    content()
                }
            }
        }

//                IconButton(
//                    onDismiss,
//                    Modifier.align(Alignment.TopEnd)
//                        .padding(horizontal = 24.dp)
//                        .padding(top = 24.dp),
//                ) {
//                    Icon(Icons.Default.Close, contentDescription = "关闭")
//                }
    } else {
        BasicAlertDialog(onDismissRequest) {
            Surface(
                modifier = Modifier,
                shape = MaterialTheme.shapes.extraLarge,
                color = BottomSheetDefaults.ContainerColor,
                contentColor = contentColorFor(BottomSheetDefaults.ContainerColor),
                tonalElevation = 0.dp,
            ) {
                content()
            }
        }
    }

    if (showLogoutDialog) {
        val asyncHandler = rememberAsyncHandler()
        AccountLogoutDialog(
            {
                asyncHandler.launch {
                    vm.logout()
                    showLogoutDialog = false
                }
            },
            onCancel = { showLogoutDialog = false },
            confirmEnabled = !asyncHandler.isWorking,
        )
    }
}

@Composable
fun AccountLogoutDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onCancel,
        icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
        text = { Text("确定要退出登录吗?") },
        confirmButton = {
            TextButton(onConfirm, enabled = confirmEnabled) {
                Text("退出登录", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onCancel) {
                Text("取消")
            }
        },
    )
}

