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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_log_copy_today_log_content
import me.him188.ani.app.ui.lang.settings_log_export_failed
import me.him188.ani.app.ui.lang.settings_log_export_file
import me.him188.ani.app.ui.lang.settings_log_export_succeed
import me.him188.ani.app.ui.lang.settings_log_exported
import me.him188.ani.app.ui.lang.settings_log_file_not_found
import me.him188.ani.app.ui.lang.settings_log_share_failed
import me.him188.ani.app.ui.lang.settings_log_share_file
import me.him188.ani.app.ui.lang.settings_log_share_today_log_file
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.io.RandomAccessFile


private val logger = logger("LogTab")

private const val EXPORT_FILE_NAME = "ani-app-log.txt"
private const val EXPORT_MIME_TYPE = "text/plain"

@Composable
internal actual fun ColumnScope.PlatformLoggingItems(listItemColors: ListItemColors) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val supportsFileSharing = LocalAniUiBehavior.current.supportsFileSharing
    val shareTodayLogFileText = stringResource(Lang.settings_log_share_today_log_file)
    val shareLogFileText = stringResource(Lang.settings_log_share_file)
    val exportLogFileText = stringResource(Lang.settings_log_export_file)
    val copyTodayLogContentText = stringResource(Lang.settings_log_copy_today_log_content)
    val logFileNotFoundText = stringResource(Lang.settings_log_file_not_found)
    val shareFailedText = stringResource(Lang.settings_log_share_failed)

    // 让用户自己挑落地位置 —— 插了 U 盘的话系统选择器里就能选到它, 这是把日志拷出电视的唯一办法.
    // 走 SAF 而不是申请存储权限: 从 Android 10 起权限已经给不到「任意路径」, 而 U 盘挂在
    // /mnt/media_rw 下, File API 无论有没有权限都读不到, 只有 DocumentsProvider 能访问.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
    ) { uri ->
        scope.launch {
            val logFile = context.getCurrentLogFile()
            if (uri == null) {
                // 用户取消, 或者设备用占位桩把选择器挡掉了 —— 仍然落到应用外部目录, 至少有个能取的地方.
                // 不记住这个结果: SAF 没有"不再询问", 这次取消不代表下次也不想选, 所以每按一次都重新问.
                exportLogFileToAppDir(context, logFile, toaster)
            } else {
                exportLogFileTo(context, logFile, uri, toaster)
            }
        }
    }

    ListItem(
        headlineContent = { Text(if (supportsFileSharing) shareTodayLogFileText else exportLogFileText) },
        Modifier.clickable {
            val logFile = context.getCurrentLogFile()
            if (!logFile.exists()) {
                toaster.toast(logFileNotFoundText)
                return@clickable
            }
            if (!supportsFileSharing) {
                // 遥控器设备上系统分享面板里没有任何能接文件的应用, 只能落盘让用户自取
                runCatching { exportLauncher.launch(EXPORT_FILE_NAME) }.onFailure {
                    // 设备连 DocumentsUI 都没装, 选择器拉不起来
                    logger.warn(it) { "Failed to launch document picker, falling back to app directory" }
                    scope.launch { exportLogFileToAppDir(context, logFile, toaster) }
                }
                return@clickable
            }
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.setType("text/plain") // Set appropriate MIME type
            shareIntent.putExtra(
                Intent.EXTRA_STREAM,
                // 权限声明是 "${applicationId}.fileprovider", 而电视变体带 applicationIdSuffix ".tv" ——
                // 用编译期常量 AndroidBuildConfig.APP_APPLICATION_ID 在电视上会拼出不存在的 authority
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    logFile,
                ),
            )
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 没有任何接收方时 chooser 自身也可能不存在, startActivity 会抛 ActivityNotFoundException
            runCatching {
                context.startActivity(Intent.createChooser(shareIntent, shareLogFileText))
            }.onFailure {
                logger.warn(it) { "Failed to share log file" }
                toaster.toast(shareFailedText)
            }
        },
        colors = listItemColors,
    )

    ListItem(
        headlineContent = { Text(copyTodayLogContentText) },
        Modifier.clickable {
            val logFile = context.getCurrentLogFile()
            if (!logFile.exists()) {
                toaster.toast(logFileNotFoundText)
                return@clickable
            }
            scope.launch {
                clipboard.setClipEntryText(readLogTailForClipboard(logFile))
            }
        },
        colors = listItemColors,
    )
}

