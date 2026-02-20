/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.log

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.buildconfig.AndroidBuildConfig
import java.io.File


@Composable
internal actual fun ColumnScope.PlatformLoggingItems(listItemColors: ListItemColors) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    ListItem(
        headlineContent = { Text("分享当日日志文件") },
        Modifier.clickable {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.setType("text/plain") // Set appropriate MIME type
            shareIntent.putExtra(
                Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(
                    context,
                    AndroidBuildConfig.APP_APPLICATION_ID + ".fileprovider",
                    context.getCurrentLogFile(),
                ),
            )
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(shareIntent, "分享日志文件"))
        },
        colors = listItemColors,
    )

    ListItem(
        headlineContent = { Text("复制当日日志内容 (很大)") },
        Modifier.clickable {
            scope.launch {
                clipboard.setClipEntryText(context.getCurrentLogFile().readText())
            }
        },
        colors = listItemColors,
    )
}

// Used also in AniApplication
fun Context.getLogsDir(): File {
    // /data/data/0/me.him188.ani/files/logs/
    val logs = applicationContext.filesDir.resolve("logs")
    if (!logs.exists()) {
        logs.mkdirs()
    }
    return logs
}

internal fun Context.getCurrentLogFile(): File {
    return getLogsDir().resolve("app.log")
}
