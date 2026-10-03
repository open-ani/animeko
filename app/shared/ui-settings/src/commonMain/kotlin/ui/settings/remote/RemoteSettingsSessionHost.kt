/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.settings.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.remote_settings_connect
import me.him188.ani.app.ui.lang.remote_settings_connecting
import me.him188.ani.app.ui.lang.remote_settings_continue
import me.him188.ani.app.ui.lang.remote_settings_device
import me.him188.ani.app.ui.lang.remote_settings_exit
import me.him188.ani.app.ui.lang.remote_settings_exit_confirm
import me.him188.ani.app.ui.lang.remote_settings_exit_description
import me.him188.ani.app.ui.lang.remote_settings_exit_title
import me.him188.ani.app.ui.lang.remote_settings_saving
import me.him188.ani.app.ui.lang.settings
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import org.jetbrains.compose.resources.stringResource

/** 设置及其编辑子页共享目标提示；子页返回保持会话，根页返回确认退出。 */
@Composable
fun RemoteSettingsSessionHost(
    vm: RemoteSettingsViewModel,
    onNavigateBack: () -> Unit,
    guardExit: Boolean = false,
    exitEnabled: Boolean = true,
    content: @Composable (Modifier, requestExit: () -> Unit) -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    val requestExit = { if (vm.isConnected && guardExit) confirmExit = true else onNavigateBack() }
    BackHandler(enabled = vm.isConnected && guardExit, onBack = requestExit)
    val session = vm.remoteSession
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(session, lifecycle) {
        if (session != null)
            lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.refreshRemoteWhileVisible()
            }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (session != null) {
                val busy by session.busy.collectAsStateWithLifecycle()
                val error by session.error.collectAsStateWithLifecycle()
                Box(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(
                            AniWindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ElevatedCard(
                        Modifier.padding(16.dp)
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .testTag("settings-remote-bubble"),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 保存状态占用图标的位置, 气泡高度不随保存变化.
                            Box(Modifier.padding(end = 12.dp).size(24.dp)) {
                                if (busy) {
                                    val saving = stringResource(Lang.remote_settings_saving)
                                    CircularProgressIndicator(
                                        Modifier.matchParentSize().semantics {
                                            contentDescription = saving
                                        },
                                        strokeWidth = 2.dp,
                                    )
                                } else Icon(Icons.Outlined.Tv, null)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        Lang.remote_settings_device,
                                        session.device.deviceName,
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                error?.let {
                                    Text(
                                        stringResource(it.messageResource()),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            TextButton(onNavigateBack, enabled = !busy && exitEnabled) {
                                Text(stringResource(Lang.remote_settings_exit))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        content(Modifier.padding(padding).fillMaxSize(), requestExit)
    }
    if (vm.isConnecting)
        AlertDialog(
            onDismissRequest = vm::cancelConnection,
            title = { Text(stringResource(Lang.remote_settings_connect)) },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(Lang.remote_settings_connecting))
                }
            },
            confirmButton = {
                TextButton(vm::cancelConnection) {
                    Text(stringResource(Lang.settings_danmaku_cancel))
                }
            },
        )
    if (confirmExit)
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(Lang.remote_settings_exit_title)) },
            text = { Text(stringResource(Lang.remote_settings_exit_description)) },
            confirmButton = {
                TextButton(
                    {
                        confirmExit = false
                        onNavigateBack()
                    },
                    Modifier.testTag("remote-settings-confirm-exit"),
                ) {
                    Text(stringResource(Lang.remote_settings_exit_confirm))
                }
            },
            dismissButton = {
                TextButton({ confirmExit = false }) {
                    Text(stringResource(Lang.remote_settings_continue))
                }
            },
        )
    vm.message?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            title = { Text(stringResource(Lang.settings)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton({ vm.message = null }) {
                    Text(stringResource(Lang.settings_danmaku_confirm))
                }
            },
        )
    }
}
