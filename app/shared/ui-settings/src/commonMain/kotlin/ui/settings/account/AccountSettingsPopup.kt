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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium

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
    mediumSizeMaxWidth: Dp = 360.dp,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
) {
    val state by vm.stateFlow.collectAsStateWithLifecycle()
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    if (windowSizeClass.isWidthAtLeastMedium) {
        AccountSettingsPopupMedium(
            onDismiss = onDismiss,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            maxWidth = mediumSizeMaxWidth,
        ) {
            AccountSettingsPopupLayout(
                state,
                onClickLogin = onNavigateToLogin,
                onClickEditAvatar = onNavigateToAccountSettings,
                onClickEditProfile = onNavigateToAccountSettings,
                onClickSettings = onNavigateToSettings,
                { showLogoutDialog = true },
                modifier.padding(bottom = 16.dp),
            )
        }
    } else {
        AccountSettingsPopupCompact(
            onDismiss = onDismiss,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            maxWidth = mediumSizeMaxWidth,
        ) {
            AccountSettingsPopupLayout(
                state,
                onClickLogin = onNavigateToLogin,
                onClickEditAvatar = onNavigateToAccountSettings,
                onClickEditProfile = onNavigateToAccountSettings,
                onClickSettings = onNavigateToSettings,
                { showLogoutDialog = true },
                modifier.padding(vertical = 16.dp),
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
private fun AccountSettingsPopupMedium(
    onDismiss: () -> Unit,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = DrawerDefaults.scrimColor,
    maxWidth: Dp = 360.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                    .windowInsetsPadding(AniWindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp),
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
                                .padding(top = 4.dp)
                                .fillMaxWidth(),
                        )

                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSettingsPopupCompact(
    onDismiss: () -> Unit,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    maxWidth: Dp = 360.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true,
        ),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = maxWidth),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
        ) {
            Column {
                content()
            }
        }
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

