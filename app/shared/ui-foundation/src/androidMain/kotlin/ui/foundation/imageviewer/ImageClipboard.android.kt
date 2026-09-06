/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import android.content.ClipData
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import me.him188.ani.utils.io.absolutePath
import java.io.File

/**
 * 以 content URI 共享导出的副本 (需要 FileProvider 暴露 `cache/image-viewer/`, 见 app/android 的 file_paths.xml).
 */
@Composable
actual fun rememberImageClipboard(): ImageClipboard? {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    return remember(context, clipboard) { AndroidImageClipboard(context.applicationContext, clipboard) }
}

private class AndroidImageClipboard(
    private val context: Context,
    private val clipboard: Clipboard,
) : ImageClipboard {
    override suspend fun copy(file: ImageViewerExportedFile) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(file.path.absolutePath),
        )
        clipboard.setClipEntry(ClipEntry(ClipData.newUri(context.contentResolver, file.fileName, uri)))
    }
}