/**
 * 剪贴板里最多放这么多日志.
 *
 * 日志配的是 root level=TRACE + 只按天滚动 (没有体积上限), 边看 BT 边跑一天的 app.log 到几十 MB 很常见,
 * 而 `ClipboardManager.setPrimaryClip` 走 Binder, 事务超过约 1MB 会抛 `TransactionTooLargeException`.
 * 只留尾部: 出问题的那段一定在最后.
 */
private const val CLIPBOARD_LOG_MAX_BYTES = 256L * 1024

/**
 * 读 app.log 的尾部, 最多 [CLIPBOARD_LOG_MAX_BYTES].
 *
 * 必须 `withContext(Dispatchers.IO)`: 调用点是 `rememberCoroutineScope().launch`, 它的调度器是主线程,
 * 直接 `readText()` 等于在主线程上一次性读进几十 MB (低端盒子上就是 ANR).
 */
private suspend fun readLogTailForClipboard(logFile: File): String = withContext(Dispatchers.IO) {
    val length = logFile.length()
    if (length <= CLIPBOARD_LOG_MAX_BYTES) {
        return@withContext logFile.readText()
    }
    RandomAccessFile(logFile, "r").use { file ->
        file.seek(length - CLIPBOARD_LOG_MAX_BYTES)
        val bytes = ByteArray(CLIPBOARD_LOG_MAX_BYTES.toInt())
        file.readFully(bytes)
        // 截断点多半落在某一行中间: 从第一个换行之后开始, 顺带丢掉被切成两半的多字节字符
        val start = bytes.indexOf('\n'.code.toByte()) + 1
        val header = "(truncated: last ${CLIPBOARD_LOG_MAX_BYTES / 1024} KB of ${length / 1024} KB)\n"
        header + String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }
}

/**
 * 把日志写进用户在系统选择器里选定的位置 (可以是 U 盘).
 */
private suspend fun exportLogFileTo(context: Context, logFile: File, target: Uri, toaster: Toaster) {
    runCatching {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target)?.use { output ->
                logFile.inputStream().use { it.copyTo(output) }
            } ?: error("Failed to open output stream for $target")
        }
    }.fold(
        // 位置是用户自己挑的, 不用再告诉他在哪
        onSuccess = { toaster.toast(getString(Lang.settings_log_exported)) },
        onFailure = {
            logger.warn(it) { "Failed to export log file to $target" }
            toaster.toast(getString(Lang.settings_log_export_failed))
        },
    )
}

/**
 * 兜底: 把日志复制到应用自己的外部存储目录 (`/sdcard/Android/data/<包名>/files/logs/`), toast 出落地路径.
 *
 * 选这个位置是因为它不需要任何存储权限, 而文件管理器、adb pull、插 U 盘的电脑都能读到 ——
 * 用户拿不出选择器时至少还有个能取的地方.
 */
private suspend fun exportLogFileToAppDir(context: Context, logFile: File, toaster: Toaster) {
    runCatching {
        withContext(Dispatchers.IO) {
            val dir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("logs")
            dir.mkdirs()
            // 固定文件名: 反复导出只保留最新一份, 免得在用户看不见的目录里越堆越多
            logFile.copyTo(dir.resolve(EXPORT_FILE_NAME), overwrite = true)
        }
    }.fold(
        onSuccess = { toaster.toast(getString(Lang.settings_log_export_succeed, it.absolutePath)) },
        onFailure = {
            logger.warn(it) { "Failed to export log file to app directory" }
            toaster.toast(getString(Lang.settings_log_export_failed))
        },
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
