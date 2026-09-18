/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.toFile
import java.util.zip.ZipInputStream

internal actual suspend fun extractZipEntryByExtension(
    archive: SystemPath,
    extension: String,
    target: SystemPath,
): Boolean = withContext(Dispatchers.IO_) {
    val suffix = ".$extension"
    ZipInputStream(archive.toFile().inputStream().buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: return@withContext false
            if (!entry.isDirectory && entry.name.endsWith(suffix, ignoreCase = true)) {
                target.toFile().outputStream().buffered().use { output ->
                    zip.copyTo(output)
                }
                return@withContext true
            }
            zip.closeEntry()
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }
}

internal actual suspend fun markExecutable(file: SystemPath) {
    withContext(Dispatchers.IO_) {
        file.toFile().setExecutable(true, false)
    }
}
