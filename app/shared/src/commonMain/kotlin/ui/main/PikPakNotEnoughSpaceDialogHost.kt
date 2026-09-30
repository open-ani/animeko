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
import me.him188.ani.torrent.pikpak.PikPakNotEnoughSpaceException
import org.jetbrains.compose.resources.stringResource

/**
 * 云盘剩余空间放不下要秒传的文件时弹出. 每个账号只报一次, 见 PikPakAccount.requireRoomFor.
 */
@Composable
internal fun PikPakNotEnoughSpaceDialogHost(events: Flow<PikPakNotEnoughSpaceException>) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(events) {
        events.collect { shown = true }
    }
    if (shown) {
        AlertDialog(
            onDismissRequest = { shown = false },
            title = { Text(stringResource(Lang.pikpak_not_enough_space_title)) },
            text = { Text(stringResource(Lang.pikpak_not_enough_space_message)) },
            confirmButton = {
                TextButton(onClick = { shown = false }) {
                    Text(stringResource(Lang.pikpak_not_enough_space_ok))
                }
            },
        )
    }
}
