/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import me.him188.ani.app.platform.BuildConfig
import me.him188.ani.app.platform.LocalContext
import java.io.File


@Composable
internal actual fun ColumnScope.PlatformDebugInfoItems() {
    val context = LocalContext.current
    FilledTonalButton(
        {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.setType("text/plain") // Set appropriate MIME type
            shareIntent.putExtra(
                Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(
                    context,
                    BuildConfig.APP_APPLICATION_ID + ".fileprovider",
                    context.getCurrentLogFile(),
                ),
            )
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(shareIntent, "分享日志文件"))
        },
    ) {
        Text("分享当日日志文件")
    }

    val clipboard = LocalClipboardManager.current
    FilledTonalButton(
        {
            clipboard.setText(AnnotatedString(context.getCurrentLogFile().readText()))
        },
    ) {
        Text("复制当日日志内容 (很大)")
    }

    FilledTonalButton(
        {
            context.applicationContext.cacheDir.resolve("torrent-caches").deleteRecursively()
        },
    ) {
        Text("清除全部下载缓存")
    }
}

fun Context.getLogsDir(): File {
    // /data/data/0/me.him188.ani/files/logs/
    val logs = applicationContext.filesDir.resolve("logs")
    if (!logs.exists()) {
        logs.mkdirs()
    }
    return logs
}

fun Context.getCurrentLogFile(): File {
    return getLogsDir().resolve("app.log")
}