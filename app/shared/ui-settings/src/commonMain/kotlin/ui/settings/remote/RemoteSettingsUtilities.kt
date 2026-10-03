/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_log_copy_today_log_content
import me.him188.ani.app.ui.lang.settings_log_copy_too_large
import me.him188.ani.app.ui.lang.settings_log_share_today_log_file
import me.him188.ani.remote.settings.generated.models.LogSnapshot
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.RemoteSettingsLogActions(
    vm: RemoteSettingsViewModel,
    colors: ListItemColors,
) {
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val tooLarge = stringResource(Lang.settings_log_copy_too_large)
    val share = rememberShareRemoteLog()
    ListItem(
        headlineContent = { Text(stringResource(Lang.settings_log_share_today_log_file)) },
        modifier = Modifier.clickable(enabled = !vm.isLoadingRemoteLog) {
            vm.fetchRemoteLog { share(it) }
        },
        colors = colors,
    )
    ListItem(
        headlineContent = { Text(stringResource(Lang.settings_log_copy_today_log_content)) },
        modifier = Modifier.clickable(enabled = !vm.isLoadingRemoteLog) {
            vm.fetchRemoteLog {
                if (it.content.length > MAX_LOG_CLIPBOARD_CHARS) toaster.toast(tooLarge)
                else clipboard.setClipEntryText(it.content)
            }
        },
        colors = colors,
    )
}

/** Android 的剪贴板经 Binder 传输文本, 过大的日志只能通过分享文件导出. */
private const val MAX_LOG_CLIPBOARD_CHARS = 128 * 1024

@Composable internal expect fun rememberShareRemoteLog(): suspend (LogSnapshot) -> Unit
