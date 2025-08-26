/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import java.awt.Desktop
import java.io.File

object DesktopFileRevealer : FileRevealer {
    private val logger = logger<DesktopFileRevealer>()
    override suspend fun revealFile(file: SystemPath): Boolean {
        return revealFile(file.toFile())
    }

    /**
     * 在 Windows 资源管理器或 macOS Finder 中打开文件所在目录, 并高亮该文件
     */
    suspend fun revealFile(file: File): Boolean {
        if (highlightFile(file)) return true

        return try {
            withContext(Dispatchers.IO) {
                if (file.isDirectory) {
                    Desktop.getDesktop().open(file)
                } else {
                    Desktop.getDesktop().open(file.parentFile)
                }
            }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to reveal file: $file" }
            false
        }
    }

    private suspend fun highlightFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext false
        }

        try {
            when (currentPlatform()) {
                is Platform.Windows -> {
                    // Windows
                    val command = listOf("explorer.exe", "/select,", file.absolutePath)
                    ProcessBuilder(command).start()
                    true
                }

                is Platform.MacOS -> {
                    // macOS
                    val command = listOf("open", "-R", file.absolutePath)
                    ProcessBuilder(command).start()
                    true
                }

                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }
}