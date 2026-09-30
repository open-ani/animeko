/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.pikpak_not_enough_space_message
import me.him188.ani.app.ui.lang.pikpak_not_enough_space_ok
import me.him188.ani.app.ui.lang.pikpak_not_enough_space_title
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.torrent.pikpak.PikPakNotEnoughSpaceException
import org.jetbrains.compose.resources.stringResource

/**
 * 云盘剩余空间放不下要秒传的文件时弹出. 播放可能已经回退到本地 BT, 这里只负责告诉用户为什么没走云盘.
 */
@Composable
internal fun PikPakNotEnoughSpaceDialogHost(events: Flow<PikPakNotEnoughSpaceException>) {
    var shown by remember { mutableStateOf<PikPakNotEnoughSpaceException?>(null) }
    LaunchedEffect(events) {
        events.collect { shown = it }
    }
    shown?.let { failure ->
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { Text(stringResource(Lang.pikpak_not_enough_space_title)) },
            text = {
                Text(
                    stringResource(
                        Lang.pikpak_not_enough_space_message,
                        failure.fileName,
                        failure.neededBytes.bytes.toString(),
                        failure.freeBytes.bytes.toString(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { shown = null }) {
                    Text(stringResource(Lang.pikpak_not_enough_space_ok))
                }
            },
        )
    }
}
