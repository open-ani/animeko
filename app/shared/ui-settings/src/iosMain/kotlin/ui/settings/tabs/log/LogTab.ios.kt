/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.copyTo
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.logging.DefaultLoggerFactory
import me.him188.ani.utils.logging.IosLoggingConfigurator
import me.him188.ani.utils.logging.writer.DailyRollingFileLogWriter
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIPopoverArrowDirectionAny
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable
internal actual fun ColumnScope.PlatformLoggingItems(listItemColors: ListItemColors) {
    val toaster = LocalToaster.current
    val uiViewController = LocalUIViewController.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    ListItem(
        headlineContent = {
            Text("分享当日日志文件")
        },
        Modifier.clickable {
            val file = getTodayLogFile()
            if (file == null) {
                toaster.toast("未找到文件")
            } else {
                shareFile(file.inSystem.absolutePath, uiViewController)
            }
        },
        colors = listItemColors,
    )

    ListItem(
        headlineContent = {
            Text("复制当日日志内容 (很大)")
        },
        Modifier.clickable {
            val file = getTodayLogFile()
            if (file == null) {
                toaster.toast("未找到文件")
            } else {
                scope.launch {
                    clipboard.setClipEntryText(file.inSystem.readText())
                }
            }
        },
        colors = listItemColors,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun shareFile(originalPath: String, hostVC: UIViewController) {
    // 1. Guarantee the file is inside our container
    val temp = SystemTemporaryDirectory

    val targetPath = Path(temp, originalPath.substringAfterLast("/").substringBeforeLast(".") + ".txt")
    Path(originalPath).inSystem.copyTo(
        targetPath.inSystem,
    )

    // 2. Build and present the share sheet on the **main queue**
    val activityVC = UIActivityViewController(
        activityItems = listOf(NSURL.fileURLWithPath(targetPath.toString())),
        applicationActivities = null,
    )
    activityVC.popoverPresentationController?.apply {
        sourceView = hostVC.view
        sourceRect = hostVC.view.bounds
        permittedArrowDirections = UIPopoverArrowDirectionAny
    }

    hostVC.presentViewController(activityVC, animated = true, completion = null)
}

private fun getTodayLogFile(): Path? {
    val factory = IosLoggingConfigurator.factory as? DefaultLoggerFactory
    return factory
        ?.writers
        ?.filterIsInstance<DailyRollingFileLogWriter>()
        ?.firstOrNull()
        ?.getTodayLogFile()
}
