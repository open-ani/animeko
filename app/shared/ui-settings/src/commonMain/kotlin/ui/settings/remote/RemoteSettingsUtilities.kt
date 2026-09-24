/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboard
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.settings.tabs.log.LogFileActions
import me.him188.ani.remote.settings.generated.models.LogSnapshot

@Composable
internal fun ColumnScope.RemoteSettingsLogActions(
    vm: RemoteSettingsViewModel,
    colors: ListItemColors,
) {
    val clipboard = LocalClipboard.current
    val share = rememberShareRemoteLog()
    LogFileActions(
        colors,
        onShare = { vm.fetchRemoteLog { share(it) } },
        onCopy = { vm.fetchRemoteLog { clipboard.setClipEntryText(it.content) } },
        enabled = !vm.isLoadingRemoteLog,
    )
}

@Composable internal expect fun rememberShareRemoteLog(): suspend (LogSnapshot) -> Unit
