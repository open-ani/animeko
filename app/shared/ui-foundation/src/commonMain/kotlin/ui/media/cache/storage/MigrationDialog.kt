/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.media.cache.storage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.media.cache.storage.MediaCacheMigrator
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes

@Composable
fun MediaCacheMigrationDialog(
    status: MediaCacheMigrator.Status,
) {
    AlertDialog(
        title = { Text(if (status !is MediaCacheMigrator.Status.Error) "正在迁移缓存" else "迁移发生错误") },
        text = {
            Column {
                Text(renderMigrationStatus(status = status))
                if (status !is MediaCacheMigrator.Status.Error) {
                    Spacer(modifier = Modifier.height(24.dp))
                    if (status is MediaCacheMigrator.Status.Cache) {
                        LinearProgressIndicator(
                            progress = { status.migratedSize.toFloat() / status.totalSize.coerceAtLeast(1L) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("迁移过程中设备可能会轻微卡顿，请不要强制关闭 Ani，否则可能导致闪退")
                }
            }
        },
        onDismissRequest = { /* not dismiss-able */ },
        confirmButton = {
            if (status !is MediaCacheMigrator.Status.Error) return@AlertDialog
            val clipboard = LocalClipboardManager.current
            TextButton(
                {
                    val errorMessage = status.throwable?.stackTraceToString()
                    if (errorMessage != null) {
                        clipboard.setText(AnnotatedString(errorMessage))
                    }
                },
            ) { Text(text = "复制") }
        },
    )
}

@Composable
fun renderMigrationStatus(status: MediaCacheMigrator.Status) = when (status) {
    is MediaCacheMigrator.Status.Init -> "正在准备..."
    is MediaCacheMigrator.Status.Cache -> {
        val type = if (status is MediaCacheMigrator.Status.TorrentCache) " BT " else "在线"
        if (status.currentFile != null)
            "迁移${type}缓存（${status.migratedSize.bytes} / ${status.totalSize.bytes}）:\n${status.currentFile}"
        else "迁移${type}缓存..."
    }

    is MediaCacheMigrator.Status.Metadata -> "合并元数据..."

    is MediaCacheMigrator.Status.Error ->
        """
            迁移时发生错误，将会忽略迁移。
            请进入 APP 设置中将日志反馈到 GitHub。
            
            错误信息:
            ${status.throwable}
        """.trimIndent()
}