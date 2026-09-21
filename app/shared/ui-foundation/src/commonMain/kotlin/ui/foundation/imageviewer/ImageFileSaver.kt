/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.readBytes

/**
 * 把查看器里的图片保存到用户选择的位置.
 */
fun interface ImageFileSaver {
    /**
     * @return `true` 表示已保存; `false` 表示用户取消. 失败时抛出异常.
     */
    suspend fun save(file: ImageViewerExportedFile): Boolean
}

/**
 * 弹出系统"另存为"对话框 (FileKit), 把 [ImageViewerExportedFile] 复制到用户选择的位置.
 */
class FileKitImageFileSaver(
    private val dialogSettings: FileKitDialogSettings = FileKitDialogSettings.createDefault(),
) : ImageFileSaver {
    override suspend fun save(file: ImageViewerExportedFile): Boolean {
        val target = FileKit.openFileSaver(
            suggestedName = file.baseName,
            extension = file.extension,
            dialogSettings = dialogSettings,
        ) ?: return false
        withContext(Dispatchers.IO_) {
            target.write(file.path.readBytes())
        }
        return true
    }
}

@Composable
fun rememberFileKitImageFileSaver(): ImageFileSaver = remember { FileKitImageFileSaver() }
