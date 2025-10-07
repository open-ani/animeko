/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlinx.io.IOException
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toKotlinInstant

object JvmLogHelper {
    @Throws(IOException::class)
    fun deleteOldLogs(logsFolder: Path) {
        val now = Clock.System.now()
        logsFolder.run {
            if (isDirectory()) {
                listDirectoryEntries()
            } else emptyList()
        }.forEach { file ->
            if (file.extension == "log" && (file.name.startsWith("app") || file.name.startsWith("cef-"))
                && now - file.getLastModifiedTime().toInstant().toKotlinInstant() > 3.days
            ) {
                file.deleteIfExists()
            }
        }
    }
}
